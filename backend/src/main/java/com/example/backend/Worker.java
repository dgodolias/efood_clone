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

    private List<String> getIPAddresses() {
        List<String> ipAddresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) ipAddresses.add(addr.getHostAddress());
                }
            }
        } catch (SocketException e) {
            System.err.println("Error getting network interfaces: " + e.getMessage());
        }
        return ipAddresses;
    }

    public void start(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            List<String> ipAddresses = getIPAddresses();
            System.out.println("Worker running on port " + port);
            if (!ipAddresses.isEmpty()) {
                System.out.println("Available on network interfaces:");
                for (String ip : ipAddresses) System.out.println(" - " + ip + ":" + port);
            } else {
                System.out.println("No external network interfaces found, only available on localhost:" + port);
            }
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
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
            while (true) {
                try {
                    CommunicationClasses.WorkerRequest request = (CommunicationClasses.WorkerRequest) in.readObject();
                    String command = request.getCommand();
                    String data = request.getData();
                    System.out.println("Worker on port " + socket.getLocalPort() + " received request: " + command);
                    System.out.flush();
                    String result = processCommand(command, data);
                    out.writeObject(new CommunicationClasses.WorkerResponse(result));
                    out.flush();
                } catch (EOFException e) {
                    break;
                } catch (ClassNotFoundException e) {
                    System.err.println("Class not found: " + e.getMessage());
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

    private String processCommand(String command, String data) {
        synchronized (stores) {
            try {
                switch (command) {
                    case "ADD_STORE":
                        return processAddStore(data);
                    case "ADD_PRODUCT":
                        return processAddProduct(data);
                    case "REMOVE_PRODUCT":
                        return processRemoveProduct(data);
                    case "GET_SALES_BY_STORE_TYPE_CATEGORY":
                        return processSalesByStoreCategory(data);
                    case "GET_SALES_BY_PRODUCT_CATEGORY":
                        return processSalesByProductCategory(data);
                    case "GET_SALES_BY_PRODUCT":
                        return processSalesByProduct(data);
                    case "BUY":
                        return processPurchase(data);
                    case "FILTER_STORES":
                        return processFilterStores(data);
                    case "FIND_STORES_WITHIN_RANGE":
                        return processFindStoresWithinRange(data);
                    case "GET_STORE_DETAILS":
                        return processGetStoreDetails(data);
                    case "REVIEW":
                        return processReview(data);
                    case "PING":
                        return "PONG";
                    default:
                        return "ERROR|Unknown command: " + command;
                }
            } catch (IOException e) {
                return "ERROR|" + e.getMessage();
            }
        }
    }

    private String processAddStore(String data) throws IOException {
        String storeName = extractField(data, "StoreName");
        if (storeName.isEmpty()) return "ERROR|Invalid store JSON - missing StoreName";
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
            for (Product p : products) store.addProduct(p);
            stores.put(storeName, store);
            updateStoresFile();
            return "SUCCESS|" + storeName;
        } catch (NumberFormatException e) {
            return "ERROR|Invalid numeric value in store data: " + e.getMessage();
        }
    }

    private String processAddProduct(String data) throws IOException {
        String[] productParts = data.split(",");
        if (productParts.length < 5) return "ERROR|Invalid ADD_PRODUCT format";
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
                return "SUCCESS|" + storeNameProd + "|" + productNameAdd;
            } else {
                return "ERROR|Store not found: " + storeNameProd;
            }
        } catch (NumberFormatException e) {
            return "ERROR|Invalid numeric value: " + e.getMessage();
        }
    }

    private String processRemoveProduct(String data) throws IOException {
        String[] removeParts = data.split(",");
        if (removeParts.length < 2) return "ERROR|Invalid REMOVE_PRODUCT format";
        String removeStoreName = removeParts[0].trim();
        String removeProductName = removeParts[1].trim();
        Store removeStore = stores.get(removeStoreName);
        if (removeStore != null) {
            boolean removed = removeStore.removeProduct(removeProductName);
            updateStoresFile();
            return removed ? "SUCCESS|" + removeStoreName + "|" + removeProductName : "ERROR|Product not found in store: " + removeProductName;
        } else {
            return "ERROR|Store not found: " + removeStoreName;
        }
    }

    private String processSalesByStoreCategory(String category) {
        List<String> salesList = new ArrayList<>();
        for (Store s : stores.values()) {
            if (s.getFoodCategory().replaceAll("^\"|\"$", "").equalsIgnoreCase(category)) {
                int storeTotalSales = s.getSales().values().stream().mapToInt(Integer::intValue).sum();
                salesList.add(s.getStoreName() + ":" + storeTotalSales);
            }
        }
        return String.join("|", salesList);
    }

    private String processSalesByProductCategory(String productCategory) {
        List<String> salesList = new ArrayList<>();
        for (Store s : stores.values()) {
            int storeTotal = 0;
            for (Product p : s.getProducts()) {
                if (p.getProductType().equals(productCategory)) storeTotal += s.getSales().getOrDefault(p.getProductName(), 0);
            }
            if (storeTotal > 0) salesList.add(s.getStoreName() + ":" + storeTotal);
        }
        return String.join("|", salesList);
    }

    private String processSalesByProduct(String productName) {
        List<String> salesList = new ArrayList<>();
        for (Store s : stores.values()) {
            int storeTotal = s.getSales().getOrDefault(productName, 0);
            if (storeTotal > 0) salesList.add(s.getStoreName() + ":" + storeTotal);
        }
        return String.join("|", salesList);
    }

    private String processPurchase(String data) throws IOException {
        String[] buyParts = data.split(",");
        if (buyParts.length < 3) return "ERROR|Invalid BUY format";
        try {
            String buyStoreName = buyParts[0].trim();
            String buyProductName = buyParts[1].trim();
            int buyQuantity = Integer.parseInt(buyParts[2].trim());
            Store buyStore = stores.get(buyStoreName);
            if (buyStore == null) return "ERROR|Store not found: " + buyStoreName;
            boolean success = buyStore.purchaseProduct(buyProductName, buyQuantity);
            updateStoresFile();
            return success ? "SUCCESS|" + buyStoreName + "|" + buyProductName + "|" + buyQuantity : "ERROR|Insufficient quantity or product not found";
        } catch (NumberFormatException e) {
            return "ERROR|Invalid quantity: " + e.getMessage();
        }
    }

    private String processFilterStores(String data) {
        String[] filterParts = data.split(";", 2);
        if (filterParts.length < 2) return "ERROR|Invalid filter format";
        String[] coordsPart = filterParts[0].split(",");
        String filterData = filterParts[1];
        if (coordsPart.length != 2) return "ERROR|Invalid coordinates format";
        try {
            double filterLat = Double.parseDouble(coordsPart[0].trim());
            double filterLon = Double.parseDouble(coordsPart[1].trim());
            Map<String, List<String>> filters = parseFilterString(filterData);
            List<String> filteredStoreJsons = new ArrayList<>();
            for (Store s : stores.values()) {
                double distance = calculateDistance(filterLat, filterLon, s.getLatitude(), s.getLongitude());
                if (distance <= 5.0 && matchesFilters(s, filters)) {
                    s.setDistance(distance);
                    String storeJson = Store.StoreToJson(s).replaceAll("\\s*\\n\\s*", "").replaceAll("\\s+", " ").trim();
                    filteredStoreJsons.add(storeJson);
                }
            }
            return "[" + String.join(",", filteredStoreJsons) + "]";
        } catch (NumberFormatException e) {
            return "ERROR|Invalid coordinate format: " + e.getMessage();
        }
    }

    private String processFindStoresWithinRange(String data) {
        String[] coordinates = data.split(",");
        if (coordinates.length != 2) return "ERROR|Invalid coordinates format";
        try {
            double lat = Double.parseDouble(coordinates[0]);
            double lon = Double.parseDouble(coordinates[1]);
            List<String> nearbyStoreJsons = new ArrayList<>();
            for (Store s : stores.values()) {
                double distance = calculateDistance(lat, lon, s.getLatitude(), s.getLongitude());
                if (distance <= 5.0) {
                    s.setDistance(distance);
                    String storeJson = Store.StoreToJson(s).replaceAll("\\s*\\n\\s*", "").replaceAll("\\s+", " ").trim();
                    nearbyStoreJsons.add(storeJson);
                }
            }
            return "[" + String.join(",", nearbyStoreJsons) + "]";
        } catch (NumberFormatException e) {
            return "ERROR|Invalid coordinate format: " + e.getMessage();
        }
    }

    private String processGetStoreDetails(String requestedStoreName) {
        Store foundStore = null;
        for (Store s : stores.values()) {
            if (s.getStoreName().equals(requestedStoreName) || ("\"" + s.getStoreName() + "\"").equals(requestedStoreName)) {
                foundStore = s;
                break;
            }
        }
        if (foundStore == null) return "ERROR|Store not found: " + requestedStoreName;
        try {
            return Store.StoreToJson(foundStore).replaceAll("\\s*\\n\\s*", "").replaceAll("\\s+", " ").trim();
        } catch (Exception e) {
            return "ERROR|Failed to serialize store data: " + e.getMessage();
        }
    }

    private String processReview(String data) throws IOException {
        String[] reviewParts = data.split(",");
        if (reviewParts.length < 2) return "ERROR|Invalid REVIEW format";
        try {
            String reviewStoreName = reviewParts[0].trim();
            int reviewRating = Integer.parseInt(reviewParts[1].trim());
            Store reviewStore = null;
            for (Store s : stores.values()) {
                if (s.getStoreName().equals(reviewStoreName) || ("\"" + s.getStoreName() + "\"").equals(reviewStoreName)) {
                    reviewStore = s;
                    break;
                }
            }
            if (reviewStore == null) return "ERROR|Store not found: " + reviewStoreName;
            float currentStars = reviewStore.getStars();
            int currentVotes = reviewStore.getNoOfVotes();
            double totalRatingPoints = currentStars * currentVotes + reviewRating;
            int newVotes = currentVotes + 1;
            double newRating = totalRatingPoints / newVotes;
            float roundedRating = (float) Math.round(newRating * 100) / 100;
            reviewStore.setStars(roundedRating);
            reviewStore.setNoOfVotes(newVotes);
            updateStoresFile();
            return "SUCCESS|" + reviewStoreName + "|" + roundedRating + "|" + newVotes;
        } catch (NumberFormatException e) {
            return "ERROR|Invalid rating format: " + e.getMessage();
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
                for (String value : values) if (!value.isEmpty()) valueList.add(value);
                filters.put(key, valueList);
            }
        }
        return filters;
    }

    private boolean matchesFilters(Store store, Map<String, List<String>> filters) {
        if (filters.containsKey("type") && !filters.get("type").isEmpty()) {
            boolean typeMatch = false;
            for (String type : filters.get("type")) {
                if (store.getFoodCategory().replaceAll("^\"|\"$", "").equalsIgnoreCase(type)) {
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
        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
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
                    String name = extractField(productJson, "ProductName").replaceAll("^\"|\"$", "");
                    String type = extractField(productJson, "ProductType").replaceAll("^\"|\"$", "");
                    String amountStr = extractField(productJson, "Available Amount");
                    String priceStr = extractField(productJson, "Price");
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