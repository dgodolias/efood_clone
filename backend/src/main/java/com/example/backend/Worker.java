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
            while ((request = in.readLine()) != null) {
                System.out.println("Worker on port " + socket.getLocalPort() + " received request: " + request);
                String[] parts = request.split(" ", 2);
                String command = parts[0];
                String data = parts.length > 1 ? parts[1] : "";

                List<String> salesList = new ArrayList<>();

                synchronized (stores) {
                    Map<String, Integer> salesByStore = new HashMap<>();
                    switch (command) {
                        case "ADD_STORE":
                            String storeName = extractField(data, "StoreName");
                            if (storeName.isEmpty()) {
                                out.println("Error: Invalid store JSON - missing StoreName");
                                break;
                            }
                            storeName = storeName.replaceAll("^\"|\"$", "");
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
                                out.println("Product removed from store: " + removeStoreName);
                            } else {
                                out.println("Store not found: " + removeStoreName);
                            }
                            break;
                        case "GET_SALES_BY_STORE_TYPE_CATEGORY":
                            String category = data;

                            for (Store s : stores.values()) {
                                if (s.getFoodCategory().replaceAll("^\"|\"$", "").equalsIgnoreCase(category)) {
                                    int totalSales = s.getSales().values().stream().mapToInt(Integer::intValue).sum();
                                    salesList.add(s.getStoreName() + ":" + totalSales);
                                }
                            }
                            out.println(String.join("|", salesList));
                            break;

                        case "GET_SALES_BY_PRODUCT_CATEGORY":
                            String productCategory = data;

                            for (Store s : stores.values()) {
                                int storeTotal = 0;
                                for (Product p : s.getProducts()) {
                                    if (p.getProductType().equals(productCategory)) {
                                        storeTotal += s.getSales().getOrDefault(p.getProductName(), 0);
                                    }
                                }
                                if (storeTotal > 0) {
                                    salesList.add(s.getStoreName() + ":" + storeTotal);
                                }
                            }
                            System.out.println(String.join("|", salesList));
                            out.println(String.join("|", salesList));
                            break;
                        case "GET_SALES_BY_PRODUCT":
                            String productName = data;

                            for (Store s : stores.values()) {
                                int storeTotal = s.getSales().getOrDefault(productName, 0);
                                if (storeTotal > 0) {
                                    salesList.add(s.getStoreName() + ":" + storeTotal);
                                }
                            }
                            System.out.println(String.join("|", salesList));
                            out.println(String.join("|", salesList));
                            break;
                        case "PING":
                            out.println("PONG");
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
        if (productsJson.startsWith("[")) productsJson = productsJson.substring(1);
        if (productsJson.endsWith("]")) productsJson = productsJson.substring(0, productsJson.length() - 1);

        int braceCount = 0;
        StringBuilder currentProduct = new StringBuilder();
        for (int i = 0; i < productsJson.length(); i++) {
            char c = productsJson.charAt(i);
            if (c == '{') braceCount++;
            else if (c == '}') braceCount--;
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
                        System.err.println("Error parsing product values: " + e.getMessage());
                    }
                }
                currentProduct = new StringBuilder();
            }
        }
        return products;
    }

    private void updateStoresFile() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(tempDir + "/memory.json"))) {
            writer.println("[");
            boolean first = true;
            for (Store store : stores.values()) {
                if (!first) writer.println(",");
                first = false;
                writer.print(storeToJson(store));
            }
            writer.println("\n]");
        }
    }

    private String storeToJson(Store store) {
        StringBuilder json = new StringBuilder();
        json.append("  {\n");
        json.append("    \"StoreName\": \"").append(sanitizeJsonValue(store.getStoreName())).append("\",\n");
        json.append("    \"Latitude\": ").append(store.getLatitude()).append(",\n");
        json.append("    \"Longitude\": ").append(store.getLongitude()).append(",\n");
        json.append("    \"FoodCategory\": \"").append(sanitizeJsonValue(store.getFoodCategory())).append("\",\n");
        json.append("    \"Stars\": ").append(store.getStars()).append(",\n");
        json.append("    \"NoOfVotes\": ").append(store.getNoOfVotes()).append(",\n");
        json.append("    \"StoreLogo\": \"").append(sanitizeJsonValue(store.getStoreLogo())).append("\",\n");
        json.append("    \"Products\": [\n");
        List<Product> products = store.getProducts();
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            json.append("      {");
            json.append("\"ProductName\": \"").append(sanitizeJsonValue(p.getProductName())).append("\", ");
            json.append("\"ProductType\": \"").append(sanitizeJsonValue(p.getProductType())).append("\", ");
            json.append("\"Available Amount\": ").append(p.getAvailableAmount()).append(", ");
            json.append("\"Price\": ").append(p.getPrice());
            json.append("}");
            if (i < products.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("    ]\n");
        json.append("  }");
        return json.toString();
    }

    private String sanitizeJsonValue(String value) {
        if (value == null) return "";
        // Remove any surrounding quotes
        value = value.replaceAll("^\"|\"$", "");
        // Escape any quotes inside the value
        return value.replace("\"", "\\\"");
    }
}