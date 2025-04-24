package com.example.backend;

import com.example.backend.Product;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Store implements Serializable {
    private String storeName;
    private double latitude;
    private double longitude;
    private String foodCategory;
    private int stars;
    private int noOfVotes;
    private String storeLogo;
    private List<Product> products;
    private Map<String, Integer> sales;
    private double distance;

    // Full constructor
    public Store(String storeName, double latitude, double longitude, String foodCategory,
                 int stars, int noOfVotes, String storeLogo) {
        this.storeName = storeName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.foodCategory = foodCategory;
        this.stars = stars;
        this.noOfVotes = noOfVotes;
        this.storeLogo = storeLogo;
        this.products = new ArrayList<>();
        this.sales = new ConcurrentHashMap<>();
        // Price category will be calculated on demand
    }

    // Simplified constructor
    public Store(String storeName, double latitude, double longitude, String foodCategory,
                 int stars, String priceCategory) {
        this.storeName = storeName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.foodCategory = foodCategory;
        this.stars = stars;
        this.noOfVotes = 0;
        this.storeLogo = "";
        this.products = new ArrayList<>();
        this.sales = new ConcurrentHashMap<>();
        // Still accept priceCategory parameter for backward compatibility
        // but it will be overridden when getPriceCategory() is called
    }

    public synchronized void removeProduct(String productName) {
        products.removeIf(p -> p.getProductName().equals(productName));
    }

    public synchronized void addProduct(Product product) {
        products.add(product);
    }

    public synchronized List<Product> getProducts() {
        return new ArrayList<>(products);
    }

    public synchronized void recordSale(String productName, int quantity) {
        sales.put(productName, sales.getOrDefault(productName, 0) + quantity);
    }

    public synchronized Map<String, Integer> getSales() {
        return new ConcurrentHashMap<>(sales);
    }

    // Getters and setters
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }

    public String getFoodCategory() { return foodCategory; }
    public void setFoodCategory(String foodCategory) { this.foodCategory = foodCategory; }

    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }

    public int getNoOfVotes() { return noOfVotes; }
    public void setNoOfVotes(int noOfVotes) { this.noOfVotes = noOfVotes; }

    public String getStoreLogo() { return storeLogo; }
    public void setStoreLogo(String storeLogo) { this.storeLogo = storeLogo; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getPriceCategory() {
        // Calculate average price of all products
        if (products.isEmpty()) {
            return "$"; // Default when no products
        }

        double totalPrice = 0;
        for (Product p : products) {
            totalPrice += p.getPrice();
        }

        double averagePrice = totalPrice / products.size();

        // Determine price category based on average price
        if (averagePrice <= 5.0) {
            return "$";
        } else if (averagePrice <= 15.0) {
            return "$$";
        } else {
            return "$$$";
        }
    }

    public double getDistance() { return distance; }
    public void setDistance(double distance) { this.distance = distance; }

    public String getFormattedDistance() {
        return String.format("%.1f km", distance);
    }

    public String getCoordinates() {
        return String.format("%.4f, %.4f", latitude, longitude);
    }

    public void purchaseProduct(String productName, int quantity) {
        int currentSales = sales.getOrDefault(productName, 0);
        sales.put(productName, currentSales + quantity);

        for (Product product : products) {
            if (product.getProductName().equals(productName)) {
                product.setAvailableAmount(product.getAvailableAmount() - quantity);
                break;
            }
        }
    }

    public static Store JsonToStore(String jsonString) throws JSONException {
        JSONObject json = new JSONObject(jsonString);

        // Extract basic store information
        String storeName = json.optString("StoreName", "");
        double latitude = json.optDouble("Latitude", 0.0);
        double longitude = json.optDouble("Longitude", 0.0);
        String foodCategory = json.optString("FoodCategory", "");
        int stars = json.optInt("Stars", 0);
        int noOfVotes = json.optInt("NoOfVotes", 0);
        String storeLogo = json.optString("StoreLogo", "");

        // Create store instance using the full constructor
        Store store = new Store(storeName, latitude, longitude, foodCategory, stars, noOfVotes, storeLogo);

        // Set distance if available
        if (json.has("Distance")) {
            store.setDistance(json.getDouble("Distance"));
        }

        // Parse products if available
        if (json.has("Products") && !json.isNull("Products")) {
            JSONArray productsArray = json.getJSONArray("Products");
            for (int i = 0; i < productsArray.length(); i++) {
                JSONObject productJson = productsArray.getJSONObject(i);
                String productName = productJson.getString("ProductName");
                String productType = productJson.getString("ProductType");
                int availableAmount = productJson.getInt("Available Amount");
                double price = productJson.getDouble("Price");

                Product product = new Product(productName, productType, availableAmount, price);
                store.addProduct(product);
            }
        }

        return store;
    }


    static String StoreToJson(Store store) {
        StringBuilder json = new StringBuilder();
        json.append("  {\n");
        json.append("    \"StoreName\": \"").append(sanitizeJsonValue(store.getStoreName())).append("\",\n");
        json.append("    \"Latitude\": ").append(store.getLatitude()).append(",\n");
        json.append("    \"Longitude\": ").append(store.getLongitude()).append(",\n");
        json.append("    \"FoodCategory\": \"").append(sanitizeJsonValue(store.getFoodCategory())).append("\",\n");
        json.append("    \"Stars\": ").append(store.getStars()).append(",\n");
        json.append("    \"NoOfVotes\": ").append(store.getNoOfVotes()).append(",\n");
        json.append("    \"StoreLogo\": \"").append(sanitizeJsonValue(store.getStoreLogo())).append("\",\n");
        json.append("    \"PriceCategory\": \"").append(sanitizeJsonValue(store.getPriceCategory())).append("\",\n");
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

    private static String sanitizeJsonValue(String value) {
        if (value == null) return "";
        // Remove any surrounding quotes
        value = value.replaceAll("^\"|\"$", "");
        // Escape any quotes inside the value
        return value.replace("\"", "\\\"");
    }
}