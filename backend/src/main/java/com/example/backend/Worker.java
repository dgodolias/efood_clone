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
                        case "BUY":
                            String[] buyParts = data.split(",");
                            if (buyParts.length < 3) {
                                out.println("Invalid BUY format");
                                continue;
                            }
                            String buyStoreName = buyParts[0].trim();
                            String buyProductName = buyParts[1].trim();
                            int buyQuantity = Integer.parseInt(buyParts[2].trim());

                            Store buyStore = stores.get(buyStoreName);
                            if (buyStore == null) {
                                out.println("Store not found: " + buyStoreName);
                                continue;
                            }

                            buyStore.purchaseProduct(buyProductName, buyQuantity);
                            updateStoresFile();
                            out.println("Purchase processed");
                            break;

                        case "FILTER_STORES":
                            Map<String, List<String>> filters = parseFilterString(data);
                            StringBuilder filteredStoresJson = new StringBuilder("[");
                            boolean firstStore = true;

                            for (Store s : stores.values()) {
                                if (matchesFilters(s, filters)) {
                                    try {
                                        String storeJson = Store.StoreToJson(s);
                                        // Compact the JSON
                                        storeJson = storeJson.replaceAll("\\s*\\n\\s*", "").replaceAll("\\s+", " ").trim();

                                        if (!firstStore) {
                                            filteredStoresJson.append(",");
                                        } else {
                                            firstStore = false;
                                        }

                                        filteredStoresJson.append(storeJson);
                                    } catch (Exception e) {
                                        System.err.println("Error serializing store to JSON: " + e.getMessage());
                                    }
                                }
                            }

                            filteredStoresJson.append("]");
                            System.out.println("Sending filtered stores as JSON array: " + filteredStoresJson.toString());
                            out.println(filteredStoresJson.toString());
                            break;

                        case "FIND_STORES_WITHIN_RANGE":
                                String[] coordinates = data.split(",");
                                if (coordinates.length != 2) {
                                    out.println("[]");  // Return empty JSON array
                                    continue;
                                }

                                double latt = Double.parseDouble(coordinates[0]);
                                double longt = Double.parseDouble(coordinates[1]);

                                // Using StringBuilder to construct the JSON array of stores
                                StringBuilder nearbyStoresJson = new StringBuilder("[");
                                boolean firstNearbyStore = true;

                                for (Store s : stores.values()) {
                                    double distance = calculateDistance(latt, longt, s.getLatitude(), s.getLongitude());
                                    if (distance <= 5.0) { // 5km range
                                        // Set the distance in the store object
                                        s.setDistance(distance);

                                        try {
                                            String storeJson = Store.StoreToJson(s);
                                            // Compact the JSON
                                            storeJson = storeJson.replaceAll("\\s*\\n\\s*", "").replaceAll("\\s+", " ").trim();

                                            if (!firstNearbyStore) {
                                                nearbyStoresJson.append(",");
                                            } else {
                                                firstNearbyStore = false;
                                            }

                                            nearbyStoresJson.append(storeJson);
                                        } catch (Exception e) {
                                            System.err.println("Error serializing store to JSON: " + e.getMessage());
                                        }
                                    }
                                }

                                nearbyStoresJson.append("]");
                                System.out.println("Sending nearby stores as JSON array: " + nearbyStoresJson.toString());
                                out.println(nearbyStoresJson.toString());
                                break;
                        case "GET_STORE_DETAILS":
                            String requestedStoreName = parts.length > 1 ? parts[1].trim() : "";
                            Store foundStore = null;

                            // Find the store by name using the Map's values
                            for (Store s : stores.values()) {
                                if (s.getStoreName().equals(requestedStoreName) ||
                                    ("\"" + s.getStoreName() + "\"").equals(requestedStoreName)) {
                                    foundStore = s;
                                    break;
                                }
                            }

                            if (foundStore == null) {
                                out.println("Error: Store not found");
                            } else {
                                // Convert store to JSON and send it as a compact string
                                try {
                                    String storeJson = Store.StoreToJson(foundStore);
                                    // Compact the JSON by removing newlines and extra spaces
                                    storeJson = storeJson.replaceAll("\\s*\\n\\s*", "").replaceAll("\\s+", " ").trim();
                                    System.out.println("Sending store details from Worker: " + storeJson);
                                    out.println(storeJson);
                                } catch (Exception e) {
                                    System.err.println("Error serializing store to JSON: " + e.getMessage());
                                    out.println("Error: Failed to serialize store data");
                                }
                            }
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

    private boolean matchesFilters(Store store, Map<String, List<String>> filters) {
        // Type filter
        if (filters.containsKey("type") && !filters.get("type").isEmpty()) {
            boolean typeMatch = false;
            for (String type : filters.get("type")) {
                if (store.getFoodCategory().equalsIgnoreCase(type)) {
                    typeMatch = true;
                    break;
                }
            }
            if (!typeMatch) return false;
        }

        // Stars filter
        if (filters.containsKey("stars") && !filters.get("stars").isEmpty()) {
            boolean starsMatch = false;
            for (String starsFilter : filters.get("stars")) {
                if (starsFilter.startsWith("above ")) {
                    float minStars = Float.parseFloat(starsFilter.replace("above ", ""));
                    if (store.getStars() >= minStars) {
                        starsMatch = true;
                        break;
                    }
                }
            }
            if (!starsMatch) return false;
        }

        // Price filter
        if (filters.containsKey("price") && !filters.get("price").isEmpty()) {
            boolean priceMatch = false;
            for (String price : filters.get("price")) {
                if (store.getPriceCategory().equals(price)) {
                    priceMatch = true;
                    break;
                }
            }
            if (!priceMatch) return false;
        }

        return true;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Haversine formula
        final int R = 6371; // Earth radius in kilometers

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
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
                writer.print(Store.StoreToJson(store));
            }
            writer.println("\n]");
        }
    }

}