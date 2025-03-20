package com.example.backend;

import java.io.*;
import java.net.*;
import java.util.*;

public class Worker {
    private Map<String, Store> stores;
    private String tempDir;

    public Worker(int port) {
        this.stores = new HashMap<>();
        this.tempDir = "data/temp_workers_data/worker_" + port;
        new File(tempDir).mkdirs();
        new File(tempDir + "/stores.txt").delete();
    }

    public void start(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Worker running on port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Master connected: " + socket.getInetAddress());
                new WorkerThread(socket, stores, tempDir).start();
            }
        } catch (IOException e) {
            System.err.println("Worker server failed on port " + port + ": " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Worker <port>");
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);
        Worker worker = new Worker(port);
        worker.start(port);
    }
}

class WorkerThread extends Thread {
    private Socket socket;
    private Map<String, Store> stores;
    private String tempDir;

    public WorkerThread(Socket socket, Map<String, Store> stores, String tempDir) {
        this.socket = socket;
        this.stores = stores;
        this.tempDir = tempDir;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            String request;
            while ((request = in.readLine()) != null) {
                String[] parts = request.split(" ", 2);
                String command = parts[0];
                String data = parts.length > 1 ? parts[1] : "";
                synchronized (stores) {
                    switch (command) {
                        case "ADD_STORE":
                            String storeName = extractField(data, "StoreName");
                            if (storeName.isEmpty()) {
                                out.println("Error: Invalid store JSON - missing StoreName");
                                break;
                            }
                            double latitude = Double.parseDouble(extractField(data, "Latitude"));
                            double longitude = Double.parseDouble(extractField(data, "Longitude"));
                            String foodCategory = extractField(data, "FoodCategory");
                            int stars = Integer.parseInt(extractField(data, "Stars"));
                            int noOfVotes = Integer.parseInt(extractField(data, "NoOfVotes"));
                            String storeLogo = extractField(data, "StoreLogo");
                            Store store = new Store(storeName, latitude, longitude, foodCategory, stars, noOfVotes, storeLogo);

                            String productsJson = extractProductsJson(data);
                            List<Product> products = parseProducts(productsJson);
                            for (Product p : products) {
                                store.addProduct(p);
                            }

                            stores.put(storeName, store);
                            updateStoresFile();
                            out.println("Store added: " + storeName);
                            break;
                        case "FILTER":
                            String filterCategory = data;
                            List<String> results = new ArrayList<>();
                            for (Store s : stores.values()) {
                                if (s.getFoodCategory().equals(filterCategory)) {
                                    results.add(s.getStoreName());
                                }
                            }
                            out.println(String.join("\n", results));
                            break;
                        case "BUY":
                            String[] buyParts = data.split(" ");
                            String buyStoreName = buyParts[0];
                            String productName = buyParts[1];
                            int quantity = Integer.parseInt(buyParts[2]);
                            Store buyStore = stores.get(buyStoreName);
                            if (buyStore != null) {
                                for (Product product : buyStore.getProducts()) {
                                    if (product.getProductName().equals(productName) && product.getAvailableAmount() >= quantity) {
                                        product.setAvailableAmount(product.getAvailableAmount() - quantity);
                                        out.println("Purchase successful");
                                        break;
                                    }
                                }
                            } else {
                                out.println("Store not found");
                            }
                            break;
                        case "GET_FOOD_STATS":
                            String category = data;
                            Map<String, Integer> stats = new HashMap<>();
                            int total = 0;
                            for (Store s : stores.values()) {
                                if (s.getFoodCategory().equals(category)) {
                                    int productCount = s.getProducts().size();
                                    stats.put(s.getStoreName(), productCount);
                                    total += productCount;
                                }
                            }
                            stats.put("total", total);
                            out.println(stats.toString());
                            break;
                        default:
                            out.println("Unknown command: " + command);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error handling request: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
        }
    }

    private String extractProductsJson(String json) {
        int start = json.indexOf("\"Products\":");
        if (start == -1) return "[]";
        start = json.indexOf("[", start);
        if (start == -1) return "[]";
        int braceCount = 1;
        int end = start + 1;
        while (end < json.length() && braceCount > 0) {
            char c = json.charAt(end);
            if (c == '[') braceCount++;
            else if (c == ']') braceCount--;
            end++;
        }
        return json.substring(start, end);
    }

    private List<Product> parseProducts(String productsJson) {
        List<Product> products = new ArrayList<>();
        if (productsJson.equals("[]")) return products;

        productsJson = productsJson.substring(1, productsJson.length() - 1).trim();
        if (productsJson.isEmpty()) return products;

        String[] productJsons = splitProducts(productsJson);
        for (String productJson : productJsons) {
            String productName = extractField(productJson, "ProductName");
            String productType = extractField(productJson, "ProductType");
            int availableAmount = Integer.parseInt(extractField(productJson, "Available Amount"));
            double price = Double.parseDouble(extractField(productJson, "Price"));
            products.add(new Product(productName, productType, availableAmount, price));
        }
        return products;
    }

    private String[] splitProducts(String productsJson) {
        List<String> productList = new ArrayList<>();
        int braceCount = 0;
        int start = 0;
        for (int i = 0; i < productsJson.length(); i++) {
            char c = productsJson.charAt(i);
            if (c == '{') braceCount++;
            else if (c == '}') braceCount--;

            if (braceCount == 0 && i > start) {
                String productJson = productsJson.substring(start, i + 1).trim();
                if (productJson.endsWith(",")) productJson = productJson.substring(0, productJson.length() - 1);
                productList.add(productJson);
                start = i + 1;
                while (start < productsJson.length() && productsJson.charAt(start) == ',') start++;
                i = start - 1;
            }
        }
        return productList.toArray(new String[0]);
    }

    private void updateStoresFile() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(tempDir + "/stores.txt"))) {
            for (String storeName : stores.keySet()) {
                writer.println(storeName);
            }
        }
    }

    private String extractField(String json, String field) {
        String search = "\"" + field + "\":";
        int start = json.indexOf(search);
        if (start == -1) return "";
        start += search.length();
        if (start >= json.length()) return "";

        char firstChar = json.charAt(start);
        if (firstChar == '"') {
            start++;
            int end = json.indexOf("\"", start);
            if (end == -1) return "";
            return json.substring(start, end);
        } else {
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            if (end == -1 || end > json.length()) return "";
            return json.substring(start, end).trim();
        }
    }
}