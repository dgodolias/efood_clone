package com.example.backend;

import java.io.*;
import java.net.*;
import java.util.*;

public class Reducer {
    private static final int DEFAULT_REDUCER_PORT = 8090; // Default port for Reducer
    private String reducerHostname; // For displaying in logs
    private int port; // The port Reducer will listen on

    public Reducer(String hostname, int port) {
        this.reducerHostname = hostname;
        this.port = port;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Reducer Server running on " + reducerHostname + ":" + port);

            System.out.println();
            while (true) {
                Socket masterSocket = serverSocket.accept();
                System.out.println("Master connected to Reducer from: " + masterSocket.getInetAddress());
                new ReducerThread(masterSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Reducer server failed: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        // Print help if requested
        if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
            System.out.println("Usage: java com.example.backend.Reducer [options]");
            System.out.println("Options:");
            System.out.println("  --port <port>    Set the Reducer port (default: 8090)");
            System.out.println("Example:");
            System.out.println("  java com.example.backend.Reducer --port 8090");
            return;
        }

        // Read custom port from arguments or system property
        int reducerPort = DEFAULT_REDUCER_PORT;
        
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--port")) {
                if (i + 1 < args.length) {
                    try {
                        reducerPort = Integer.parseInt(args[++i]);
                        System.out.println("Using custom port: " + reducerPort);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid port number: " + args[i] + ", using default: " + DEFAULT_REDUCER_PORT);
                    }
                }
            }
        }
        
        // Fallback to system property if no port specified in args
        if (reducerPort == DEFAULT_REDUCER_PORT) {
            String customPort = System.getProperty("reducer.port");
            if (customPort != null && !customPort.isEmpty()) {
                try {
                    reducerPort = Integer.parseInt(customPort);
                    System.out.println("Using custom port from system property: " + reducerPort);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid custom port from system property: " + customPort + ", using default: " + DEFAULT_REDUCER_PORT);
                }
            }
        }

        // Get IP address of the current machine instead of hostname
        String ipAddress = "localhost";
        List<String> availableIPs = new ArrayList<>();
        
        try {
            // Try to get the actual IP address instead of hostname
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                // Skip loopback interfaces and disabled interfaces
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // Skip IPv6 addresses and loopback addresses
                    if (addr instanceof Inet6Address || addr.isLoopbackAddress()) {
                        continue;
                    }
                    availableIPs.add(addr.getHostAddress());
                }
            }
            
            // Use the first non-loopback IP address we found
            if (!availableIPs.isEmpty()) {
                ipAddress = availableIPs.get(0);
                System.out.println("Using IP address: " + ipAddress);
                
                // Print all available IP addresses if there are multiple
                if (availableIPs.size() > 1) {
                    System.out.println("All available IP addresses:");
                    for (int i = 0; i < availableIPs.size(); i++) {
                        System.out.println(" - " + availableIPs.get(i) + ":" + reducerPort);
                    }
                }
            } else {
                System.out.println("No external network interfaces found, using 'localhost'");
            }
        } catch (SocketException e) {
            System.out.println("Could not determine IP address, using 'localhost'");
        }

        System.out.println("Starting Reducer...");
        
        // Start the Reducer without worker connections
        Reducer reducer = new Reducer(ipAddress, reducerPort);
        reducer.start();
    }
}

class ReducerThread extends Thread {
    private Socket masterSocket;
    
    // Wait-notify synchronization for MapReduce coordination
    private final Object mapReduceLock = new Object();
    private int expectedMapResults = 0;
    private int receivedMapResults = 0;
    private boolean allResultsReceived = false;

