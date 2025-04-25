package com.example.backend;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Master {
    private static final int PORT = 8080;
    private static final int REPLICATION_FACTOR = 3;
    private List<WorkerConnection> workers;
    private List<Process> workerProcesses;
    private Map<String, List<WorkerConnection>> storeToWorkers;
    private ScheduledExecutorService heartbeatScheduler;
    private PrintWriter out;

public Master(int startPort) throws IOException {
    workers = new ArrayList<>();
    workerProcesses = new ArrayList<>();
    storeToWorkers = new HashMap<>();

    out = new PrintWriter(System.out, true);

    deleteDirectory(new File("data/temp_workers_data"));

    // Count stores and calculate dynamic worker count
    int storeCount = countStoresInJsonFile();
    int workerCount = Math.max(1, (int)Math.sqrt(storeCount));

    System.out.println("Initializing " + workerCount + " workers for " + storeCount + " stores");

    for (int i = 0; i < workerCount; i++) {
        int workerPort = startPort + i;
        spawnWorker(workerPort);
        WorkerConnection wc = new WorkerConnection("localhost", workerPort);
        workers.add(wc);
        System.out.println("Started and connected to worker at localhost:" + workerPort);
    }

    loadInitialStores();
    startHeartbeat();
}

private int countStoresInJsonFile() {
    try {
        File storesFile = new File("data/stores.json");
        if (!storesFile.exists()) {
            System.out.println("No stores.json file found, using default worker count");
            return 2; // Default if file not found
        }

        String jsonContent = new String(Files.readAllBytes(Paths.get("data/stores.json"))).trim();
        if (!jsonContent.startsWith("[") || !jsonContent.endsWith("]")) {
            System.err.println("Invalid JSON format in stores.json");
            return 2; // Default if invalid format
        }

        List<String> storeJsons = parseStoreJsons(jsonContent);
        return storeJsons.size();
    } catch (IOException e) {
        System.err.println("Error reading stores.json: " + e.getMessage());
        return 2; // Default on error
    }
}

    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    private void loadInitialStores() throws IOException {
        File storesFile = new File("data/stores.json");
        try (BufferedReader reader = new BufferedReader(new FileReader(storesFile))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) content.append(line).append("\n");

        }

        if (!storesFile.exists()) {
            System.out.println("No initial stores found in data/stores.json");
            return;
        }
        String jsonContent = new String(Files.readAllBytes(Paths.get("data/stores.json"))).trim();
        if (!jsonContent.startsWith("[") || !jsonContent.endsWith("]")) {
            System.err.println("Invalid JSON format in stores.json");
            return;
        }

        List<String> storeJsons = parseStoreJsons(jsonContent);
        for (String storeJson : storeJsons) {
            String storeName = extractField(storeJson, "StoreName");
            if (storeName.isEmpty()) {
                System.err.println("Failed to extract StoreName from: " + storeJson);
                continue;
            }

            List<WorkerConnection> assignedWorkers = getWorkersForStore(storeName);
            for (WorkerConnection worker : assignedWorkers) {
                try {
                    worker.sendRequest("ADD_STORE " + storeJson);
                } catch (IOException e) {
                    System.err.println("Failed to send store to worker: " + e.getMessage());
                }
            }
        }
    }

    private List<String> parseStoreJsons(String jsonContent) {
        List<String> stores = new ArrayList<>();
        jsonContent = jsonContent.substring(1, jsonContent.length() - 1).trim();
        int braceCount = 0;
        int start = -1;
        for (int i = 0; i < jsonContent.length(); i++) {
            char c = jsonContent.charAt(i);
            if (c == '{') {
                if (braceCount == 0) start = i;
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0 && start != -1) {
                    String storeJson = jsonContent.substring(start, i + 1).trim();
                    stores.add(storeJson);
                    start = -1;
                }
            }
        }
        return stores;
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

    private void spawnWorker(int port) throws IOException {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        String className = Worker.class.getName();

        ProcessBuilder pb = new ProcessBuilder(
                javaBin, "-cp", classpath, className, String.valueOf(port)
        );
        pb.inheritIO();
        Process process = pb.start();
        workerProcesses.add(process);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Master Server running on port " + PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected: " + socket.getInetAddress());
                new MasterThread(socket, workers, storeToWorkers, REPLICATION_FACTOR).start();
            }
        } catch (IOException e) {
            System.err.println("Master server failed: " + e.getMessage());
        } finally {
            shutdownWorkers();
        }
    }

    private void shutdownWorkers() {
        for (Process p : workerProcesses) {
            p.destroy();
        }
        for (WorkerConnection wc : workers) {
            try {
                wc.close();
            } catch (IOException e) {
                System.err.println("Error closing worker connection: " + e.getMessage());
            }
        }
        heartbeatScheduler.shutdown();
    }

    private List<WorkerConnection> getWorkersForStore(String storeName) {
        int primaryIndex = Math.abs(storeName.hashCode()) % workers.size();
        List<WorkerConnection> assignedWorkers = new ArrayList<>();
        for (int i = 0; i < REPLICATION_FACTOR && i < workers.size(); i++) {
            int index = (primaryIndex + i) % workers.size();
            assignedWorkers.add(workers.get(index));
        }
        storeToWorkers.put(storeName, assignedWorkers);
        return assignedWorkers;
    }

    private void startHeartbeat() {
        heartbeatScheduler = Executors.newScheduledThreadPool(1);
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            for (WorkerConnection w : workers) {
                try {
                    String response = w.sendRequest("PING"); // Capture the response
                    System.out.println("Master received from worker " + w.getPort() + ": " + response); // Print the response
                } catch (IOException e) {
                    System.err.println("Worker at " + w.getPort() + " is down");
                }
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

public static void main(String[] args) {
    try {
        int startPort = 8081;
        Master master = new Master(startPort);
        master.start();
    } catch (IOException e) {
        System.err.println("Failed to initialize Master: " + e.getMessage());
    }
}
}

class MasterThread extends Thread {
    private Socket socket;
    private List<WorkerConnection> workers;
    private Map<String, List<WorkerConnection>> storeToWorkers;
    private final int replicationFactor;

    public MasterThread(Socket socket, List<WorkerConnection> workers, Map<String, List<WorkerConnection>> storeToWorkers, int replicationFactor) {
        this.socket = socket;
        this.workers = workers;
        this.storeToWorkers = storeToWorkers;
        this.replicationFactor = replicationFactor;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            String request;
            while ((request = in.readLine()) != null) {
                System.out.println("Received command: " + request);
                if (workers.isEmpty()) {
                    out.println("No workers available to process request: " + request);
                    continue;
                }
                String[] parts = request.split(" ", 2);
                String command = parts[0];
                String data = parts.length > 1 ? parts[1] : "";

                Map<String, Integer> salesByStore = new HashMap<>();

                switch (command) {
                    // ########################### MANAGER COMMANDS ###########################
                    case "ADD_STORE":
                        String storeName = extractField(data, "StoreName");
                        System.out.println("ADD_STORE storeName: [" + storeName + "]");
                        if (storeName.isEmpty()) {
                            out.println("Error: Invalid store JSON");
                            out.println("END");
                            continue;
                        }
                        List<WorkerConnection> assignedWorkers = getWorkersForStore(storeName);
                        for (WorkerConnection worker : assignedWorkers) {
                            try {
                                worker.sendRequest("ADD_STORE " + data);
                            } catch (IOException e) {
                                System.err.println("Failed to send store to worker: " + e.getMessage());
                            }
                        }
                        StringBuilder storeResult = new StringBuilder();
                        storeResult.append("Store added: ").append(storeName).append("\n");
                        storeResult.append("END");
                        out.println(storeResult.toString());
                        break;

                    case "ADD_PRODUCT":
                        String[] productParts = data.split(",");
                        if (productParts.length < 5) {
                            out.println("Invalid ADD_PRODUCT format");
                            out.println("END");
                            continue;
                        }
                        String storeNameProd = productParts[0].trim();


                        List<WorkerConnection> prodWorkers = storeToWorkers.get(storeNameProd);
                        if (prodWorkers == null) {

                            prodWorkers = storeToWorkers.get("\"" + storeNameProd + "\"");
                        }

                        if (prodWorkers == null) {
                            out.println("Store not found: " + storeNameProd);
                            out.println("END");
                            continue;
                        }
                        for (WorkerConnection worker : prodWorkers) {
                            try {
                                worker.sendRequest(request);
                            } catch (IOException e) {
                                System.err.println("Failed to add product to worker: " + e.getMessage());
                            }
                        }
                        StringBuilder prodResult = new StringBuilder();
                        prodResult.append("Product added to store: ").append(storeNameProd).append("\n");
                        prodResult.append("END");
                        out.println(prodResult.toString());
                        break;

                    case "REMOVE_PRODUCT":
                        String[] removeParts = data.split(",");
                        if (removeParts.length < 2) {
                            out.println("Invalid REMOVE_PRODUCT format");
                            out.println("END");
                            continue;
                        }
                        String removeStoreName = removeParts[0].trim();


                        List<WorkerConnection> removeWorkers = storeToWorkers.get(removeStoreName);
                        if (removeWorkers == null) {

                            removeWorkers = storeToWorkers.get("\"" + removeStoreName + "\"");
                        }

                        if (removeWorkers == null) {
                            out.println("Store not found: " + removeStoreName);
                            out.println("END");
                            continue;
                        }

                        for (WorkerConnection worker : removeWorkers) {
                            try {
                                worker.sendRequest(request);
                            } catch (IOException e) {
                                System.err.println("Failed to remove product from worker: " + e.getMessage());
                            }
                        }
                        StringBuilder removeResult = new StringBuilder();
                        removeResult.append("Product removed from store: ").append(removeStoreName).append("\n");
                        removeResult.append("END");
                        out.println(removeResult.toString());
                        break;
                    case "GET_SALES_BY_STORE_TYPE_CATEGORY":
                            String foodCategory = parts.length > 1 ? parts[1].trim() : "";
                            System.out.println("Processing GET_SALES_BY_STORE_TYPE_CATEGORY request for category: " + foodCategory);

                            //MapReduce

                            Set<String> processedStores = new HashSet<>();
                            int total = 0;


                            for (WorkerConnection worker : workers) {
                                try {
                                    String response = worker.sendRequest("GET_SALES_BY_STORE_TYPE_CATEGORY " + foodCategory);


                                    if (!response.isEmpty()) {
                                        String[] storesSales = response.split("\\|");
                                        for (String storeSale : storesSales) {
                                            if (!storeSale.isEmpty()) {
                                                String[] storeParts = storeSale.split(":");
                                                if (storeParts.length == 2) {
                                                    String store = storeParts[0];
                                                    int amount = Integer.parseInt(storeParts[1]);

                                                    if (!processedStores.contains(store)) {
                                                        salesByStore.put(store, amount);
                                                        total += amount;
                                                        processedStores.add(store);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (IOException e) {
                                    System.err.println("Error communicating with worker for sales by store type: " + e.getMessage());
                                }
                            }


                            StringBuilder result = new StringBuilder();
                            for (Map.Entry<String, Integer> entry : salesByStore.entrySet()) {
                                if (result.length() > 0) {
                                    result.append("\n");
                                }
                                result.append("\"").append(entry.getKey()).append("\": ").append(entry.getValue());
                            }
                            if (!salesByStore.isEmpty()) {
                                result.append("\n");
                            }
                            result.append("\"total\": ").append(total);

                            System.out.println("Sending sales by store type category results: " + result);
                            out.println(result.toString());
                            out.println("END");
                            break;
                    case "GET_SALES_BY_PRODUCT_CATEGORY":
                                String productCategory = parts.length > 1 ? parts[1].trim() : "";
                                System.out.println("Processing GET_SALES_BY_PRODUCT_CATEGORY request for category: " + productCategory);

                                //MapReduce
                                Map<String, Integer> salesByStoreProdCat = new HashMap<>();
                                Set<String> processedStoresProdCat = new HashSet<>();
                                int totalProdCat = 0;

                                for (WorkerConnection worker : workers) {
                                    try {
                                        String response = worker.sendRequest("GET_SALES_BY_PRODUCT_CATEGORY " + productCategory);

                                        if (!response.isEmpty()) {
                                            String[] storesSales = response.split("\\|");
                                            for (String storeSale : storesSales) {
                                                if (!storeSale.isEmpty()) {
                                                    String[] storeParts = storeSale.split(":");
                                                    if (storeParts.length == 2) {
                                                        String store = storeParts[0];
                                                        int amount = Integer.parseInt(storeParts[1]);

                                                        if (!processedStoresProdCat.contains(store)) {
                                                            salesByStoreProdCat.put(store, amount);
                                                            totalProdCat += amount;
                                                            processedStoresProdCat.add(store);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } catch (IOException e) {
                                        System.err.println("Error communicating with worker for sales by product category: " + e.getMessage());
                                    }
                                }

                                StringBuilder resultProdCat = new StringBuilder();
                                for (Map.Entry<String, Integer> entry : salesByStoreProdCat.entrySet()) {
                                    if (resultProdCat.length() > 0) {
                                        resultProdCat.append("\n");
                                    }
                                    resultProdCat.append("\"").append(entry.getKey()).append("\": ").append(entry.getValue());
                                }
                                if (!salesByStoreProdCat.isEmpty()) {
                                    resultProdCat.append("\n");
                                }
                                resultProdCat.append("\"total\": ").append(totalProdCat);

                                System.out.println("Sending sales by product category results: " + resultProdCat);
                                out.println(resultProdCat.toString());
                                out.println("END");
                                break;

                    case "GET_SALES_BY_PRODUCT":
                        String productName = parts.length > 1 ? parts[1].trim() : "";
                        System.out.println("Processing GET_SALES_BY_PRODUCT request for product: " + productName);

                        //MapReduce
                        Map<String, Integer> salesByStoreProd = new HashMap<>();
                        Set<String> processedStoresProd = new HashSet<>();
                        int totalProd = 0;

                        for (WorkerConnection worker : workers) {
                            try {
                                String response = worker.sendRequest("GET_SALES_BY_PRODUCT " + productName);

                                if (!response.isEmpty()) {
                                    String[] storesSales = response.split("\\|");
                                    for (String storeSale : storesSales) {
                                        if (!storeSale.isEmpty()) {
                                            String[] storeParts = storeSale.split(":");
                                            if (storeParts.length == 2) {
                                                String store = storeParts[0];
                                                int amount = Integer.parseInt(storeParts[1]);

                                                if (!processedStoresProd.contains(store)) {
                                                    salesByStoreProd.put(store, amount);
                                                    totalProd += amount;
                                                    processedStoresProd.add(store);
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (IOException e) {
                                System.err.println("Error communicating with worker for sales by product: " + e.getMessage());
                            }
                        }

                        StringBuilder resultProd = new StringBuilder();
                        for (Map.Entry<String, Integer> entry : salesByStoreProd.entrySet()) {
                            if (resultProd.length() > 0) {
                                resultProd.append("\n");
                            }
                            resultProd.append("\"").append(entry.getKey()).append("\": ").append(entry.getValue());
                        }
                        if (!salesByStoreProd.isEmpty()) {
                            resultProd.append("\n");
                        }
                        resultProd.append("\"total\": ").append(totalProd);

                        System.out.println("Sending sales by product results: " + resultProd);
                        out.println(resultProd.toString());
                        out.println("END");
                        break;

                    // ########################### CLIENT COMMANDS ###########################
                    case "FIND_STORES_WITHIN_RANGE":
                        String[] coords = data.split(",");
                        if (coords.length != 2) {
                            out.println("Invalid coordinates format");
                            out.println("END");
                            continue;
                        }

                        StringBuilder combinedStoresJson = new StringBuilder("[");
                        boolean firstStore = true;
                        Set<String> addedStoreNames = new HashSet<>(); // Track stores we've already added

                        for (WorkerConnection worker : workers) {
                            try {
                                String response = worker.sendRequest("FIND_STORES_WITHIN_RANGE " + data);
                                if (response != null && !response.isEmpty() && response.startsWith("[") && response.endsWith("]")) {
                                    // Extract store objects from the JSON array
                                    String storesContent = response.substring(1, response.length() - 1).trim();
                                    if (!storesContent.isEmpty()) {
                                        // Split by valid JSON objects
                                        List<String> storeObjects = splitJsonObjects(storesContent);

                                        for (String storeJson : storeObjects) {
                                            // Extract store name to check for duplicates
                                            String sName = extractField(storeJson, "StoreName");
                                            if (!addedStoreNames.contains(sName)) {
                                                if (!firstStore) {
                                                    combinedStoresJson.append(",");
                                                } else {
                                                    firstStore = false;
                                                }
                                                combinedStoresJson.append(storeJson);
                                                addedStoreNames.add(sName);
                                            }
                                        }
                                    }
                                }
                            } catch (IOException e) {
                                System.err.println("Error communicating with worker: " + e.getMessage());
                            }
                        }

                        combinedStoresJson.append("]");
                        out.println(combinedStoresJson.toString());
                        out.println("END");
                        break;
                    case "FILTER_STORES":
                            System.out.println("Processing filter request: " + data);
                            Map<String, List<String>> filters = parseFilterString(data);

                            List<String> individualStores = new ArrayList<>();
                            Set<String> processedStoreNames = new HashSet<>();

                            for (WorkerConnection worker : workers) {
                                try {
                                    String response = worker.sendRequest(request);
                                    if (response != null && !response.isEmpty()) {
                                        // Check if response is a JSON array
                                        if (response.startsWith("[") && response.endsWith("]")) {
                                            // Extract individual store objects from the array
                                            List<String> storeObjects = splitJsonObjects(
                                                response.substring(1, response.length() - 1).trim()
                                            );

                                            for (String storeJson : storeObjects) {
                                                String sName = extractField(storeJson, "StoreName");
                                                if (!processedStoreNames.contains(sName)) {
                                                    individualStores.add(storeJson);
                                                    processedStoreNames.add(sName);
                                                }
                                            }
                                        }
                                    }
                                } catch (IOException e) {
                                    System.err.println("Error communicating with worker for filter: " + e.getMessage());
                                }
                            }

                            if (individualStores.isEmpty()) {
                                out.println("No stores found with the specified filters.");
                            } else {
                                for (String store : individualStores) {
                                    out.println(store);
                                }
                            }
                            out.println("END");
                            break;
                    case "BUY":
                        String[] buyParts = data.split(",");
                        if (buyParts.length < 3) {
                            out.println("Invalid BUY format");
                            out.println("END");
                            continue;
                        }
                        String buyStoreName = buyParts[0].trim();
                        String buyProductName = buyParts[1].trim();
                        int buyQuantity = Integer.parseInt(buyParts[2].trim());

                        List<WorkerConnection> buyWorkers = storeToWorkers.get(buyStoreName);
                        if (buyWorkers == null) {
                            buyWorkers = storeToWorkers.get("\"" + buyStoreName + "\"");
                        }

                        if (buyWorkers == null) {
                            out.println("Store not found: " + buyStoreName);
                            out.println("END");
                            continue;
                        }

                        for (WorkerConnection worker : buyWorkers) {
                            try {
                                worker.sendRequest("BUY " + data);
                            } catch (IOException e) {
                                System.err.println("Failed to send purchase to worker: " + e.getMessage());
                            }
                        }

                        out.println("Purchase completed: " + buyQuantity + " of " + buyProductName + " from " + buyStoreName);
                        out.println("END");
                        break;
                    case "GET_STORE_DETAILS":
                        String detailsStoreName = data.trim();
                        System.out.println("Processing GET_STORE_DETAILS for: " + detailsStoreName);


                        List<WorkerConnection> storeWorkers = storeToWorkers.get(detailsStoreName);
                        if (storeWorkers == null) {
                            storeWorkers = storeToWorkers.get("\"" + detailsStoreName + "\"");
                        }

                        if (storeWorkers == null) {
                            out.println("Error: Store not found");
                            out.println("END");
                            continue;
                        }

                        String storeDetails = null;
                        for (WorkerConnection worker : storeWorkers) {
                            try {
                                storeDetails = worker.sendRequest("GET_STORE_DETAILS " + detailsStoreName);
                                if (storeDetails != null && !storeDetails.isEmpty()) {
                                    break;
                                }
                            } catch (IOException e) {
                                System.err.println("Failed to get store details from worker: " + e.getMessage());
                            }
                        }

                        if (storeDetails == null || storeDetails.isEmpty()) {
                            out.println("Error: Could not retrieve store details");
                        } else {
                            out.println(storeDetails);
                        }
                        System.out.println("Sending store details from Master: " + storeDetails);
                        out.println("END");
                        break;
                    case "REVIEW":
                        String[] reviewParts = data.split(",");
                        if (reviewParts.length < 2) {
                            out.println("Invalid REVIEW format");
                            out.println("END");
                            continue;
                        }
                        String reviewStoreName = reviewParts[0].trim();
                        int rating = Integer.parseInt(reviewParts[1].trim());

                        List<WorkerConnection> reviewWorkers = storeToWorkers.get(reviewStoreName);
                        if (reviewWorkers == null) {
                            reviewWorkers = storeToWorkers.get("\"" + reviewStoreName + "\"");
                        }

                        if (reviewWorkers == null) {
                            out.println("Store not found: " + reviewStoreName);
                            out.println("END");
                            continue;
                        }

                        for (WorkerConnection worker : reviewWorkers) {
                            try {
                                worker.sendRequest(request);
                            } catch (IOException e) {
                                System.err.println("Failed to send review to worker: " + e.getMessage());
                            }
                        }

                        out.println("Review submitted for store: " + reviewStoreName);
                        out.println("END");
                        break;
                    default:
                        out.println("Unknown command: " + command);
                }
            }
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
        }
    }

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

    private Map<String, List<String>> parseFilterString(String filterData) {
        Map<String, List<String>> filters = new HashMap<>();
        String[] filterGroups = filterData.split(";");

        for (String group : filterGroups) {
            if (group.isEmpty()) continue;

            String[] parts = group.split(":");
            if (parts.length == 2) {
                String key = parts[0];
                String[] values = parts[1].split(",");
                List<String> valueList = new ArrayList<>();
                for (String value : values) {
                    if (!value.isEmpty()) {
                        valueList.add(value);
                    }
                }
                filters.put(key, valueList);
            }
        }

        return filters;
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

    private List<WorkerConnection> getWorkersForStore(String storeName) {
        return storeToWorkers.computeIfAbsent(storeName, k -> {
            int primaryIndex = Math.abs(storeName.hashCode()) % workers.size();
            List<WorkerConnection> assigned = new ArrayList<>();
            for (int i = 0; i < replicationFactor && i < workers.size(); i++) {
                int index = (primaryIndex + i) % workers.size();
                assigned.add(workers.get(index));
            }
            return assigned;
        });
    }
}