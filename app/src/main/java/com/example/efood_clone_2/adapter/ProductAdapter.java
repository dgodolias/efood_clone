package com.example.efood_clone_2.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.efood_clone_2.R;
import com.example.efood_clone_2.interfaces.CartUpdateListener;
import com.example.efood_clone_2.model.Cart;
import com.example.efood_clone_2.model.Product;
import java.util.List;
import java.text.NumberFormat;
import java.util.Locale;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {

    private List<Product> productList;
    private NumberFormat currencyFormat;
    private int expandedPosition = -1;
    private CartUpdateListener cartUpdateListener;

    public ProductAdapter(List<Product> productList, CartUpdateListener cartUpdateListener) {
        this.productList = productList;
        this.currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);
        this.cartUpdateListener = cartUpdateListener;
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

        // Manage expansion state
        final boolean isExpanded = position == expandedPosition;
        holder.expandableLayout.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        holder.itemView.setActivated(isExpanded);

        // Reset quantity when collapsed
        if (!isExpanded) {
            holder.tvQuantity.setText("1");
        }

        holder.itemView.setOnClickListener(v -> {
            expandedPosition = isExpanded ? -1 : position;
            notifyDataSetChanged();
        });

        // Quantity controls
        holder.btnMinus.setOnClickListener(v -> {
            int quantity = Integer.parseInt(holder.tvQuantity.getText().toString());
            if (quantity > 1) {
                quantity--;
                holder.tvQuantity.setText(String.valueOf(quantity));
            }
        });

        holder.btnPlus.setOnClickListener(v -> {
            int quantity = Integer.parseInt(holder.tvQuantity.getText().toString());
            int available = product.getAvailableAmount();
            if (quantity < available) {
                quantity++;
                holder.tvQuantity.setText(String.valueOf(quantity));
            }
        });

        holder.btnAddToCart.setOnClickListener(v -> {
            int quantity = Integer.parseInt(holder.tvQuantity.getText().toString());
            Cart.getInstance().addItem(product, quantity);
            expandedPosition = -1;
            notifyDataSetChanged();
            if (cartUpdateListener != null) {
                cartUpdateListener.onCartUpdated();
            }
        });
    }

    @Override
    public int getItemCount() {
        return productList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvProductName, tvProductType, tvProductAvailability, tvProductPrice, tvQuantity;
        LinearLayout expandableLayout;
        ImageButton btnMinus, btnPlus;
        Button btnAddToCart;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvProductType = itemView.findViewById(R.id.tvProductType);
            tvProductAvailability = itemView.findViewById(R.id.tvProductAvailability);
            tvProductPrice = itemView.findViewById(R.id.tvProductPrice);
            expandableLayout = itemView.findViewById(R.id.expandableLayout);
            tvQuantity = itemView.findViewById(R.id.tvQuantity);
            btnMinus = itemView.findViewById(R.id.btnMinus);
            btnPlus = itemView.findViewById(R.id.btnPlus);
            btnAddToCart = itemView.findViewById(R.id.btnAddToCart);
        }
    }
}