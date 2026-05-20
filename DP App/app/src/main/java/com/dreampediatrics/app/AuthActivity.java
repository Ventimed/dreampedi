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
import android.text.InputType;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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

// Google Sign-In imports
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.GoogleAuthProvider;

import java.util.HashMap;
import java.util.List;

public class AuthActivity extends AppCompatActivity {

    // Preference keys
    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_NIGHT_MODE = "night_mode";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final int REQ_POST_NOTIFICATIONS = 101;
    private static final int RC_GOOGLE_SIGN_IN = 9001;


    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private GoogleSignInClient mGoogleSignInClient;

    // Landing screen views
    private LinearLayout loginLayout;
    private EditText loginUsername;
    private MaterialButton btnGoogleSignIn;
    private MaterialButton btnContinueEmail;
    private LinearLayout emailInputWrap;

    // Password login screen views (existing users)
    private LinearLayout passwordLoginLayout;
    private EditText loginPassword;
    private MaterialButton btnLogin;
    private MaterialButton btnGoogleSignInFromLogin;
    private LinearLayout backToLanding;
    private LinearLayout emailChipLogin;
    private TextView chipEmailText;
    private ImageView togglePasswordVisibility;

    // Register views (new users)
    private LinearLayout registerLayout;
    private EditText registerName;
    private EditText registerPassword;
    private EditText registerconfPassword;
    private MaterialButton btnRegister;
    private LinearLayout backToLandingFromSignup;
    private LinearLayout emailChipSignup;
    private TextView chipEmailTextSignup;
    private ImageView toggleRegisterPasswordVisibility;
    private ImageView toggleConfirmPasswordVisibility;

    // Forgot password views
    private LinearLayout forgotpassLayout;
    private EditText resetEmail;
    private Button btnBack, btnForgotpass;
    private TextView forgotpass;
    private LinearLayout backToLoginFromForgot;

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

        // Configure Google Sign-In (only if OAuth is configured in Firebase)
        // Note: GoogleSignInOptions is deprecated but still fully functional
        // We'll migrate to Credential Manager API in a future update
        try {
            @SuppressWarnings("deprecation")
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build();
            mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        } catch (Exception e) {
            Log.w("AuthActivity", "Google Sign-In not configured yet: " + e.getMessage());
            // Google Sign-In will be disabled until Firebase is properly configured
            mGoogleSignInClient = null;
        }

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

