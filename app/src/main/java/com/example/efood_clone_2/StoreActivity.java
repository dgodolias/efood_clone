package com.example.efood_clone_2;

        import android.os.Bundle;
        import android.view.View;
        import android.widget.Button;
        import android.widget.ImageView;
        import android.widget.TextView;

        import androidx.appcompat.app.AppCompatActivity;
        import androidx.constraintlayout.widget.ConstraintLayout;
        import androidx.recyclerview.widget.LinearLayoutManager;
        import androidx.recyclerview.widget.RecyclerView;

        import com.example.efood_clone_2.adapter.CartAdapter;
        import com.example.efood_clone_2.adapter.ProductAdapter;
        import com.example.efood_clone_2.interfaces.CartUpdateListener;
        import com.example.efood_clone_2.model.Cart;
        import com.example.efood_clone_2.model.CartItem;
        import com.example.efood_clone_2.model.Product;
        import com.example.efood_clone_2.model.Store;
        import com.google.android.material.bottomsheet.BottomSheetDialog;

        import java.text.NumberFormat;
        import java.util.List;
        import java.util.Locale;

        public class StoreActivity extends AppCompatActivity implements CartUpdateListener {

            private Store store;
            private TextView tvStoreName, tvStoreStars, tvStoreType, tvStorePrice;
            private RecyclerView productsRecyclerView;
            private ProductAdapter productAdapter;
            private Button btnCart;
            private NumberFormat currencyFormat;

            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_store);

                // Initialize views
                tvStoreName = findViewById(R.id.tvStoreName);
                tvStoreStars = findViewById(R.id.tvStoreStars);
                tvStoreType = findViewById(R.id.tvStoreType);
                tvStorePrice = findViewById(R.id.tvStorePrice);
                productsRecyclerView = findViewById(R.id.productsRecyclerView);
                ImageView backButton = findViewById(R.id.backButton);
                btnCart = findViewById(R.id.btnCart);

                currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);

                // Get store from intent
                if (getIntent().hasExtra("store")) {
                    store = (Store) getIntent().getSerializableExtra("store");
                }

                // Set up back button
                backButton.setOnClickListener(v -> finish());

                // Set up cart button click listener
                btnCart.setOnClickListener(v -> showCartBottomSheet());

                // Populate store information
                if (store != null) {
                    tvStoreName.setText(store.getName());
                    tvStoreStars.setText("★ " + store.getStars());
                    tvStoreType.setText(store.getFoodType());
                    tvStorePrice.setText(store.getPriceCategory());

                    // Set up products recycler view
                    setupProductsRecyclerView(store.getProducts());
                }

                // Update cart button
                updateCartButton();
            }

            private void setupProductsRecyclerView(List<Product> products) {
                productsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
                productAdapter = new ProductAdapter(products, this);
                productsRecyclerView.setAdapter(productAdapter);
            }

            private void showCartBottomSheet() {
                BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
                View bottomSheetView = getLayoutInflater().inflate(R.layout.cart_bottom_sheet, null);
                bottomSheetDialog.setContentView(bottomSheetView);

                RecyclerView cartRecyclerView = bottomSheetView.findViewById(R.id.cartRecyclerView);
                TextView tvTotalPrice = bottomSheetView.findViewById(R.id.tvTotalPrice);
                Button btnClearCart = bottomSheetView.findViewById(R.id.btnClearCart);
                Button btnCheckout = bottomSheetView.findViewById(R.id.btnCheckout);

                // Setup cart recyclerview
                cartRecyclerView.setLayoutManager(new LinearLayoutManager(this));
                CartAdapter cartAdapter = new CartAdapter(Cart.getInstance().getItems());
                cartRecyclerView.setAdapter(cartAdapter);

                // Display total price
                tvTotalPrice.setText(currencyFormat.format(Cart.getInstance().getTotalPrice()));

                // Clear cart button
                btnClearCart.setOnClickListener(v -> {
                    Cart.getInstance().clear();
                    updateCartButton();
                    bottomSheetDialog.dismiss();
                });

                // Checkout button (no functionality yet)
                btnCheckout.setOnClickListener(v -> {
                    bottomSheetDialog.dismiss();
                });

                bottomSheetDialog.show();
            }

            private void updateCartButton() {
                int itemCount = Cart.getInstance().getTotalQuantity();
                if (itemCount > 0) {
                    btnCart.setText("Cart (" + itemCount + ") 🛒");
                    btnCart.setVisibility(View.VISIBLE);
                } else {
                    btnCart.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCartUpdated() {
                updateCartButton();
            }
        }