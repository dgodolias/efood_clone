package com.example.efood_clone_2.model;

import java.util.ArrayList;
import java.util.List;

public class StoreDataManager {
    private static StoreDataManager instance;
    private List<Store> stores;
    private boolean isLoading = false;

    private StoreDataManager() {
        stores = new ArrayList<>();
    }

    public static synchronized StoreDataManager getInstance() {
        if (instance == null) {
            instance = new StoreDataManager();
        }
        return instance;
    }

    public void setStores(List<Store> stores) {
        this.stores = stores;
    }

    public List<Store> getStores() {
        return stores;
    }

    public boolean hasStores() {
        return !stores.isEmpty();
    }

    public void setLoading(boolean loading) {
        isLoading = loading;
    }

    public boolean isLoading() {
        return isLoading;
    }
}