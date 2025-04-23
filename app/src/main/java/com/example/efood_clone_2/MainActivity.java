package com.example.efood_clone_2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        clearAppData(); // Clear data to simulate fresh install on every run

        // Immediately redirect to BootingActivity
        Intent intent = new Intent(MainActivity.this, BootingActivity.class);
        startActivity(intent);
        finish();
    }

    private void clearAppData() {
        // Clear shared preferences
        SharedPreferences preferences = getSharedPreferences(getPackageName(), MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.commit();

        // Delete cache and files directories
        deleteDir(getCacheDir());
        deleteDir(getFilesDir());
    }

    private boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            for (String child : children) {
                boolean success = deleteDir(new File(dir, child));
                if (!success) {
                    return false;
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        } else {
            return false;
        }
    }
}