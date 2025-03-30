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
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class FilterStoresFragment extends Fragment {

    private RecyclerView recyclerView;
    private StoreAdapter storeAdapter;
    private List<Store> storeList = new ArrayList<>();

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

        return view;
    }

    private void filterStores(int minStars) {
        String filePath = "data/stores.json";
        try (FileReader reader = new FileReader(filePath)) {
            Gson gson = new Gson();
            Type storeListType = new TypeToken<List<Store>>() {}.getType();
            List<Store> stores = gson.fromJson(reader, storeListType);

            storeList.clear();
            for (Store store : stores) {
                if (store.getStars() >= minStars) {
                    storeList.add(store);
                }
            }
            storeAdapter.notifyDataSetChanged();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Error loading stores", Toast.LENGTH_SHORT).show();
        }
    }
}