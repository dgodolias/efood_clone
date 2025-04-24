package com.example.efood_clone_2.frontend;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.efood_clone_2.interfaces.StoreDetailsCallback;
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
    private static final String SERVER_HOST = "10.0.2.2";
    private static final int SERVER_PORT = 8080;
    private final Handler mainHandler;
    private final ExecutorService executor;

    // Private constructor to prevent direct instantiation
    private TCPClient() {
        mainHandler = new Handler(Looper.getMainLooper());
        executor = Executors.newSingleThreadExecutor();
    }

    // Static holder class for lazy initialization and thread safety
    private static class Holder {
        private static final TCPClient INSTANCE = new TCPClient();
    }

    public static TCPClient getInstance() {
        return Holder.INSTANCE;
    }

    public interface StoreListCallback {
        void onStoresReceived(List<Store> stores);
        void onError(String error);
    }

    public interface ResultCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
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
                Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String request = String.format("FIND_STORES_WITHIN_RANGE %f,%f", latitude, longitude);
                out.println(request);
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.equals("END")) break;
                    responseBuilder.append(line);
                }
                String response = responseBuilder.toString();
                List<Store> stores = new ArrayList<>();
                if (response.startsWith("[") && response.endsWith("]")) {
                    try {
                        JSONArray storesArray = new JSONArray(response);
                        for (int i = 0; i < storesArray.length(); i++) {
                            JSONObject storeJson = storesArray.getJSONObject(i);
                            Store store = Store.JsonToStore(storeJson.toString());
                            if (store.getDistance() == 0) {
                                double distance = calculateDistance(latitude, longitude,
                                        store.getLatitude(), store.getLongitude());
                                store.setDistance(distance);
                            }
                            stores.add(store);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing JSON: " + e.getMessage(), e);
                        mainHandler.post(() -> callback.onError("Error parsing store data"));
                        socket.close();
                        return;
                    }
                } else {
                    String[] storeJsonStrings = response.split("\\|");
                    for (String storeStr : storeJsonStrings) {
                        if (!storeStr.isEmpty()) {
                            try {
                                Store store = Store.JsonToStore(storeStr);
                                if (store.getDistance() == 0) {
                                    double distance = calculateDistance(latitude, longitude,
                                            store.getLatitude(), store.getLongitude());
                                    store.setDistance(distance);
                                }
                                stores.add(store);
                            } catch (JSONException e) {
                                Log.e(TAG, "Error parsing store: " + e.getMessage());
                            }
                        }
                    }
                }
                Collections.sort(stores, (s1, s2) -> Double.compare(s1.getDistance(), s2.getDistance()));
                for (Store store : stores) {
                    Log.d(TAG, "Store: " + store.getStoreName() + ", Distance: " + store.getFormattedDistance());
                }
                mainHandler.post(() -> callback.onStoresReceived(stores));
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error connecting to server: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
    }

    public void getFilteredStores(Map<String, List<String>> filters, double latitude, double longitude,
                                  StoreListCallback callback) {
        new Thread(() -> {
            try {
                Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                StringBuilder request = new StringBuilder("FILTER_STORES ");
                request.append(latitude).append(",").append(longitude).append(";");
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
                String requestStr = request.toString();
                Log.d(TAG, "Sending filter request: " + requestStr);
                out.println(requestStr);
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.equals("END")) break;
                    responseBuilder.append(line).append("\n");
                }
                String response = responseBuilder.toString().trim();
                Log.d(TAG, "Filter response: " + response);
                List<Store> stores = new ArrayList<>();
                if (response.isEmpty() || response.equals("No stores found with the specified filters.")) {
                } else if (response.startsWith("[") && response.endsWith("]")) {
                    try {
                        JSONArray jsonArray = new JSONArray(response);
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject storeJson = jsonArray.getJSONObject(i);
                            Store store = Store.JsonToStore(storeJson.toString());
                            if (store.getDistance() == 0) {
                                double distance = calculateDistance(latitude, longitude,
                                        store.getLatitude(), store.getLongitude());
                                store.setDistance(distance);
                            }
                            stores.add(store);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing JSON array: " + e.getMessage());
                    }
                } else {
                    String[] storeJsons = response.split("\n");
                    for (String storeJson : storeJsons) {
                        if (!storeJson.trim().isEmpty()) {
                            try {
                                if (storeJson.trim().startsWith("{") && storeJson.trim().endsWith("}")) {
                                    Store store = Store.JsonToStore(storeJson);
                                    double distance = calculateDistance(latitude, longitude,
                                            store.getLatitude(), store.getLongitude());
                                    store.setDistance(distance);
                                    stores.add(store);
                                } else {
                                    Log.w(TAG, "Skipping non-JSON response: " + storeJson);
                                }
                            } catch (JSONException e) {
                                Log.e(TAG, "Error parsing store JSON: " + e.getMessage() + " for input: " + storeJson);
                            }
                        }
                    }
                }
                if (!stores.isEmpty()) {
                    Collections.sort(stores, (s1, s2) -> Double.compare(s1.getDistance(), s2.getDistance()));
                    for (Store store : stores) {
                        Log.d(TAG, "Store: " + store.getStoreName() + ", Distance: " + store.getFormattedDistance());
                    }
                }
                in.close();
                out.close();
                socket.close();
                Handler mainHandler = new Handler(Looper.getMainLooper());
                List<Store> finalStores = stores;
                mainHandler.post(() -> callback.onStoresReceived(finalStores));
            } catch (Exception e) {
                Log.e(TAG, "Error in getFilteredStores: " + e.getMessage(), e);
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
            }
        }).start();
    }

    public void getStoreDetails(String storeName, StoreDetailsCallback callback) {
        executor.execute(() -> {
            try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                Log.d(TAG, "Requesting store details for: " + storeName);
                out.println("GET_STORE_DETAILS " + storeName);
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

    public void purchaseProduct(String storeName, String productName, int quantity, ResultCallback callback) {
        executor.execute(() -> {
            try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                Log.d(TAG, "Sending purchase request - Store: " + storeName +
                        ", Product: " + productName + ", Quantity: " + quantity);
                String buyCommand = String.format("BUY %s,%s,%d", storeName, productName, quantity);
                out.println(buyCommand);
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
        Map<String, Map<String, Integer>> purchaseMap = compactToBuyFormat(compactFormString);
        Log.d(TAG, "Processing purchases from: " + compactFormString);
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
        for (Map.Entry<String, Map<String, Integer>> storeEntry : purchaseMap.entrySet()) {
            String storeName = storeEntry.getKey();
            Map<String, Integer> products = storeEntry.getValue();
            for (Map.Entry<String, Integer> productEntry : products.entrySet()) {
                String productName = productEntry.getKey();
                int quantity = productEntry.getValue();
                Log.d(TAG, "Purchasing - Store: " + storeName +
                        ", Product: " + productName + ", Quantity: " + quantity);
                purchaseProduct(storeName, productName, quantity, callback);
            }
        }
    }

    private Map<String, Map<String, Integer>> compactToBuyFormat(String compactForm) {
        Map<String, Map<String, Integer>> purchaseMap = new HashMap<>();
        System.out.println("Parsing: " + compactForm);
        String[] storeEntries = compactForm.split("\\|");
        if (storeEntries.length < 2) {
            System.out.println("Invalid format: Not enough segments in " + compactForm);
            return purchaseMap;
        }
        String storeName = storeEntries[0].trim();
        Map<String, Integer> productMap = new HashMap<>();
        String productSegment = storeEntries[1].trim();
        String[] productEntries = productSegment.split("#");
        for (String productEntry : productEntries) {
            String[] parts = productEntry.split("\\*");
            if (parts.length == 2) {
                try {
                    int quantity = Integer.parseInt(parts[0].trim());
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
        System.out.println("Parsed map size: " + purchaseMap.size());
        return purchaseMap;
    }

    public void sendReview(String storeName, int rating) {
        new Thread(() -> {
            try {
                Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String command = "REVIEW " + storeName + "," + rating;
                System.out.println("TCPClient " + "Sending review command: " + command);
                out.println(command);
                String response;
                while ((response = in.readLine()) != null) {
                    if (response.equals("END")) {
                        break;
                    }
                    System.out.println("TCPClient received: " + response);
                }
                socket.close();
            } catch (IOException e) {
                System.err.println("Error sending review: " + e.getMessage());
            }
        }).start();
    }
}