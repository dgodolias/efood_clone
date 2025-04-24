package com.example.efood_clone_2.adapter;

import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.efood_clone_2.R;
import com.example.efood_clone_2.StoreActivity;
import com.example.efood_clone_2.model.Store;
import java.util.List;

public class StoreAdapter extends RecyclerView.Adapter<StoreAdapter.ViewHolder> {

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
        holder.tvName.setText(store.getStoreName());
        holder.tvStars.setText(store.getStars() + "★");
        holder.tvType.setText(store.getFoodCategory());
        holder.tvDistance.setText(store.getFormattedDistance());
        holder.tvCoordinates.setText(store.getCoordinates());

        // In your StoreAdapter's onBindViewHolder method
        Log.d("PriceCategory", "Store: " + store.getStoreName() +
              ", Price: " + store.getPriceCategory() +
              ", Products: " + store.getProducts().size());

        // Make sure to use the dynamic price category here
        holder.tvPrice.setText(store.getPriceCategory());

        // Set click listener
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
        TextView tvName, tvStars, tvCoordinates, tvType, tvPrice, tvDistance;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvStoreName);
            tvStars = itemView.findViewById(R.id.tvStars);
            tvCoordinates = itemView.findViewById(R.id.tvCoordinates);
            tvType = itemView.findViewById(R.id.tvType);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            tvDistance = itemView.findViewById(R.id.tvDistance);
        }
    }
}