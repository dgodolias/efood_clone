package com.example.efood_clone_2.adapter;

import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.efood_clone_2.R;
import com.example.efood_clone_2.StoreActivity;
import com.example.efood_clone_2.model.Store;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class StoreAdapter extends RecyclerView.Adapter<StoreAdapter.ViewHolder> {

    private static final String TAG = "StoreAdapter";
    private List<Store> storeList;

    public StoreAdapter(List<Store> storeList) {
        this.storeList = storeList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_store, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Store store = storeList.get(position);
        holder.tvStoreName.setText(store.getStoreName());
        holder.tvStars.setText(store.getStars() + "★");
        holder.tvType.setText(store.getFoodCategory());
        holder.tvDistance.setText(store.getFormattedDistance());
        holder.tvPrice.setText(store.getPriceCategory());

try {
    String logoPath = store.getStoreLogo();
    File logoFile = new File(logoPath);
    String assetFileName = "logos/" + logoFile.getName();

    AssetManager assetManager = holder.itemView.getContext().getAssets();
    InputStream is = assetManager.open(assetFileName);
    Drawable logoDrawable = Drawable.createFromStream(is, null);
    holder.ivStoreLogo.setImageDrawable(logoDrawable);
} catch (IOException e) {
    Drawable defaultIcon = ContextCompat.getDrawable(
            holder.itemView.getContext(), android.R.drawable.ic_menu_compass);
    holder.ivStoreLogo.setImageDrawable(defaultIcon);
    Log.d(TAG, "Using default logo for: " + store.getStoreName());
}

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), StoreActivity.class);
            intent.putExtra("store", store);
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return storeList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvStoreName, tvStars, tvType, tvPrice, tvDistance;
        ImageView ivStoreLogo;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStoreName = itemView.findViewById(R.id.tvStoreName);
            tvStars = itemView.findViewById(R.id.tvStars);
            tvType = itemView.findViewById(R.id.tvType);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            tvDistance = itemView.findViewById(R.id.tvDistance);
            ivStoreLogo = itemView.findViewById(R.id.ivStoreLogo);
        }
    }
}