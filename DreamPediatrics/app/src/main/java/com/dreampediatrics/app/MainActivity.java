package com.dreampediatrics.app;

import static android.view.View.GONE;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.os.Build;
import android.view.Window;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.content.ContextCompat;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.ViewPager;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

/**
 * MainActivity (updated)
 * - Retains your existing app features
 * - Removed automatic assets->DB population on startup
 * - Adds keystore + unwrap + decrypt + populate flow for downloaded AES-GCM encrypted JSON
 */
public class MainActivity extends AppCompatActivity {
    private ViewPager viewPager;
    private BottomNavigationView bottomNavigation;
    private Toolbar toolbar;
    private SharedPreferences preferences;
    private static MainActivity instance;
    // Firebase DB listener for feature unlocks
    private DatabaseReference userFeaturesDbRef = null;
    private ValueEventListener featuresListener = null;

    private UserSettings userSettings;
    private List<BookmarkItem> bookmarks;
    private List<HistoryItem> readingHistory;
    private Map<String, SearchResult> searchDatabase;
    private AlertDialog loadingDialog;
    // DB executor for background work
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private static final String KEY_ALIAS = "dp_device_key";
    // <<-- REPLACE this with your real Cloud Function URL:
    private static final String CLOUD_FUNCTION_WRAP_URL = "https://wrapaeskey-4jzb4qgvzq-uc.a.run.app";

    // reference to DAO
    private AppDao dao;

    private static final String PREFS_NAME = "app_prefs";
    private static final String PREF_FCM_TOKEN = "fcm_token";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme BEFORE inflating layout
        preferences = getSharedPreferences("DreamPediatricsPrefs", Context.MODE_PRIVATE);
        boolean darkMode = preferences.getBoolean("dark_mode", false);
        AppCompatDelegate.setDefaultNightMode(
                darkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );

        // Check if device ID mismatch flag is set in shared preferences
        SharedPreferences prefs = getSharedPreferences("DreamPediatricsPrefs", MODE_PRIVATE);
        boolean isMismatch = prefs.getBoolean("device_id_mismatch", false);

        if (isMismatch) {
            // If mismatch detected, navigate directly to FailedActivity and finish MainActivity
            navigateToFailedActivity("Device verification failed. Please login from the registered device.");
            return;
        }

        ensureFcmTokenExists();
        FirebaseMessaging.getInstance().subscribeToTopic("all");

        super.onCreate(savedInstanceState);
        instance = this;
        setContentView(R.layout.activity_main);
        updateStatusBarColor();


        // initialize DAO
        dao = AppDatabase.getInstance(this).appDao();

        // Perform account verification checks
        performAccountVerificationChecks();

