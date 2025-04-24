package com.example.efood_clone_2.frontend;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.efood_clone_2.model.Product;
import com.example.efood_clone_2.model.Store;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Haversine formula
        final int R = 6371; // Earth radius in kilometers

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
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
                        // Calculate distance if not already set
                        if (store.getDistance() == 0) {
                            double distance = calculateDistance(latitude, longitude,
                                store.getLatitude(), store.getLongitude());
                            store.setDistance(distance);
                        }
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

    public void getFilteredStores(Map<String, List<String>> filters, double latitude, double longitude,
                                StoreListCallback callback) {
        new Thread(() -> {
            try {
                // Create connection
                Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Prepare filter string
                StringBuilder request = new StringBuilder("FILTER_STORES ");

                // Append location
                request.append(latitude).append(",").append(longitude).append(";");

                // Append filters
                for (Map.Entry<String, List<String>> entry : filters.entrySet()) {
                    if (!entry.getValue().isEmpty()) {
                        request.append(entry.getKey()).append(":");
                        for (int i = 0; i < entry.getValue().size(); i++) {
                            request.append(entry.getValue().get(i));
                            if (i < entry.getValue().size() - 1) {
                                request.append(",");
                            }
                        }
                        request.append(";");
                    }
                }

                // Send request
                out.println(request.toString());

                // Parse the response
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.equals("END")) break;
                    responseBuilder.append(line).append("\n");
                }

                // Check if response is a JSON array
                String response = responseBuilder.toString().trim();
                List<Store> stores = new ArrayList<>();

                if (response.startsWith("[") && response.endsWith("]")) {
                    // Parse JSON array
                    JSONArray jsonArray = new JSONArray(response);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject storeJson = jsonArray.getJSONObject(i);
                        Store store = Store.JsonToStore(storeJson.toString());

                        // Ensure distance is calculated
                        if (store.getDistance() == 0) {
                            double distance = calculateDistance(latitude, longitude,
                                store.getLatitude(), store.getLongitude());
                            store.setDistance(distance);
                        }
                        stores.add(store);
                    }
                } else if (!response.equals("No stores found with the specified filters.")) {
                    // Handle old format (individual store JSON objects)
                    String[] storeJsons = response.split("\n");
                    for (String storeJson : storeJsons) {
                        if (!storeJson.trim().isEmpty()) {
                            Store store = Store.JsonToStore(storeJson);
                            // Calculate distance
                            double distance = calculateDistance(latitude, longitude,
                                store.getLatitude(), store.getLongitude());
                            store.setDistance(distance);
                            stores.add(store);
                        }
                    }
                }

                // Sort stores by distance
                Collections.sort(stores, (s1, s2) -> Double.compare(s1.getDistance(), s2.getDistance()));

                // Log store distances
                for (Store store : stores) {
                    Log.d(TAG, "Store: " + store.getStoreName() + ", Distance: " + store.getFormattedDistance());
                }

                // Close resources
                in.close();
                out.close();
                socket.close();

                // Return results on the main thread
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> callback.onStoresReceived(stores));

            } catch (Exception e) {
                e.printStackTrace();
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
            }
        }).start();
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

    // Method to make a purchase
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