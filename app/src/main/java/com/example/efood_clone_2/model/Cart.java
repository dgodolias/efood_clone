package com.example.efood_clone_2.model;

import java.util.ArrayList;
import java.util.List;

public class Cart {
    private static Cart instance;
    private List<CartItem> items = new ArrayList<>();

    private Cart() {}

    public static synchronized Cart getInstance() {
        if (instance == null) {
            instance = new Cart();
        }
        return instance;
    }

    public void addItem(Product product, int quantity) {
        for (CartItem item : items) {
            if (item.getProduct().getProductName().equals(product.getProductName())) {
                item.setQuantity(item.getQuantity() + quantity);
                return;
            }
        }
        items.add(new CartItem(product, quantity));
    }

    public void removeItem(CartItem item) {
        items.remove(item);
    }

    public void clear() {
        items.clear();
    }

    public List<CartItem> getItems() {
        return items;
    }

    public int getTotalQuantity() {
        int total = 0;
        for (CartItem item : items) {
            total += item.getQuantity();
        }
        return total;
    }

    public double getTotalPrice() {
        double total = 0;
        for (CartItem item : items) {
            total += item.getSubtotal();
        }
        return total;
    }
}