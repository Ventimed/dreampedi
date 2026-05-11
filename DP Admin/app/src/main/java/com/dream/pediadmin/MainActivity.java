package com.dream.pediadmin;
import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * MainActivity — shows pending/verified lists and a payment dialog for each user.
 * Updated to map paymentMethod -> drawable shown in the payment details modal.
 */
public class MainActivity extends AppCompatActivity {

    private LinearLayout navHome, navPremium, navNotifications;
    private View homePage, premiumPage, notificationsPage;
    private TextView statPendingNumber, statTotalNumber;

    private RecyclerView verificationRecycler, premiumRecycler;
    private PendingAdapter pendingAdapter;
    private VerifiedAdapter verifiedAdapter;
    private List<UserModel> pendingList = new ArrayList<>();
    private List<UserModel> verifiedList = new ArrayList<>();

    private DatabaseReference dbPending, dbVerified, dbUsers;
    private String notificationType = "all";

    // FCM server key (legacy). Replace with your server key.
    private static final String SERVER_KEY = "YOUR_FCM_SERVER_KEY_HERE";
    private static final int REQ_POST_NOTIFICATIONS = 101;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Force dark mode
        // Set status bar color and icons
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.amber_600));

            // Make status bar icons dark (black)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decorView = window.getDecorView();
                decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        }
        FirebaseApp.initializeApp(this);
        setContentView(R.layout.activity_main);
        requestNotificationPermissionIfNeeded();

        // Init firebase refs
        dbPending = FirebaseDatabase.getInstance().getReference("payments");
        dbVerified = FirebaseDatabase.getInstance().getReference("verified");
        dbUsers = FirebaseDatabase.getInstance().getReference("users");

        // Views
        navHome = findViewById(R.id.nav_home);
        navPremium = findViewById(R.id.nav_premium);
        navNotifications = findViewById(R.id.nav_notifications);

        homePage = findViewById(R.id.home_page);
        premiumPage = findViewById(R.id.premium_page);
        notificationsPage = findViewById(R.id.notifications_page);

        statPendingNumber = findViewById(R.id.stat_pending_number);
        statTotalNumber = findViewById(R.id.stat_total_number);

        verificationRecycler = findViewById(R.id.verification_recycler);
        premiumRecycler = findViewById(R.id.premium_recycler);

        verificationRecycler.setLayoutManager(new LinearLayoutManager(this));
        pendingAdapter = new PendingAdapter(pendingList, user -> showPaymentDialog(user));
        verificationRecycler.setAdapter(pendingAdapter);

        premiumRecycler.setLayoutManager(new LinearLayoutManager(this));
        verifiedAdapter = new VerifiedAdapter(this, verifiedList);
        premiumRecycler.setAdapter(verifiedAdapter);

        // bottom nav click handlers
        navHome.setOnClickListener(v -> showPage("home"));
        navPremium.setOnClickListener(v -> showPage("premium"));
        navNotifications.setOnClickListener(v -> showPage("notifications"));
        // notifications page widgets
        LinearLayout btnAll = findViewById(R.id.btn_send_all);
        LinearLayout btnSpecific = findViewById(R.id.btn_send_specific);
        LinearLayout fcmGroup = findViewById(R.id.fcm_group);
        EditText fcmInput = findViewById(R.id.fcm_input);
        EditText titleInput = findViewById(R.id.title_input);
        EditText bodyInput = findViewById(R.id.body_input);
        Button sendNotificationBtn = findViewById(R.id.send_notification_btn);

        // Make sure XML doesn't set selected background by default.
        // Force both to the default drawable first (deterministic startup)
        int defRes = R.drawable.btn_default_bg;
        btnAll.setBackgroundResource(defRes);
        btnSpecific.setBackgroundResource(defRes);

        updateNotificationSelection("all", btnAll, btnSpecific, sendNotificationBtn, fcmGroup);

        btnAll.setOnClickListener(v -> updateNotificationSelection("all", btnAll, btnSpecific, sendNotificationBtn, fcmGroup));
        btnSpecific.setOnClickListener(v -> updateNotificationSelection("specific", btnAll, btnSpecific, sendNotificationBtn, fcmGroup));


        sendNotificationBtn.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            String body = bodyInput.getText().toString().trim();
            String fcm = fcmInput.getText().toString().trim();

            if (TextUtils.isEmpty(title) || TextUtils.isEmpty(body)) {
                Toast.makeText(this, "Please fill in title and body", Toast.LENGTH_SHORT).show();
                return;
            }
            if (notificationType.equals("specific") && TextUtils.isEmpty(fcm)) {
                Toast.makeText(this, "Please enter FCM token for specific user", Toast.LENGTH_SHORT).show();
                return;
            }

            if (notificationType.equals("all")) {
                sendFCMToTopic("all", title, body);
            } else {
                sendFCMToToken(fcm, title, body);
            }

            titleInput.setText("");
            bodyInput.setText("");
            fcmInput.setText("");
            Toast.makeText(this, "Notification send request triggered", Toast.LENGTH_SHORT).show();
        });

        // premium search
        EditText premiumSearch = findViewById(R.id.premium_search);
        premiumSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a){}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c){}
            @Override public void afterTextChanged(android.text.Editable s) {
                filterPremium(s.toString());
            }
        });

        // load initial data
        loadPendingUsers();
        loadVerifiedUsers();
        updateStats();
    }

    private void showPage(String page) {
        homePage.setVisibility(page.equals("home") ? View.VISIBLE : View.GONE);
        premiumPage.setVisibility(page.equals("premium") ? View.VISIBLE : View.GONE);
        notificationsPage.setVisibility(page.equals("notifications") ? View.VISIBLE : View.GONE);

    }

    private void loadPendingUsers() {
        // Clear and fetch once, but also attach listener to keep UI in sync
        dbPending.addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                pendingList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    UserModel u = child.getValue(UserModel.class);
                    if (u == null) {
                        // try manual mapping
                        u = new UserModel();
                        u.uid = child.getKey();
                        u.fullName = child.child("userName").getValue(String.class);
                        u.paymentMethod = child.child("paymentMethod").getValue(String.class);
                        u.transactionId = child.child("transactionId").getValue(String.class);
                        u.fcm = child.child("fcm").getValue(String.class);
                        u.amount = child.child("amount").getValue(String.class);
                        u.date = child.child("date").getValue(String.class);
                        u.status = child.child("status").getValue(String.class);
                    } else {
                        if (u.uid == null) u.uid = child.getKey();
                    }
                    pendingList.add(u);
                }
                pendingAdapter.notifyDataSetChanged();
                updateStats();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadVerifiedUsers() {
        dbVerified.addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                verifiedList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    UserModel u = child.getValue(UserModel.class);
                    if (u == null) {
                        u = new UserModel();
                        u.uid = child.getKey();
                        u.fullName = child.child("userName").getValue(String.class);
                        u.paymentMethod = child.child("paymentMethod").getValue(String.class);
                        u.transactionId = child.child("transactionId").getValue(String.class);
                    } else {
                        if (u.uid == null) u.uid = child.getKey();
                    }
                    verifiedList.add(u);
                }
                verifiedAdapter.updateList(verifiedList);
                updateStats();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateStats() {
        dbPending.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                long pendingCount = snapshot.getChildrenCount();
                statPendingNumber.setText(String.valueOf(pendingCount));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        dbVerified.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                long totalCount = snapshot.getChildrenCount();
                statTotalNumber.setText(String.valueOf(totalCount));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // Show payment details dialog (custom)
    private void showPaymentDialog(UserModel user) {
        LayoutInflater li = LayoutInflater.from(this);
        View dialogView = li.inflate(R.layout.dialog_payment, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        ImageView close = dialogView.findViewById(R.id.modal_close);
        TextView modalUid = dialogView.findViewById(R.id.modal_uid);
        //TextView modalName = dialogView.findViewById(R.id.modal_user_name);
        //TextView modalPayment = dialogView.findViewById(R.id.modal_payment_method);
        TextView modalTxn = dialogView.findViewById(R.id.modal_transaction_id);
        TextView modalAmount = dialogView.findViewById(R.id.modal_amount);
        //TextView modalDate = dialogView.findViewById(R.id.modal_date);
        TextView modalFcm = dialogView.findViewById(R.id.modal_fcm);
        ImageView copyFcm = dialogView.findViewById(R.id.modal_copy_fcm);
        ImageView paymentMethodImage = dialogView.findViewById(R.id.payment_method_image);

        Button btnVerify = dialogView.findViewById(R.id.btn_verify);
        Button btnSavePremium = dialogView.findViewById(R.id.btn_save_premium);
        ProgressBar saveProgress = dialogView.findViewById(R.id.save_progress);
        Button btnLock = dialogView.findViewById(R.id.btn_lock);
        Button btnDelete = dialogView.findViewById(R.id.btn_delete);

        // populate
        modalUid.setText(user.uid);
        //modalName.setText(user.userName);
        //modalPayment.setText(user.paymentMethod);
        modalTxn.setText(user.transactionId);
        modalAmount.setText(user.amount != null ? user.amount : "");
        // modalDate.setText(user.date != null ? user.date : "");
        // show last 8 chars of fcm token
        String fcmShort = "";
        if (user.fcm != null && user.fcm.length() > 8) {
            fcmShort = "..." + user.fcm.substring(user.fcm.length() - 8);
        } else fcmShort = user.fcm != null ? user.fcm : "";
        modalFcm.setText(fcmShort);

        // --- NEW: set payment method image based on paymentMethod value ---
        int drawableRes = getDrawableForPaymentMethod(user.paymentMethod);
        try {
            paymentMethodImage.setImageResource(drawableRes);
            paymentMethodImage.setVisibility(View.VISIBLE);
        } catch (Resources.NotFoundException rnfe) {
            // fallback safely
            paymentMethodImage.setImageResource(R.drawable.ic_bank_placeholder);
            paymentMethodImage.setVisibility(View.VISIBLE);
        }

        // copy FCM -> copies entire token
        copyFcm.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("fcm", user.fcm != null ? user.fcm : "");
            cm.setPrimaryClip(clip);
            Toast.makeText(MainActivity.this, "FCM token copied", Toast.LENGTH_SHORT).show();
        });

        close.setOnClickListener(v -> dialog.dismiss());

        // Verify action: asks are you sure then set users/{uid}/featuresLocked = false and send notification on success
        btnVerify.setOnClickListener(v -> {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Confirm")
                    .setMessage("Are you sure?")
                    .setPositiveButton("Yes", (dialogInterface, i) -> {
                        // update featuresLocked to false
                        Map<String, Object> update = new HashMap<>();
                        update.put("featuresLocked", false);
                        update.put("serverTime", ServerValue.TIMESTAMP);
                        dbUsers.child(user.uid).updateChildren(update).addOnSuccessListener(aVoid -> {
                            Toast.makeText(MainActivity.this, "Payment verified for " + user.fullName, Toast.LENGTH_SHORT).show();

                            // send notification to this user's FCM token
                            if (user.fcm != null && !user.fcm.isEmpty()) {
                                sendFCMToToken(user.fcm, "Payment Verified", "Your payment has been verified.");
                            }
                        }).addOnFailureListener(e -> {
                            Toast.makeText(MainActivity.this, "Failed to verify: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    })
                    .setNegativeButton("No", null)
                    .show();
        });

        // Save as Premium: show progress in save button, remove from pending and add to verified with server timestamp
        btnSavePremium.setOnClickListener(v -> {
            btnSavePremium.setEnabled(false);
            saveProgress.setVisibility(View.VISIBLE);

            // Build verified object
            Map<String, Object> verifiedObj = new HashMap<>();
            verifiedObj.put("userName", user.fullName);
            verifiedObj.put("paymentMethod", user.paymentMethod);
            verifiedObj.put("transactionId", user.transactionId);
            verifiedObj.put("serverTime", ServerValue.TIMESTAMP);

            dbVerified.child(user.uid).setValue(verifiedObj).addOnSuccessListener(aVoid -> {
                // remove from pending
                dbPending.child(user.uid).removeValue().addOnSuccessListener(aVoid1 -> {
                    // update UI: remove from list
                    pendingAdapter.removeUser(user.uid);
                    Toast.makeText(MainActivity.this, user.fullName + " saved as premium user", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }).addOnFailureListener(e -> {
                    Toast.makeText(MainActivity.this, "Failed to remove pending: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnSavePremium.setEnabled(true);
                    saveProgress.setVisibility(View.GONE);
                });
            }).addOnFailureListener(e -> {
                Toast.makeText(MainActivity.this, "Failed to save premium: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                btnSavePremium.setEnabled(true);
                saveProgress.setVisibility(View.GONE);
            });
        });

        // Lock button: set users/{uid}/featuresLocked = true
        btnLock.setOnClickListener(v -> {
            Map<String, Object> update = new HashMap<>();
            update.put("featuresLocked", true);
            dbUsers.child(user.uid).updateChildren(update).addOnSuccessListener(aVoid -> {
                Toast.makeText(MainActivity.this, "Successfully locked", Toast.LENGTH_SHORT).show();
            }).addOnFailureListener(e -> {
                Toast.makeText(MainActivity.this, "Failed to lock: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        });

        // Delete: remove from pending folder
        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Delete")
                    .setMessage("Delete payment request for " + user.fullName + "?")
                    .setPositiveButton("Yes", (dialogInterface, i) -> {
                        dbPending.child(user.uid).removeValue().addOnSuccessListener(aVoid -> {
                            pendingAdapter.removeUser(user.uid);
                            Toast.makeText(MainActivity.this, "Payment request deleted for " + user.fullName, Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        }).addOnFailureListener(e -> {
                            Toast.makeText(MainActivity.this, "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    })
                    .setNegativeButton("No", null).show();
        });

        dialog.show();
    }

    /**
     * Map common paymentMethod values (from Firebase) to drawable resource IDs.
     * Add or change mappings here to match your drawable filenames.
     */
    private int getDrawableForPaymentMethod(String paymentMethod) {
        if (paymentMethod == null) return R.drawable.ic_bank_placeholder;
        String pm = paymentMethod.trim().toLowerCase();

        // Use contains to handle values like "telebirr", "tele-birr", "tele birr", "TeleBirr", etc.
        if (pm.contains("e-birr") || pm.contains("e birr")) {
            return R.drawable.ic_ebirr;          // <- make sure this drawable exists
        }
        if (pm.contains("telebirr")){
            return R.drawable.ic_tele;       // <- make sure this drawable exists
        }
        if (pm.contains("cbe") || pm.contains("bank") || pm.contains("cbe bank")) {
            return R.drawable.ic_cbe;            // <- make sure this drawable exists
        }

        // Add other mappings if needed (e.g., "paypal", "mpesa" etc.)

        // default placeholder
        return R.drawable.ic_bank_placeholder;
    }

    private void filterPremium(String query) {
        if (TextUtils.isEmpty(query)) {
            verifiedAdapter.updateList(verifiedList);
            return;
        }
        String q = query.toLowerCase();
        List<UserModel> filtered = new ArrayList<>();
        for (UserModel u : verifiedList) {
            boolean matches = false;
            if (u.uid != null && u.uid.toLowerCase().contains(q)) matches = true;
            if (u.transactionId != null && u.transactionId.toLowerCase().contains(q)) matches = true;
            if (matches) filtered.add(u);
        }
        verifiedAdapter.updateList(filtered);
    }

    // FCM senders (legacy HTTP)
// Add these methods to your MainActivity class

    // FCM senders using HTTP v1 API (replace the legacy methods)
    private void sendFCMToTopic(String topic, String title, String body) {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();

                // Get access token
                AccessToken accessTokenHelper = new AccessToken();
                String accessToken = accessTokenHelper.getAccessToken();

                if (accessToken == null) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to get access token", Toast.LENGTH_SHORT).show());
                    return;
                }

                // Build JSON payload for HTTP v1 API
                JSONObject json = new JSONObject();
                JSONObject message = new JSONObject();

                // Set topic
                message.put("topic", topic);

                // Set notification
                JSONObject notification = new JSONObject();
                notification.put("title", title);
                notification.put("body", body);
                message.put("notification", notification);

                json.put("message", message);

                MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                RequestBody requestBody = RequestBody.create(json.toString(), JSON);

                Request request = new Request.Builder()
                        .url("https://fcm.googleapis.com/v1/projects/dreampedi/messages:send") // Replace YOUR_PROJECT_ID with your actual project ID
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .addHeader("Content-Type", "application/json")
                        .post(requestBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        final String msg = "FCM send failed: " + response.code() + " " + response.message();
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
                    } else {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Notification sent to topic: " + topic, Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (Exception e) {
                final String err = e.getMessage() != null ? e.getMessage() : "unknown error";
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "FCM error: " + err, Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void sendFCMToToken(String token, String title, String body) {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                // Get access token
                AccessToken accessTokenHelper = new AccessToken();
                String accessToken = accessTokenHelper.getAccessToken();

                if (accessToken == null) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to get access token", Toast.LENGTH_SHORT).show());
                    return;
                }

                // Build JSON payload for HTTP v1 API
                JSONObject json = new JSONObject();
                JSONObject message = new JSONObject();

                // Set token
                message.put("token", token);

                // Set notification
                JSONObject notification = new JSONObject();
                notification.put("title", title);
                notification.put("body", body);
                message.put("notification", notification);

                json.put("message", message);

                MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                RequestBody requestBody = RequestBody.create(json.toString(), JSON);

                Request request = new Request.Builder()
                        .url("https://fcm.googleapis.com/v1/projects/dreampedi/messages:send") // Replace YOUR_PROJECT_ID with your actual project ID
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .addHeader("Content-Type", "application/json")
                        .post(requestBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        final String msg = "FCM send failed: " + response.code() + " " + response.message();
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
                    } else {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Notification sent", Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (Exception e) {
                final String err = e.getMessage() != null ? e.getMessage() : "unknown error";
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "FCM error: " + err, Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void updateNotificationSelection(String type,
                                             LinearLayout btnAll,
                                             LinearLayout btnSpecific,
                                             Button sendBtn,
                                             LinearLayout fcmGroup) {
        notificationType = type;

        // drawable resources
        int selRes = R.drawable.btn_selected_bg;
        int defRes = R.drawable.btn_default_bg;

        if ("all".equals(type)) {
            // selected = All
            btnAll.setBackgroundResource(selRes);
            btnAll.setElevation(dpToPx(2f));
            btnAll.setTranslationZ(dpToPx(2f));

            // other to default
            btnSpecific.setBackgroundResource(defRes);
            btnSpecific.setElevation(dpToPx(0f));
            btnSpecific.setTranslationZ(dpToPx(0f));

            fcmGroup.setVisibility(View.GONE);
            sendBtn.setText("Send to All");
        } else {
            // selected = Specific
            btnSpecific.setBackgroundResource(selRes);
            btnSpecific.setElevation(dpToPx(2f));
            btnSpecific.setTranslationZ(dpToPx(2f));

            // other to default
            btnAll.setBackgroundResource(defRes);
            btnAll.setElevation(dpToPx(0f));
            btnAll.setTranslationZ(dpToPx(0f));

            fcmGroup.setVisibility(View.VISIBLE);
            sendBtn.setText("Send to User");
        }

        // force redraw
        btnAll.invalidate();
        btnSpecific.invalidate();
        sendBtn.invalidate();
    }

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
                                    ActivityCompat.requestPermissions(MainActivity.this,
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
    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
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
