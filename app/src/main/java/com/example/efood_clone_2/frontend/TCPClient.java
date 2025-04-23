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
import java.util.ArrayList;
import java.util.Collections;
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
                Log.e("TCPClient", "Error connecting to server: " + e.getMessage(), e);
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
            Log.e("TCPClient", "Error parsing store: " + storeString + " - " + e.getMessage());
        }
        return null;
    }

    public void getFilteredStores(Map<String, List<String>> filters, StoreListCallback callback) {
        executor.execute(() -> {
            try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                Log.d(TAG, "Connected to server for filtered stores");

                // Build filter string without using JSON libraries
                StringBuilder filterString = new StringBuilder("FILTER_STORES ");
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

                    // Parse the store information
                    int dashIndex = line.indexOf(" - ");
                    int bracketIndex = line.lastIndexOf(" (");

                    if (dashIndex > 0 && bracketIndex > dashIndex) {
                        String name = line.substring(0, dashIndex);
                        String foodType = line.substring(dashIndex + 3, bracketIndex);
                        String distanceStr = line.substring(bracketIndex + 2, line.length() - 3);

                        try {
                            double distance = Double.parseDouble(distanceStr);
                            // Default values for coordinates, will be set by server
                            Store store = new Store(name, 0, 0, foodType, 4, "$$");
                            store.setDistance(distance);
                            storeList.add(store);
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Error parsing distance: " + e.getMessage());
                        }
                    }
                }

                mainHandler.post(() -> callback.onStoresReceived(storeList));

            } catch (IOException e) {
                Log.e(TAG, "Error connecting to server: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
    }
}