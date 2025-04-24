package com.example.efood_clone_2.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.efood_clone_2.R;
import com.example.efood_clone_2.interfaces.CartUpdateListener;
import com.example.efood_clone_2.model.Cart;
import com.example.efood_clone_2.model.CartItem;
import com.example.efood_clone_2.model.Product;
import com.example.efood_clone_2.frontend.TCPClient;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {

    private List<Product> products;
    private CartUpdateListener cartUpdateListener;
    private NumberFormat currencyFormat;
    private String storeName;

    public ProductAdapter(List<Product> products, CartUpdateListener cartUpdateListener, String storeName) {
        this.products = products;
        this.cartUpdateListener = cartUpdateListener;
        this.storeName = storeName;
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
        Product product = products.get(position);
        holder.tvProductName.setText(product.getProductName());
        holder.tvProductPrice.setText(currencyFormat.format(product.getPrice()));
        holder.tvProductType.setText(product.getProductType());
        holder.tvProductAvailability.setText("Available: " + product.getAvailableAmount());


        holder.quantity = 1;
        holder.tvQuantity.setText(String.valueOf(holder.quantity));

        int quantityInCart = getQuantityInCart(product);


        updatePlusButtonState(holder, product);

        holder.btnMinus.setOnClickListener(v -> {
            if (holder.quantity > 1) {
                holder.quantity--;
                holder.tvQuantity.setText(String.valueOf(holder.quantity));

                updatePlusButtonState(holder, product);
            }
        });

        holder.btnPlus.setOnClickListener(v -> {
            int availableForSelection = product.getAvailableAmount() - quantityInCart;
            if (holder.quantity < availableForSelection) {
                holder.quantity++;
                holder.tvQuantity.setText(String.valueOf(holder.quantity));

                updatePlusButtonState(holder, product);
            } else {
                Toast.makeText(v.getContext(), "Cannot add more than available stock", Toast.LENGTH_SHORT).show();
            }
        });

        holder.btnAddToCart.setOnClickListener(v -> {

            int quantityToAdd = holder.quantity;
            int totalRequestedQuantity = quantityInCart + quantityToAdd;

            if (totalRequestedQuantity <= product.getAvailableAmount()) {

                Cart.getInstance().addItem(product, quantityToAdd);

                cartUpdateListener.onCartUpdated();

                holder.expandableLayout.setVisibility(View.GONE);

                Toast.makeText(v.getContext(),
                        quantityToAdd + " " + product.getProductName() + " added to cart",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(v.getContext(),
                        "Cannot add more than available stock (" + product.getAvailableAmount() + ")",
                        Toast.LENGTH_SHORT).show();
            }
        });


        holder.itemView.setOnClickListener(v -> {
            if (holder.expandableLayout.getVisibility() == View.VISIBLE) {
                holder.expandableLayout.setVisibility(View.GONE);
            } else {
                holder.expandableLayout.setVisibility(View.VISIBLE);
                holder.quantity = 1;
                holder.tvQuantity.setText(String.valueOf(holder.quantity));
                updatePlusButtonState(holder, product);
            }
        });
    }

    private void updatePlusButtonState(ViewHolder holder, Product product) {
        int quantityInCart = getQuantityInCart(product);
        int availableForSelection = product.getAvailableAmount() - quantityInCart;


        holder.btnPlus.setEnabled(holder.quantity < availableForSelection);
        holder.btnPlus.setAlpha(holder.quantity < availableForSelection ? 1.0f : 0.5f);
    }

    private int getQuantityInCart(Product product) {
        for (CartItem item : Cart.getInstance().getItems()) {
            if (item.getProduct().getProductName().equals(product.getProductName())) {
                return item.getQuantity();
            }
        }
        return 0;
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvProductName, tvProductPrice, tvProductType, tvProductAvailability, tvQuantity;
        ImageButton btnMinus, btnPlus;
        Button btnAddToCart;
        LinearLayout expandableLayout;
        int quantity = 1;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvProductPrice = itemView.findViewById(R.id.tvProductPrice);
            tvProductType = itemView.findViewById(R.id.tvProductType);
            tvProductAvailability = itemView.findViewById(R.id.tvProductAvailability);
            tvQuantity = itemView.findViewById(R.id.tvQuantity);
            btnMinus = itemView.findViewById(R.id.btnMinus);
            btnPlus = itemView.findViewById(R.id.btnPlus);
            btnAddToCart = itemView.findViewById(R.id.btnAddToCart);
            expandableLayout = itemView.findViewById(R.id.expandableLayout);
        }
    }
}