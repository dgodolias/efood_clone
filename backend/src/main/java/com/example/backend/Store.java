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
    }

    public synchronized void addProduct(Product product) {
        // proeretika
        addFakeSales(product);
        //
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
        System.out.println("Recorded sale: " + quantity + " units of " + productName + " in store " + storeName);
    }

    public synchronized Map<String, Integer> getSales() {
        return new ConcurrentHashMap<>(sales);
    }

    // Getters and Setters
    public String getStoreName() { return storeName; }
    public String getFoodCategory() { return foodCategory; }

    private void addFakeSales(Product product) {
        // Προσθήκη mock πωλήσεων για το νέο προϊόν
        sales.put(product.getProductName(), (int) (Math.random() * 100) + 1); // Τυχαίες πωλήσεις από 1-100
    }
}