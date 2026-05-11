package com.dreampediatrics.app;

import static android.view.View.GONE;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FailedActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_failed);

        // Get error message from intent
        String errorMessage = getIntent().getStringExtra("error_message");
        if (errorMessage == null) {
            errorMessage = "Account verification failed. Please contact support.";
        }

        // Initialize views
        TextView errorText = findViewById(R.id.errorText);
        TextView errorDetails = findViewById(R.id.errorDetails);
        MaterialButton logoutButton = findViewById(R.id.logoutButton);
        MaterialButton contactSupportButton = findViewById(R.id.contactSupportButton);

        // Set error message
        errorText.setText("Account Verification Failed");
        errorDetails.setText(errorMessage);

        // Set up button listeners
        logoutButton.setOnClickListener(v -> logoutAndReturnToAuth());
        contactSupportButton.setOnClickListener(v -> openSupportLink());
    }

    /**
     * Logout user and return to AuthActivity
     */
    private void logoutAndReturnToAuth() {
        new AlertDialog.Builder(this)
                .setTitle("Logout Confirmation")
                .setMessage("This will sign you out and clear your local data. You can try logging in again from the correct device.")
                .setPositiveButton("Logout", (dialog, which) -> {
                    performLogout();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Perform actual logout
     */
    private void performLogout() {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

            // Helper to clear client state and navigate to AuthActivity
            Runnable clearAndGotoAuth = () -> {
                // Clear shared preferences
                try {
                    SharedPreferences dp = getSharedPreferences("DreamPediatricsPrefs", MODE_PRIVATE);
                    dp.edit().clear().apply();

                    SharedPreferences app = getSharedPreferences("app_prefs", MODE_PRIVATE);
                    app.edit().clear().apply();
                } catch (Exception ignored) {}

                // Ensure Room DB cleared (run off main thread)
                new Thread(() -> {
                    try {
                        AppDatabase db = AppDatabase.getInstance(FailedActivity.this);
                        if (db != null && db.appDao() != null) {
                            db.appDao().deleteAllChapters();
                            db.appDao().deleteAllTopics();
                            db.appDao().deleteAllBookmarks();
                            db.appDao().deleteAllHistory();
                        }
                    } catch (Exception ex) {
                        Log.w("FailedActivity", "Error clearing Room DB on logout: " + ex.getMessage());
                    }
                }).start();

                // Sign out from Firebase Auth (safe even if already signed out)
                try {
                    FirebaseAuth.getInstance().signOut();
                } catch (Exception ignored) {}

                // Navigate to AuthActivity and finish
                Intent intent = new Intent(FailedActivity.this, AuthActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            };

            if (user == null || user.getUid() == null) {
                // No user present — just clear local state and go to auth
                clearAndGotoAuth.run();
                return;
            }

            // First check accountReset flag on server
            DatabaseReference accRef = FirebaseDatabase.getInstance().getReference()
                    .child("users").child(user.getUid()).child("accountReset");

            accRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Boolean accountReset = snapshot.getValue(Boolean.class);

                    if (Boolean.TRUE.equals(accountReset)) {
                        // If accountReset == true -> hide logout button and inform user to contact support
                        runOnUiThread(() -> {
                            View logoutBtn = findViewById(R.id.logoutButton);
                            if (logoutBtn != null) logoutBtn.setVisibility(View.GONE);
                            Toast.makeText(FailedActivity.this,
                                    "Please contact support for assistance.",
                                    Toast.LENGTH_LONG).show();
                        });
                        return;
                    }

                    // accountReset is null or false -> proceed with the normal logout flow:
                    // set accountReset = true, loggedIn = false and update deviceId to current device on server,
                    // then clear local and sign out
                    DatabaseReference userRef = FirebaseDatabase.getInstance().getReference()
                            .child("users").child(user.getUid());

                    // Get current device id (Android ID fallback)
                    String currentDeviceId = null;
                    try {
                        currentDeviceId = Settings.Secure.getString(FailedActivity.this.getContentResolver(), Settings.Secure.ANDROID_ID);
                        if (currentDeviceId == null || currentDeviceId.isEmpty()) {
                            currentDeviceId = UUID.randomUUID().toString();
                        }
                    } catch (Exception e) {
                        currentDeviceId = UUID.randomUUID().toString();
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("accountReset", true);
                    updates.put("loggedIn", false);
                    updates.put("deviceId", currentDeviceId);

                    userRef.updateChildren(updates)
                            .addOnSuccessListener(aVoid -> {
                                // On success, clear client data and sign out
                                clearAndGotoAuth.run();
                            })
                            .addOnFailureListener(e -> {
                                String msg = e != null && e.getMessage() != null ? e.getMessage() : "unknown";
                                Toast.makeText(FailedActivity.this, "Logout failed (server): " + msg, Toast.LENGTH_LONG).show();
                                Log.e("FailedActivity", "Failed to update server on logout: " + msg);
                                // Still attempt local cleanup to avoid leaving user stuck
                                clearAndGotoAuth.run();
                            });
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.w("FailedActivity", "accountReset read cancelled: " + error.getMessage());
                    // On DB read failure, attempt to set flags and deviceId then clean up locally
                    DatabaseReference userRef = FirebaseDatabase.getInstance().getReference()
                            .child("users").child(user.getUid());

                    String currentDeviceId;
                    try {
                        currentDeviceId = Settings.Secure.getString(FailedActivity.this.getContentResolver(), Settings.Secure.ANDROID_ID);
                        if (currentDeviceId == null || currentDeviceId.isEmpty()) {
                            currentDeviceId = UUID.randomUUID().toString();
                        }
                    } catch (Exception e) {
                        currentDeviceId = UUID.randomUUID().toString();
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("accountReset", true);
                    updates.put("loggedIn", false);
                    updates.put("deviceId", currentDeviceId);

                    userRef.updateChildren(updates)
                            .addOnSuccessListener(aVoid -> clearAndGotoAuth.run())
                            .addOnFailureListener(e -> clearAndGotoAuth.run());
                }
            });

        } catch (Exception e) {
            String em = e != null && e.getMessage() != null ? e.getMessage() : "unknown";
            Toast.makeText(this, "Logout failed: " + em, Toast.LENGTH_LONG).show();
            Log.e("FailedActivity", "performLogout exception: " + em, e);
        }
    }

    /**
     * Open email app to contact support
     */
    private void contactSupport() {
        try {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:support@dreampediatrics64.com"));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Account Verification Issue");
            emailIntent.putExtra(Intent.EXTRA_TEXT,
                    "Hello,\n\nI'm experiencing an account verification issue with Dream Pediatrics app.\n\n" +
                            "Error: " + getIntent().getStringExtra("error_message") + "\n\n" +
                            "Device Model: " + Build.MODEL + "\n" +
                            "Android Version: " + Build.VERSION.RELEASE + "\n\n" +
                            "Please help resolve this issue.\n\nThank you.");

            startActivity(Intent.createChooser(emailIntent, "Contact Support"));
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open email app", Toast.LENGTH_SHORT).show();
        }
    }

    // Replace with your real support URL
    private static final String SUPPORT_URL = "https://t.me/dream_pedi";

    private void openSupportLink() {
        try {
            String url = SUPPORT_URL;
            if (url == null || url.trim().isEmpty()) {
                Toast.makeText(this, "Support link not configured", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            // prefer browser; if none, fallback to chooser
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (ActivityNotFoundException anf) {
            // no activity to handle URL
            Toast.makeText(this, "No browser available to open support link", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e("FailedActivity", "openSupportLink error", e);
            Toast.makeText(this, "Unable to open support link", Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * Prevent user from navigating back
     */
    @Override
    public void onBackPressed() {
        // Show dialog explaining why they can't go back
        new AlertDialog.Builder(this)
                .setTitle("Account Verification Required")
                .setMessage("Please resolve the account verification issue before continuing.")
                .setPositiveButton("OK", null)
                .show();
    }
}
