package com.example.backend;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Reducer {
    private static final int DEFAULT_REDUCER_PORT = 8090; // Default port for Reducer
    private List<WorkerConnection> workers;
    private String reducerHostname; // For displaying in logs
    private int port; // The port Reducer will listen on

    public Reducer(List<WorkerConnection> workers, String hostname, int port) {
        this.workers = workers;
        this.reducerHostname = hostname;
        this.port = port;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Reducer Server running on " + reducerHostname + ":" + port);
            while (true) {
                Socket masterSocket = serverSocket.accept();
                System.out.println("Master connected to Reducer from: " + masterSocket.getInetAddress());
                new ReducerThread(masterSocket, workers).start();
            }
        } catch (IOException e) {
            System.err.println("Reducer server failed: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: Reducer <workerHost1:workerPort1> <workerHost2:workerPort2> ...");
            System.exit(1);
        }

        // Read custom port from system property, or use default
        int reducerPort = DEFAULT_REDUCER_PORT;
        String customPort = System.getProperty("reducer.port");
        if (customPort != null && !customPort.isEmpty()) {
            try {
                reducerPort = Integer.parseInt(customPort);
                System.out.println("Using custom port: " + reducerPort);
            } catch (NumberFormatException e) {
                System.err.println("Invalid custom port: " + customPort + ", using default: " + DEFAULT_REDUCER_PORT);
            }
        }

        // Get IP address of the current machine instead of hostname
        String ipAddress = "localhost";
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
                    ipAddress = addr.getHostAddress();
                    System.out.println("Using IP address: " + ipAddress);
                    break;
                }
                
                if (!ipAddress.equals("localhost")) {
                    break; // Stop after finding a valid IP
                }
            }
        } catch (SocketException e) {
            System.out.println("Could not determine IP address, using 'localhost'");
        }

        List<WorkerConnection> workerConnections = new ArrayList<>();
        for (String workerAddr : args) {
            try {
                String[] parts = workerAddr.split(":");
                if (parts.length != 2) {
                    System.err.println("Invalid worker address format: " + workerAddr);
                    continue;
                }
                String host = parts[0];
                int port = Integer.parseInt(parts[1]);
                workerConnections.add(new WorkerConnection(host, port));
                System.out.println("Reducer configured for worker at " + host + ":" + port);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number in worker address: " + workerAddr);
            } catch (IOException e) {
                System.err.println("Error connecting to worker at " + workerAddr + ": " + e.getMessage());
            }
        }

        if (workerConnections.isEmpty()) {
            System.err.println("No valid worker addresses provided. Reducer cannot start.");
            System.exit(1);
        }

        Reducer reducer = new Reducer(workerConnections, ipAddress, reducerPort);
        reducer.start();
    }
}

class ReducerThread extends Thread {
    private Socket masterSocket;
    private List<WorkerConnection> workers;

