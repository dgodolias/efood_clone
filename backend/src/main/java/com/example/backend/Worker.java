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
                System.out.println("Worker on port " + socket.getLocalPort() + " received request: " + request.split(" ")[0]);
                System.out.flush();

                String[] parts = request.split(" ", 2);
                String command = parts[0];
                String data = parts.length > 1 ? parts[1] : "";

                synchronized (stores) {
                    switch (command) {
                        // MANAGER OPERATIONS - MAP PHASE
                        case "ADD_STORE":
                            processAddStore(data, out);
                            break;
                        case "ADD_PRODUCT":
                            processAddProduct(data, out);
                            break;
                        case "REMOVE_PRODUCT":
                            processRemoveProduct(data, out);
                            break;
                            
                        // ANALYTICS OPERATIONS - MAP PHASE
                        case "GET_SALES_BY_STORE_TYPE_CATEGORY":
                            processSalesByStoreCategory(data, out);
                            break;
                        case "GET_SALES_BY_PRODUCT_CATEGORY":
                            processSalesByProductCategory(data, out);
                            break;
                        case "GET_SALES_BY_PRODUCT":
                            processSalesByProduct(data, out);
                            break;
                            
                        // CLIENT OPERATIONS - MAP PHASE
                        case "BUY":
                            processPurchase(data, out);
                            break;
                        case "FILTER_STORES":
                            processFilterStores(data, out);
                            break;
                        case "FIND_STORES_WITHIN_RANGE":
                            processFindStoresWithinRange(data, out);
                            break;
                        case "GET_STORE_DETAILS":
                            processGetStoreDetails(parts.length > 1 ? parts[1].trim() : "", out);
                            break;
                        case "REVIEW":
                            processReview(data, out);
                            break;
                        case "PING":
                            out.println("PONG");
                            break;
                        default:
                            out.println("ERROR|Unknown command: " + command);
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
    
    // MANAGER OPERATIONS IMPLEMENTATIONS
    private void processAddStore(String data, PrintWriter out) throws IOException {
        String storeName = extractField(data, "StoreName");
        if (storeName.isEmpty()) {
            out.println("ERROR|Invalid store JSON - missing StoreName");
            return;
        }
        storeName = storeName.replaceAll("^\"|\"$", "");
        
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
            updateStoresFile();
            
            // Format for Map-Reduce: SUCCESS|details
            out.println("SUCCESS|" + storeName);
        } catch (NumberFormatException e) {
            out.println("ERROR|Invalid numeric value in store data: " + e.getMessage());
        }
    }

    private void processAddProduct(String data, PrintWriter out) throws IOException {
        String[] productParts = data.split(",");
        if (productParts.length < 5) {
            out.println("ERROR|Invalid ADD_PRODUCT format");
            return;
        }
        
        try {
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
                
                // Format for Map-Reduce: SUCCESS|details
                out.println("SUCCESS|" + storeNameProd + "|" + productNameAdd);
            } else {
                out.println("ERROR|Store not found: " + storeNameProd);
            }
        } catch (NumberFormatException e) {
            out.println("ERROR|Invalid numeric value: " + e.getMessage());
        }
    }

    private void processRemoveProduct(String data, PrintWriter out) throws IOException {
        String[] removeParts = data.split(",");
        if (removeParts.length < 2) {
            out.println("ERROR|Invalid REMOVE_PRODUCT format");
            return;
        }
        
        String removeStoreName = removeParts[0].trim();
        String removeProductName = removeParts[1].trim();
        Store removeStore = stores.get(removeStoreName);
        
        if (removeStore != null) {
            boolean removed = (boolean) removeStore.removeProduct(removeProductName);
            updateStoresFile();
            
            if (removed) {
                // Format for Map-Reduce: SUCCESS|details
                out.println("SUCCESS|" + removeStoreName + "|" + removeProductName);
            } else {
                out.println("ERROR|Product not found in store: " + removeProductName);
            }
        } else {
            out.println("ERROR|Store not found: " + removeStoreName);
        }
    }
    
    // ANALYTICS OPERATIONS IMPLEMENTATIONS
    private void processSalesByStoreCategory(String category, PrintWriter out) {
        List<String> salesList = new ArrayList<>();
        int totalSales = 0;
        
        for (Store s : stores.values()) {
            if (s.getFoodCategory().replaceAll("^\"|\"$", "").equalsIgnoreCase(category)) {
                int storeTotalSales = s.getSales().values().stream().mapToInt(Integer::intValue).sum();
                salesList.add(s.getStoreName() + ":" + storeTotalSales);
                totalSales += storeTotalSales;
            }
        }
        
        // Format for Map-Reduce: Join with pipe for easy parsing by reducer
        out.println(String.join("|", salesList));
    }

    private void processSalesByProductCategory(String productCategory, PrintWriter out) {
        List<String> salesList = new ArrayList<>();
        int totalSales = 0;
        
        for (Store s : stores.values()) {
            int storeTotal = 0;
            for (Product p : s.getProducts()) {
                if (p.getProductType().equals(productCategory)) {
                    storeTotal += s.getSales().getOrDefault(p.getProductName(), 0);
                }
            }
            
            if (storeTotal > 0) {
                salesList.add(s.getStoreName() + ":" + storeTotal);
                totalSales += storeTotal;
            }
        }
        
        // Format for Map-Reduce: Join with pipe for easy parsing by reducer
        out.println(String.join("|", salesList));
    }

    private void processSalesByProduct(String productName, PrintWriter out) {
        List<String> salesList = new ArrayList<>();
        int totalSales = 0;
        
        for (Store s : stores.values()) {
            int storeTotal = s.getSales().getOrDefault(productName, 0);
            if (storeTotal > 0) {
                salesList.add(s.getStoreName() + ":" + storeTotal);
                totalSales += storeTotal;
            }
        }
        
        // Format for Map-Reduce: Join with pipe for easy parsing by reducer
        out.println(String.join("|", salesList));
    }
    
    // CLIENT OPERATIONS IMPLEMENTATIONS
    private void processPurchase(String data, PrintWriter out) throws IOException {
        String[] buyParts = data.split(",");
        if (buyParts.length < 3) {
            out.println("ERROR|Invalid BUY format");
            return;
        }
        
        try {
            String buyStoreName = buyParts[0].trim();
            String buyProductName = buyParts[1].trim();
            int buyQuantity = Integer.parseInt(buyParts[2].trim());

            Store buyStore = stores.get(buyStoreName);
            if (buyStore == null) {
                out.println("ERROR|Store not found: " + buyStoreName);
                return;
            }

            boolean success = (boolean) buyStore.purchaseProduct(buyProductName, buyQuantity);
            updateStoresFile();
            
            if (success) {
                out.println("SUCCESS|" + buyStoreName + "|" + buyProductName + "|" + buyQuantity);
            } else {
                out.println("ERROR|Insufficient quantity or product not found");
            }
        } catch (NumberFormatException e) {
            out.println("ERROR|Invalid quantity: " + e.getMessage());
        }
    }

    private void processFilterStores(String data, PrintWriter out) {
        String[] filterParts = data.split(";", 2);
        if (filterParts.length < 2) {
            out.println("ERROR|Invalid filter format");
            return;
        }
        
        String[] coordsPart = filterParts[0].split(",");
        String filterData = filterParts[1];

        if (coordsPart.length != 2) {
            out.println("ERROR|Invalid coordinates format");
            return;
        }

        try {
            double filterLat = Double.parseDouble(coordsPart[0].trim());
            double filterLon = Double.parseDouble(coordsPart[1].trim());
            Map<String, List<String>> filters = parseFilterString(filterData);
            
            List<String> filteredStoreJsons = new ArrayList<>();
            
            for (Store s : stores.values()) {
                double distance = calculateDistance(filterLat, filterLon, s.getLatitude(), s.getLongitude());
                if (distance <= 5.0) {
                    if (matchesFilters(s, filters)) {
                        s.setDistance(distance);
                        try {
                            String storeJson = Store.StoreToJson(s);
                            storeJson = storeJson.replaceAll("\\s*\\n\\s*", "").replaceAll("\\s+", " ").trim();
                            filteredStoreJsons.add(storeJson);
                        } catch (Exception e) {
                            System.err.println("Error serializing store: " + e.getMessage());
                        }
                    }
                }
            }
            
            // Format as JSON array for consistency
            StringBuilder response = new StringBuilder("[");
            for (int i = 0; i < filteredStoreJsons.size(); i++) {
                if (i > 0) response.append(",");
                response.append(filteredStoreJsons.get(i));
            }
            response.append("]");
            
            out.println(response.toString());
        } catch (NumberFormatException e) {
            out.println("ERROR|Invalid coordinate format: " + e.getMessage());
        }
    }

    private void processFindStoresWithinRange(String data, PrintWriter out) {
        String[] coordinates = data.split(",");
        if (coordinates.length != 2) {
            out.println("ERROR|Invalid coordinates format");
            return;
        }

        try {
            double lat = Double.parseDouble(coordinates[0]);
            double lon = Double.parseDouble(coordinates[1]);
            
            List<String> nearbyStoreJsons = new ArrayList<>();
            
            for (Store s : stores.values()) {
                double distance = calculateDistance(lat, lon, s.getLatitude(), s.getLongitude());
                if (distance <= 5.0) {
                    s.setDistance(distance);
                    try {
                        String storeJson = Store.StoreToJson(s);
                        storeJson = storeJson.replaceAll("\\s*\\n\\s*", "").replaceAll("\\s+", " ").trim();
                        nearbyStoreJsons.add(storeJson);
                    } catch (Exception e) {
                        System.err.println("Error serializing store: " + e.getMessage());
                    }
                }
            }
            
            // Format as JSON array for consistency
            StringBuilder response = new StringBuilder("[");
            for (int i = 0; i < nearbyStoreJsons.size(); i++) {
                if (i > 0) response.append(",");
                response.append(nearbyStoreJsons.get(i));
            }
            response.append("]");
            
            out.println(response.toString());
        } catch (NumberFormatException e) {
            out.println("ERROR|Invalid coordinate format: " + e.getMessage());
        }
    }

    private void processGetStoreDetails(String requestedStoreName, PrintWriter out) {
        Store foundStore = null;
        for (Store s : stores.values()) {
            if (s.getStoreName().equals(requestedStoreName) ||
                ("\"" + s.getStoreName() + "\"").equals(requestedStoreName)) {
                foundStore = s;
                break;
            }
        }

        if (foundStore == null) {
            out.println("ERROR|Store not found: " + requestedStoreName);
        } else {
            try {
                String storeJson = Store.StoreToJson(foundStore);
                storeJson = storeJson.replaceAll("\\s*\\n\\s*", "").replaceAll("\\s+", " ").trim();
                out.println(storeJson);
            } catch (Exception e) {
                out.println("ERROR|Failed to serialize store data: " + e.getMessage());
            }
        }
    }

    private void processReview(String data, PrintWriter out) throws IOException {
        String[] reviewParts = data.split(",");
        if (reviewParts.length < 2) {
            out.println("ERROR|Invalid REVIEW format");
            return;
        }
        
        try {
            String reviewStoreName = reviewParts[0].trim();
            int reviewRating = Integer.parseInt(reviewParts[1].trim());

            Store reviewStore = null;
            for (Store s : stores.values()) {
                if (s.getStoreName().equals(reviewStoreName) ||
                    ("\"" + s.getStoreName() + "\"").equals(reviewStoreName)) {
                    reviewStore = s;
                    break;
                }
            }

            if (reviewStore == null) {
                out.println("ERROR|Store not found: " + reviewStoreName);
                return;
            }

            float currentStars = reviewStore.getStars();
            int currentVotes = reviewStore.getNoOfVotes();

            double totalRatingPoints = currentStars * currentVotes + reviewRating;
            int newVotes = currentVotes + 1;
            double newRating = totalRatingPoints / newVotes;
            float roundedRating = (float) Math.round(newRating * 100) / 100;

            reviewStore.setStars(roundedRating);
            reviewStore.setNoOfVotes(newVotes);
            updateStoresFile();
            
            out.println("SUCCESS|" + reviewStoreName + "|" + roundedRating + "|" + newVotes);
        } catch (NumberFormatException e) {
            out.println("ERROR|Invalid rating format: " + e.getMessage());
        }
    }

    // HELPER METHODS
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
        if (filters.containsKey("type") && !filters.get("type").isEmpty()) {
            boolean typeMatch = false;
            for (String type : filters.get("type")) {
                String foodCategory = store.getFoodCategory().replaceAll("^\"|\"$", "");
                if (foodCategory.equalsIgnoreCase(type)) {
                    typeMatch = true;
                    break;
                }
            }
            if (!typeMatch) return false;
        }

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
        final int R = 6371;

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