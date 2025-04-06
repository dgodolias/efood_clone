package com.example.efood_clone_2;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.efood_clone_2.adapter.StoreAdapter;
import com.example.efood_clone_2.model.Store;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private StoreAdapter storeAdapter;
    private List<Store> storeList;
    private Map<String, List<String>> selectedFilters = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Create list of fake stores
        storeList = createStoreList();

        // Set up RecyclerView
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        storeAdapter = new StoreAdapter(storeList);
        recyclerView.setAdapter(storeAdapter);

        // Set up filter icon click listener
        ImageView filterIcon = findViewById(R.id.filterIcon);
        filterIcon.setOnClickListener(v -> showFilterPopup(v));

        // Initialize filter selections
        selectedFilters.put("type", new ArrayList<>());
        selectedFilters.put("stars", new ArrayList<>());
        selectedFilters.put("price", new ArrayList<>());
    }

    private void showFilterPopup(View anchorView) {
        // Inflate the popup layout
        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_filter, null);

        // Create the popup window
        int width = ViewGroup.LayoutParams.MATCH_PARENT;
        int height = ViewGroup.LayoutParams.WRAP_CONTENT;
        final PopupWindow popupWindow = new PopupWindow(popupView, width, height, true);
        popupWindow.setOutsideTouchable(true);

        // Get the filter groups
        LinearLayout typeGroup = popupView.findViewById(R.id.typeGroup);
        LinearLayout starsGroup = popupView.findViewById(R.id.starsGroup);
        LinearLayout priceGroup = popupView.findViewById(R.id.priceGroup);

        // Add type options
        String[] types = {"pizza", "burger", "souvlaki", "sweet", "cooked", "coffee", "sushi",
                          "grilled", "fish", "salad", "kebab", "chinese", "mexican", "snacks",
                          "brunch", "sandwich", "juices"};
        addCheckboxesToGroup(typeGroup, types, "type");

        // Add stars options
        String[] stars = {"above 1.5", "above 2.5", "above 3.5", "above 4.5"};
        addCheckboxesToGroup(starsGroup, stars, "stars");

        // Add price options
        String[] prices = {"$", "$$", "$$$"};
        addCheckboxesToGroup(priceGroup, prices, "price");

        // Set up apply button
       // Set up apply button
        Button applyButton = popupView.findViewById(R.id.applyButton);
        applyButton.setOnClickListener(v -> {
            // Create JSON from selected filters
            JSONObject jsonFilters = new JSONObject();
            try {
                for (Map.Entry<String, List<String>> entry : selectedFilters.entrySet()) {
                    JSONArray filterArray = new JSONArray();
                    for (String value : entry.getValue()) {
                        filterArray.put(value);
                    }
                    jsonFilters.put(entry.getKey(), filterArray);
                }

                // Log the JSON to console only
                String jsonString = jsonFilters.toString(2);
                Log.d("FilterSelected", jsonString);

                // Load filtered data
                loadFilteredStores();

            } catch (JSONException e) {
                e.printStackTrace();
            }

            // Dismiss the popup
            popupWindow.dismiss();
        });

        // Show the popup window
        popupWindow.showAsDropDown(anchorView);
    }

    private void addCheckboxesToGroup(LinearLayout group, String[] items, String filterKey) {
        for (String item : items) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(item);
            checkBox.setTextColor(0xFF000000);

            // Set layout parameters with margins
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 16, 0);
            checkBox.setLayoutParams(params);

            // Check if this filter value was previously selected
            if (selectedFilters.get(filterKey).contains(item)) {
                checkBox.setChecked(true);
            }

            // Set the checkbox listener
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                List<String> values = selectedFilters.get(filterKey);
                if (isChecked) {
                    if (!values.contains(item)) {
                        values.add(item);
                    }
                } else {
                    values.remove(item);
                }
                selectedFilters.put(filterKey, values);
            });

            group.addView(checkBox);
        }
    }

    private void loadFilteredStores() {
        try {
            // Load filtered store data from assets
            InputStream inputStream = getAssets().open("mock/filtered_universal_stores.txt");
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            inputStream.close();

            // Convert bytes to JSON string
            String jsonString = new String(buffer, StandardCharsets.UTF_8);

            // Parse JSON array
            JSONArray jsonArray = new JSONArray(jsonString);

            // Clear existing list
            storeList.clear();

            // Extract each store object
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject storeObject = jsonArray.getJSONObject(i);

                String name = storeObject.getString("StoreName");
                double latitude = storeObject.getDouble("Latitude");
                double longitude = storeObject.getDouble("Longitude");
                String category = storeObject.getString("FoodCategory");
                int stars = storeObject.getInt("Stars");

                // Convert stars to price category
                String priceCategory = stars <= 2 ? "$" : (stars <= 4 ? "$$" : "$$$");

                // Create store object and add to list
                Store store = new Store(name, latitude, longitude, category, stars, priceCategory);
                storeList.add(store);
            }

            // Notify adapter that data has changed
            storeAdapter.notifyDataSetChanged();

        } catch (IOException | JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error loading stores: " + e.getMessage(),
                          Toast.LENGTH_SHORT).show();
        }
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