package com.example.efood;

public class Product {
    private String productName;
    private String productType;
    private int availableAmount;
    private double price;

    // Getters and setters
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }

    public int getAvailableAmount() { return availableAmount; }
    public void setAvailableAmount(int availableAmount) { this.availableAmount = availableAmount; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
}