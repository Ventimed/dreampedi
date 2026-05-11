package com.dreampediatrics.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        updateStatusBarColor();
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("About Us");
        }

        // Setup click listeners for links
        setupLinks();
    }

    private void setupLinks() {
        TextView email1 = findViewById(R.id.email1);
        email1.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:yonasdg.12@gmail.com"));
            startActivity(Intent.createChooser(intent, "Send Email"));
        });

        TextView email2 = findViewById(R.id.email2);
        email2.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:support@ventimedguide.com"));
            startActivity(Intent.createChooser(intent, "Send Email"));
        });



        TextView twitter = findViewById(R.id.twitter);
        twitter.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/dream_pedi"));
            startActivity(intent);
        });

        TextView tgvm = findViewById(R.id.tgvm);
        tgvm.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/ventimed_support"));
            startActivity(intent);
        });

        // Previous apps - show toast on click (as placeholders)
        LinearLayout cardio = findViewById(R.id.vmb);
        cardio.setOnClickListener(v -> Toast.makeText(this, "Cardio App Coming Soon!", Toast.LENGTH_SHORT).show());

        LinearLayout neuro = findViewById(R.id.vmg);
        neuro.setOnClickListener(v -> Toast.makeText(this, "Neuro App Coming Soon!", Toast.LENGTH_SHORT).show());

        LinearLayout ortho = findViewById(R.id.gg);
        ortho.setOnClickListener(v -> Toast.makeText(this, "Ortho App Coming Soon!", Toast.LENGTH_SHORT).show());

        LinearLayout derma = findViewById(R.id.derma);
        derma.setOnClickListener(v -> Toast.makeText(this, "Derma App Coming Soon!", Toast.LENGTH_SHORT).show());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateStatusBarColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            int color = ContextCompat.getColor(this, R.color.toolbar_bg);
            window.setStatusBarColor(color);
            WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
            boolean useDarkIcons = ColorUtils.calculateLuminance(color) > 0.5;
            insetsController.setAppearanceLightStatusBars(useDarkIcons);
        }
    }
}