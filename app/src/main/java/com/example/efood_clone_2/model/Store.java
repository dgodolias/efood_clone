package com.example.efood_clone_2.model;

    import java.io.Serializable;
    import java.util.ArrayList;
    import java.util.List;

    public class Store implements Serializable {
        private String name;
        private double latitude;
        private double longitude;
        private String foodType;
        private int stars;
        private String priceCategory;
        private List<Product> products;

        // In Store.java
        private double distance;

        public Store(String name, double latitude, double longitude, String foodType, int stars, String priceCategory) {
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
            this.foodType = foodType;
            this.stars = stars;
            this.priceCategory = priceCategory;
            this.products = new ArrayList<>();
            this.distance = -1; // Default value
        }

        public double getDistance() { return distance; }
        public void setDistance(double distance) { this.distance = distance; }
        public String getFormattedDistance() {
            return distance >= 0 ? String.format("%.1f km", distance) : "";
        }

        public String getName() { return name; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public String getFoodType() { return foodType; }
        public int getStars() { return stars; }
        public String getPriceCategory() { return priceCategory; }
        public String getCoordinates() { return latitude + ", " + longitude; }

        // New getters/setters for products
        public List<Product> getProducts() { return products; }
        public void setProducts(List<Product> products) { this.products = products; }
        public void addProduct(Product product) { this.products.add(product); }
    }