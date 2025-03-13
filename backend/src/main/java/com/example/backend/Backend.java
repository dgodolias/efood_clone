package com.example.backend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Backend {
    private List<Store> stores;

    // Constructor
    public Backend() {
        this.stores = new ArrayList<>();
    }

    // Διαχείριση καταστημάτων
    public void addStore(Store store) {
        stores.add(store);
    }

    public void removeStore(String storeName) {
        stores.removeIf(store -> store.getStoreName().equals(storeName));
    }

    public void updateStore(String storeName, Store updatedStore) {
        for (int i = 0; i < stores.size(); i++) {
            if (stores.get(i).getStoreName().equals(storeName)) {
                stores.set(i, updatedStore);
                break;
            }
        }
    }

    // Διαχείριση προϊόντων
    public void addProductToStore(String storeName, Product product) {
        for (Store store : stores) {
            if (store.getStoreName().equals(storeName)) {
                store.addProduct(product);
                break;
            }
        }
    }

    public void removeProductFromStore(String storeName, String productName) {
        for (Store store : stores) {
            if (store.getStoreName().equals(storeName)) {
                store.removeProduct(productName);
                break;
            }
        }
    }

    // Υπολογισμός στατιστικών για κατηγορίες τροφίμων
    public Map<String, Integer> getFoodCategoryStats(String foodCategory) {
        Map<String, Integer> stats = new HashMap<>();
        int total = 0;
        for (Store store : stores) {
            if (store.getFoodCategory().equals(foodCategory)) {
                int productCount = store.getProducts().size();
                stats.put(store.getStoreName(), productCount);
                total += productCount;
            }
        }
        stats.put("total", total);
        return stats;
    }

    // Υπολογισμός στατιστικών για κατηγορίες προϊόντων
    public Map<String, Integer> getProductCategoryStats(String productType) {
        Map<String, Integer> stats = new HashMap<>();
        int total = 0;
        for (Store store : stores) {
            int count = (int) store.getProducts().stream()
                    .filter(p -> p.getProductType().equals(productType))
                    .count();
            if (count > 0) {
                stats.put(store.getStoreName(), count);
                total += count;
            }
        }
        stats.put("total", total);
        return stats;
    }

    public List<Store> getStores() {
        return stores;
    }
}
