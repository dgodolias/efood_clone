package com.example.efood_clone_2.model;

public class Store {
    private String name;
    private double latitude;
    private double longitude;
    private String foodType;
    private int stars;
    private String priceCategory;

    public Store(String name, double latitude, double longitude, String foodType, int stars, String priceCategory) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.foodType = foodType;
        this.stars = stars;
        this.priceCategory = priceCategory;
    }

    public String getName() { return name; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getFoodType() { return foodType; }
    public int getStars() { return stars; }
    public String getPriceCategory() { return priceCategory; }
    public String getCoordinates() { return latitude + ", " + longitude; }
}