package com.dream.pediadmin;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private static final String PREFS = "DreamPediatricsPrefs";
    private static final String KEY_LOGGED_IN_ONCE = "logged_in_once";

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private ProgressBar progress;
    private TextView tvError;

    private FirebaseAuth mAuth;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Firebase
        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        // If user previously logged in once OR there is an active Firebase user, skip login.
        boolean loggedInOnce = prefs.getBoolean(KEY_LOGGED_IN_ONCE, false);
        FirebaseUser current = mAuth.getCurrentUser();
        if (loggedInOnce || current != null) {
            // User allowed to proceed even if offline (because we saved the flag)
            startMainAndFinish();
            return;
        }

        // else show login UI
        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        progress = findViewById(R.id.progress);
        tvError = findViewById(R.id.tvError);

        btnLogin.setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
        tvError.setVisibility(View.GONE);

        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String pass = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Required");
            etEmail.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(pass)) {
            etPassword.setError("Required");
            etPassword.requestFocus();
            return;
        }

        // First-time login requires network
        if (!isOnline()) {
            tvError.setText("No internet connection. First-time login requires network access.");
            tvError.setVisibility(View.VISIBLE);
            return;
        }

        // show progress & disable button
        progress.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        mAuth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener(this, task -> {
                    progress.setVisibility(View.GONE);
                    btnLogin.setEnabled(true);

                    if (task.isSuccessful()) {
                        // successful login -> mark flag so we can open offline later
                        prefs.edit().putBoolean(KEY_LOGGED_IN_ONCE, true).apply();

                        Toast.makeText(LoginActivity.this, "Login successful", Toast.LENGTH_SHORT).show();
                        startMainAndFinish();
                    } else {
                        String msg = task.getException() != null ? task.getException().getMessage() : "Authentication failed";
                        tvError.setText(msg);
                        tvError.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void startMainAndFinish() {
        Intent i = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(i);
        finish();
    }

    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    // Optional: if you want to allow sign out from MainActivity to clear the logged_in_once flag:
    public static void clearLoggedInOnceFlag(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_LOGGED_IN_ONCE, false).apply();
    }
}

