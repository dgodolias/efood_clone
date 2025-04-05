package com.example.efood_clone_2;

    import android.content.SharedPreferences;
    import android.os.Bundle;
    import android.view.Gravity;
    import android.view.LayoutInflater;
    import android.view.View;
    import android.view.ViewGroup;
    import android.widget.Button;
    import android.widget.PopupWindow;
    import android.widget.TextView;
    import android.widget.Toast;
    import androidx.appcompat.app.AppCompatActivity;
    import androidx.cardview.widget.CardView;
    import androidx.recyclerview.widget.LinearLayoutManager;
    import androidx.recyclerview.widget.RecyclerView;
    import com.example.efood_clone_2.adapter.StoreAdapter;
    import com.example.efood_clone_2.model.Store;
    import java.util.ArrayList;
    import java.util.List;
    import java.util.Random;

    public class HomeActivity extends AppCompatActivity {
        private double savedLatitude = 37.9838;
        private double savedLongitude = 23.7275;
        private SharedPreferences preferences;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_home);

            preferences = getPreferences(MODE_PRIVATE);

            // Load saved location if it exists
            savedLatitude = preferences.getFloat("latitude", (float) savedLatitude);
            savedLongitude = preferences.getFloat("longitude", (float) savedLongitude);

            // Create list of fake stores
            List<Store> storeList = createStoreList();

            // Set up RecyclerView
            RecyclerView recyclerView = findViewById(R.id.recyclerView);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setAdapter(new StoreAdapter(storeList));

            // Set up location icon click listener
            CardView locationIconContainer = findViewById(R.id.locationIconContainer);
            locationIconContainer.setOnClickListener(v -> showLocationPopup(v));
        }

        private void showLocationPopup(View anchorView) {
            // Inflate the popup layout
            View popupView = LayoutInflater.from(this).inflate(R.layout.popup_location, null);

            // Create the popup window - use MATCH_PARENT for width
            int width = ViewGroup.LayoutParams.MATCH_PARENT;
            int height = ViewGroup.LayoutParams.WRAP_CONTENT;
            final PopupWindow popupWindow = new PopupWindow(popupView, width, height, true);

            // Make the popup dismissable when clicked outside
            popupWindow.setOutsideTouchable(true);

            // Show the current saved location coordinates
            TextView tvCoordinates = popupView.findViewById(R.id.tvCoordinates);
            tvCoordinates.setText("Current Location: " + savedLatitude + ", " + savedLongitude);

            // Set up the set location button
            Button btnSetLocation = popupView.findViewById(R.id.btnSetLocation);
            btnSetLocation.setOnClickListener(v -> {
                // Simulate selecting a random location nearby
                Random random = new Random();
                savedLatitude = 37.9838 + (random.nextDouble() - 0.5) * 0.02;
                savedLongitude = 23.7275 + (random.nextDouble() - 0.5) * 0.02;

                // Format to 4 decimal places for display
                String coordinates = String.format("%.4f, %.4f", savedLatitude, savedLongitude);

                // Save the location to preferences
                SharedPreferences.Editor editor = preferences.edit();
                editor.putFloat("latitude", (float) savedLatitude);
                editor.putFloat("longitude", (float) savedLongitude);
                editor.apply();

                // Display the selected location
                Toast.makeText(HomeActivity.this,
                    "Location set to: " + coordinates, Toast.LENGTH_SHORT).show();

                popupWindow.dismiss();
            });

            // Show the popup window
            popupWindow.showAsDropDown(anchorView, 0, 10, Gravity.TOP);
        }

        private List<Store> createStoreList() {
            List<Store> stores = new ArrayList<>();

            // Add 10 fake stores
            stores.add(new Store("Pizza Paradise", 37.9838, 23.7275, "Pizza", 4, "$$"));
            stores.add(new Store("Greek Gyros", 37.9759, 23.7357, "Souvlaki", 5, "$"));
            stores.add(new Store("Sushi Master", 37.9683, 23.7299, "Sushi", 4, "$$$"));
            stores.add(new Store("Burger House", 37.9785, 23.7365, "Burgers", 3, "$"));
            stores.add(new Store("Pasta Italiana", 37.9833, 23.7312, "Italian", 5, "$$"));
            stores.add(new Store("Taco Town", 37.9721, 23.7256, "Mexican", 4, "$"));
            stores.add(new Store("BBQ Masters", 37.9801, 23.7231, "Barbecue", 3, "$$"));
            stores.add(new Store("Veggie Heaven", 37.9865, 23.7189, "Vegetarian", 4, "$$$"));
            stores.add(new Store("Street Food Corner", 37.9736, 23.7321, "Street Food", 5, "$"));
            stores.add(new Store("Spicy Noodles", 37.9712, 23.7406, "Asian", 4, "$$"));

            return stores;
        }
    }