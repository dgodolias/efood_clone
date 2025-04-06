package com.example.efood_clone_2.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.efood_clone_2.R;
import com.example.efood_clone_2.model.Product;
import java.util.List;
import java.text.NumberFormat;
import java.util.Locale;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {

    private List<Product> productList;
    private NumberFormat currencyFormat;

    public ProductAdapter(List<Product> productList) {
        this.productList = productList;
        this.currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Product product = productList.get(position);
        holder.tvProductName.setText(product.getProductName());
        holder.tvProductType.setText(product.getProductType());
        holder.tvProductAvailability.setText("Available: " + product.getAvailableAmount());
        holder.tvProductPrice.setText(currencyFormat.format(product.getPrice()));
    }

    @Override
    public int getItemCount() {
        return productList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvProductName, tvProductType, tvProductAvailability, tvProductPrice;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvProductType = itemView.findViewById(R.id.tvProductType);
            tvProductAvailability = itemView.findViewById(R.id.tvProductAvailability);
            tvProductPrice = itemView.findViewById(R.id.tvProductPrice);
        }
    }
}