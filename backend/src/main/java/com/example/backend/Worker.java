package com.example.backend;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Worker {
    private Map<String, Store> stores;
    private String tempDir;

    public Worker(int port) {
        this.stores = new HashMap<>();
        this.tempDir = "data/temp_workers_data/worker_" + port;
        new File(tempDir).mkdirs();
        initializeStoresFile();
    }

    private void initializeStoresFile() {
        try {
            new File(tempDir).mkdirs();
            try (PrintWriter writer = new PrintWriter(new FileWriter(tempDir + "/memory.json"))) {

                writer.println("[]");
            }
            System.out.println("Created initial memory file at " + tempDir + "/memory.json");
        } catch (IOException e) {
            System.err.println("Error creating memory file: " + e.getMessage());
        }
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
            System.out.println("Worker on port " + socket.getLocalPort() + " waiting for request...");
            while ((request = in.readLine()) != null) {
                System.out.println("Worker on port " + socket.getLocalPort() + " received request: " + request);
                String[] parts = request.split(" ", 2);
                String command = parts[0];
                String data = parts.length > 1 ? parts[1] : "";
                synchronized (stores) {
                    switch (command) {
                        case "ADD_STORE":
                            String storeName = extractField(data, "StoreName");
                            if (storeName.isEmpty()) {
                                out.println("Error: Invalid store JSON - missing StoreName");
                                System.err.println("Failed to extract StoreName from: " + data);
                                break;
                            }
                            storeName = storeName.replaceAll("^\"|\"$", "");
                            System.out.println("Worker on port " + socket.getLocalPort() + " received store: " + storeName);

                            try {
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
                                System.out.println("Worker on port " + socket.getLocalPort() + " added store: " + storeName);

                                updateStoresFile();
                                out.println("Store added: " + storeName);
                            } catch (NumberFormatException e) {
                                System.err.println("Error parsing numeric field: " + e.getMessage());
                                out.println("Error: Failed to parse store data - " + e.getMessage());
                            } catch (Exception e) {
                                System.err.println("Error processing store data: " + e.getMessage());
                                out.println("Error: Failed to process store data - " + e.getMessage());
                            }
                            break;
                        case "ADD_PRODUCT":
                            String[] productParts = data.split(",");
                            if (productParts.length < 5) {
                                out.println("Invalid ADD_PRODUCT format");
                                continue;
                            }
                            String storeNameProd = productParts[0].trim();
                            String productNameAdd = productParts[1].trim();
                            String productType = productParts[2].trim();
                            int amount = Integer.parseInt(productParts[3].trim());
                            double price = Double.parseDouble(productParts[4].trim());
                            Store storeAdd = stores.get(storeNameProd);
                            if (storeAdd != null) {
                                Product newProduct = new Product(productNameAdd, productType, amount, price);
                                storeAdd.addProduct(newProduct);
                                updateStoresFile();
                                System.out.println("Adding product " + productNameAdd + " to store " + storeNameProd);
                                out.println("Product added to store: " + storeNameProd);
                            } else {
                                out.println("Store not found: " + storeNameProd);
                            }
                            break;
                        case "REMOVE_PRODUCT":
                            String[] removeParts = data.split(",");
                            if (removeParts.length < 2) {
                                out.println("Invalid REMOVE_PRODUCT format");
                                continue;
                            }
                            String removeStoreName = removeParts[0].trim();
                            String removeProductName = removeParts[1].trim();
                            Store removeStore = stores.get(removeStoreName);
                            if (removeStore != null) {
                                removeStore.removeProduct(removeProductName);
                                updateStoresFile();
                                System.out.println("Removing product " + removeProductName + " from store " + removeStoreName);
                                out.println("Product removed from store: " + removeStoreName);
                            } else {
                                out.println("Store not found: " + removeStoreName);
                            }
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
                            String buyProductName = buyParts[1];
                            int quantity = Integer.parseInt(buyParts[2]);
                            Store buyStore = stores.get(buyStoreName);
                            if (buyStore != null) {
                                for (Product product : buyStore.getProducts()) {
                                    if (product.getProductName().equals(buyProductName) && product.getAvailableAmount() >= quantity) {
                                        product.setAvailableAmount(product.getAvailableAmount() - quantity);
                                        buyStore.recordSale(buyProductName, quantity);
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
                        case "GET_SALES_STATS":
                            Map<String, Integer> totalSales = new ConcurrentHashMap<>();
                            for (Store s : stores.values()) {
                                for (Map.Entry<String, Integer> entry : s.getSales().entrySet()) {
                                    totalSales.put(entry.getKey(), totalSales.getOrDefault(entry.getKey(), 0) + entry.getValue());
                                }
                            }
                            StringBuilder salesResponse = new StringBuilder();
                            for (Map.Entry<String, Integer> entry : totalSales.entrySet()) {
                                salesResponse.append(entry.getKey()).append(":").append(entry.getValue()).append(" ");
                            }
                            String salesStr = salesResponse.toString().trim();
                            System.out.println("Returning sales stats: " + salesStr);
                            out.println(salesStr);
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
        productsJson = productsJson.trim();
        if (productsJson.startsWith("[")) {
            productsJson = productsJson.substring(1);
        }
        if (productsJson.endsWith("]")) {
            productsJson = productsJson.substring(0, productsJson.length() - 1);
        }

        int braceCount = 0;
        StringBuilder currentProduct = new StringBuilder();
        for (int i = 0; i < productsJson.length(); i++) {
            char c = productsJson.charAt(i);
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
            }
            currentProduct.append(c);

            if (braceCount == 0 && currentProduct.length() > 0) {
                String productJson = currentProduct.toString().trim();
                if (!productJson.isEmpty() && productJson.startsWith("{") && productJson.endsWith("}")) {
                    String name = extractField(productJson, "ProductName");
                    String type = extractField(productJson, "ProductType");
                    String amountStr = extractField(productJson, "Available Amount");
                    String priceStr = extractField(productJson, "Price");

                    name = name.replaceAll("^\"|\"$", "");
                    type = type.replaceAll("^\"|\"$", "");

                    try {
                        int amount = Integer.parseInt(amountStr.trim());
                        double price = Double.parseDouble(priceStr.trim());
                        products.add(new Product(name, type, amount, price));
                    } catch (NumberFormatException e) {
                        System.err.println("Error parsing product values: " + e.getMessage() + " for product: " + productJson);
                        throw e;
                    }
                }
                currentProduct = new StringBuilder();
            }
        }
        return products;
    }

    // In Worker.java, modify the updateStoresFile method in WorkerThread class
    private void updateStoresFile() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(tempDir + "/memory.json"))) {
            writer.println("[");
            boolean first = true;
            for (Store store : stores.values()) {
                if (!first) {
                    writer.println(",");
                }
                first = false;
                writer.print(storeToJson(store));
            }
            writer.println("\n]");
        }
        System.out.println("Updated memory file at " + tempDir + "/memory.json with " + stores.size() + " stores");
    }

private String storeToJson(Store store) {
    StringBuilder json = new StringBuilder();
    json.append("  {\n");
    json.append("    \"StoreName\": \"").append(store.getStoreName()).append("\",\n");

    // Use actual Store values instead of hardcoded 0.0
    json.append("    \"Latitude\": ").append(store.getLatitude()).append(",\n");
    json.append("    \"Longitude\": ").append(store.getLongitude()).append(",\n");

    // Fix the quotes in FoodCategory
    String category = store.getFoodCategory().replace("\"", "");
    json.append("    \"FoodCategory\": \"").append(category).append("\",\n");

    // Use actual Store values instead of hardcoded 0
    json.append("    \"Stars\": ").append(store.getStars()).append(",\n");
    json.append("    \"NoOfVotes\": ").append(store.getNoOfVotes()).append(",\n");
    json.append("    \"StoreLogo\": ").append(store.getStoreLogo()).append(",\n");

    json.append("    \"Products\": [\n");

    List<Product> products = store.getProducts();
    for (int i = 0; i < products.size(); i++) {
        Product p = products.get(i);
        json.append("      {");
        json.append("\"ProductName\": \"").append(p.getProductName()).append("\", ");
        json.append("\"ProductType\": \"").append(p.getProductType()).append("\", ");
        json.append("\"Available Amount\": ").append(p.getAvailableAmount()).append(", ");
        json.append("\"Price\": ").append(p.getPrice());
        json.append("}");
        if (i < products.size() - 1) {
            json.append(",");
        }
        json.append("\n");
    }

    json.append("    ]\n");
    json.append("  }");
    return json.toString();
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