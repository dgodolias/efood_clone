package com.example.efood_clone_2;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

import com.example.efood_clone_2.frontend.TCPClient;
import com.example.efood_clone_2.model.Store;
import com.example.efood_clone_2.model.StoreDataManager;

import java.util.List;

public class BootingActivity extends AppCompatActivity {
    private static final String TAG = "BootingActivity";
    private boolean dataLoaded = false;
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booting);

        ImageView imageView = findViewById(R.id.imageView);

        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(1500);
        imageView.startAnimation(fadeIn);

        // Preload store data
        preloadStoreData();

        // Give at least 2.5 seconds for splash screen
        handler.postDelayed(this::proceedToHomeActivity, 2500);
    }

    private void preloadStoreData() {
        // Default coordinates (Athens, Greece)
        double latitude = 37.9838;
        double longitude = 23.7275;

        StoreDataManager.getInstance().setLoading(true);

        TCPClient client = TCPClient.getInstance();
        client.getNearbyStores(latitude, longitude, new TCPClient.StoreListCallback() {
            @Override
            public void onStoresReceived(List<Store> stores) {
                Log.d(TAG, "Preloaded " + stores.size() + " stores");
                StoreDataManager.getInstance().setStores(stores);
                StoreDataManager.getInstance().setLoading(false);
                dataLoaded = true;

            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error preloading stores: " + error);
                StoreDataManager.getInstance().setLoading(false);
                // Will continue to HomeActivity after minimum splash time
            }
        });
    }

    private void proceedToHomeActivity() {
        if (!isFinishing()) {
            Intent intent = new Intent(BootingActivity.this, HomeActivity.class);
            startActivity(intent);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}