    public ReducerThread(Socket masterSocket) {
        this.masterSocket = masterSocket;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(masterSocket.getInputStream()));
             PrintWriter out = new PrintWriter(masterSocket.getOutputStream(), true)) {

            String request;
            List<String> mapResults = new ArrayList<>();
            String command = "";
            boolean isReduceMode = false;
            
            while ((request = in.readLine()) != null) {
                System.out.println("Reducer received: " + request);
                
                if (request.startsWith("REDUCE ")) {
                    // Start collecting map results
                    isReduceMode = true;
                    command = request.substring(7); // Extract the command part
                    mapResults.clear();
                    
                    // Reset synchronization state for new reduce operation
                    synchronized (mapReduceLock) {
                        expectedMapResults = 0;  // Will be set by EXPECT_MAP_RESULTS
                        receivedMapResults = 0;
                        allResultsReceived = false;
                    }
                    continue;
                } else if (request.startsWith("EXPECT_MAP_RESULTS ")) {
                    // Master tells us how many map results to expect
                    if (isReduceMode) {
                        synchronized (mapReduceLock) {
                            try {
                                expectedMapResults = Integer.parseInt(request.substring(19));
                                System.out.println("Reducer expects " + expectedMapResults + " map results for " + command);
                            } catch (NumberFormatException e) {
                                System.err.println("Invalid expected map results count: " + request.substring(19));
                            }
                        }
                    }
                    continue;
                } else if (request.startsWith("MAP_RESULT ")) {
                    // Collect an intermediate result from the Map phase
                    if (isReduceMode) {
                        mapResults.add(request.substring(11)); // Extract the result part
                        
                        // Update received count and notify if all results received
                        synchronized (mapReduceLock) {
                            receivedMapResults++;
                            System.out.println("Reducer received " + receivedMapResults + "/" + expectedMapResults + 
                                             " map results for " + command);
                            
                            if (expectedMapResults > 0 && receivedMapResults >= expectedMapResults) {
                                allResultsReceived = true;
                                mapReduceLock.notifyAll(); // Notify any waiting threads
                                System.out.println("Reducer notified that all map results received");
                            }
                        }
                    }
                    continue;
                } else if (request.equals("END_MAP_RESULTS")) {
                    // All map results received, now process them (REDUCE phase)
                    if (isReduceMode) {
                        // If we're expecting a specific number of results, wait for them
                        if (expectedMapResults > 0) {
                            synchronized (mapReduceLock) {
                                if (!allResultsReceived) {
                                    System.out.println("Reducer waiting for remaining map results (" + 
                                                     receivedMapResults + "/" + expectedMapResults + ")");
                                    try {
                                        // Wait for up to 5 seconds for remaining results
                                        mapReduceLock.wait(5000);
                                        
                                        if (!allResultsReceived) {
                                            System.out.println("Reducer timed out waiting for map results. " +
                                                             "Proceeding with " + receivedMapResults + "/" + 
                                                             expectedMapResults + " results.");
                                        }
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        System.err.println("Interrupted while waiting for map results");
                                    }
                                }
                            }
                        }
                        
                        String reduceResult = processMapResults(command, mapResults);
                        out.println(reduceResult);
                        out.println("END_REDUCER");
                        isReduceMode = false;
                    }
                    continue;
                } else if (isReduceMode) {
                    // Ignore other inputs during reduce mode
                    continue;
                } else {
                    // Invalid request outside of MapReduce pattern
                    out.println("ERROR: Invalid request. Use REDUCE command to start MapReduce operation.");
                    out.println("END_REDUCER");
                }
            }
        } catch (IOException e) {
            System.err.println("Error handling Master connection in Reducer: " + e.getMessage());
        } finally {
            try {
                masterSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing Master socket in Reducer: " + e.getMessage());
            }
        }
    }
    
    /**
     * Process the map results for the given command
     * This is the core of the Reduce phase in the MapReduce pattern
     * It implements fault tolerance by detecting when workers are unavailable
     * and using backup replica data in such cases
     */
    private String processMapResults(String command, List<String> mapResults) {
        System.out.println("REDUCE PHASE: Processing " + mapResults.size() + " map results for command: " + command);
        
        // Check if any replicas are down (if we have fewer responses than expected)
        boolean hasWorkerFailures = detectWorkerFailures(mapResults);
        if (hasWorkerFailures) {
            System.out.println("FAULT TOLERANCE: Detected worker failures, using backup replica data");
        }
        
        switch (command) {
            case "ADD_STORE":
                return reduceAddStoreResults(mapResults);
            case "ADD_PRODUCT":
                return reduceAddProductResults(mapResults);
            case "REMOVE_PRODUCT":
                return reduceRemoveProductResults(mapResults);
            case "FILTER_STORES":
                return reduceFilterStoresResults(mapResults);
            case "FIND_STORES_WITHIN_RANGE":
                return reduceFindStoresResults(mapResults);
            case "GET_STORE_DETAILS":
                return reduceGetStoreDetailsResults(mapResults);
            case "BUY":
                return reducePurchaseResults(mapResults);
            case "REVIEW":
                return reduceReviewResults(mapResults);
            case "GET_SALES_BY_STORE_TYPE_CATEGORY":
            case "GET_SALES_BY_PRODUCT_CATEGORY":
            case "GET_SALES_BY_PRODUCT":
                return reduceSalesAnalytics(command, mapResults);
            default:
                return "Reducer Error: Unknown command for reduction: " + command;
        }
    }
    
    /**
     * Detect worker failures by analyzing the map results
     * @param mapResults The results from the map phase
     * @return true if worker failures were detected, false otherwise
     */
    private boolean detectWorkerFailures(List<String> mapResults) {
        // If we have no results at all, that's a clear failure
        if (mapResults.isEmpty()) {
            return true;
        }
        
        // Count error responses that might indicate worker failure
        int errorCount = 0;
        for (String result : mapResults) {
            if (result == null || result.startsWith("ERROR|") || result.contains("Connection refused")) {
                errorCount++;
            }
        }
        
        // If all results are errors, that's a complete failure
        // If some results are errors, we can still proceed with partial data (from replicas)
        return errorCount > 0;
    }
    
    private String reduceAddStoreResults(List<String> mapResults) {
        boolean anySuccess = false;
        String successStoreName = "";
        StringBuilder errorMessages = new StringBuilder();
        
        for (String response : mapResults) {
            if (response != null && response.startsWith("SUCCESS|")) {
                anySuccess = true;
                successStoreName = response.split("\\|")[1];
            } else if (response != null && response.startsWith("ERROR|")) {
                if (errorMessages.length() > 0) errorMessages.append("; ");
                errorMessages.append(response.substring(6));
            }
        }
        
        if (anySuccess) {
            return "Store added: " + successStoreName;
        } else {
            return "Error adding store: " + (errorMessages.length() > 0 ? errorMessages.toString() : "Unknown error");
        }
    }
    
    private String reduceAddProductResults(List<String> mapResults) {
        boolean anySuccess = false;
        String successStoreName = "";
        String successProductName = "";
        StringBuilder errorMessages = new StringBuilder();
        
        for (String response : mapResults) {
            if (response != null && response.startsWith("SUCCESS|")) {
                anySuccess = true;
                String[] parts = response.split("\\|");
                successStoreName = parts[1];
                successProductName = parts.length > 2 ? parts[2] : "";
            } else if (response != null && response.startsWith("ERROR|")) {
                if (errorMessages.length() > 0) errorMessages.append("; ");
                errorMessages.append(response.substring(6));
            }
        }
        
        if (anySuccess) {
            return "Product added to store: " + successStoreName;
        } else {
            return "Error adding product: " + (errorMessages.length() > 0 ? errorMessages.toString() : "Unknown error");
        }
    }
    
    private String reduceRemoveProductResults(List<String> mapResults) {
        boolean anySuccess = false;
        String successStoreName = "";
        String successProductName = "";
        StringBuilder errorMessages = new StringBuilder();
        
        for (String response : mapResults) {
            if (response != null && response.startsWith("SUCCESS|")) {
                anySuccess = true;
                String[] parts = response.split("\\|");
                successStoreName = parts[1];
                successProductName = parts.length > 2 ? parts[2] : "";
            } else if (response != null && response.startsWith("ERROR|")) {
                if (errorMessages.length() > 0) errorMessages.append("; ");
                errorMessages.append(response.substring(6));
            }
        }
        
        if (anySuccess) {
            return "Product removed from store: " + successStoreName;
        } else {
            return "Error removing product: " + (errorMessages.length() > 0 ? errorMessages.toString() : "Unknown error");
        }
    }
    
    private String reduceFilterStoresResults(List<String> mapResults) {
        Map<String, String> storeMap = new HashMap<>(); // StoreName -> StoreJSON
        
        for (String response : mapResults) {
            if (response != null && response.startsWith("[") && response.endsWith("]")) {
                String content = response.substring(1, response.length() - 1).trim();
                if (!content.isEmpty()) {
                    List<String> storeObjects = splitJsonObjects(content);
                    for (String storeJson : storeObjects) {
                        String storeName = extractField(storeJson, "StoreName");
                        if (!storeMap.containsKey(storeName)) {
                            storeMap.put(storeName, storeJson);
                        }
                    }
                }
            }
        }
        
        // Build the combined response
        StringBuilder result = new StringBuilder("[");
        boolean first = true;
        for (String storeJson : storeMap.values()) {
            if (!first) result.append(",");
            result.append(storeJson);
            first = false;
        }
        result.append("]");
        
        return result.toString();
    }
    
    private String reduceFindStoresResults(List<String> mapResults) {
        Map<String, String> storeMap = new HashMap<>(); // StoreName -> StoreJSON
        
        for (String response : mapResults) {
            if (response != null && response.startsWith("[") && response.endsWith("]")) {
                String content = response.substring(1, response.length() - 1).trim();
                if (!content.isEmpty()) {
                    List<String> storeObjects = splitJsonObjects(content);
                    for (String storeJson : storeObjects) {
                        String storeName = extractField(storeJson, "StoreName");
                        if (!storeMap.containsKey(storeName)) {
                            storeMap.put(storeName, storeJson);
                        }
                    }
                }
            }
        }
        
        // Build the combined response
        StringBuilder result = new StringBuilder("[");
        boolean first = true;
        for (String storeJson : storeMap.values()) {
            if (!first) result.append(",");
            result.append(storeJson);
            first = false;
        }
        result.append("]");
        
        return result.toString();
    }
    
    private String reduceGetStoreDetailsResults(List<String> mapResults) {
        // Pick the first valid response from any replica
        for (String response : mapResults) {
            if (response != null && !response.isEmpty() && !response.startsWith("ERROR|")) {
                return response; // Return first valid store details
            }
        }
        
        return "Error: Store not found or could not retrieve store details.";
    }
    
    private String reducePurchaseResults(List<String> mapResults) {
        boolean anySuccess = false;
        String successStoreName = "";
        String successProductName = "";
        int successQuantity = 0;
        StringBuilder errorMessages = new StringBuilder();
        
        for (String response : mapResults) {
            if (response != null && response.startsWith("SUCCESS|")) {
                anySuccess = true;
                String[] parts = response.split("\\|");
                successStoreName = parts[1];
                successProductName = parts.length > 2 ? parts[2] : "";
                successQuantity = parts.length > 3 ? Integer.parseInt(parts[3]) : 0;
            } else if (response != null && response.startsWith("ERROR|")) {
                if (errorMessages.length() > 0) errorMessages.append("; ");
                errorMessages.append(response.substring(6));
            }
        }
        
        if (anySuccess) {
            return "Purchase completed: " + successQuantity + " of " + successProductName + " from " + successStoreName;
        } else {
            return "Error: Purchase failed - " + (errorMessages.length() > 0 ? errorMessages.toString() : "Unknown error");
        }
    }
    
    private String reduceReviewResults(List<String> mapResults) {
        boolean anySuccess = false;
        String successStoreName = "";
        float newRating = 0;
        int newVotes = 0;
        StringBuilder errorMessages = new StringBuilder();
        
        for (String response : mapResults) {
            if (response != null && response.startsWith("SUCCESS|")) {
                anySuccess = true;
                String[] parts = response.split("\\|");
                successStoreName = parts[1];
                newRating = parts.length > 2 ? Float.parseFloat(parts[2]) : 0;
                newVotes = parts.length > 3 ? Integer.parseInt(parts[3]) : 0;
            } else if (response != null && response.startsWith("ERROR|")) {
                if (errorMessages.length() > 0) errorMessages.append("; ");
                errorMessages.append(response.substring(6));
            }
        }
        
        if (anySuccess) {
            return "Review submitted for store: " + successStoreName + " (new rating: " + newRating + " from " + newVotes + " votes)";
        } else {
            return "Error: Failed to submit review - " + (errorMessages.length() > 0 ? errorMessages.toString() : "Unknown error");
        }
    }
    
    private String reduceSalesAnalytics(String command, List<String> mapResults) {
        Map<String, Integer> salesByStore = new HashMap<>();
        Set<String> processedStores = new HashSet<>(); // Use Set to avoid duplicates from replicas
        int total = 0;
        
        for (String response : mapResults) {
            if (response != null && !response.isEmpty()) {
                String[] storesSales = response.split("\\|"); // Worker returns StoreName:Amount pairs separated by |
                for (String storeSale : storesSales) {
                    if (!storeSale.isEmpty()) {
                        String[] storeParts = storeSale.split(":");
                        if (storeParts.length == 2) {
                            String storeName = storeParts[0].trim();
                            try {
                                int amount = Integer.parseInt(storeParts[1].trim());

                                // Only add/sum if this store hasn't been processed yet from another worker (replica)
                                if (!processedStores.contains(storeName)) {
                                    salesByStore.put(storeName, amount);
                                    total += amount;
                                    processedStores.add(storeName); // Mark store as processed
                                }
                            } catch (NumberFormatException nfe) {
                                System.err.println("Reducer: Invalid amount format for store '" + storeName + "': " + storeParts[1]);
                            }
                        } else {
                            System.err.println("Reducer: Invalid store:amount format: " + storeSale);
                        }
                    }
                }
            }
        }

        // Format the result string as expected by the client
        StringBuilder resultBuilder = new StringBuilder();
        for (Map.Entry<String, Integer> entry : salesByStore.entrySet()) {
            if (resultBuilder.length() > 0) {
                resultBuilder.append("\n"); // Newline between store entries
            }
            resultBuilder.append("\"").append(entry.getKey()).append("\": ").append(entry.getValue());
        }
        if (!salesByStore.isEmpty()) {
            resultBuilder.append("\n"); // Newline before total
        }
        resultBuilder.append("\"total\": ").append(total);
        
        return resultBuilder.toString();
    }

    
    
    // Helper methods
    private List<String> splitJsonObjects(String jsonContent) {
        List<String> objects = new ArrayList<>();
        int braceCount = 0;
        int startIndex = -1;

        for (int i = 0; i < jsonContent.length(); i++) {
            char c = jsonContent.charAt(i);

            if (c == '{') {
                if (braceCount == 0) {
                    startIndex = i;
                }
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0 && startIndex != -1) {
                    objects.add(jsonContent.substring(startIndex, i+1));
                    startIndex = -1;
                }
            }
        }

        return objects;
    }
    
    private String extractField(String json, String field) {
        String search = "\"" + field + "\":";
        int start = json.indexOf(search);
        if (start == -1) return "";
        start += search.length();
        if (json.charAt(start) == '"') {
            start++;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } else {
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            return json.substring(start, end).trim();
        }
    }
}