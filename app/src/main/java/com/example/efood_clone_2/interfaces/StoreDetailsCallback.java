package com.example.efood_clone_2.interfaces;


import com.example.efood_clone_2.model.Store;;

public interface StoreDetailsCallback {
    void onStoreDetailsReceived(Store store);
    void onError(String error);
}