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
import com.example.efood_clone_2.frontend.TCPClient;
import com.example.efood_clone_2.model.Product;
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

    double latitude = 37.9838;
    double longitude = 23.7275;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        storeList = new ArrayList<>();

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        storeAdapter = new StoreAdapter(storeList);
        recyclerView.setAdapter(storeAdapter);

        ImageView filterIcon = findViewById(R.id.filterIcon);
        filterIcon.setOnClickListener(v -> showFilterPopup(v));

        ImageView locationIcon = findViewById(R.id.locationIcon);
        locationIcon.setOnClickListener(v -> showLocationPopup(v));

        TCPClient client = new TCPClient();
        client.getNearbyStores(latitude, longitude, new TCPClient.StoreListCallback() {
            @Override
            public void onStoresReceived(List<Store> stores) {
                storeList.clear();
                storeList.addAll(stores);
                storeAdapter.notifyDataSetChanged();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(HomeActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }


    private void showFilterPopup(View anchorView) {
        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_filter, null);

        int width = ViewGroup.LayoutParams.MATCH_PARENT;
        int height = ViewGroup.LayoutParams.WRAP_CONTENT;
        final PopupWindow popupWindow = new PopupWindow(popupView, width, height, true);
        popupWindow.setOutsideTouchable(true);

        LinearLayout typeGroup = popupView.findViewById(R.id.typeGroup);
        LinearLayout starsGroup = popupView.findViewById(R.id.starsGroup);
        LinearLayout priceGroup = popupView.findViewById(R.id.priceGroup);

        String[] types = {"pizza", "burger", "souvlaki", "sweet", "cooked", "coffee", "sushi",
                          "grilled", "fish", "salad", "kebab", "chinese", "mexican", "snacks",
                          "brunch", "sandwich", "juices"};
        addCheckboxesToGroup(typeGroup, types, "type");

        String[] stars = {"above 1.5", "above 2.5", "above 3.5", "above 4.5"};
        addCheckboxesToGroup(starsGroup, stars, "stars");

        String[] prices = {"$", "$$", "$$$"};
        addCheckboxesToGroup(priceGroup, prices, "price");

        Button applyButton = popupView.findViewById(R.id.applyButton);
        applyButton.setOnClickListener(v -> {
            Log.d("FilterSelected", "Applying filters through TCP client");
            TCPClient client = new TCPClient();

            client.getFilteredStores(selectedFilters, latitude, longitude, new TCPClient.StoreListCallback() {
    @Override
    public void onStoresReceived(List<Store> stores) {
        storeList.clear();
        storeList.addAll(stores);
        storeAdapter.notifyDataSetChanged();
    }

    @Override
    public void onError(String error) {
        Toast.makeText(HomeActivity.this, error, Toast.LENGTH_LONG).show();
    }
});

            popupWindow.dismiss();
        });

        popupWindow.showAsDropDown(anchorView);
    }

    private void showLocationPopup(View anchorView) {
        View popupView = LayoutInflater.from(this).inflate(R.layout.location_popup, null);

        int width = ViewGroup.LayoutParams.MATCH_PARENT;
        int height = ViewGroup.LayoutParams.WRAP_CONTENT;
        final PopupWindow popupWindow = new PopupWindow(popupView, width, height, true);
        popupWindow.setOutsideTouchable(true);

        Button closeButton = popupView.findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> popupWindow.dismiss());

        popupWindow.showAsDropDown(anchorView);
    }

    private void addCheckboxesToGroup(LinearLayout group, String[] items, String filterKey) {
        for (String item : items) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(item);
            checkBox.setTextColor(0xFF000000);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 16, 0);
            checkBox.setLayoutParams(params);

           if (selectedFilters.get(filterKey) != null && selectedFilters.get(filterKey).contains(item)){
                checkBox.setChecked(true);
           }
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
}