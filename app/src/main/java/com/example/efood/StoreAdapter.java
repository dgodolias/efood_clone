package com.example.efood;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class StoreAdapter extends RecyclerView.Adapter<StoreAdapter.StoreViewHolder> {

    private List<Store> storeList;

    public StoreAdapter(List<Store> storeList) {
        this.storeList = storeList;
    }

    @NonNull
    @Override
    public StoreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_store, parent, false);
        return new StoreViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StoreViewHolder holder, int position) {
        Store store = storeList.get(position);
        holder.textViewName.setText(store.getName());
        
        // Show coordinates as address (since we don't have actual address in JSON)
        holder.textViewAddress.setText(String.format("Location: %.6f, %.6f", 
                store.getLatitude(), store.getLongitude()));
        
        holder.textViewStars.setText(String.format("%d Stars (%d votes)", 
                store.getStars(), store.getNoOfVotes()));
        
        // Show food category since we don't have cuisines array in JSON
        holder.textViewCuisines.setText(store.getFoodCategory());
    }

    @Override
    public int getItemCount() {
        return storeList.size();
    }

    static class StoreViewHolder extends RecyclerView.ViewHolder {
        TextView textViewName, textViewAddress, textViewStars, textViewCuisines;

        public StoreViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewName = itemView.findViewById(R.id.textViewName);
            textViewAddress = itemView.findViewById(R.id.textViewAddress);
            textViewStars = itemView.findViewById(R.id.textViewStars);
            textViewCuisines = itemView.findViewById(R.id.textViewCuisines);
        }
    }
}