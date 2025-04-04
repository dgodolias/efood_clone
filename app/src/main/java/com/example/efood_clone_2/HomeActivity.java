package com.example.efood_clone_2;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.efood_clone_2.adapter.StoreAdapter;
import com.example.efood_clone_2.model.Store;
import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Create list of fake stores
        List<Store> storeList = createStoreList();

        // Set up RecyclerView
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new StoreAdapter(storeList));
    }

    private List<Store> createStoreList() {
        List<Store> stores = new ArrayList<>();

        // Add 10 fake stores
        stores.add(new Store("Pizza Paradise", 37.9838, 23.7275, "Pizza", 4, "$$"));
        stores.add(new Store("Greek Gyros", 37.9759, 23.7357, "Souvlaki", 5, "$"));
        stores.add(new Store("Sushi Master", 37.9683, 23.7299, "Sushi", 4, "$$$"));
        stores.add(new Store("Burger House", 37.9785, 23.7365, "Burgers", 3, "$"));
        stores.add(new Store("Pasta Italiana", 37.9833, 23.7312, "Italian", 5, "$$"));
        stores.add(new Store("Taco Town", 37.9721, 23.7256, "Mexican", 4, "$"));
        stores.add(new Store("BBQ Masters", 37.9801, 23.7231, "Barbecue", 3, "$$"));
        stores.add(new Store("Veggie Heaven", 37.9865, 23.7189, "Vegetarian", 4, "$$$"));
        stores.add(new Store("Street Food Corner", 37.9736, 23.7321, "Street Food", 5, "$"));
        stores.add(new Store("Spicy Noodles", 37.9712, 23.7406, "Asian", 4, "$$"));

        return stores;
    }
}