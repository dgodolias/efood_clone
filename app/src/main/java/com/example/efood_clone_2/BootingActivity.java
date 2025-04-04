package com.example.efood_clone_2;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

public class BootingActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booting);

        // Find the ImageView
        ImageView imageView = findViewById(R.id.imageView);

        // Create fade-in animation
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(1500); // 1.5 seconds
        imageView.startAnimation(fadeIn);

        // Navigate to HomeActivity after 2.5 seconds
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(BootingActivity.this, HomeActivity.class);
            startActivity(intent);
            finish(); // Close splash screen
        }, 2500); // 1.5s fade + 1s wait
    }
}