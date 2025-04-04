package com.example.efood_clone_2;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Immediately redirect to BootingActivity
        Intent intent = new Intent(MainActivity.this, BootingActivity.class);
        startActivity(intent);
        finish();
    }
}