package com.example.efood_clone_2;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.efood_clone_2.adapter.ProductAdapter;
import com.example.efood_clone_2.model.Product;
import com.example.efood_clone_2.model.Store;

import java.util.List;

public class StoreActivity extends AppCompatActivity {

    private Store store;
    private TextView tvStoreName, tvStoreStars, tvStoreType, tvStorePrice;
    private RecyclerView productsRecyclerView;
    private ProductAdapter productAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store);

        // Initialize views
        tvStoreName = findViewById(R.id.tvStoreName);
        tvStoreStars = findViewById(R.id.tvStoreStars);
        tvStoreType = findViewById(R.id.tvStoreType);
        tvStorePrice = findViewById(R.id.tvStorePrice);
        productsRecyclerView = findViewById(R.id.productsRecyclerView);
        ImageView backButton = findViewById(R.id.backButton);

        // Get store from intent
        if (getIntent().hasExtra("store")) {
            store = (Store) getIntent().getSerializableExtra("store");
        }

        // Set up back button
        backButton.setOnClickListener(v -> finish());

        // Populate store information
        if (store != null) {
            tvStoreName.setText(store.getName());
            tvStoreStars.setText("★ " + store.getStars());
            tvStoreType.setText(store.getFoodType());
            tvStorePrice.setText(store.getPriceCategory());

            // Set up products recycler view
            setupProductsRecyclerView(store.getProducts());
        }
    }

    private void setupProductsRecyclerView(List<Product> products) {
        productsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        productAdapter = new ProductAdapter(products);
        productsRecyclerView.setAdapter(productAdapter);
    }
}