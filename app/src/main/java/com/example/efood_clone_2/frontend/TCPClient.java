package com.example.efood_clone_2.frontend;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.efood_clone_2.model.Product;
import com.example.efood_clone_2.model.Store;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TCPClient {
    private static final String TAG = "TCPClient";
    private static final String SERVER_HOST = "10.0.2.2"; // localhost from Android emulator
    private static final int SERVER_PORT = 8080;
    private final Handler mainHandler;
    private final ExecutorService executor;

    public interface StoreListCallback {
        void onStoresReceived(List<Store> stores);
        void onError(String error);
    }

    public interface ResultCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public interface SalesReportCallback {
        void onSalesDataReceived(Map<String, Integer> salesData, int total);
        void onError(String error);
    }

    public TCPClient() {
        mainHandler = new Handler(Looper.getMainLooper());
        executor = Executors.newSingleThreadExecutor();
    }

    public void getNearbyStores(double latitude, double longitude, StoreListCallback callback) {
        executor.execute(() -> {
            try {
                // Connect to the server
                Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Send the request
                String request = String.format("FIND_STORES_WITHIN_RANGE %f,%f", latitude, longitude);
                out.println(request);

                // Read multiple lines from the server
                List<Store> stores = new ArrayList<>();
                String line;
                while ((line = in.readLine()) != null) {
                    // Optional: Stop at a specific end marker, e.g., "END"
                    if (line.equals("END")) break;

                    // Parse each line as a store
                    Store store = parseSingleStore(line);
                    if (store != null) {
                        stores.add(store);
                    }
                }

                // Sort stores by distance
                Collections.sort(stores, (s1, s2) -> Double.compare(s1.getDistance(), s2.getDistance()));

                // Send the result back to the main thread
                mainHandler.post(() -> callback.onStoresReceived(stores));

                // Clean up
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error connecting to server: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
    }

    // Helper method to parse a single store from a line
    private Store parseSingleStore(String storeString) {
        try {
            String[] parts = storeString.split("\\^");
            if (parts.length >= 7) {
                String name = parts[0];
                double lat = Double.parseDouble(parts[1]);
                double lon = Double.parseDouble(parts[2]);
                String category = parts[3].replace("\"", ""); // Remove quotes
                int stars = Integer.parseInt(parts[4]);
                String price = parts[5];
                double distance = Double.parseDouble(parts[6]);

                Store store = new Store(name, lat, lon, category, stars, price);
                store.setDistance(distance);
                return store;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing store: " + storeString + " - " + e.getMessage());
        }
        return null;
    }

    public void getFilteredStores(Map<String, List<String>> filters, double latitude, double longitude, StoreListCallback callback) {
        executor.execute(() -> {
            try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                Log.d(TAG, "Connected to server for filtered stores");

                // Build filter string including coordinates
                StringBuilder filterString = new StringBuilder("FILTER_STORES ");
                // Add coordinates first
                filterString.append(latitude).append(",").append(longitude).append(";");

                // Add the rest of the filters
                for (Map.Entry<String, List<String>> entry : filters.entrySet()) {
                    if (entry.getValue().isEmpty()) continue;

                    filterString.append(entry.getKey()).append(":");
                    for (int i = 0; i < entry.getValue().size(); i++) {
                        filterString.append(entry.getValue().get(i));
                        if (i < entry.getValue().size() - 1) {
                            filterString.append(",");
                        }
                    }
                    filterString.append(";");
                }

                out.println(filterString.toString());
                Log.d(TAG, "Sent filter command: " + filterString);

                // Read response
                List<Store> storeList = new ArrayList<>();
                String line;
                while ((line = in.readLine()) != null && !line.equals("END")) {
                    if (line.equals("No stores found with the specified filters.")) {
                        mainHandler.post(() -> callback.onStoresReceived(new ArrayList<>()));
                        return;
                    }

                    // Use parseSingleStore to create store objects
                    Store store = parseSingleStore(line);
                    if (store != null) {
                        storeList.add(store);
                    }
                }

                // Sort stores by distance
                Collections.sort(storeList, (s1, s2) -> Double.compare(s1.getDistance(), s2.getDistance()));

                mainHandler.post(() -> callback.onStoresReceived(storeList));

            } catch (IOException e) {
                Log.e(TAG, "Error connecting to server: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
    }

    public interface StoreDetailsCallback {
        void onStoreDetailsReceived(Store store);
        void onError(String error);
    }

    public void getStoreDetails(String storeName, StoreDetailsCallback callback) {
        executor.execute(() -> {
            try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                Log.d(TAG, "Requesting store details for: " + storeName);

                // Request store details with GET_STORE_DETAILS command
                out.println("GET_STORE_DETAILS " + storeName);

                // Read the response which should be a JSON store object
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null && !line.equals("END")) {
                    response.append(line);
                }

                String jsonResponse = response.toString();
                Log.d(TAG, "Received store details: " + jsonResponse);

                if (jsonResponse.isEmpty() || jsonResponse.contains("Store not found")) {
                    mainHandler.post(() -> callback.onError("Store details not found: " + storeName));
                    return;
                }

                try {
                    // Parse the JSON into a Store object with products
                    Store store = Store.JsonToStore(jsonResponse);
                    mainHandler.post(() -> callback.onStoreDetailsReceived(store));
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing store JSON: " + e.getMessage(), e);
                    mainHandler.post(() -> callback.onError("Error parsing store data: " + e.getMessage()));
                }

            } catch (IOException e) {
                Log.e(TAG, "Network error getting store details: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });

    }

    // Also add this method to make a purchase
    public void purchaseProduct(String storeName, String productName, int quantity, ResultCallback callback) {
        executor.execute(() -> {
            try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                Log.d(TAG, "Sending purchase request - Store: " + storeName +
                            ", Product: " + productName + ", Quantity: " + quantity);

                // Format: BUY storeName,productName,quantity
                String buyCommand = String.format("BUY %s,%s,%d", storeName, productName, quantity);
                out.println(buyCommand);

                // Read response
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null && !line.equals("END")) {
                    response.append(line).append("\n");
                }

                String result = response.toString().trim();
                Log.d(TAG, "Purchase result: " + result);

                mainHandler.post(() -> callback.onSuccess(result));

            } catch (IOException e) {
                Log.e(TAG, "Error during purchase: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
    }


    public void buy(String compactFormString) {
        // Convert the compact format to a map of store name -> (product name -> quantity)
        Map<String, Map<String, Integer>> purchaseMap = compactToBuyFormat(compactFormString);

        // Debug log
        Log.d(TAG, "Processing purchases from: " + compactFormString);

        // Create a simple callback for purchase results
        ResultCallback callback = new ResultCallback() {
            @Override
            public void onSuccess(String message) {
                Log.d(TAG, "Purchase success: " + message);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Purchase error: " + error);
            }
        };

        // Process each store's purchases
        for (Map.Entry<String, Map<String, Integer>> storeEntry : purchaseMap.entrySet()) {
            String storeName = storeEntry.getKey();
            Map<String, Integer> products = storeEntry.getValue();

            // Process each product in this store
            for (Map.Entry<String, Integer> productEntry : products.entrySet()) {
                String productName = productEntry.getKey();
                int quantity = productEntry.getValue();

                // Log the purchase being made
                Log.d(TAG, "Purchasing - Store: " + storeName +
                      ", Product: " + productName + ", Quantity: " + quantity);

                // Call the purchaseProduct method to send the BUY command
                purchaseProduct(storeName, productName, quantity, callback);
            }
        }
    }

    private Map<String, Map<String, Integer>> compactToBuyFormat(String compactForm) {
        Map<String, Map<String, Integer>> purchaseMap = new HashMap<>();

        // Add debug log to see the input string
        System.out.println("Parsing: " + compactForm);

        // Split by store delimiter |
        String[] storeEntries = compactForm.split("\\|");
        if (storeEntries.length < 2) {
            System.out.println("Invalid format: Not enough segments in " + compactForm);
            return purchaseMap; // Return empty map if format invalid
        }

        String storeName = storeEntries[0].trim();
        Map<String, Integer> productMap = new HashMap<>();

        // Get the product segment (everything after the first |)
        String productSegment = storeEntries[1].trim();

        // Split products by # delimiter
        String[] productEntries = productSegment.split("#");

        // Process each product entry (format: quantity*"productName")
        for (String productEntry : productEntries) {
            String[] parts = productEntry.split("\\*");

            if (parts.length == 2) {
                try {
                    int quantity = Integer.parseInt(parts[0].trim());
                    // Remove surrounding quotes if present
                    String productName = parts[1].trim().replaceAll("^\"|\"$", "");
                    productMap.put(productName, quantity);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid quantity format in: " + productEntry);
                }
            }
        }

        if (!productMap.isEmpty()) {
            purchaseMap.put(storeName, productMap);
        }

        // Debug log the result
        System.out.println("Parsed map size: " + purchaseMap.size());

        return purchaseMap;
    }
}