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
            try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                Log.d(TAG, "Connected to server at " + SERVER_HOST + ":" + SERVER_PORT);

                // Send the command to find stores within range
                String command = "FIND_STORES_WITHIN_RANGE " + latitude + "," + longitude;
                out.println(command);
                Log.d(TAG, "Sent command: " + command);

                // Read the response
                List<Store> storeList = new ArrayList<>();
                String line;
                String response = "";
                StringBuilder sb = new StringBuilder();

                while ((line = in.readLine()) != null && !line.equals("END")) {
                    if (line.equals("No stores found within 5km of your location.")) {
                        mainHandler.post(() -> callback.onStoresReceived(new ArrayList<>()));
                        return;
                    }

                    // Parse the store information
                    // Format: "StoreName - FoodCategory (Distance km)"
                    int dashIndex = line.indexOf(" - ");
                    int bracketIndex = line.lastIndexOf(" (");

                    if (dashIndex > 0 && bracketIndex > dashIndex) {
                        String name = line.substring(0, dashIndex);
                        String foodType = line.substring(dashIndex + 3, bracketIndex);
                        String distanceStr = line.substring(bracketIndex + 2, line.length() - 3);

                        try {
                            double distance = Double.parseDouble(distanceStr);
                            Store store = new Store(name, latitude, longitude, foodType, 4, "$$");
                            store.setDistance(distance);
                            storeList.add(store);
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Error parsing distance: " + e.getMessage());
                        }
                    }
                    sb.append(line);
                }
                response = sb.toString();
                Log.d(TAG,"response: "+response);

                
                // Notify result on main thread
                mainHandler.post(() -> callback.onStoresReceived(storeList));

            } catch (IOException e) {
                Log.e(TAG, "Error connecting to server: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
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