    public ReducerThread(Socket masterSocket, List<WorkerConnection> workers) {
        this.masterSocket = masterSocket;
        this.workers = workers;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(masterSocket.getInputStream()));
             PrintWriter out = new PrintWriter(masterSocket.getOutputStream(), true)) {

            String request;
            while ((request = in.readLine()) != null) {
                System.out.println("Reducer received command: " + request);
                if (workers.isEmpty()) {
                    out.println("Reducer Error: No workers configured.");
                    out.println("END_REDUCER"); // Use a distinct end marker
                    continue;
                }

                String[] parts = request.split(" ", 2);
                String command = parts[0];
                String data = parts.length > 1 ? parts[1] : "";

                String result;
                switch (command) {
                    // ANALYTICS OPERATIONS - REDUCE PHASE
                    case "GET_SALES_BY_STORE_TYPE_CATEGORY":
                    case "GET_SALES_BY_PRODUCT_CATEGORY":
                    case "GET_SALES_BY_PRODUCT":
                        result = processSalesAnalytics(command, data);
                        break;
                        
                    // MANAGER OPERATIONS - REDUCE PHASE
                    case "ADD_STORE":
                        result = processAddStoreResults(data);
                        break;
                    case "ADD_PRODUCT":
                        result = processAddProductResults(data);
                        break;
                    case "REMOVE_PRODUCT":
                        result = processRemoveProductResults(data);
                        break;
                        
                    // CLIENT OPERATIONS - REDUCE PHASE
                    case "FILTER_STORES":
                        result = processFilterStoresResults(data);
                        break;
                    case "FIND_STORES_WITHIN_RANGE":
                        result = processFindStoresResults(data);
                        break;
                    case "GET_STORE_DETAILS":
                        result = processGetStoreDetailsResults(data);
                        break;
                    case "BUY":
                        result = processPurchaseResults(data);
                        break;
                    case "REVIEW":
                        result = processReviewResults(data);
                        break;
                    default:
                        result = "Reducer Error: Unknown command " + command;
                        break;
                }

                // Send multi-line result back to Master
                if (result != null && !result.isEmpty()) {
                    out.println(result);
                }
                out.println("END_REDUCER"); // Signal end of response
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

    // This method encapsulates the MapReduce logic for sales analytics
    private String processSalesAnalytics(String command, String data) {
        Map<String, Integer> salesByStore = new HashMap<>();
        Set<String> processedStores = new HashSet<>(); // Use Set to avoid duplicates from replicas
        int total = 0;

        String workerCommand = command + (data.isEmpty() ? "" : " " + data);
        System.out.println("Reducer querying workers with command: " + workerCommand);

        for (WorkerConnection worker : workers) {
            try {
                String response = worker.sendRequest(workerCommand);
                System.out.println("Reducer received from worker " + worker.getPort() + ": " + response);

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
                                    System.err.println("Reducer: Invalid amount format from worker " + worker.getPort() + " for store '" + storeName + "': " + storeParts[1]);
                                }
                            } else {
                                System.err.println("Reducer: Invalid store:amount format from worker " + worker.getPort() + ": " + storeSale);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Reducer: Error communicating with worker " + worker.getPort() + " for command '" + command + "': " + e.getMessage());
                // Continue to next worker if one fails
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

    // Process manager operation results
    private String processAddStoreResults(String data) {
        boolean anySuccess = false;
        String successStoreName = "";
        StringBuilder errorMessages = new StringBuilder();
        
        for (WorkerConnection worker : workers) {
            try {
                String response = worker.sendRequest("ADD_STORE " + data);
                if (response != null && response.startsWith("SUCCESS|")) {
                    anySuccess = true;
                    successStoreName = response.split("\\|")[1];
                } else if (response != null && response.startsWith("ERROR|")) {
                    if (errorMessages.length() > 0) errorMessages.append("; ");
                    errorMessages.append(response.substring(6));
                }
            } catch (IOException e) {
                System.err.println("Reducer: Error communicating with worker " + worker.getPort() + ": " + e.getMessage());
            }
        }
        
        if (anySuccess) {
            return "Store added: " + successStoreName;
        } else {
            return "Error adding store: " + (errorMessages.length() > 0 ? errorMessages.toString() : "Unknown error");
        }
    }
    
    private String processAddProductResults(String data) {
        boolean anySuccess = false;
        String successStoreName = "";
        String successProductName = "";
        StringBuilder errorMessages = new StringBuilder();
        
        for (WorkerConnection worker : workers) {
            try {
                String response = worker.sendRequest("ADD_PRODUCT " + data);
                if (response != null && response.startsWith("SUCCESS|")) {
                    anySuccess = true;
                    String[] parts = response.split("\\|");
                    successStoreName = parts[1];
                    successProductName = parts.length > 2 ? parts[2] : "";
                } else if (response != null && response.startsWith("ERROR|")) {
                    if (errorMessages.length() > 0) errorMessages.append("; ");
                    errorMessages.append(response.substring(6));
                }
            } catch (IOException e) {
                System.err.println("Reducer: Error communicating with worker " + worker.getPort() + ": " + e.getMessage());
            }
        }
        
        if (anySuccess) {
            return "Product added to store: " + successStoreName;
        } else {
            return "Error adding product: " + (errorMessages.length() > 0 ? errorMessages.toString() : "Unknown error");
        }
    }
    
    private String processRemoveProductResults(String data) {
        boolean anySuccess = false;
        String successStoreName = "";
        String successProductName = "";
        StringBuilder errorMessages = new StringBuilder();
        
        for (WorkerConnection worker : workers) {
            try {
                String response = worker.sendRequest("REMOVE_PRODUCT " + data);
                if (response != null && response.startsWith("SUCCESS|")) {
                    anySuccess = true;
                    String[] parts = response.split("\\|");
                    successStoreName = parts[1];
                    successProductName = parts.length > 2 ? parts[2] : "";
                } else if (response != null && response.startsWith("ERROR|")) {
                    if (errorMessages.length() > 0) errorMessages.append("; ");
                    errorMessages.append(response.substring(6));
                }
            } catch (IOException e) {
                System.err.println("Reducer: Error communicating with worker " + worker.getPort() + ": " + e.getMessage());
            }
        }
        
        if (anySuccess) {
            return "Product removed from store: " + successStoreName;
        } else {
            return "Error removing product: " + (errorMessages.length() > 0 ? errorMessages.toString() : "Unknown error");
        }
    }
    
    // Process client operation results
    private String processFilterStoresResults(String data) {
        // Combine store results from all workers, remove duplicates
        Map<String, String> storeMap = new HashMap<>(); // StoreName -> StoreJSON
        
        for (WorkerConnection worker : workers) {
            try {
                String response = worker.sendRequest("FILTER_STORES " + data);
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
            } catch (IOException e) {
                System.err.println("Reducer: Error communicating with worker " + worker.getPort() + ": " + e.getMessage());
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
    
    private String processFindStoresResults(String data) {
        // Similar to filter stores, combine results and remove duplicates
        Map<String, String> storeMap = new HashMap<>(); // StoreName -> StoreJSON
        
        for (WorkerConnection worker : workers) {
            try {
                String response = worker.sendRequest("FIND_STORES_WITHIN_RANGE " + data);
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
            } catch (IOException e) {
                System.err.println("Reducer: Error communicating with worker " + worker.getPort() + ": " + e.getMessage());
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
    
    private String processGetStoreDetailsResults(String storeName) {
        // Pick the first valid response from any replica
        for (WorkerConnection worker : workers) {
            try {
                String response = worker.sendRequest("GET_STORE_DETAILS " + storeName);
                if (response != null && !response.isEmpty() && !response.startsWith("ERROR|")) {
                    return response; // Return first valid store details
                }
            } catch (IOException e) {
                System.err.println("Reducer: Error communicating with worker " + worker.getPort() + ": " + e.getMessage());
            }
        }
        
        return "Error: Store not found or could not retrieve store details.";
    }
    
    private String processPurchaseResults(String data) {
        boolean anySuccess = false;
        String successStoreName = "";
        String successProductName = "";
        int successQuantity = 0;
        StringBuilder errorMessages = new StringBuilder();
        
        for (WorkerConnection worker : workers) {
            try {
                String response = worker.sendRequest("BUY " + data);
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
            } catch (IOException e) {
                System.err.println("Reducer: Error communicating with worker " + worker.getPort() + ": " + e.getMessage());
            }
        }
        
        if (anySuccess) {
            return "Purchase completed: " + successQuantity + " of " + successProductName + " from " + successStoreName;
        } else {
            return "Error: Purchase failed - " + (errorMessages.length() > 0 ? errorMessages.toString() : "Unknown error");
        }
    }
    
    private String processReviewResults(String data) {
        boolean anySuccess = false;
        String successStoreName = "";
        float newRating = 0;
        int newVotes = 0;
        StringBuilder errorMessages = new StringBuilder();
        
        for (WorkerConnection worker : workers) {
            try {
                String response = worker.sendRequest("REVIEW " + data);
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
            } catch (IOException e) {
                System.err.println("Reducer: Error communicating with worker " + worker.getPort() + ": " + e.getMessage());
            }
        }
        
        if (anySuccess) {
            return "Review submitted for store: " + successStoreName + " (new rating: " + newRating + " from " + newVotes + " votes)";
        } else {
            return "Error: Failed to submit review - " + (errorMessages.length() > 0 ? errorMessages.toString() : "Unknown error");
        }
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