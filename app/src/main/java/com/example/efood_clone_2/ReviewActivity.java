package com.example.efood_clone_2;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.efood_clone_2.frontend.TCPClient;

public class ReviewActivity extends AppCompatActivity {

    private ImageView[] stars;
    private Button btnSubmitReview;
    private String storeName;
    private int selectedRating = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review);

        // Get store name from intent
        storeName = getIntent().getStringExtra("STORE_NAME");
        TextView tvStoreName = findViewById(R.id.tvStoreNameReview);
        tvStoreName.setText(storeName);

        // Initialize star views
        stars = new ImageView[5];
        stars[0] = findViewById(R.id.star1);
        stars[1] = findViewById(R.id.star2);
        stars[2] = findViewById(R.id.star3);
        stars[3] = findViewById(R.id.star4);
        stars[4] = findViewById(R.id.star5);

        // Initialize submit button
        btnSubmitReview = findViewById(R.id.btnSubmitReview);

        // Set up click listeners for stars
        for (int i = 0; i < stars.length; i++) {
            final int starPosition = i;
            stars[i].setOnClickListener(v -> {
                // Update the selected rating
                selectedRating = starPosition + 1;

                // Update star colors
                updateStars(selectedRating);

                // Show submit button
                btnSubmitReview.setVisibility(View.VISIBLE);
            });
        }

        // Set up submit button click listener
        btnSubmitReview.setOnClickListener(v -> {
            // Prepare the review command
            String reviewCommand = "REVIEW " + storeName + "," + selectedRating;

            // Show what will be sent to the master
            Toast.makeText(ReviewActivity.this,
                    "Sending: " + reviewCommand,
                    Toast.LENGTH_LONG).show();

            // Send review to server
            TCPClient client = new TCPClient();
            client.sendReview(storeName, selectedRating);

            // Return to home activity
            Intent intent = new Intent(ReviewActivity.this, HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void updateStars(int rating) {
        // Update all stars to reflect the current rating
        for (int i = 0; i < stars.length; i++) {
            if (i < rating) {
                // Fill stars up to the rating
                stars[i].setImageResource(android.R.drawable.btn_star_big_on);
            } else {
                // Empty stars for the rest
                stars[i].setImageResource(android.R.drawable.btn_star_big_off);
            }
        }
    }
}