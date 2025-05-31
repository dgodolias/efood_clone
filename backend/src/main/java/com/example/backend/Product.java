package com.example.backend;

public class Product {
    private final String productName;
    private final String productType;
    private volatile int availableAmount;
    private final double price;

    public Product(String productName, String productType, int availableAmount, double price) {
        this.productName = productName;
        this.productType = productType;
        this.availableAmount = availableAmount;
        this.price = price;
    }

    public String getProductName() { return productName; }
    public String getProductType() { return productType; }
    public synchronized int getAvailableAmount() { return availableAmount; }
    public synchronized void setAvailableAmount(int availableAmount) { this.availableAmount = availableAmount; }
    public double getPrice() { return price; }
}