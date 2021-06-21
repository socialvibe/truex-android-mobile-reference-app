package com.truex.referenceapp;

import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.truex.referenceapp.detail.DetailFragment;
import com.truex.referenceapp.player.PlayerFragment;

public class MainActivity extends AppCompatActivity {
    private static final String CLASSTAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(CLASSTAG, "onCreate");
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        loadDetailFragment();
    }

    private void loadDetailFragment() {
        getSupportFragmentManager().beginTransaction()
            .add(R.id.activity_main, new DetailFragment())
            .commit();
    }
}