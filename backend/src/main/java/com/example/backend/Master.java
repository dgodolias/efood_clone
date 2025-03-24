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
    private static final int REPLICATION_FACTOR = 2;
    private List<WorkerConnection> workers;
    private List<Process> workerProcesses;
    private Map<String, List<WorkerConnection>> storeToWorkers;
    private ScheduledExecutorService heartbeatScheduler;

    public Master(int workerCount, int startPort) throws IOException {
        workers = new ArrayList<>();
        workerProcesses = new ArrayList<>();
        storeToWorkers = new HashMap<>();

        deleteDirectory(new File("data/temp_workers_data"));

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
                    w.sendRequest("PING");
                } catch (IOException e) {
                    System.err.println("Worker at " + w.getPort() + " is down");
                }
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    public static void main(String[] args) {
        try {
            int workerCount = args.length > 0 ? Integer.parseInt(args[0]) : 2;
            int startPort = 8081;
            Master master = new Master(workerCount, startPort);
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

                        // Check the store name with and without quotes
                        List<WorkerConnection> prodWorkers = storeToWorkers.get(storeNameProd);
                        if (prodWorkers == null) {
                            // Try with quotes
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

                        // Check the store name with and without quotes
                        List<WorkerConnection> removeWorkers = storeToWorkers.get(removeStoreName);
                        if (removeWorkers == null) {
                            // Try with quotes
                            removeWorkers = storeToWorkers.get("\"" + removeStoreName + "\"");
                        }

                        if (removeWorkers == null) {
                            out.println("Store not found: " + removeStoreName);
                            out.println("END");
                            continue;
                        }
                        // Rest of the code remains the same...
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
                        String foodCategory = data;

                        int total = 0;
                        for (WorkerConnection worker : workers) {
                            try {
                                String response = worker.sendRequest("GET_SALES_BY_STORE_TYPE_CATEGORY " + foodCategory);
                                if (response.isEmpty()) continue;
                                String[] sales = response.split("\\|");
                                for (String sale : sales) {
                                    int lastColon = sale.lastIndexOf(":");
                                    if (lastColon != -1) {
                                        String store = sale.substring(0, lastColon).trim();
                                        String amountStr = sale.substring(lastColon + 1).trim();
                                        try {
                                            int amount = Integer.parseInt(amountStr);
                                            salesByStore.put(store, salesByStore.getOrDefault(store, 0) + amount);
                                            total += amount;
                                        } catch (NumberFormatException e) {
                                            System.err.println("Invalid amount format: " + amountStr);
                                        }
                                    }
                                }
                            } catch (IOException e) {
                                System.err.println("Failed to get sales from worker: " + e.getMessage());
                            }
                        }
                        StringBuilder result = new StringBuilder();
                        for (Map.Entry<String, Integer> entry : salesByStore.entrySet()) {
                            result.append("\"").append(entry.getKey()).append("\": ").append(entry.getValue()).append("\n");
                        }
                        result.append("\"total\": ").append(total).append("\n");
                        result.append("END");
                        out.println(result.toString());
                        break;
                    case "GET_SALES_BY_PRODUCT_CATEGORY":
                        String productCategory = data;

                        int totalCategory = 0;
                        for (WorkerConnection worker : workers) {
                            try {
                                String response = worker.sendRequest("GET_SALES_BY_PRODUCT_CATEGORY " + productCategory);
                                if (response.isEmpty()) continue;
                                String[] sales = response.split("\\|");
                                for (String sale : sales) {
                                    int lastColon = sale.lastIndexOf(":");
                                    if (lastColon != -1) {
                                        String store = sale.substring(0, lastColon).trim();
                                        String amountStr = sale.substring(lastColon + 1).trim();
                                        try {
                                            int amount = Integer.parseInt(amountStr);
                                            salesByStore.put(store, salesByStore.getOrDefault(store, 0) + amount);
                                            totalCategory += amount;
                                        } catch (NumberFormatException e) {
                                            System.err.println("Invalid amount format: " + amountStr);
                                        }
                                    }
                                }
                            } catch (IOException e) {
                                System.err.println("Failed to get sales from worker: " + e.getMessage());
                            }
                        }
                        StringBuilder categoryResult = new StringBuilder();
                        for (Map.Entry<String, Integer> entry : salesByStore.entrySet()) {
                            categoryResult.append("\"").append(entry.getKey()).append("\": ").append(entry.getValue()).append("\n");
                        }
                        categoryResult.append("\"total\": ").append(totalCategory).append("\n");
                        categoryResult.append("END");
                        out.println(categoryResult.toString());
                        break;
                    case "GET_SALES_BY_PRODUCT":
                        String productName = data;
                        Map<String, Integer> productSalesByStore = new HashMap<>();
                        int totalProductSales = 0;

                        for (WorkerConnection worker : workers) {
                            try {
                                String response = worker.sendRequest("GET_SALES_BY_PRODUCT " + productName);
                                System.out.println("Worker on port " + worker.getPort() + " reported: " + response);

                                // Parse the pipe-delimited response
                                if (!response.isEmpty()) {
                                    String[] storeSales = response.split("\\|");
                                    for (String storeSale : storeSales) {
                                        int colonIndex = storeSale.lastIndexOf(":");
                                        if (colonIndex > 0) {
                                            String store = storeSale.substring(0, colonIndex).trim();
                                            int amount = Integer.parseInt(storeSale.substring(colonIndex + 1).trim());
                                            productSalesByStore.put(store, productSalesByStore.getOrDefault(store, 0) + amount);
                                            totalProductSales += amount;
                                        }
                                    }
                                }
                            } catch (IOException e) {
                                System.err.println("Failed to get sales from worker: " + e.getMessage());
                            }
                        }

                        // Format the response like GET_SALES_BY_PRODUCT_CATEGORY
                        StringBuilder productResult = new StringBuilder();
                        for (Map.Entry<String, Integer> entry : productSalesByStore.entrySet()) {
                            productResult.append("\"").append(entry.getKey()).append("\": ").append(entry.getValue()).append("\n");
                        }
                        productResult.append("\"total\": ").append(totalProductSales).append("\n");
                        productResult.append("END");
                        out.println(productResult.toString());
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