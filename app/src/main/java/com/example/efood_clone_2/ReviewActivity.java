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

        storeName = getIntent().getStringExtra("STORE_NAME");
        TextView tvStoreName = findViewById(R.id.tvStoreNameReview);
        tvStoreName.setText(storeName);

        stars = new ImageView[5];
        stars[0] = findViewById(R.id.star1);
        stars[1] = findViewById(R.id.star2);
        stars[2] = findViewById(R.id.star3);
        stars[3] = findViewById(R.id.star4);
        stars[4] = findViewById(R.id.star5);

        btnSubmitReview = findViewById(R.id.btnSubmitReview);

        for (int i = 0; i < stars.length; i++) {
            final int starPosition = i;
            stars[i].setOnClickListener(v -> {
                selectedRating = starPosition + 1;

                updateStars(selectedRating);

                btnSubmitReview.setVisibility(View.VISIBLE);
            });
        }

        btnSubmitReview.setOnClickListener(v -> {
            String reviewCommand = "REVIEW " + storeName + "," + selectedRating;

            Toast.makeText(ReviewActivity.this,
                    "Sending: " + reviewCommand,
                    Toast.LENGTH_LONG).show();

            TCPClient client = new TCPClient();
            client.sendReview(storeName, selectedRating);

            Intent intent = new Intent(ReviewActivity.this, HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void updateStars(int rating) {
        for (int i = 0; i < stars.length; i++) {
            if (i < rating) {
                stars[i].setImageResource(android.R.drawable.btn_star_big_on);
            } else {
                stars[i].setImageResource(android.R.drawable.btn_star_big_off);
            }
        }
    }
}