        // NOTE: removed automatic population from assets on startup.
        // We keep loading persistent data (bookmarks/history) and then initialize UI.
        dbExecutor.execute(() -> {
            // load persisted bookmarks/history into memory (after DB is ready)
            loadPersistentData();

            // Switch to UI thread to finish initialization (views / fragments)
            runOnUiThread(() -> {
                // complete UI setup
                initializeUserData();
                initializeViews();
                setupViewPager();
                setupBottomNavigation();

                // At end of onCreate(...) after viewpager/bottomnav setup and only on cold start:
                if (savedInstanceState == null) {
                    long last = getLastOpenedTopicRowId();
                    if (last != -1L) {
                        // Start TopicActivity so user resumes reading.
                        Intent resume = new Intent(this, TopicActivity.class);
                        resume.putExtra(TopicActivity.EXTRA_TOPIC_ROWID, last);
                        resume.putExtra(TopicActivity.EXTRA_RESUMED_FROM_RESUME, true);
                        startActivity(resume);
                    }
                }
            });
        });
    }

    /**
     * Perform comprehensive account verification checks on app startup
     */
    private void performAccountVerificationChecks() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user != null) {
            // User is signed in - perform online verification
            performOnlineVerification(user);
        } else {
            // User not signed in - check offline state
            performOfflineVerification();
        }
    }

    /**
     * Online verification when user is signed in
     */
    private void performOnlineVerification(FirebaseUser user) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference()
                .child("users").child(user.getUid());

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Get server data
                    Boolean featuresLocked = snapshot.child("featuresLocked").getValue(Boolean.class);
                    Boolean loggedIn = snapshot.child("loggedIn").getValue(Boolean.class);
                    String serverDeviceId = snapshot.child("deviceId").getValue(String.class);

                    // Get current device ID
                    String currentDeviceId = getCurrentDeviceId();

                    // Check device ID mismatch
                    if (loggedIn != null && loggedIn &&
                            serverDeviceId != null &&
                            !serverDeviceId.equals(currentDeviceId)) {
                        // Device ID mismatch - go to failed activity
                        SharedPreferences prefs = getSharedPreferences("DreamPediatricsPrefs", MODE_PRIVATE);
                        prefs.edit().putBoolean("device_id_mismatch", true).apply();

                        navigateToFailedActivity("Device verification failed. Please login from the registered device.");
                        return;
                    }

                    // Handle feature verification
                    handleFeatureVerification(featuresLocked);
                } else {
                    // User data doesn't exist on server - handle as new user
                    handleFeatureVerification(true); // Default to locked
                }

                // Start listening for feature unlocks
                listenForFeatureUnlocks();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("MainActivity", "Failed to verify account: " + error.getMessage());
                // Fallback to offline verification
                performOfflineVerification();
            }
        });
    }

    /**
     * Offline verification when user is not signed in or online check failed
     */
    private void performOfflineVerification() {
        boolean featuresUnlocked = preferences.getBoolean("features_unlocked", false);

        if (featuresUnlocked) {
            // Features were previously unlocked - notify HomeFragment
            notifyHomeFragmentToUpdateUI();
        }

        // Clear any locked content if features are locked offline
        if (!featuresUnlocked) {
            clearRoomDatabase();
        }
    }

    /**
     * Handle feature verification logic based on server state
     * Fixed: Only save features_unlocked = true when content is actually downloaded
     */
    private void handleFeatureVerification(Boolean featuresLocked) {
        // Default to true if null
        boolean locked = featuresLocked == null || featuresLocked;

        if (!locked) {
            // Features are unlocked on server, but DON'T mark as unlocked locally yet
            // This will happen only after successful download and DB population

            // Check if Room DB already has content
            dbExecutor.execute(() -> {
                List<ChapterEntity> chapters = dao.getAllChapters();
                boolean roomHasContent = chapters != null && !chapters.isEmpty();

                if (roomHasContent) {
                    // Content already exists - mark as unlocked locally
                    preferences.edit().putBoolean("features_unlocked", true).apply();
                }

                runOnUiThread(() -> {
                    // Always notify HomeFragment to update UI based on current state
                    notifyHomeFragmentToUpdateUI();
                });
            });
        } else {
            // Features are locked - clear local flag and database
            preferences.edit().putBoolean("features_unlocked", false).apply();
            clearRoomDatabase();

            // Notify HomeFragment
            notifyHomeFragmentToUpdateUI();
        }
    }

    /**
     * Notify HomeFragment to update UI
     */
    private void notifyHomeFragmentToUpdateUI() {
        Fragment homeFragment = getSupportFragmentManager().findFragmentByTag("f0");
        if (homeFragment instanceof HomeFragment) {
            ((HomeFragment) homeFragment).updateUIBasedOnVerificationState();
        }
    }

    @SuppressLint("HardwareIds")
    private String getCurrentDeviceId() {
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId != null && !androidId.isEmpty()) {
            return androidId;
        }

        String storedDeviceId = preferences.getString("generated_device_id", null);
        if (storedDeviceId == null) {
            storedDeviceId = UUID.randomUUID().toString();
            preferences.edit().putString("generated_device_id", storedDeviceId).apply();
        }
        return storedDeviceId;
    }

    private void navigateToFailedActivity(String errorMessage) {
        // Check if the mismatch flag is set
        SharedPreferences prefs = getSharedPreferences("DreamPediatricsPrefs", MODE_PRIVATE);
        boolean isMismatch = prefs.getBoolean("device_id_mismatch", false);

        if (isMismatch) {
            clearRoomDatabase();
            // If there is a mismatch, go directly to failed activity
            Intent intent = new Intent(this, FailedActivity.class);
            intent.putExtra("error_message", errorMessage);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish(); // finish the current MainActivity
            return;
        }

        // Existing navigation logic for other errors
        Intent intent = new Intent(this, FailedActivity.class);
        intent.putExtra("error_message", errorMessage);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Clear Room database content
     */
    private void clearRoomDatabase() {
        dbExecutor.execute(() -> {
            try {
                dao.deleteAllChapters();
                dao.deleteAllTopics();
                dao.deleteAllBookmarks();
                dao.deleteAllHistory();
                Log.d("MainActivity", "Room database cleared due to feature lock");

                runOnUiThread(() -> {
                    // Refresh HomeFragment UI
                    notifyHomeFragmentToUpdateUI();
                });
            } catch (Exception e) {
                Log.e("MainActivity", "Error clearing Room database", e);
            }
        });
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        viewPager = findViewById(R.id.viewPager);
        bottomNavigation = findViewById(R.id.bottomNavigation);

        // initial circle (first letter of username)
        TextView toolbarInitial = findViewById(R.id.toolbar_initial);
        View initialCard = findViewById(R.id.toolbarInitialCard);
        if (initialCard != null) {
            initialCard.setOnClickListener(v -> {
                if (viewPager != null) viewPager.setCurrentItem(4); // settings is index 4
            });
        }

        // set initial letter from username if present
        updateToolbarInitial();

        // Purchase button wiring if present in layout
        MaterialButton btnPurchaseCard = findViewById(R.id.btnPurchaseCard);
        if (btnPurchaseCard != null) {
            btnPurchaseCard.setOnClickListener(v -> {
                PaymentDialogFragment dialog = new PaymentDialogFragment();
                dialog.show(getSupportFragmentManager(), "payment_dialog");
            });
        }
    }


    private void updateToolbarInitial() {
        TextView initialTv = findViewById(R.id.toolbar_initial);
        if (initialTv == null) return;

        String username = null;
        if (userSettings != null && userSettings.getUsername() != null && !userSettings.getUsername().trim().isEmpty()) {
            username = userSettings.getUsername().trim();
        } else {
            username = preferences.getString("username", "");
        }

        if (!TextUtils.isEmpty(username)) {
            String trimmed = username.trim();
            if (trimmed.toUpperCase(Locale.ROOT).startsWith("dr.")) {
                trimmed = trimmed.replaceFirst("(?i)^dr\\.\\s*", "");
            }
            if (!trimmed.isEmpty()) {
                String first = trimmed.substring(0, 1).toUpperCase(Locale.getDefault());
                initialTv.setText(first);
                return;
            }
        }

        initialTv.setText("N");
    }

    private void initializeUserData() {
        String savedName = preferences.getString("username", null);
        String savedEmail = preferences.getString("email", null);
        String savedUid = preferences.getString("uid", null);

        boolean savedDarkMode = preferences.getBoolean("dark_mode", false);
        boolean savedNotifications = preferences.getBoolean("notifications", true);

        if (TextUtils.isEmpty(savedName)) {
            savedName = "User";
        }

        userSettings = new UserSettings(savedName, savedEmail, savedUid);
        userSettings.setDarkMode(savedDarkMode);
        userSettings.setNotifications(savedNotifications);

        bookmarks = new ArrayList<>();
        readingHistory = new ArrayList<>();
    }

    private void loadPersistentData() {
        dbExecutor.execute(() -> {
            // load bookmarks
            List<BookmarkEntity> be = dao.getAllBookmarks();
            List<BookmarkItem> loadedBookmarks = new ArrayList<>();
            for (BookmarkEntity b : be) {
                loadedBookmarks.add(new BookmarkItem(b.topicId, b.title, b.snippet, b.timestamp));
            }

            // load history (limit to e.g. 200 most recent)
            List<HistoryEntity> he = dao.getHistoryLimited(200);
            List<HistoryItem> loadedHistory = new ArrayList<>();
            for (HistoryEntity h : he) {
                loadedHistory.add(new HistoryItem(h.topicId, h.title, h.snippet, h.info, h.progress, h.timestamp));
            }

            runOnUiThread(() -> {
                bookmarks.clear();
                bookmarks.addAll(loadedBookmarks);
                readingHistory.clear();
                readingHistory.addAll(loadedHistory);
            });
        });
    }

    // show a simple indeterminate loading dialog (used during decrypt/populate)
    private void showLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) return;

        LayoutInflater li = LayoutInflater.from(this);
        View v = li.inflate(R.layout.dialog_custom, null);

        TextView title = v.findViewById(R.id.loadingText);
        TextView message = v.findViewById(R.id.dialogMessage);
        ProgressBar pb = v.findViewById(R.id.loadingProgress);
        LinearLayout buttonContainer = v.findViewById(R.id.buttonContainer);
        MaterialButton pos = v.findViewById(R.id.dialogPositiveButton);
        MaterialButton neg = v.findViewById(R.id.dialogNegativeButton);

        // Configure views for loading dialog
        title.setText("Installing textbook…");
        title.setVisibility(View.VISIBLE);
        message.setVisibility(View.GONE);         // no message text
        pb.setVisibility(View.VISIBLE);           // show spinner
        buttonContainer.setVisibility(View.GONE); // hide buttons for loading

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        loadingDialog = b.setView(v).setCancelable(false).create();
        loadingDialog.show();

        // make dialog window transparent so rounded layout shows without rectangular spill
        if (loadingDialog.getWindow() != null) {
            loadingDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }


    private void dismissLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

    // ----- helpers used by fragments -----
    public SharedPreferences getPrefs() { return preferences; }

    public void updateGreeting(String newNamePlain) {
        if (!TextUtils.isEmpty(newNamePlain)) {
            preferences.edit().putString("username", newNamePlain).apply();
            userSettings.setUsername(newNamePlain);
        }
        updateToolbarInitial();
        Fragment f = getSupportFragmentManager().findFragmentByTag("f0");
        if (f instanceof HomeFragment) {
            ((HomeFragment) f).refreshGreeting();
        }
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

    public synchronized void addBookmark(BookmarkItem item) {
        if (item == null || item.getTopicId() == null) return;
        final long now = System.currentTimeMillis();
        final BookmarkEntity entity = new BookmarkEntity(item.getTopicId(), item.getTitle(), item.getSnippet(), now);

        dbExecutor.execute(() -> {
            dao.insertBookmark(entity);
            runOnUiThread(() -> {
                for (int i = 0; i < bookmarks.size(); i++) {
                    BookmarkItem b = bookmarks.get(i);
                    if (b.getTopicId() != null && b.getTopicId().equals(item.getTopicId())) {
                        bookmarks.remove(i);
                        break;
                    }
                }
                bookmarks.add(0, new BookmarkItem(entity.topicId, entity.title, entity.snippet, entity.timestamp));
                if (bookmarks.size() > 200) bookmarks.remove(bookmarks.size() - 1);
                showNotification("Bookmarked");
            });
        });
    }

    public synchronized void removeBookmark(String topicId) {
        if (topicId == null) return;
        dbExecutor.execute(() -> {
            dao.deleteBookmarkByTopicId(topicId);
            runOnUiThread(() -> {
                for (int i = 0; i < bookmarks.size(); i++) {
                    BookmarkItem b = bookmarks.get(i);
                    if (topicId.equals(b.getTopicId())) {
                        bookmarks.remove(i);
                        showNotification("Removed: " + b.getTitle());
                        return;
                    }
                }
                showNotification("Bookmark not found");
            });
        });
    }

    public synchronized boolean isTopicBookmarked(String id) {
        if (id == null) return false;
        for (BookmarkItem b : bookmarks) {
            if (id.equals(b.getTopicId())) return true;
        }
        return false;
    }

    public synchronized void recordTopicViewed(HistoryItem item) {
        if (item == null || item.getTopicId() == null) return;

        final long now = System.currentTimeMillis();
        dbExecutor.execute(() -> {
            HistoryEntity existing = dao.getHistoryByTopicId(item.getTopicId());
            if (existing != null) {
                existing.title = item.getTitle() != null ? item.getTitle() : existing.title;
                existing.snippet = item.getSnippet() != null ? item.getSnippet() : existing.snippet;
                existing.info = item.getInfo() != null ? item.getInfo() : "Viewed";
                existing.progress = item.getProgress() > 0 ? item.getProgress() : existing.progress;
                existing.timestamp = now;
                dao.updateHistory(existing);

                runOnUiThread(() -> {
                    for (int i = 0; i < readingHistory.size(); i++) {
                        HistoryItem h = readingHistory.get(i);
                        if (h.getTopicId() != null && h.getTopicId().equals(item.getTopicId())) {
                            readingHistory.remove(i);
                            break;
                        }
                    }
                    readingHistory.add(0, new HistoryItem(existing.topicId, existing.title, existing.snippet, existing.info, existing.progress, existing.timestamp));
                    if (readingHistory.size() > 200) readingHistory.remove(readingHistory.size() - 1);
                });
            } else {
                HistoryEntity newEntity = new HistoryEntity(item.getTopicId(), item.getTitle(), item.getSnippet(), item.getInfo() != null ? item.getInfo() : "Viewed", item.getProgress(), now);
                dao.insertHistory(newEntity);
                runOnUiThread(() -> {
                    readingHistory.add(0, new HistoryItem(newEntity.topicId, newEntity.title, newEntity.snippet, newEntity.info, newEntity.progress, newEntity.timestamp));
                    if (readingHistory.size() > 200) readingHistory.remove(readingHistory.size() - 1);
                });
            }
        });
    }

    public synchronized List<HistoryItem> getReadingHistoryLimited(int limit) {
        if (readingHistory == null) return new ArrayList<>();
        if (limit <= 0 || limit >= readingHistory.size()) {
            return new ArrayList<>(readingHistory);
        } else {
            int end = Math.min(limit, readingHistory.size());
            return new ArrayList<>(readingHistory.subList(0, end));
        }
    }

    public synchronized void saveRecentSearch(String query) {
        if (query == null) return;
        query = query.trim();
        if (query.isEmpty()) return;

        final String KEY = "recent_searches";
        String raw = preferences.getString(KEY, "");
        List<String> list = new ArrayList<>();
        if (!TextUtils.isEmpty(raw)) {
            list.addAll(Arrays.asList(raw.split("\u241F")));
        }

        list.remove(query);
        list.add(0, query);
        while (list.size() > 5) list.remove(list.size() - 1);

        String joined = TextUtils.join("\u241F", list);
        preferences.edit().putString(KEY, joined).apply();
    }

    public synchronized List<String> getRecentSearches() {
        final String KEY = "recent_searches";
        String raw = preferences.getString(KEY, "");
        if (TextUtils.isEmpty(raw)) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split("\u241F")));
    }

    public void openChapterById(String chapterId, String title) {
        HistoryItem historyItem = new HistoryItem(
                chapterId,
                title,
                "",
                "Chapter opened",
                0,
                System.currentTimeMillis()
        );
        Intent i = new Intent(this, ChapterActivity.class);
        i.putExtra(ChapterActivity.EXTRA_CHAPTER_ID, chapterId);
        i.putExtra(ChapterActivity.EXTRA_CHAPTER_TITLE, title);
        startActivity(i);
    }

    public static MainActivity getInstance() {
        return instance;
    }

    private void setupViewPager() {
        ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager());
        adapter.addFragment(new HomeFragment(), "Home");
        adapter.addFragment(new HistoryFragment(), "History");
        adapter.addFragment(new BookmarksFragment(), "Bookmarks");
        adapter.addFragment(new SearchFragment(), "Search");
        adapter.addFragment(new SettingsFragment(), "Settings");

        viewPager.setAdapter(adapter);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}
            @Override
            public void onPageSelected(int position) {
                bottomNavigation.getMenu().getItem(position).setChecked(true);
                updateToolbarTitle(position);
            }
            @Override public void onPageScrollStateChanged(int state) {}
        });
    }

    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) viewPager.setCurrentItem(0);
            else if (id == R.id.nav_history) viewPager.setCurrentItem(1);
            else if (id == R.id.nav_bookmarks) viewPager.setCurrentItem(2);
            else if (id == R.id.nav_search) viewPager.setCurrentItem(3);
            else if (id == R.id.nav_settings) viewPager.setCurrentItem(4);
            else return false;
            return true;
        });
    }

    private void updateToolbarTitle(int position) {
        String[] titles = {"Dream Pediatrics", "History", "Bookmarks", "Search", "Settings"};
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(titles[position]);
        }
    }

    public void openTopic(String topicIdOrRow) {
        if (topicIdOrRow == null) return;

        try {
            long row = Long.parseLong(topicIdOrRow);
            Intent intent = new Intent(this, TopicActivity.class);
            intent.putExtra(TopicActivity.EXTRA_TOPIC_ROWID, row);
            startActivity(intent);
            return;
        } catch (NumberFormatException ignore) { }

        new Thread(() -> {
            AppDao dao = AppDatabase.getInstance(this).appDao();
            TopicEntity topic = dao.getTopicByTopicId(topicIdOrRow);
            if (topic == null) {
                runOnUiThread(() -> showNotification("Content not found"));
                return;
            }
            long resolvedRow = topic.rowid;
            runOnUiThread(() -> {
                Intent intent = new Intent(MainActivity.this, TopicActivity.class);
                intent.putExtra(TopicActivity.EXTRA_TOPIC_ROWID, resolvedRow);
                startActivity(intent);
            });
        }).start();
    }

    public void showNotification(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
    }

    public void toggleDarkMode() {
        boolean newDarkMode = !userSettings.isDarkMode();
        userSettings.setDarkMode(newDarkMode);
        if (newDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            showNotification("Dark mode enabled");
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            showNotification("Light mode enabled");
        }
        preferences.edit().putBoolean("dark_mode", newDarkMode).apply();
    }

    public void toggleNotifications() {
        userSettings.setNotifications(!userSettings.isNotifications());
        if (userSettings.isNotifications()) showNotification("Notifications enabled");
        else showNotification("Notifications disabled");
        preferences.edit().putBoolean("notifications", userSettings.isNotifications()).apply();
    }

    public void editProfile() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Username");

        final EditText input = new EditText(this);
        input.setText(userSettings.getUsername());
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String newUsername = input.getText().toString().trim();
            if (!newUsername.isEmpty()) {
                userSettings.setUsername(newUsername);
                showNotification("Profile updated successfully");
                updateToolbarInitial();
                preferences.edit().putString("username", newUsername).apply();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    public void showAbout() {
        String aboutText = "A comprehensive pediatric medical textbook application designed for healthcare professionals, medical students, and pediatric specialists.\n\n" +
                "Features:\n" +
                "• Complete textbook with 6 comprehensive chapters\n" +
                "• Advanced search functionality\n" +
                "• Reading progress tracking\n" +
                "• Bookmark system for quick reference\n" +
                "• Dark/light mode support\n" +
                "• Offline reading capability\n\n" +
                "Developed by VentiMed Apps\n" +
                "© 2025 All rights reserved\n\n" +
                "For support: support@dreampediatrics.com\n" +
                "Website: www.dreampediatrics.com\n\n" +
                "Version v2.1.0";

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_about);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        ImageView aboutImage = dialog.findViewById(R.id.aboutImage);
        TextView aboutTitle = dialog.findViewById(R.id.aboutTitle);
        TextView aboutBody = dialog.findViewById(R.id.aboutBody);
        MaterialButton btnYes = dialog.findViewById(R.id.aboutPositive);
        MaterialButton btnNo = dialog.findViewById(R.id.aboutNegative);

        aboutTitle.setText("About Dream Pediatrics");
        aboutBody.setText(aboutText);
        btnNo.setVisibility(GONE);

        int drawableId = getResources().getIdentifier("ic_book", "drawable", getPackageName());
        if (drawableId == 0) drawableId = getApplicationInfo().icon;
        aboutImage.setImageResource(drawableId);

        btnYes.setText("Okay");
        btnYes.setOnClickListener(v -> {
            showNotification("Thanks for checking Dream Pediatrics!");
            dialog.dismiss();
        });

        btnNo.setOnClickListener(v -> dialog.dismiss());

        dialog.setCancelable(true);
        dialog.show();
    }

    public UserSettings getUserSettings() { return userSettings; }
    public synchronized List<BookmarkItem> getBookmarks() { return new ArrayList<>(bookmarks); }
    public synchronized List<HistoryItem> getReadingHistory() { return new ArrayList<>(readingHistory); }

    public synchronized void clearAllHistory() {
        dbExecutor.execute(() -> {
            dao.deleteAllHistory();
            preferences.edit().remove("last_topic_rowid").apply();
            runOnUiThread(() -> {
                if (readingHistory != null) readingHistory.clear();
                showNotification("Reading history cleared");
            });
        });
    }

    public synchronized void saveLastOpenedTopic(long rowid) {
        if (preferences == null) preferences = getSharedPreferences("DreamPediatricsPrefs", Context.MODE_PRIVATE);
        preferences.edit().putLong("last_topic_rowid", rowid).apply();
    }

    public synchronized long getLastOpenedTopicRowId() {
        if (preferences == null) preferences = getSharedPreferences("DreamPediatricsPrefs", Context.MODE_PRIVATE);
        return preferences.getLong("last_topic_rowid", -1L);
    }

    // ViewPager Adapter
    private class ViewPagerAdapter extends FragmentPagerAdapter {
        private final List<Fragment> fragments = new ArrayList<>();
        private final List<String> fragmentTitles = new ArrayList<>();

        public ViewPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @Override public Fragment getItem(int position) { return fragments.get(position); }
        @Override public int getCount() { return fragments.size(); }
        @Override public CharSequence getPageTitle(int position) { return fragmentTitles.get(position); }
        public void addFragment(Fragment fragment, String title) { fragments.add(fragment); fragmentTitles.add(title); }
    }

    // ---------- Cryptographic helpers for secure download flow ----------

    /**
     * Ensure RSA keypair exists in Android Keystore with maximum compatibility.
     */
    private String ensureKeystoreKeyAndGetPublicBase64() {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);

            // Always recreate key to ensure clean state and compatibility
            if (ks.containsAlias(KEY_ALIAS)) {
                ks.deleteEntry(KEY_ALIAS);
                Log.d("MainActivity", "Deleted existing key for clean recreation");
            }

            Log.d("MainActivity", "Creating new RSA key with broad compatibility");
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(2048)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    // Support both SHA-1 and SHA-256 for maximum compatibility
                    .setDigests(KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_SHA256)
                    .build();
            kpg.initialize(spec);
            kpg.generateKeyPair();
            Log.d("MainActivity", "Created RSA key successfully");

            KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) ks.getEntry(KEY_ALIAS, null);
            PublicKey pub = entry.getCertificate().getPublicKey();
            byte[] pubBytes = pub.getEncoded();
            String result = Base64.encodeToString(pubBytes, Base64.NO_WRAP);
            Log.d("MainActivity", "Public key ready, length: " + result.length());
            return result;
        } catch (Exception e) {
            Log.e("MainActivity", "ensureKeystoreKeyAndGetPublicBase64 err", e);
            return null;
        }
    }


    /**
     * Unwrap AES key using the most compatible Android Keystore approach.
     * Tries multiple cipher approaches in order of compatibility.
     */
    private byte[] unwrapAesKeyWithKeystore(String wrappedB64) throws Exception {
        byte[] wrapped = android.util.Base64.decode(wrappedB64, android.util.Base64.DEFAULT);
        Log.d("MainActivity", "Attempting to unwrap key, wrapped length: " + wrapped.length);

        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        KeyStore.Entry entry = ks.getEntry(KEY_ALIAS, null);
        if (!(entry instanceof KeyStore.PrivateKeyEntry)) {
            throw new IllegalStateException("Keystore entry not found for alias: " + KEY_ALIAS);
        }
        PrivateKey priv = ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();

        // Try multiple approaches in order of compatibility
        Exception lastException = null;

        // Approach 1: Generic OAEP (most compatible - lets Android choose defaults)
        try {
            Log.d("MainActivity", "Trying generic OAEP padding");
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            cipher.init(Cipher.DECRYPT_MODE, priv);
            byte[] result = cipher.doFinal(wrapped);
            Log.d("MainActivity", "Success with generic OAEP, AES key length: " + result.length);
            return result;
        } catch (Exception e) {
            Log.w("MainActivity", "Generic OAEP failed", e);
            lastException = e;
        }

        // Approach 2: Explicit SHA-1 parameters (matches our server)
        try {
            Log.d("MainActivity", "Trying explicit SHA-1 OAEP parameters");
            OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                    "SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT);
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            cipher.init(Cipher.DECRYPT_MODE, priv, oaepParams);
            byte[] result = cipher.doFinal(wrapped);
            Log.d("MainActivity", "Success with SHA-1 OAEP, AES key length: " + result.length);
            return result;
        } catch (Exception e) {
            Log.w("MainActivity", "SHA-1 OAEP with params failed", e);
            lastException = e;
        }

        // Approach 3: Try explicit transformation string
        try {
            Log.d("MainActivity", "Trying RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
            cipher.init(Cipher.DECRYPT_MODE, priv);
            byte[] result = cipher.doFinal(wrapped);
            Log.d("MainActivity", "Success with explicit SHA-1 transform, AES key length: " + result.length);
            return result;
        } catch (Exception e) {
            Log.w("MainActivity", "Explicit SHA-1 transform failed", e);
            lastException = e;
        }

        // All approaches failed
        Log.e("MainActivity", "All RSA decryption approaches failed");
        throw new IllegalStateException("Failed to unwrap key with any approach - device may not support RSA-OAEP", lastException);
    }

    /**
     * Decrypt downloaded textbook.enc and populate DB.
     */
    public void decryptAndPopulate(File encFile) {
        dbExecutor.execute(() -> {
            try {
                Log.d("MainActivity", "Starting decryptAndPopulate");
                runOnUiThread(() -> showLoadingDialog());

                Log.d("MainActivity", "Ensuring keystore key exists");
                String publicB64 = ensureKeystoreKeyAndGetPublicBase64();
                if (publicB64 == null) throw new IllegalStateException("Failed to get public key");

                Log.d("MainActivity", "Requesting wrapped key from cloud function");
                String wrappedKeyB64 = requestWrappedKeyFromCloudFunction(publicB64);
                if (wrappedKeyB64 == null) throw new IllegalStateException("Failed to get wrapped key from server");

                Log.d("MainActivity", "Unwrapping AES key");
                byte[] aesKey = unwrapAesKeyWithKeystore(wrappedKeyB64);

                Log.d("MainActivity", "Reading encrypted file: " + encFile.getName());
                byte[] fileBytes = readFileToBytes(encFile);
                if (fileBytes == null || fileBytes.length < 28) { // 12 nonce + 16 min tag
                    throw new IllegalStateException("Encrypted file too small: " +
                            (fileBytes != null ? fileBytes.length : 0) + " bytes");
                }

                Log.d("MainActivity", "Decrypting with AES-GCM, file size: " + fileBytes.length);
                // Parse: nonce(12) + ciphertext+tag
                byte[] nonce = Arrays.copyOfRange(fileBytes, 0, 12);
                byte[] cipherAndTag = Arrays.copyOfRange(fileBytes, 12, fileBytes.length);

                // AES-GCM decrypt
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                GCMParameterSpec spec = new GCMParameterSpec(128, nonce); // 128-bit auth tag
                SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
                cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
                byte[] plain = cipher.doFinal(cipherAndTag);
                String json = new String(plain, StandardCharsets.UTF_8);

                Log.d("MainActivity", "Decrypted JSON length: " + json.length());

                // Populate Room DB from JSON
                TextbookRepository.getInstance(this).populateFromJsonString(this, json);

                // Mark features unlocked on client (server already authorized download)
                // This will make payment_card hidden and update settings badge/download visibility
                if (preferences == null) preferences = getSharedPreferences("DreamPediatricsPrefs", Context.MODE_PRIVATE);
                preferences.edit()
                        .putBoolean("features_unlocked", true)
                        .putBoolean("payment_submitted", false)
                        .apply();

                // Clean up
                if (encFile.delete()) {
                    Log.d("MainActivity", "Deleted temporary files");
                }

                runOnUiThread(() -> {
                    dismissLoadingDialog();
                    showNotification("Textbook installed successfully!");

                    // 1) If you have a ViewPager, switch to Home tab (index 0) first.
                    if (viewPager != null) {
                        try {
                            viewPager.setCurrentItem(0, true); // switch to Home tab (animated)
                        } catch (Exception ignored) {
                            // ignore if viewPager not ready for animation
                            try { viewPager.setCurrentItem(0); } catch (Exception e) { /* ignore */ }
                        }

                        // Post a task so the fragment has a chance to be attached/created before we look it up.
                        viewPager.post(() -> {
                            FragmentManager fm = getSupportFragmentManager();

                            // Common ViewPager fragment tag pattern
                            String pagerTag = "android:switcher:" + viewPager.getId() + ":" + 0;
                            Fragment homeFragment = fm.findFragmentByTag(pagerTag);

                            // fallback to old tag used in your code
                            if (homeFragment == null) {
                                homeFragment = fm.findFragmentByTag("f0");
                            }

                            // fallback to a container id lookup if you also use fragment replacement by id
                            if (homeFragment == null) {
                                homeFragment = fm.findFragmentById(R.id.viewPager); // replace with your container id if needed
                            }

                            if (homeFragment instanceof HomeFragment) {
                                // Call the fragment's public method to refresh UI
                                ((HomeFragment) homeFragment).onTextbookInstalled();
                            } else {
                                // Final fallback: replace/create a fresh HomeFragment instance
                                try {
                                    HomeFragment newHome = new HomeFragment(); // or HomeFragment.newInstance(...) if you have args
                                    fm.beginTransaction()
                                            .replace(R.id.viewPager, newHome, "HOME_FRAGMENT")
                                            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                                            .commitAllowingStateLoss();
                                } catch (IllegalStateException ise) {
                                    Log.w("decryptAndPopulate", "Failed to replace HomeFragment: " + ise.getMessage());
                                }
                            }

                            // Update Settings fragment (index 4) using same robust lookup pattern
                            Fragment settingsFragment = null;
                            String settingsPagerTag = "android:switcher:" + viewPager.getId() + ":" + 4;
                            if (fm != null) settingsFragment = fm.findFragmentByTag(settingsPagerTag);
                            if (settingsFragment == null) settingsFragment = fm.findFragmentByTag("f4");
                            if (settingsFragment instanceof SettingsFragment) {
                                ((SettingsFragment) settingsFragment).setupUserProfileFromPrefs();
                            }
                        });

                    } else {
                        // No viewPager available — try to find/refresh HomeFragment immediately
                        FragmentManager fm = getSupportFragmentManager();

                        Fragment homeFragment = fm.findFragmentByTag("f0");
                        if (homeFragment == null) {
                            homeFragment = fm.findFragmentById(R.id.viewPager);
                        }

                        if (homeFragment instanceof HomeFragment) {
                            ((HomeFragment) homeFragment).onTextbookInstalled();
                        } else {
                            try {
                                HomeFragment newHome = new HomeFragment();
                                fm.beginTransaction()
                                        .replace(R.id.viewPager, newHome, "HOME_FRAGMENT")
                                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                                        .commitAllowingStateLoss();
                            } catch (IllegalStateException ise) {
                                Log.w("decryptAndPopulate", "Failed to replace HomeFragment: " + ise.getMessage());
                            }
                        }

                        // Settings fragment fallback
                        Fragment sf = fm.findFragmentByTag("f4");
                        if (sf instanceof SettingsFragment) {
                            ((SettingsFragment) sf).setupUserProfileFromPrefs();
                        }
                    }
                });


            } catch (Exception e) {
                Log.e("MainActivity", "decryptAndPopulate error", e);

                final String msg;
                if (e == null) {
                    msg = "Unknown error occurred";
                } else if (e.getMessage() != null && !e.getMessage().trim().isEmpty()) {
                    msg = e.getMessage();
                } else {
                    msg = e.getClass().getSimpleName();
                }

                runOnUiThread(() -> {
                    dismissLoadingDialog();
                    showNotification("Installation failed: " + msg);
                });
            }
        });
    }

    /**
     * HTTP POST to cloud function to receive wrapped AES key.
     */
    private String requestWrappedKeyFromCloudFunction(String devicePubB64) throws Exception {
        FirebaseUser user = ensureFirebaseUserSignedIn();

        GetTokenResult tokenResult = Tasks.await(user.getIdToken(true));
        String idToken = tokenResult != null ? tokenResult.getToken() : null;
        if (idToken == null || idToken.isEmpty()) {
            throw new IllegalStateException("Failed to obtain Firebase ID token");
        }

        Log.d("MainActivity", "Making request to cloud function with token");

        URL url = new URL(CLOUD_FUNCTION_WRAP_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        conn.setDoOutput(true);

        JSONObject body = new JSONObject();
        body.put("device_public_key_b64", devicePubB64);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = is.read(buffer)) != -1) baos.write(buffer, 0, read);
        String resp = new String(baos.toByteArray(), StandardCharsets.UTF_8);

        Log.d("MainActivity", "Cloud function response code: " + code);

        if (code < 200 || code >= 300) {
            throw new IllegalStateException("Cloud function error (" + code + "): " + resp);
        }

        JSONObject json = new JSONObject(resp);
        if (!json.has("wrapped_key_b64")) {
            throw new IllegalStateException("Invalid cloud function response - missing wrapped_key_b64");
        }

        String wrappedKey = json.getString("wrapped_key_b64");
        Log.d("MainActivity", "Received wrapped key, length: " + wrappedKey.length());
        return wrappedKey;
    }


    private byte[] readFileToBytes(File f) throws Exception {
        try (InputStream is = new java.io.FileInputStream(f)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int r;
            while ((r = is.read(buffer)) != -1) baos.write(buffer, 0, r);
            return baos.toByteArray();
        }
    }

    /**
     * Ensure a Firebase user exists. If none, sign in anonymously synchronously.
     */
    private FirebaseUser ensureFirebaseUserSignedIn() throws Exception {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) return user;

        Log.d("MainActivity", "Signing in anonymously");
        Task<AuthResult> signInTask = auth.signInAnonymously();
        AuthResult res = Tasks.await(signInTask, 20, java.util.concurrent.TimeUnit.SECONDS);
        if (res == null || res.getUser() == null) {
            throw new IllegalStateException("Anonymous sign-in failed");
        }
        // after signing in:
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            listenForFeatureUnlocks();
        }
        Log.d("MainActivity", "Anonymous sign-in successful");
        return res.getUser();
    }

    /**
     * Start listening to /users/{uid}/featuresLocked in Realtime Database.
     * When the server sets featuresLocked==false we persist features_unlocked=true
     * and notify UI to update (download button etc).
     */
    public void listenForFeatureUnlocks() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        if (preferences == null) preferences = getSharedPreferences("DreamPediatricsPrefs", Context.MODE_PRIVATE);

        // detach previous listener if any
        if (userFeaturesDbRef != null && featuresListener != null) {
            try { userFeaturesDbRef.removeEventListener(featuresListener); } catch (Exception ignored) {}
        }

        userFeaturesDbRef = FirebaseDatabase.getInstance().getReference().child("users").child(user.getUid()).child("featuresLocked");
        featuresListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean locked = snapshot.getValue(Boolean.class);
                if (locked != null && !locked) {
                    // Server says features unlocked - check if we already have content
                    dbExecutor.execute(() -> {
                        List<ChapterEntity> chapters = dao.getAllChapters();
                        boolean roomHasContent = chapters != null && !chapters.isEmpty();

                        runOnUiThread(() -> {
                            if (roomHasContent) {
                                // Content exists - mark as unlocked locally
                                preferences.edit().putBoolean("features_unlocked", true).apply();
                            } else {
                                // FIXED: Features unlocked but no content - show download button
                                // Mark as unlocked and clear payment_submitted to transition from pending
                                preferences.edit()
                                        .putBoolean("features_unlocked", true)
                                        .putBoolean("payment_submitted", false)
                                        .apply();
                            }

                            notifyHomeFragmentToUpdateUI();

                            Fragment sf = getSupportFragmentManager().findFragmentByTag("f4");
                            if (sf instanceof SettingsFragment) {
                                ((SettingsFragment) sf).setupUserProfileFromPrefs();
                            }
                        });
                    });
                } else if (locked != null && locked) {
                    // server explicitly says locked: clear local unlocked flag and database
                    preferences.edit().putBoolean("features_unlocked", false).apply();
                    clearRoomDatabase();

                    runOnUiThread(() -> {
                        notifyHomeFragmentToUpdateUI();
                        Fragment sf = getSupportFragmentManager().findFragmentByTag("f4");
                        if (sf instanceof SettingsFragment) {
                            ((SettingsFragment) sf).setupUserProfileFromPrefs();
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w("MainActivity", "featuresListener cancelled: " + error.getMessage());
            }
        };

        userFeaturesDbRef.addValueEventListener(featuresListener);
    }

    /**
     * Sign out and return to AuthActivity
     */
    public void logout() {
        // Inflate custom dialog
        LayoutInflater li = LayoutInflater.from(this);
        View v = li.inflate(R.layout.dialog_custom, null);

        TextView title = v.findViewById(R.id.loadingText);
        TextView message = v.findViewById(R.id.dialogMessage);
        ProgressBar pb = v.findViewById(R.id.loadingProgress);
        MaterialButton pos = v.findViewById(R.id.dialogPositiveButton);
        MaterialButton neg = v.findViewById(R.id.dialogNegativeButton);
        ProgressBar posSpinner = v.findViewById(R.id.dialogPositiveProgress);

        pb.setVisibility(View.GONE);
        title.setText("Logout");
        message.setText("Are you sure you want to log out? Your reading progress will be saved.");
        title.setVisibility(View.VISIBLE);
        message.setVisibility(View.VISIBLE);

        pos.setText("Yes");
        neg.setText("Cancel");

        AlertDialog dialog = new AlertDialog.Builder(this).setView(v).create();
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        pos.setOnClickListener(view -> {
            // original logout logic
            showPositiveButtonProgress(pos, posSpinner);
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                hidePositiveButtonProgress(pos, posSpinner);
                clearClientPrefsAndGotoAuth();
                dialog.dismiss();
                return;
            }

            DatabaseReference loggedRef = FirebaseDatabase.getInstance().getReference()
                    .child("users").child(user.getUid()).child("loggedIn");

            loggedRef.setValue(false)
                    .addOnSuccessListener(aVoid -> {
                        // cleanup and sign out on success
                        if (userFeaturesDbRef != null && featuresListener != null) {
                            try { userFeaturesDbRef.removeEventListener(featuresListener); } catch (Exception ignored) {}
                            userFeaturesDbRef = null;
                            featuresListener = null;
                        }
                        FirebaseAuth.getInstance().signOut();
                        hidePositiveButtonProgress(pos, posSpinner);
                        clearClientPrefsAndGotoAuth();
                        dialog.dismiss();
                    })
                    .addOnFailureListener(e -> {
                        hidePositiveButtonProgress(pos, posSpinner);
                        String msg = e != null ? e.getMessage() : "unknown";
                        showNotification("Logout failed (server): " + msg);
                        Log.e("MainActivity", "Failed to write loggedIn=false on logout: " + msg);
                        // keep dialog open or dismiss as desired; here we keep it open so user can retry/cancel
                    });
        });


        neg.setOnClickListener(view -> dialog.dismiss());
    }


    /** Helper to clear client prefs and navigate to AuthActivity */
    private void clearClientPrefsAndGotoAuth() {
        // clear important prefs used by app
        try {
            SharedPreferences dp = getSharedPreferences("DreamPediatricsPrefs", MODE_PRIVATE);
            dp.edit().clear().apply();

            SharedPreferences app = getSharedPreferences("app_prefs", MODE_PRIVATE);
            app.edit().clear().apply();
        } catch (Exception ex) {
            Log.w("MainActivity", "Error clearing prefs on logout: " + ex.getMessage());
        }

        // go back to auth
        Intent intent = new Intent(MainActivity.this, AuthActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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

    /**
     * Ensure an FCM token is present. If none in SharedPreferences, request a new one.
     */
    private void ensureFcmTokenExists() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String existing = prefs.getString(PREF_FCM_TOKEN, null);
        if (!TextUtils.isEmpty(existing)) {
            // token exists — you can optionally verify it's still valid on your server
            android.util.Log.d("FCM", "Existing token found: " + maskToken(existing));
            return;
        }

        // No token cached — request one from FirebaseMessaging
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (!task.isSuccessful()) {
                            android.util.Log.w("FCM", "Fetching FCM registration token failed", task.getException());
                            return;
                        }

                        // Get new FCM registration token
                        String token = task.getResult();
                        if (token != null) {
                            // Save to SharedPreferences
                            prefs.edit().putString(PREF_FCM_TOKEN, token).apply();
                            android.util.Log.d("FCM", "FCM token retrieved: " + maskToken(token));

                            // Optional: send token to your server so you can target this device
                            sendTokenToServer(token);
                        }
                    }
                });
    }

    /** Utility to avoid logging full token in production logs */
    private String maskToken(String token) {
        if (token == null) return null;
        if (token.length() <= 8) return token;
        return "..." + token.substring(token.length() - 8);
    }

    /** Placeholder - implement this to notify your backend of the token */
    private void sendTokenToServer(String token) {
        // TODO: implement network call to register token for this user/device
        // Example: call your REST endpoint with OkHttp or Firebase Functions callable.
        android.util.Log.d("FCM", "sendTokenToServer() should be implemented. Token (masked): " + maskToken(token));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusBarColor();
        // Re-perform verification checks on resume
        performAccountVerificationChecks();
    }
}
