package com.example.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Store {
    private String storeName;
    private double latitude;
    private double longitude;
    private String foodCategory;
    private int stars;
    private int noOfVotes;
    private String storeLogo;
    private List<Product> products;
    private Map<String, Integer> sales;
    private String priceCategory;
    private double distance; // Add distance field

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
        // Determine price category based on stars
        this.priceCategory = stars <= 2 ? "$" : (stars <= 4 ? "$$" : "$$$");
    }

    public synchronized void addProduct(Product product) {
        products.add(product);
    }

    public synchronized void removeProduct(String productName) {
        products.removeIf(p -> p.getProductName().equals(productName));
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

    // Getters
    public String getStoreName() { return storeName; }
    public String getFoodCategory() { return foodCategory; }
    public int getStars() { return stars; }
    public int getNoOfVotes() { return noOfVotes; }
    public String getStoreLogo() { return storeLogo; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getPriceCategory() { return priceCategory; }
    public void setPriceCategory(String priceCategory) { this.priceCategory = priceCategory; }

    // Add distance related methods
    public double getDistance() { return distance; }
    public void setDistance(double distance) { this.distance = distance; }

    // Add the missing method
    public String getFormattedDistance() {
        return String.format("%.1f km", distance);
    }

    // For coordinate display
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
}