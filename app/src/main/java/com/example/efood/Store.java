package com.example.efood;

import java.util.List;

public class Store {
    private String id;
    private String name;
    private String address;
    private int stars;
    private String[] cuisines;
    private double latitude;
    private double longitude;
    private String foodCategory;
    private int noOfVotes;
    private String storeLogo;
    private List<Product> products;
    private String priceCategory;

    public Store() {
        // Default constructor required for JSON parsing
    }

    public Store(String id, String name, String address, int stars, String[] cuisines) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.stars = stars;
        this.cuisines = cuisines;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getStars() {
        return stars;
    }

    public void setStars(int stars) {
        this.stars = stars;
    }

    public String[] getCuisines() {
        return cuisines;
    }

    public void setCuisines(String[] cuisines) {
        this.cuisines = cuisines;
    }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getFoodCategory() { return foodCategory; }
    public void setFoodCategory(String foodCategory) { this.foodCategory = foodCategory; }

    public int getNoOfVotes() { return noOfVotes; }
    public void setNoOfVotes(int noOfVotes) { this.noOfVotes = noOfVotes; }

    public String getStoreLogo() { return storeLogo; }
    public void setStoreLogo(String storeLogo) { this.storeLogo = storeLogo; }

    public List<Product> getProducts() { return products; }
    public void setProducts(List<Product> products) { this.products = products; }

    public String getPriceCategory() { return priceCategory; }
    public void setPriceCategory(String priceCategory) { this.priceCategory = priceCategory; }
}
