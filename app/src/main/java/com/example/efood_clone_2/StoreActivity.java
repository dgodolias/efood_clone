package com.example.efood_clone_2;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.efood_clone_2.adapter.CartAdapter;
import com.example.efood_clone_2.adapter.ProductAdapter;
import com.example.efood_clone_2.interfaces.CartUpdateListener;
import com.example.efood_clone_2.interfaces.StoreDetailsCallback;
import com.example.efood_clone_2.model.Cart;
import com.example.efood_clone_2.model.CartItem;
import com.example.efood_clone_2.model.Product;
import com.example.efood_clone_2.model.Store;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.example.efood_clone_2.frontend.TCPClient;

import java.io.InputStream;
import java.net.URL;
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
    private StringBuilder orderSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store);

        orderSummary = new StringBuilder();

        tvStoreName = findViewById(R.id.tvStoreName);
        tvStoreStars = findViewById(R.id.tvStoreStars);
        tvStoreType = findViewById(R.id.tvStoreType);
        tvStorePrice = findViewById(R.id.tvStorePrice);
        productsRecyclerView = findViewById(R.id.productsRecyclerView);
        ImageView backButton = findViewById(R.id.backButton);
        btnCart = findViewById(R.id.btnCart);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        ImageView ivStoreLogo = findViewById(R.id.ivStoreLogo);

        currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);
        tcpClient = TCPClient.getInstance();

        if (getIntent().hasExtra("store")) {
            store = (Store) getIntent().getSerializableExtra("store");

            tvStoreName.setText(store.getStoreName());
            tvStoreStars.setText("★ " + store.getStars());
            tvStoreType.setText(store.getFoodCategory());
            tvStorePrice.setText(store.getPriceCategory());

            String logoPath = store.getStoreLogo();
            if (logoPath != null && !logoPath.isEmpty()) {
                if (logoPath.startsWith("http://") || logoPath.startsWith("https://")) {
                    // Handle remote URL
                    new Thread(() -> {
                        try {
                            URL url = new URL(logoPath);
                            final Bitmap bitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream());
                            runOnUiThread(() -> ivStoreLogo.setImageBitmap(bitmap));
                        } catch (Exception e) {
                            e.printStackTrace();
                            runOnUiThread(() -> ivStoreLogo.setImageResource(R.drawable.default_store));
                        }
                    }).start();
                } else {
                    // Handle local asset path
                    try {
                        // Extract just the filename if it's a path
                        String assetFileName = logoPath;
                        if (logoPath.contains("/")) {
                            assetFileName = logoPath.substring(logoPath.lastIndexOf("/") + 1);
                        }

                        // Try to load from assets
                        InputStream is = getAssets().open("logos/" + assetFileName);
                        Bitmap bitmap = BitmapFactory.decodeStream(is);
                        ivStoreLogo.setImageBitmap(bitmap);
                    } catch (Exception e) {
                        e.printStackTrace();
                        ivStoreLogo.setImageResource(R.drawable.default_store);
                    }
                }
            } else {
                ivStoreLogo.setImageResource(R.drawable.default_store);
            }

            loadingProgressBar.setVisibility(View.VISIBLE);
            productsRecyclerView.setVisibility(View.GONE);

            tcpClient.getStoreDetails(store.getStoreName(), new StoreDetailsCallback() {
                @Override
                public void onStoreDetailsReceived(Store fullStore) {
                    store = fullStore;

                    setupProductsRecyclerView(store.getProducts());

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

        backButton.setOnClickListener(v -> finish());

        btnCart.setOnClickListener(v -> showCartBottomSheet());

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

        cartRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        CartAdapter cartAdapter = new CartAdapter(Cart.getInstance().getItems());
        cartRecyclerView.setAdapter(cartAdapter);

        tvTotalPrice.setText(currencyFormat.format(Cart.getInstance().getTotalPrice()));

        btnClearCart.setOnClickListener(v -> {

            Cart.getInstance().clear();
            updateCartButton();
            bottomSheetDialog.dismiss();

        });

        btnCheckout.setOnClickListener(v -> {
            List<CartItem> items = Cart.getInstance().getItems();
            if (items.isEmpty()) {
                Toast.makeText(StoreActivity.this, "Your cart is empty", Toast.LENGTH_SHORT).show();
            } else {
                StringBuilder summary = new StringBuilder("Order Summary:\n\n");
                StringBuilder compactFormat = new StringBuilder();
                boolean firstItem = true;

                compactFormat.append(store.getStoreName());

                for (CartItem item : Cart.getInstance().getItems()) {
                    summary.append(item.getQuantity())
                           .append(" x ")
                           .append(item.getProduct().getProductName())
                           .append(" - ")
                           .append(currencyFormat.format(item.getSubtotal()))
                           .append("\n");

                    if (firstItem) {
                        compactFormat.append("|");
                    } else {
                        compactFormat.append("#");
                    }
                    compactFormat.append(item.getQuantity())
                                .append("*\"")
                                .append(item.getProduct().getProductName())
                                .append("\"");
                    firstItem = false;
                }

                summary.append("\nTotal: ")
                       .append(currencyFormat.format(Cart.getInstance().getTotalPrice()));

                Log.d("Checkout", summary.toString());
                Log.d("Checkout", "Compact format: " + compactFormat.toString());

                TCPClient client = TCPClient.getInstance();
                client.buy(compactFormat.toString());

                Toast.makeText(StoreActivity.this,
                        "Order placed!\n" + summary.toString(),
                        Toast.LENGTH_LONG).show();

                Cart.getInstance().clear();
                updateCartButton();
                bottomSheetDialog.dismiss();

                Intent intent = new Intent(StoreActivity.this, ReviewActivity.class);
                intent.putExtra("STORE_NAME", store.getStoreName());
                startActivity(intent);
            }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Cart.getInstance().clear();
    }
}