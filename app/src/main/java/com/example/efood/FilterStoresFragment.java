package com.example.efood;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class FilterStoresFragment extends Fragment {

    private RecyclerView recyclerView;
    private StoreAdapter storeAdapter;
    private List<Store> storeList = new ArrayList<>();

    public FilterStoresFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_filter_stores, container, false);

        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        storeAdapter = new StoreAdapter(storeList);
        recyclerView.setAdapter(storeAdapter);

        Button filterStoresButton = view.findViewById(R.id.filterStoresButton);
        filterStoresButton.setOnClickListener(v -> filterStores(4));

        // Load stores when fragment is created
        loadStoresFromAssets();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Initialize UI components and set up listeners here
    }

    private void loadStoresFromAssets() {
        try {
            // Load from assets folder
            InputStream inputStream = getContext().getAssets().open("stores.json");
            Reader reader = new InputStreamReader(inputStream);
            
            Gson gson = new Gson();
            Type storeListType = new TypeToken<List<StoreJsonWrapper>>() {}.getType();
            List<StoreJsonWrapper> storeWrappers = gson.fromJson(reader, storeListType);
            
            storeList.clear();
            for (StoreJsonWrapper wrapper : storeWrappers) {
                Store store = new Store();
                store.setName(wrapper.StoreName);
                store.setLatitude(wrapper.Latitude);
                store.setLongitude(wrapper.Longitude);
                store.setStars(wrapper.Stars);
                store.setNoOfVotes(wrapper.NoOfVotes);
                store.setFoodCategory(wrapper.FoodCategory);
                store.setStoreLogo(wrapper.StoreLogo);
                storeList.add(store);
            }
            storeAdapter.notifyDataSetChanged();
            
            reader.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Error loading stores from assets", Toast.LENGTH_SHORT).show();
        }
    }

    private void filterStores(int minStars) {
        try {
            // Load from assets folder
            InputStream inputStream = getContext().getAssets().open("stores.json");
            Reader reader = new InputStreamReader(inputStream);
            
            Gson gson = new Gson();
            Type storeListType = new TypeToken<List<StoreJsonWrapper>>() {}.getType();
            List<StoreJsonWrapper> storeWrappers = gson.fromJson(reader, storeListType);
            
            storeList.clear();
            for (StoreJsonWrapper wrapper : storeWrappers) {
                if (wrapper.Stars >= minStars) {
                    Store store = new Store();
                    store.setName(wrapper.StoreName);
                    store.setLatitude(wrapper.Latitude);
                    store.setLongitude(wrapper.Longitude);
                    store.setStars(wrapper.Stars);
                    store.setNoOfVotes(wrapper.NoOfVotes);
                    store.setFoodCategory(wrapper.FoodCategory);
                    store.setStoreLogo(wrapper.StoreLogo);
                    storeList.add(store);
                }
            }
            storeAdapter.notifyDataSetChanged();
            
            reader.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Error loading stores", Toast.LENGTH_SHORT).show();
        }
    }

    // Wrapper class to match the JSON structure in stores.json
    private static class StoreJsonWrapper {
        public String StoreName;
        public double Latitude;
        public double Longitude;
        public String FoodCategory;
        public int Stars;
        public int NoOfVotes;
        public String StoreLogo;
        public List<ProductJsonWrapper> Products;
    }

    private static class ProductJsonWrapper {
        public String ProductName;
        public String ProductType;
        public int AvailableAmount;
        public double Price;
    }
}