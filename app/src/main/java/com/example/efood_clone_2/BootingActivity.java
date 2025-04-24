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


        ImageView imageView = findViewById(R.id.imageView);

        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(1500);
        imageView.startAnimation(fadeIn);

        new Handler().postDelayed(() -> {
            Intent intent = new Intent(BootingActivity.this, HomeActivity.class);
            startActivity(intent);
            finish();
        }, 2500);
    }
}