package com.example.backend;
import java.util.ArrayList;
import java.util.List;

public class Store {
    private String storeName;
    private double latitude;
    private double longitude;
    private String foodCategory;
    private int stars;
    private int noOfVotes;
    private String storeLogo;
    private List<Product> products;

    // Constructor
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
    }

    // Getters και Setters
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public String getFoodCategory() { return foodCategory; }
    public void setFoodCategory(String foodCategory) { this.foodCategory = foodCategory; }
    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }
    public int getNoOfVotes() { return noOfVotes; }
    public void setNoOfVotes(int noOfVotes) { this.noOfVotes = noOfVotes; }
    public String getStoreLogo() { return storeLogo; }
    public void setStoreLogo(String storeLogo) { this.storeLogo = storeLogo; }
    public List<Product> getProducts() { return products; }
    public void addProduct(Product product) { this.products.add(product); }
    public void removeProduct(String productName) {
        this.products.removeIf(p -> p.getProductName().equals(productName));
    }
}
