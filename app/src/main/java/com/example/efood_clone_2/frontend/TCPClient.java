package com.example.efood_clone_2.frontend;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.efood_clone_2.communication.ClientRequest;
import com.example.efood_clone_2.communication.ClientResponse;
import com.example.efood_clone_2.interfaces.StoreDetailsCallback;
import com.example.efood_clone_2.model.Store;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TCPClient {
    private static final String TAG = "TCPClient";
    private static String SERVER_HOST = "10.0.2.2"; // Default for Android emulator
    private static int SERVER_PORT = 8080;
    private final Handler mainHandler;
    private final ExecutorService executor;
    private static boolean isConfigLoaded = false;
    private static Context appContext;
    private static boolean fallbackToDefaultEnabled = true;
    private static volatile boolean hasSuccessfullyFallenBack = false;

    public static void initialize(Context context) {
        appContext = context.getApplicationContext();
        loadConfiguration();
    }

    private static void loadConfiguration() {
        if (isConfigLoaded || appContext == null) {
            return;
        }

        Log.d(TAG, "Starting to load configuration from text file...");
        try {
            InputStream inputStream = appContext.getAssets().open("server_config.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            
            StringBuilder fileContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                fileContent.append(line).append("\n");
                line = line.trim();
                if (line.startsWith("master.host=")) {
                    SERVER_HOST = line.substring("master.host=".length()).trim();
                    Log.d(TAG, "Setting master host from config: " + SERVER_HOST);
                } else if (line.startsWith("master.port=")) {
                    try {
                        SERVER_PORT = Integer.parseInt(line.substring("master.port=".length()).trim());
                        Log.d(TAG, "Setting master port from config: " + SERVER_PORT);
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Invalid port in config, using default: " + SERVER_PORT);
                    }
                }
            }
            reader.close();
            inputStream.close();
            Log.d(TAG, "Raw text file content: " + fileContent.toString());
            Log.d(TAG, "Final connection settings: " + SERVER_HOST + ":" + SERVER_PORT);
            isConfigLoaded = true;
            testHostReachability(SERVER_HOST);
        } catch (IOException e) {
            Log.e(TAG, "Error loading configuration: " + e.getMessage(), e);
        }
    }
    
    private static void testHostReachability(String host) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Testing reachability of configured host: " + host);
                InetAddress address = InetAddress.getByName(host);
                String resolvedIP = address.getHostAddress();
                Log.d(TAG, "Resolved " + host + " to " + resolvedIP);
                boolean reachable = address.isReachable(3000);
                Log.d(TAG, "Host is reachable via ICMP ping: " + reachable);
                try {
                    Socket testSocket = new Socket();
                    testSocket.connect(new InetSocketAddress(host, SERVER_PORT), 3000);
                    Log.d(TAG, "Successfully connected to socket at " + host + ":" + SERVER_PORT);
                    testSocket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to connect to " + host + ":" + SERVER_PORT + " - " + e.getMessage());
                    if (fallbackToDefaultEnabled) {
                        Log.d(TAG, "Will attempt fallback to default (10.0.2.2:8080) when connections are made");
                    }
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "Unknown host: " + host + " - " + e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, "IO error testing reachability: " + e.getMessage());
            }
        }).start();
    }

    static {
        String configuredHost = System.getProperty("master.host");
        if (configuredHost != null && !configuredHost.isEmpty()) {
            SERVER_HOST = configuredHost;
            Log.d(TAG, "Using master host from system property: " + SERVER_HOST);
        }
    }

    private TCPClient() {
        mainHandler = new Handler(Looper.getMainLooper());
        executor = Executors.newSingleThreadExecutor();
    }

    private static class Holder {
        private static final TCPClient INSTANCE = new TCPClient();
    }

    public static TCPClient getInstance() {
        if (!isConfigLoaded && appContext != null) {
            loadConfiguration();
        }
        return Holder.INSTANCE;
    }

    private Socket createAndConnectSocket() throws IOException {
        String hostToUse = SERVER_HOST;
        int portToUse = SERVER_PORT;
        
        if (hasSuccessfullyFallenBack && fallbackToDefaultEnabled) {
            Log.d(TAG, "Using known working fallback address 10.0.2.2:8080 (skipping configured address)");
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress("10.0.2.2", 8080), 3000);
            return socket;
        }
        
        Log.d(TAG, "Attempting to connect to: " + hostToUse + ":" + portToUse);
        
        Socket socket = null;
        IOException lastException = null;
        
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(hostToUse, portToUse), 3000);
            Log.d(TAG, "Successfully connected to: " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
            return socket;
        } catch (IOException e) {
            lastException = e;
            Log.e(TAG, "Failed to connect to " + hostToUse + ":" + portToUse + " - " + e.getMessage());
            if (fallbackToDefaultEnabled && !hostToUse.equals("10.0.2.2")) {
                try {
                    Log.d(TAG, "Attempting fallback connection to default 10.0.2.2:8080");
                    socket = new Socket();
                    socket.connect(new InetSocketAddress("10.0.2.2", 8080), 3000);
                    Log.d(TAG, "Successfully connected using fallback to: " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
                    hasSuccessfullyFallenBack = true;
                    return socket;
                } catch (IOException fallbackEx) {
                    Log.e(TAG, "Fallback connection also failed: " + fallbackEx.getMessage());
                }
            }
            throw lastException;
        }
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
        double lonDistance = Math.toRadians(lon1 - lon2);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public void getNearbyStores(double latitude, double longitude, StoreListCallback callback) {
        executor.execute(() -> {
            Socket socket = null;
            ObjectOutputStream out = null;
            ObjectInputStream in = null;
            
            try {
                // Create socket and establish connection
                socket = createAndConnectSocket();
                
                // Important: Create and flush ObjectOutputStream FIRST to prevent deadlock
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                
                // Then create ObjectInputStream
                in = new ObjectInputStream(socket.getInputStream());
                
                // Prepare and send the request
                String data = String.format("%f,%f", latitude, longitude);
                ClientRequest request = new ClientRequest("FIND_STORES_WITHIN_RANGE", data);
                out.writeObject(request);
                out.flush();
                
                // Read the response
                Object responseObj = in.readObject();
                if (!(responseObj instanceof ClientResponse)) {
                    throw new ClassCastException("Unexpected response type: " + responseObj.getClass().getName());
                }
                
                ClientResponse response = (ClientResponse) responseObj;
                String responseData = response.getMessage();
                List<Store> stores = new ArrayList<>();
                
                if (responseData.startsWith("[") && responseData.endsWith("]")) {
                    try {
                        JSONArray storesArray = new JSONArray(responseData);
                        for (int i = 0; i < storesArray.length(); i++) {
                            JSONObject storeJson = storesArray.getJSONObject(i);
                            Store store = Store.JsonToStore(storeJson.toString());
                            if (store.getDistance() == 0) {
                                double distance = calculateDistance(latitude, longitude, store.getLatitude(), store.getLongitude());
                                store.setDistance(distance);
                            }
                            stores.add(store);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing JSON: " + e.getMessage(), e);
                        mainHandler.post(() -> callback.onError("Error parsing store data: " + e.getMessage()));
                        return;
                    }
                }
                
                Collections.sort(stores, (s1, s2) -> Double.compare(s1.getDistance(), s2.getDistance()));
                mainHandler.post(() -> callback.onStoresReceived(stores));
                
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Class not found during deserialization: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Serialization error: " + e.getMessage()));
            } catch (ClassCastException e) {
                Log.e(TAG, "Unexpected response type: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Protocol error: " + e.getMessage()));
            } catch (IOException e) {
                Log.e(TAG, "Error connecting to server: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            } finally {
                // Close resources in reverse order of creation
                try {
                    if (in != null) in.close();
                    if (out != null) out.close();
                    if (socket != null) socket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing resources: " + e.getMessage(), e);
                }
            }
        });
    }

    public void getStoreDetails(String storeName, StoreDetailsCallback callback) {
        executor.execute(() -> {
            Socket socket = null;
            ObjectOutputStream out = null;
            ObjectInputStream in = null;
            
            try {
                // Create socket and establish connection
                socket = createAndConnectSocket();
                
                // Important: Create and flush ObjectOutputStream FIRST to prevent deadlock
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                
                // Then create ObjectInputStream
                in = new ObjectInputStream(socket.getInputStream());
                
                ClientRequest request = new ClientRequest("GET_STORE_DETAILS", storeName);
                out.writeObject(request);
                out.flush();
                
                Object responseObj = in.readObject();
                if (!(responseObj instanceof ClientResponse)) {
                    throw new ClassCastException("Unexpected response type: " + responseObj.getClass().getName());
                }
                
                ClientResponse response = (ClientResponse) responseObj;
                String responseData = response.getMessage();
                
                if (responseData != null && responseData.startsWith("ERROR")) {
                    mainHandler.post(() -> callback.onError(responseData.substring(6)));
                } else {
                    try {
                        JSONObject storeJson = new JSONObject(responseData);
                        Store store = Store.JsonToStore(storeJson.toString());
                        mainHandler.post(() -> callback.onStoreDetailsReceived(store));
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing JSON: " + e.getMessage(), e);
                        mainHandler.post(() -> callback.onError("Error parsing store data: " + e.getMessage()));
                    }
                }
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Class not found during deserialization: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Serialization error: " + e.getMessage()));
            } catch (ClassCastException e) {
                Log.e(TAG, "Unexpected response type: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Protocol error: " + e.getMessage()));
            } catch (IOException e) {
                Log.e(TAG, "Error connecting to server: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            } finally {
                // Close resources in reverse order of creation
                try {
                    if (in != null) in.close();
                    if (out != null) out.close();
                    if (socket != null) socket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing resources: " + e.getMessage(), e);
                }
            }
        });
    }
    
    public void getFilteredStores(Map<String, List<String>> filters, double latitude, double longitude, StoreListCallback callback) {
        executor.execute(() -> {
            Socket socket = null;
            ObjectOutputStream out = null;
            ObjectInputStream in = null;
            
            try {
                // Create socket and establish connection
                socket = createAndConnectSocket();
                
                // Important: Create and flush ObjectOutputStream FIRST to prevent deadlock
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                
                // Then create ObjectInputStream
                in = new ObjectInputStream(socket.getInputStream());
                
                // Convert filters to JSON format
                JSONObject filtersJson = new JSONObject();
                for (Map.Entry<String, List<String>> entry : filters.entrySet()) {
                    JSONArray valuesArray = new JSONArray();
                    for (String value : entry.getValue()) {
                        valuesArray.put(value);
                    }
                    filtersJson.put(entry.getKey(), valuesArray);
                }
                
                String data = filtersJson.toString() + "|" + String.format("%f,%f", latitude, longitude);
                ClientRequest request = new ClientRequest("FILTER_STORES", data);
                out.writeObject(request);
                out.flush();
                
                Object responseObj = in.readObject();
                if (!(responseObj instanceof ClientResponse)) {
                    throw new ClassCastException("Unexpected response type: " + responseObj.getClass().getName());
                }
                
                ClientResponse response = (ClientResponse) responseObj;
                String responseData = response.getMessage();
                List<Store> stores = new ArrayList<>();
                
                if (responseData.startsWith("[") && responseData.endsWith("]")) {
                    try {
                        JSONArray storesArray = new JSONArray(responseData);
                        for (int i = 0; i < storesArray.length(); i++) {
                            JSONObject storeJson = storesArray.getJSONObject(i);
                            Store store = Store.JsonToStore(storeJson.toString());
                            if (store.getDistance() == 0) {
                                double distance = calculateDistance(latitude, longitude, store.getLatitude(), store.getLongitude());
                                store.setDistance(distance);
                            }
                            stores.add(store);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing JSON: " + e.getMessage(), e);
                        mainHandler.post(() -> callback.onError("Error parsing store data: " + e.getMessage()));
                        return;
                    }
                }
                
                Collections.sort(stores, (s1, s2) -> Double.compare(s1.getDistance(), s2.getDistance()));
                mainHandler.post(() -> callback.onStoresReceived(stores));
                
            } catch (JSONException e) {
                Log.e(TAG, "Error creating filters JSON: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Error creating filters: " + e.getMessage()));
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Class not found during deserialization: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Serialization error: " + e.getMessage()));
            } catch (ClassCastException e) {
                Log.e(TAG, "Unexpected response type: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Protocol error: " + e.getMessage()));
            } catch (IOException e) {
                Log.e(TAG, "Error connecting to server: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            } finally {
                // Close resources in reverse order of creation
                try {
                    if (in != null) in.close();
                    if (out != null) out.close();
                    if (socket != null) socket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing resources: " + e.getMessage(), e);
                }
            }
        });
    }
    
    public void buy(String orderDetails) {
        executor.execute(() -> {
            Socket socket = null;
            ObjectOutputStream out = null;
            ObjectInputStream in = null;
            
            try {
                // Create socket and establish connection
                socket = createAndConnectSocket();
                
                // Important: Create and flush ObjectOutputStream FIRST to prevent deadlock
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                
                // Then create ObjectInputStream
                in = new ObjectInputStream(socket.getInputStream());
                
                ClientRequest request = new ClientRequest("BUY", orderDetails);
                out.writeObject(request);
                out.flush();
                
                Object responseObj = in.readObject();
                if (!(responseObj instanceof ClientResponse)) {
                    throw new ClassCastException("Unexpected response type: " + responseObj.getClass().getName());
                }
                
                ClientResponse response = (ClientResponse) responseObj;
                String responseData = response.getMessage();
                Log.d(TAG, "Buy response: " + responseData);
                
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Class not found during deserialization: " + e.getMessage(), e);
            } catch (ClassCastException e) {
                Log.e(TAG, "Unexpected response type: " + e.getMessage(), e);
            } catch (IOException e) {
                Log.e(TAG, "Error during buy operation: " + e.getMessage(), e);
            } finally {
                // Close resources in reverse order of creation
                try {
                    if (in != null) in.close();
                    if (out != null) out.close();
                    if (socket != null) socket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing resources: " + e.getMessage(), e);
                }
            }
        });
    }
    
    public void sendReview(String storeName, int rating) {
        executor.execute(() -> {
            Socket socket = null;
            ObjectOutputStream out = null;
            ObjectInputStream in = null;
            
            try {
                // Create socket and establish connection
                socket = createAndConnectSocket();
                
                // Important: Create and flush ObjectOutputStream FIRST to prevent deadlock
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                
                // Then create ObjectInputStream
                in = new ObjectInputStream(socket.getInputStream());
                
                String reviewData = storeName + "," + rating;
                ClientRequest request = new ClientRequest("REVIEW", reviewData);
                out.writeObject(request);
                out.flush();
                
                Object responseObj = in.readObject();
                if (!(responseObj instanceof ClientResponse)) {
                    throw new ClassCastException("Unexpected response type: " + responseObj.getClass().getName());
                }
                
                ClientResponse response = (ClientResponse) responseObj;
                String responseData = response.getMessage();
                Log.d(TAG, "Review response: " + responseData);
                
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Class not found during deserialization: " + e.getMessage(), e);
            } catch (ClassCastException e) {
                Log.e(TAG, "Unexpected response type: " + e.getMessage(), e);
            } catch (IOException e) {
                Log.e(TAG, "Error sending review: " + e.getMessage(), e);
            } finally {
                // Close resources in reverse order of creation
                try {
                    if (in != null) in.close();
                    if (out != null) out.close();
                    if (socket != null) socket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing resources: " + e.getMessage(), e);
                }
            }
        });
    }
}