        // Initial state: show landing screen
        showLanding();
    }

    private void initViews() {
        // Landing screen views
        loginLayout = findViewById(R.id.loginLayout);
        loginUsername = findViewById(R.id.loginUsername);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        btnContinueEmail = findViewById(R.id.btnContinueEmail);
        emailInputWrap = findViewById(R.id.emailInputWrap);
        
        // Hide Google Sign-In button if not configured
        if (mGoogleSignInClient == null && btnGoogleSignIn != null) {
            btnGoogleSignIn.setVisibility(View.GONE);
        }

        // Password login screen views (existing users)
        passwordLoginLayout = findViewById(R.id.passwordLoginLayout);
        loginPassword = findViewById(R.id.loginPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogleSignInFromLogin = findViewById(R.id.btnGoogleSignInFromLogin);
        forgotpass = findViewById(R.id.forgotpasstext);
        backToLanding = findViewById(R.id.backToLanding);
        emailChipLogin = findViewById(R.id.emailChipLogin);
        chipEmailText = findViewById(R.id.chipEmailText);
        togglePasswordVisibility = findViewById(R.id.togglePasswordVisibility);
        
        // Hide Google Sign-In button on login screen if not configured
        if (mGoogleSignInClient == null && btnGoogleSignInFromLogin != null) {
            btnGoogleSignInFromLogin.setVisibility(View.GONE);
        }

        // Register screen views (new users)
        registerLayout = findViewById(R.id.registerLayout);
        registerName = findViewById(R.id.registerName);
        registerPassword = findViewById(R.id.registerPassword);
        registerconfPassword = findViewById(R.id.confirm_registerPassword);
        btnRegister = findViewById(R.id.btnRegister);
        backToLandingFromSignup = findViewById(R.id.backToLandingFromSignup);
        emailChipSignup = findViewById(R.id.emailChipSignup);
        chipEmailTextSignup = findViewById(R.id.chipEmailTextSignup);
        toggleRegisterPasswordVisibility = findViewById(R.id.toggleRegisterPasswordVisibility);
        toggleConfirmPasswordVisibility = findViewById(R.id.toggleConfirmPasswordVisibility);

        // Forgot password views
        forgotpassLayout = findViewById(R.id.forgotpassLayout);
        resetEmail = findViewById(R.id.resetemail);
        btnBack = findViewById(R.id.btnBack);
        btnForgotpass = findViewById(R.id.btnForgotpass);
        backToLoginFromForgot = findViewById(R.id.backToLoginFromForgot);
    }

    private void setupClickListeners() {
        // Google Sign-In button (only if configured)
        if (btnGoogleSignIn != null && mGoogleSignInClient != null) {
            btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());
        }
        
        // Google Sign-In button on login screen (only if configured)
        if (btnGoogleSignInFromLogin != null && mGoogleSignInClient != null) {
            btnGoogleSignInFromLogin.setOnClickListener(v -> signInWithGoogle());
        }
        
        // Continue with email - detect if user exists
        btnContinueEmail.setOnClickListener(v -> detectUserEmail());
        
        // Back navigation
        backToLanding.setOnClickListener(v -> showLanding());
        backToLandingFromSignup.setOnClickListener(v -> showLanding());
        backToLoginFromForgot.setOnClickListener(v -> showPasswordLogin());
        emailChipLogin.setOnClickListener(v -> showLanding());
        emailChipSignup.setOnClickListener(v -> showLanding());
        
        // Password visibility toggles
        if (togglePasswordVisibility != null) {
            togglePasswordVisibility.setOnClickListener(v -> togglePasswordVisibility(loginPassword, togglePasswordVisibility));
        }
        if (toggleRegisterPasswordVisibility != null) {
            toggleRegisterPasswordVisibility.setOnClickListener(v -> togglePasswordVisibility(registerPassword, toggleRegisterPasswordVisibility));
        }
        if (toggleConfirmPasswordVisibility != null) {
            toggleConfirmPasswordVisibility.setOnClickListener(v -> togglePasswordVisibility(registerconfPassword, toggleConfirmPasswordVisibility));
        }
        
        // Authentication actions
        btnLogin.setOnClickListener(v -> attemptLogin());
        btnRegister.setOnClickListener(v -> attemptRegister());
        forgotpass.setOnClickListener(v -> showForgot());
        btnForgotpass.setOnClickListener(v -> showResetConfirmationDialog());
        btnBack.setOnClickListener(v -> showPasswordLogin());
    }

    private void togglePasswordVisibility(EditText passwordField, ImageView toggleIcon) {
        if (passwordField.getInputType() == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
            // Show password - change to open eye icon
            passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            toggleIcon.setImageResource(R.drawable.ic_passvis);
        } else {
            // Hide password - change to closed eye icon
            passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            toggleIcon.setImageResource(R.drawable.ic_passhide);
        }
        // Move cursor to end
        passwordField.setSelection(passwordField.getText().length());
    }

    /**
     * Google Sign-In flow - handles both new and existing users automatically
     */
    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_GOOGLE_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Log.w("AuthActivity", "Google sign in failed", e);
                Toast.makeText(this, "Google sign-in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        progressDialog.setMessage("Signing in with Google...");
        progressDialog.show();
        
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Save user data
                            String displayName = user.getDisplayName() != null ? user.getDisplayName() : "User";
                            saveUsernameToPrefs(displayName);
                            saveLoginState(true, displayName);
                            
                            // Check if user exists in database, if not create
                            DatabaseReference userRef = mDatabase.child("users").child(user.getUid());
                            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    if (!snapshot.exists()) {
                                        // New Google user - create account
                                        saveUserToDatabase(user, displayName);
                                    } else {
                                        // Existing user - update login status
                                        updateUserLoginStatus(user.getUid(), true);
                                    }
                                    
                                    // Read featuresLocked and save to client prefs
                                    Boolean featuresLocked = snapshot.hasChild("featuresLocked") ? 
                                        snapshot.child("featuresLocked").getValue(Boolean.class) : true;
                                    SharedPreferences dp = getSharedPreferences("DreamPediatricsPrefs", MODE_PRIVATE);
                                    dp.edit().putBoolean("features_unlocked", featuresLocked != null && !featuresLocked).apply();
                                    dp.edit().putBoolean("user_verified", featuresLocked != null && !featuresLocked).apply();
                                    
                                    navigateToMainActivity();
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    Log.w("AuthActivity", "Database check failed: " + error.getMessage());
                                    navigateToMainActivity(); // Continue anyway
                                }
                            });
                        }
                    } else {
                        Toast.makeText(AuthActivity.this, 
                            "Authentication failed: " + task.getException().getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Email detection - checks if email exists and routes to appropriate screen
     * Like the HTML: existing@doc.com → login, any other → sign up
     * 
     * Strategy: Try to create account with a dummy password. If it fails with 
     * "email already in use", the account exists → show login. Otherwise → show signup.
     * If it's a Google-only account, we'll detect that when they try to login.
     */
    private void detectUserEmail() {
        String email = loginUsername.getText().toString().trim();
        
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

        progressDialog.setMessage("Checking your account...");
        progressDialog.show();

        // Strategy: Try to create account with a dummy password to check if email exists
        String dummyPassword = "TempCheck123!@#$%^&*()_+";
        
        mAuth.createUserWithEmailAndPassword(email, dummyPassword)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Account was created - delete it immediately and show signup
                        FirebaseUser tempUser = mAuth.getCurrentUser();
                        if (tempUser != null) {
                            tempUser.delete().addOnCompleteListener(deleteTask -> {
                                progressDialog.dismiss();
                                Log.d("AuthActivity", "New user detected - showing sign-up screen");
                                chipEmailTextSignup.setText(email);
                                showRegister();
                            });
                        } else {
                            progressDialog.dismiss();
                            chipEmailTextSignup.setText(email);
                            showRegister();
                        }
                    } else {
                        // Check the exception type
                        Exception exception = task.getException();
                        progressDialog.dismiss();
                        
                        if (exception instanceof FirebaseAuthUserCollisionException) {
                            // Email already exists - show login screen
                            // (We'll detect if it's Google-only when they try to login)
                            Log.d("AuthActivity", "Existing user detected - showing login screen");
                            chipEmailText.setText(email);
                            showPasswordLogin();
                        } else {
                            // Other error - default to signup
                            Log.d("AuthActivity", "Error or new user - showing sign-up screen");
                            chipEmailTextSignup.setText(email);
                            showRegister();
                        }
                    }
                });
    }

    /**
     * Check if account is Google-only (no password) after login attempt fails
     */
    private void checkIfGoogleOnlyAccount(String email) {
        Log.d("AuthActivity", "Checking if account is Google-only for: " + email);
        
        mAuth.fetchSignInMethodsForEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        List<String> signInMethods = task.getResult().getSignInMethods();
                        
                        Log.d("AuthActivity", "fetchSignInMethodsForEmail SUCCESS");
                        Log.d("AuthActivity", "Sign-in methods for " + email + ": " + 
                            (signInMethods != null ? signInMethods.toString() : "null"));
                        Log.d("AuthActivity", "Sign-in methods size: " + (signInMethods != null ? signInMethods.size() : "null"));
                        
                        // Check if account only has Google provider (no password)
                        boolean hasPassword = signInMethods != null && 
                            (signInMethods.contains("password") || signInMethods.contains("emailLink"));
                        boolean hasGoogle = signInMethods != null && 
                            signInMethods.contains("google.com");
                        
                        Log.d("AuthActivity", "hasPassword: " + hasPassword + ", hasGoogle: " + hasGoogle);
                        
                        if (hasGoogle && !hasPassword) {
                            // Account exists with Google only - show helpful dialog
                            Log.d("AuthActivity", "Showing Google-only account dialog");
                            showGoogleOnlyAccountDialog(email);
                        } else if (signInMethods == null || signInMethods.isEmpty()) {
                            // Empty sign-in methods - this might be a Google-only account
                            // Show the dialog to be safe
                            Log.d("AuthActivity", "Empty sign-in methods - showing Google-only dialog as fallback");
                            showGoogleOnlyAccountDialog(email);
                        } else {
                            // Has password or unknown - show normal error
                            Log.d("AuthActivity", "Account has password - showing normal error");
                            Toast.makeText(AuthActivity.this, "Invalid password. Please try again.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        // Could not check - show the dialog as a fallback
                        Log.e("AuthActivity", "fetchSignInMethodsForEmail FAILED: " + 
                            (task.getException() != null ? task.getException().getMessage() : "unknown"));
                        Log.d("AuthActivity", "Showing Google-only dialog as fallback");
                        showGoogleOnlyAccountDialog(email);
                    }
                });
    }

    /**
     * Show dialog when user tries to sign in with email/password but account was created with Google
     */
    private void showGoogleOnlyAccountDialog(String email) {
        LayoutInflater li = LayoutInflater.from(this);
        View v = li.inflate(R.layout.dialog_custom, null);

        TextView title = v.findViewById(R.id.loadingText);
        TextView message = v.findViewById(R.id.dialogMessage);
        ProgressBar pb = v.findViewById(R.id.loadingProgress);
        MaterialButton pos = v.findViewById(R.id.dialogPositiveButton);
        MaterialButton neg = v.findViewById(R.id.dialogNegativeButton);

        pb.setVisibility(View.GONE);
        title.setText("Password Sign-In Not Available");
        message.setText("This account does not support password sign-in, please try Google sign-in method or password reset.");
        title.setVisibility(View.VISIBLE);
        message.setVisibility(View.VISIBLE);

        pos.setText("Use Google Sign-In");
        neg.setText("Reset Password");

        AlertDialog dialog = new AlertDialog.Builder(this).setView(v).setCancelable(true).create();
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        pos.setOnClickListener(view -> {
            dialog.dismiss();
            // Trigger Google Sign-In
            if (mGoogleSignInClient != null) {
                signInWithGoogle();
            } else {
                Toast.makeText(AuthActivity.this, "Google Sign-In not configured", Toast.LENGTH_SHORT).show();
            }
        });

        neg.setOnClickListener(view -> {
            dialog.dismiss();
            // Send password reset email so they can set a password
            sendPasswordResetEmailForGoogleAccount(email);
        });
    }

    /**
     * Send password reset email for Google-only accounts
     */
    private void sendPasswordResetEmailForGoogleAccount(String email) {
        progressDialog.setMessage("Sending password reset email...");
        progressDialog.show();

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful()) {
                        showPasswordResetSentDialog(email);
                    } else {
                        Toast.makeText(AuthActivity.this, 
                            "Failed to send reset email: " + 
                            (task.getException() != null ? task.getException().getMessage() : "Unknown error"), 
                            Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Show confirmation that password reset email was sent
     */
    private void showPasswordResetSentDialog(String email) {
        LayoutInflater li = LayoutInflater.from(this);
        View v = li.inflate(R.layout.dialog_custom, null);

        TextView title = v.findViewById(R.id.loadingText);
        TextView message = v.findViewById(R.id.dialogMessage);
        ProgressBar pb = v.findViewById(R.id.loadingProgress);
        MaterialButton pos = v.findViewById(R.id.dialogPositiveButton);
        MaterialButton neg = v.findViewById(R.id.dialogNegativeButton);

        pb.setVisibility(View.GONE);
        title.setText("Password Reset Email Sent");
        message.setText("We've sent a password reset email to " + email + ".\n\nCheck your inbox and follow the link to set a password. After that, you'll be able to sign in with email and password.");
        title.setVisibility(View.VISIBLE);
        message.setVisibility(View.VISIBLE);

        pos.setText("OK");
        neg.setVisibility(View.GONE);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(v).setCancelable(false).create();
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        pos.setOnClickListener(view -> {
            dialog.dismiss();
            showLanding();
        });
    }

    private void attemptLogin() {
        // Get email from the chip (already validated during detection)
        String email = chipEmailText.getText().toString().trim();
        String password = loginPassword.getText().toString().trim();

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
                        String errorMessage = exception != null ? exception.getMessage() : "";
                        
                        Log.e("AuthActivity", "Login failed: " + errorMessage);
                        
                        if (exception instanceof FirebaseAuthInvalidUserException) {
                            Toast.makeText(AuthActivity.this, "Account doesn't exist. Please create account first.", Toast.LENGTH_LONG).show();
                        } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
                            // Always check if it's a Google-only account when credentials are invalid
                            // We'll let checkIfGoogleOnlyAccount determine if it's truly a Google-only account
                            // or just a wrong password
                            checkIfGoogleOnlyAccount(email);
                        } else {
                            Toast.makeText(AuthActivity.this, "Login failed: " + errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void attemptRegister() {
        // Get email from the chip (already validated during detection)
        String email = chipEmailTextSignup.getText().toString().trim();
        String name = registerName.getText().toString().trim();
        String password = registerPassword.getText().toString().trim();
        String confirmPassword = registerconfPassword.getText().toString().trim();

        // Validation - email already validated, no need to check again
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
        showLanding(); // Redirect to landing screen instead
    }

    /** Shows the landing screen with email input and Google Sign-In */
    private void showLanding() {
        loginLayout.setVisibility(View.VISIBLE);
        passwordLoginLayout.setVisibility(View.GONE);
        registerLayout.setVisibility(View.GONE);
        forgotpassLayout.setVisibility(View.GONE);
        // Clear the email input when returning to landing
        loginUsername.setText("");
        clearErrors();
    }

    /** Shows the password login screen for existing users */
    private void showPasswordLogin() {
        loginLayout.setVisibility(View.GONE);
        passwordLoginLayout.setVisibility(View.VISIBLE);
        registerLayout.setVisibility(View.GONE);
        forgotpassLayout.setVisibility(View.GONE);
        // Clear password field when showing login screen
        loginPassword.setText("");
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
        passwordLoginLayout.setVisibility(View.GONE);
        registerLayout.setVisibility(View.VISIBLE);
        forgotpassLayout.setVisibility(View.GONE);
        // Clear password fields when showing register screen
        registerName.setText("");
        registerPassword.setText("");
        registerconfPassword.setText("");
        clearErrors();
    }

    /** Shows only the forgot password form */
    private void showForgot() {
        loginLayout.setVisibility(View.GONE);
        passwordLoginLayout.setVisibility(View.GONE);
        registerLayout.setVisibility(View.GONE);
        forgotpassLayout.setVisibility(View.VISIBLE);
        clearErrors();
    }

    private void clearErrors() {
        if (loginUsername != null) loginUsername.setError(null);
        if (loginPassword != null) loginPassword.setError(null);
        if (registerName != null) registerName.setError(null);
        if (registerPassword != null) registerPassword.setError(null);
        if (registerconfPassword != null) registerconfPassword.setError(null);
        if (resetEmail != null) resetEmail.setError(null);
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
            int color = ContextCompat.getColor(this, R.color.primary);
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