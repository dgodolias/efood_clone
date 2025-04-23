package com.example.efood_clone_2;

        import android.content.Intent;
        import android.os.Bundle;
        import android.util.Log;
        import android.view.View;
        import android.widget.Button;
        import android.widget.ImageView;
        import android.widget.TextView; import android.widget.ProgressBar; import android.widget.Toast;

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
        import com.example.efood_clone_2.frontend.TCPClient;

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
            private TCPClient tcpClient;
            private ProgressBar loadingProgressBar;

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
                loadingProgressBar = findViewById(R.id.loadingProgressBar);

                currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);
                tcpClient = new TCPClient();

                // Get store from intent
                if (getIntent().hasExtra("store")) {
                    store = (Store) getIntent().getSerializableExtra("store");

                    // Set basic store information first
                    tvStoreName.setText(store.getStoreName());
                    tvStoreStars.setText("★ " + store.getStars());
                    tvStoreType.setText(store.getFoodCategory());
                    tvStorePrice.setText(store.getPriceCategory());

                    // Show loading indicator
                    loadingProgressBar.setVisibility(View.VISIBLE);
                    productsRecyclerView.setVisibility(View.GONE);

                    // Get full store details including products
                    tcpClient.getStoreDetails(store.getStoreName(), new TCPClient.StoreDetailsCallback() {
                        @Override
                        public void onStoreDetailsReceived(Store fullStore) {
                            store = fullStore; // Update store with full details

                            // Setup products recycler view with the products from full store
                            setupProductsRecyclerView(store.getProducts());

                            // Hide loading indicator
                            loadingProgressBar.setVisibility(View.GONE);
                            productsRecyclerView.setVisibility(View.VISIBLE);

                            Log.d("StoreActivity", "Loaded " + store.getProducts().size() +
                                    " products for " + store.getStoreName());
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(StoreActivity.this,
                                    "Error loading store details: " + error, Toast.LENGTH_LONG).show();
                            loadingProgressBar.setVisibility(View.GONE);
                            Log.e("StoreActivity", "Error loading store details: " + error);
                        }
                    });
                }

                // Set up back button
                backButton.setOnClickListener(v -> finish());

                // Set up cart button click listener
                btnCart.setOnClickListener(v -> showCartBottomSheet());

                // Update cart button
                updateCartButton();
            }

            private void setupProductsRecyclerView(List<Product> products) {
                productsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
                productAdapter = new ProductAdapter(products, this, store.getStoreName());
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