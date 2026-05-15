package com.dreampediatrics.app;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
public class AuthActivity extends AppCompatActivity {

    // Preference keys
    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_NIGHT_MODE = "night_mode";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final int REQ_POST_NOTIFICATIONS = 101;


    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    // Login views
    private LinearLayout loginLayout;
    private EditText loginUsername;
    private EditText loginPassword;
    private Button btnLogin;
    private TextView linkToSignUp;

    // Register views
    private LinearLayout registerLayout;
    private EditText registerEmail;
    private EditText registerName;
    private EditText registerPassword;
    private EditText registerconfPassword;
    private Button btnRegister;
    private TextView linkToLogin;

    // Forgot password views
    private LinearLayout forgotpassLayout;
    private EditText resetEmail;
    private Button btnBack, btnForgotpass;
    private TextView forgotpass;

    private AlertDialog loadingDialog;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        // Ask for notification permission right away (or call later)
        requestNotificationPermissionIfNeeded();
        updateStatusBarColor();

        // Check if user is already logged in
        if (checkIfUserLoggedIn()) {
            navigateToMainActivity();
            return;
        }

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Apply night mode
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int nightMode = prefs.getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(nightMode);

        // Initialize views
        initViews();

        // Set navigation bar color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.setNavigationBarColor(ContextCompat.getColor(this, R.color.snackbar));
        }

        // Initialize progress dialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Please wait...");
        progressDialog.setCancelable(false);

        // Set up click listeners
        setupClickListeners();

        // Initial state: show login
        showLogin();
    }

    private void initViews() {
        // Login views
        loginLayout = findViewById(R.id.loginLayout);
        loginUsername = findViewById(R.id.loginUsername);
        loginPassword = findViewById(R.id.loginPassword);
        btnLogin = findViewById(R.id.btnLogin);
        linkToSignUp = findViewById(R.id.linkToSignUp);

        // Register views
        registerLayout = findViewById(R.id.registerLayout);
        registerEmail = findViewById(R.id.registerEmail);
        registerName = findViewById(R.id.registerName);
        registerPassword = findViewById(R.id.registerPassword);
        registerconfPassword = findViewById(R.id.confirm_registerPassword);
        btnRegister = findViewById(R.id.btnRegister);
        linkToLogin = findViewById(R.id.linkToLogin);

        // Forgot password views
        forgotpassLayout = findViewById(R.id.forgotpassLayout);
        resetEmail = findViewById(R.id.resetemail);
        btnBack = findViewById(R.id.btnBack);
        btnForgotpass = findViewById(R.id.btnForgotpass);
        forgotpass = findViewById(R.id.forgotpasstext);
    }

    private void setupClickListeners() {
        // Navigation between forms
        linkToSignUp.setOnClickListener(v -> showRegister());
        linkToLogin.setOnClickListener(v -> showLogin());
        forgotpass.setOnClickListener(v -> showForgot());
        btnBack.setOnClickListener(v -> showLogin());

        // Authentication actions
        btnLogin.setOnClickListener(v -> attemptLogin());
        btnRegister.setOnClickListener(v -> attemptRegister());
        btnForgotpass.setOnClickListener(v -> showResetConfirmationDialog());
    }

    private void attemptLogin() {
        String email = loginUsername.getText().toString().trim();
        String password = loginPassword.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(email)) {
            loginUsername.setError("Email is required");
            loginUsername.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            loginUsername.setError("Please enter a valid email");
            loginUsername.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            loginPassword.setError("Password is required");
            loginPassword.requestFocus();
            return;
        }

        if (password.length() < 8) {
            loginPassword.setError("Password must be at least 8 characters");
            loginPassword.requestFocus();
            return;
        }

        progressDialog.show();

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            if (user.isEmailVerified()) {
                                // Save login state and username to ShareSharedPreferences
                                saveLoginState(true, getStoredUsername());
                                // Update login status in database
                                updateUserLoginStatus(user.getUid(), true);
                                // Ensure user data exists and fill missing fields:
                                DatabaseReference userRef = mDatabase.child("users").child(user.getUid());
                                userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        boolean changed = false;
                                        HashMap<String,Object> updates = new HashMap<>();
                                        if (!snapshot.exists()) {
                                            // create node with defaults
                                            String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                                            updates.put("deviceId", deviceId);
                                            updates.put("loggedIn", true);
                                            updates.put("featuresLocked", true);
                                            changed = true;
                                        } else {
                                            // fill missing keys if any
                                            if (!snapshot.hasChild("deviceId")) {
                                                updates.put("deviceId", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
                                                changed = true;
                                            }
                                            if (!snapshot.hasChild("loggedIn")) {
                                                updates.put("loggedIn", true);
                                                changed = true;
                                            }
                                            if (!snapshot.hasChild("featuresLocked")) {
                                                updates.put("featuresLocked", true);
                                                changed = true;
                                            }
                                        }

                                        if (changed) {
                                            userRef.updateChildren(updates).addOnCompleteListener(t -> {
                                                if (!t.isSuccessful()) {
                                                    Log.w("AuthActivity", "Failed to update missing user data: " + (t.getException() != null ? t.getException().getMessage() : "unknown"));
                                                }
                                            });
                                        }

                                        // read featuresLocked and save to client prefs for offline logic
                                        Boolean featuresLocked = snapshot.hasChild("featuresLocked") ? snapshot.child("featuresLocked").getValue(Boolean.class) : true;
                                        SharedPreferences dp = getSharedPreferences("DreamPediatricsPrefs", MODE_PRIVATE);
                                        dp.edit().putBoolean("features_unlocked", featuresLocked != null && !featuresLocked).apply();
                                        dp.edit().putBoolean("user_verified", featuresLocked != null && !featuresLocked).apply(); // keep compatibility
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                        Log.w("AuthActivity", "Could not read user node: " + error.getMessage());
                                    }
                                });

                                navigateToMainActivity();
                            } else {
                                showEmailVerificationDialog(user);
                            }
                        }
                    } else {
                        Exception exception = task.getException();
                        if (exception instanceof FirebaseAuthInvalidUserException) {
                            Toast.makeText(AuthActivity.this, "Account doesn't exist. Please create account first.", Toast.LENGTH_LONG).show();
                        } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
                            Toast.makeText(AuthActivity.this, "Invalid password. Please try again.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(AuthActivity.this, "Login failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void attemptRegister() {
        String email = registerEmail.getText().toString().trim();
        String name = registerName.getText().toString().trim();
        String password = registerPassword.getText().toString().trim();
        String confirmPassword = registerconfPassword.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(email)) {
            registerEmail.setError("Email is required");
            registerEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            registerEmail.setError("Please enter a valid email");
            registerEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(name)) {
            registerName.setError("Name is required");
            registerName.requestFocus();
            return;
        }

        if (name.length() < 3) {
            registerName.setError("Name must be at least 3 characters");
            registerName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            registerPassword.setError("Password is required");
            registerPassword.requestFocus();
            return;
        }

        if (password.length() < 8) {
            registerPassword.setError("Password must be at least 8 characters");
            registerPassword.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            registerconfPassword.setError("Please confirm your password");
            registerconfPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            registerconfPassword.setError("Passwords do not match");
            registerconfPassword.requestFocus();
            return;
        }

        progressDialog.show();

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Save username to SharedPreferences
                            saveUsernameToPrefs(name);
                            // Send email verification
                            sendEmailVerification(user);
                            // Save user data to database
                            saveUserToDatabase(user, name);
                        }
                    } else {
                        Exception exception = task.getException();
                        if (exception instanceof FirebaseAuthUserCollisionException) {
                            Toast.makeText(AuthActivity.this, "Account already exists. Please login instead.", Toast.LENGTH_LONG).show();
                        } else if (exception instanceof FirebaseAuthWeakPasswordException) {
                            Toast.makeText(AuthActivity.this, "Password is too weak. Please choose a stronger password.", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(AuthActivity.this, "Registration failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void sendEmailVerification(FirebaseUser user) {
        user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        showEmailVerificationSentDialog();
                    } else {
                        Toast.makeText(AuthActivity.this, "Failed to send verification email: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveUserToDatabase(FirebaseUser user, String name) {
        if (user == null) return;
        final String uid = user.getUid();
        final String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        final DatabaseReference userRef = mDatabase.child("users").child(uid);

        HashMap<String, Object> userMap = new HashMap<>();
        userMap.put("deviceId", deviceId);
        userMap.put("loggedIn", false);  // Will be true when they verify and login
        userMap.put("featuresLocked", true);

        userRef.setValue(userMap)
                .addOnSuccessListener(aVoid -> Log.d("AuthActivity", "User data saved successfully"))
                .addOnFailureListener(e -> {
                    Log.w("AuthActivity", "First attempt to save user data failed: " + e.getMessage());
                    // retry once
                    userRef.setValue(userMap)
                            .addOnSuccessListener(aVoid -> Log.d("AuthActivity", "User data saved successfully on retry"))
                            .addOnFailureListener(e2 -> {
                                Log.e("AuthActivity", "Failed to save user data after retry: " + e2.getMessage());
                                runOnUiThread(() -> Toast.makeText(AuthActivity.this, "Failed to create account data: " + e2.getMessage(), Toast.LENGTH_LONG).show());
                            });
                });
    }

    private void updateUserLoginStatus(String uid, boolean isLoggedIn) {
        HashMap<String, Object> updates = new HashMap<>();
        updates.put("loggedIn", isLoggedIn);
        mDatabase.child("users").child(uid).updateChildren(updates);
    }

    private void showEmailVerificationSentDialog() {
        LayoutInflater li = LayoutInflater.from(this);
        View v = li.inflate(R.layout.dialog_custom, null);

        TextView title = v.findViewById(R.id.loadingText);
        TextView message = v.findViewById(R.id.dialogMessage);
        ProgressBar pb = v.findViewById(R.id.loadingProgress);
        MaterialButton pos = v.findViewById(R.id.dialogPositiveButton);
        MaterialButton neg = v.findViewById(R.id.dialogNegativeButton);

        // Configure the UI
        pb.setVisibility(View.GONE);
        title.setText("Email Verification Sent");
        message.setText("A verification email has been sent to your email address. Please check your inbox or spam folder and verify your email before logging in.");
        title.setVisibility(View.VISIBLE);
        message.setVisibility(View.VISIBLE);

        // Buttons: only positive OK
        neg.setVisibility(View.GONE);
        pos.setText("OK");
        pos.setOnClickListener(dialogView -> {
            // will dismiss via dialog object below
        });

        AlertDialog dialog = new AlertDialog.Builder(this).setView(v).setCancelable(false).create();
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // set positive action AFTER show so we can dismiss the dialog object
        pos.setOnClickListener(view -> {
            dialog.dismiss();
            showLogin(); // Take user back to login
        });
    }

    private void showEmailVerificationDialog(FirebaseUser user) {
        LayoutInflater li = LayoutInflater.from(this);
        View v = li.inflate(R.layout.dialog_custom, null);

        TextView title = v.findViewById(R.id.loadingText);
        TextView message = v.findViewById(R.id.dialogMessage);
        ProgressBar pb = v.findViewById(R.id.loadingProgress);
        MaterialButton pos = v.findViewById(R.id.dialogPositiveButton);
        MaterialButton neg = v.findViewById(R.id.dialogNegativeButton);

        pb.setVisibility(View.GONE);
        title.setText("Email Not Verified");
        message.setText("Please verify your email address before logging in. Check your inbox or spam folder.");
        title.setVisibility(View.VISIBLE);
        message.setVisibility(View.VISIBLE);

        pos.setText("Resend Verification");
        neg.setText("OK");

        AlertDialog dialog = new AlertDialog.Builder(this).setView(v).setCancelable(false).create();
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        pos.setOnClickListener(view -> {
            sendEmailVerification(user);
            dialog.dismiss();
        });

        neg.setOnClickListener(view -> {
            mAuth.signOut();  // Sign out unverified user
            dialog.dismiss();
        });
    }

    private void showResetConfirmationDialog() {
        String email = resetEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            resetEmail.setError("Email is required");
            resetEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            resetEmail.setError("Please enter a valid email");
            resetEmail.requestFocus();
            return;
        }

        LayoutInflater li = LayoutInflater.from(this);
        View v = li.inflate(R.layout.dialog_custom, null);

        TextView title = v.findViewById(R.id.loadingText);
        TextView message = v.findViewById(R.id.dialogMessage);
        ProgressBar pb = v.findViewById(R.id.loadingProgress);
        MaterialButton pos = v.findViewById(R.id.dialogPositiveButton);
        MaterialButton neg = v.findViewById(R.id.dialogNegativeButton);
        ProgressBar posSpinner = v.findViewById(R.id.dialogPositiveProgress);

        pb.setVisibility(View.GONE);
        title.setText("Reset Password");
        message.setText("Are you sure you want to reset your password for " + email + "?");
        pos.setText("Yes");
        neg.setText("Cancel");

        AlertDialog dialog = new AlertDialog.Builder(this).setView(v).create();
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        pos.setOnClickListener(view -> {
            // show spinner on the 'Yes' button
            showPositiveButtonProgress(pos, posSpinner);

            // call the existing method to actually send reset; adapt it to accept a callback
            mAuth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        // hide spinner regardless of success/failure
                        hidePositiveButtonProgress(pos, posSpinner);

                        if (task.isSuccessful()) {
                            // show success custom dialog (or reuse same dialog pattern)
                            dialog.dismiss();
                            sendPasswordResetEmail(email); // example helper to show success
                        } else {
                            Exception exception = task.getException();
                            if (exception instanceof FirebaseAuthInvalidUserException) {
                                Toast.makeText(AuthActivity.this, "No account found with this email address.", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(AuthActivity.this, "Failed to send reset email: " + (exception != null ? exception.getMessage() : "unknown"), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        });

        neg.setOnClickListener(view -> dialog.dismiss());
    }

    private void sendPasswordResetEmail(String email) {
        progressDialog.show();

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful()) {
                        // Use custom dialog for success
                        LayoutInflater li = LayoutInflater.from(this);
                        View v = li.inflate(R.layout.dialog_custom, null);

                        TextView title = v.findViewById(R.id.loadingText);
                        TextView message = v.findViewById(R.id.dialogMessage);
                        ProgressBar pb = v.findViewById(R.id.loadingProgress);
                        MaterialButton pos = v.findViewById(R.id.dialogPositiveButton);
                        MaterialButton neg = v.findViewById(R.id.dialogNegativeButton);

                        pb.setVisibility(View.GONE);
                        title.setText("Password Reset Email Sent");
                        message.setText("A password reset email has been sent to " + email + ". Please check your inbox or spam folder.");
                        title.setVisibility(View.VISIBLE);
                        message.setVisibility(View.VISIBLE);

                        pos.setText("OK");
                        neg.setVisibility(View.GONE);

                        AlertDialog dialog = new AlertDialog.Builder(this).setView(v).setCancelable(false).create();
                        dialog.show();
                        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

                        pos.setOnClickListener(view -> {
                            dialog.dismiss();
                            showLogin();  // Take user back to login
                        });

                    } else {
                        Exception exception = task.getException();
                        if (exception instanceof FirebaseAuthInvalidUserException) {
                            Toast.makeText(AuthActivity.this, "No account found with this email address.", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(AuthActivity.this, "Failed to send reset email: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    /** Shows only the login form */
    private void showLogin() {
        loginLayout.setVisibility(View.VISIBLE);
        registerLayout.setVisibility(View.GONE);
        forgotpassLayout.setVisibility(View.GONE);
        clearErrors();
    }

    private boolean checkIfUserLoggedIn() {
        // Check offline state first
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isLoggedInOffline = prefs.getBoolean(KEY_IS_LOGGED_IN, false);

        // If offline login state exists, go to main activity
        if (isLoggedInOffline) {
            return true;
        }

        // Check Firebase Auth state
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.isEmailVerified()) {
            // User is logged in with Firebase and verified, save offline state
            String storedUsername = prefs.getString(KEY_USERNAME, "User");
            saveLoginState(true, storedUsername);
            return true;
        }

        return false;
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(AuthActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void saveLoginState(boolean isLoggedIn, String username) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_IS_LOGGED_IN, isLoggedIn);
        if (username != null && !username.isEmpty()) {
            editor.putString(KEY_USERNAME, username);
        }
        editor.apply();
    }

    private void saveUsernameToPrefs(String username) {
        SharedPreferences dp = getSharedPreferences("DreamPediatricsPrefs", MODE_PRIVATE);
        dp.edit().putString("username", username).apply();
    }

    private String getStoredUsername() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(KEY_USERNAME, "User");
    }

    private void clearLoginState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_IS_LOGGED_IN, false);
        editor.apply();
    }

    /** Shows only the register form */
    private void showRegister() {
        loginLayout.setVisibility(View.GONE);
        registerLayout.setVisibility(View.VISIBLE);
        forgotpassLayout.setVisibility(View.GONE);
        clearErrors();
    }

    /** Shows only the forgot password form */
    private void showForgot() {
        loginLayout.setVisibility(View.GONE);
        registerLayout.setVisibility(View.GONE);
        forgotpassLayout.setVisibility(View.VISIBLE);
        clearErrors();
    }

    private void clearErrors() {
        loginUsername.setError(null);
        loginPassword.setError(null);
        registerEmail.setError(null);
        registerName.setError(null);
        registerPassword.setError(null);
        registerconfPassword.setError(null);
        resetEmail.setError(null);
    }

    private void showPositiveButtonProgress(MaterialButton positiveButton, ProgressBar positiveProgress) {
        if (positiveButton == null || positiveProgress == null) return;
        // save the original text on the view tag so we can restore it
        if (positiveButton.getTag() == null) positiveButton.setTag(positiveButton.getText().toString());
        positiveButton.setEnabled(false);
        positiveButton.setText(""); // hide text while spinner shows
        positiveProgress.setVisibility(View.VISIBLE);
    }

    private void hidePositiveButtonProgress(MaterialButton positiveButton, ProgressBar positiveProgress) {
        if (positiveButton == null || positiveProgress == null) return;
        CharSequence original = positiveButton.getTag() != null ? (CharSequence) positiveButton.getTag() : "OK";
        positiveProgress.setVisibility(View.GONE);
        positiveButton.setText(original);
        positiveButton.setEnabled(true);
        positiveButton.setTag(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
    /**
     * Request POST_NOTIFICATIONS permission on Android 13+ if not granted.
     * Call this early (e.g., in onCreate or when user navigates to Notifications page).
     */
    private void requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                // Optionally show rationale
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                    // Show a simple rationale then request permission
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Notification Permission")
                            .setMessage("This app needs permission to send notifications. Please allow to receive important updates.")
                            .setPositiveButton("OK", (dialog, which) ->
                                    ActivityCompat.requestPermissions(AuthActivity.this,
                                            new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS))
                            .setNegativeButton("Cancel", null)
                            .show();
                } else {
                    // Direct request
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS);
                }
            } // else already granted
        }
    }

    private void updateStatusBarColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            int color = ContextCompat.getColor(this, R.color.primary_dark);
            window.setStatusBarColor(color);
            WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
            boolean useDarkIcons = ColorUtils.calculateLuminance(color) > 0.5;
            insetsController.setAppearanceLightStatusBars(useDarkIcons);
        }
    }
    // handle user's response
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_POST_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notifications permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Notifications permission denied — notifications may not appear", Toast.LENGTH_LONG).show();
            }
        }
    }

}