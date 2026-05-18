package com.dreampediatrics.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.dreampediatrics.app.PaymentDialogFragment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
public class HomeFragment extends Fragment {
    private LinearLayout chaptersContainer;
    private TextView greetingText;
    private TextView greetingTimeText;
    private LinearLayout btnContinue;
    private LinearLayout subscribeCard, downloadCard, streakCard;
    private MaterialButton btnSubscribe, btnDownload;
    private LinearProgressIndicator downloadProgressBar;
    private TextView goalValue, goalStreak;

    private final Executor io = Executors.newSingleThreadExecutor();
    private List<ChapterEntity> chapters = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        chaptersContainer = view.findViewById(R.id.chaptersContainer);
        greetingText = view.findViewById(R.id.greetingText);
        greetingTimeText = view.findViewById(R.id.greetingTimeText);
        btnContinue = view.findViewById(R.id.btnContinue);
        
        // Initialize new card views
        subscribeCard = view.findViewById(R.id.subscribeCard);
        downloadCard = view.findViewById(R.id.downloadCard);
        streakCard = view.findViewById(R.id.streakCard);
        btnSubscribe = view.findViewById(R.id.btnSubscribe);
        btnDownload = view.findViewById(R.id.btnDownload);
        downloadProgressBar = view.findViewById(R.id.downloadProgressBar);
        goalValue = view.findViewById(R.id.goalValue);
        goalStreak = view.findViewById(R.id.goalStreak);

        // load chapters & progress from DB
        loadChaptersFromDb();

        // --- Subscribe button wiring ---
        btnSubscribe.setOnClickListener(v -> {
            if (!isAdded()) return;
            PaymentBottomSheet bottomSheet = new PaymentBottomSheet();
            bottomSheet.show(getParentFragmentManager(), "payment_bottom_sheet");
        });

        // --- Download button wiring ---
        btnDownload.setOnClickListener(v -> {
            startTextbookDownload();
        });

        // Update UI based on current verification state
        updateUIBasedOnVerificationState();

        // initial greeting population
        refreshGreeting();
        btnContinue.setOnClickListener(v -> {
            MainActivity main = (MainActivity) getActivity();
            if (main == null) {
                Toast.makeText(requireContext(), "Unable to continue reading", Toast.LENGTH_SHORT).show();
                return;
            }
            long last = main.getLastOpenedTopicRowId();
            if (last != -1L) {
                Intent i = new Intent(requireActivity(), TopicActivity.class);
                i.putExtra(TopicActivity.EXTRA_TOPIC_ROWID, last);
                i.putExtra(TopicActivity.EXTRA_RESUMED_FROM_RESUME, true);
                startActivity(i);
            } else {
                Toast.makeText(requireContext(), "No recent reading session", Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }

    /**
     * OPTIMIZED: Update UI based on current account verification state
     * Shows subscribe card when not verified, download card when verified but not downloaded,
     * and streak card when content is available
     */
    public void updateUIBasedOnVerificationState() {
        Context ctx = getContext();
        if (ctx == null) return;

        SharedPreferences prefs = ctx.getSharedPreferences("DreamPediatricsPrefs", Context.MODE_PRIVATE);
        boolean featuresUnlocked = prefs.getBoolean("features_unlocked", false);
        boolean paymentSubmitted = prefs.getBoolean("payment_submitted", false);

        // Check Room DB content in background
        io.execute(() -> {
            boolean roomHasContent = false;
            try {
                List<ChapterEntity> dbChapters = AppDatabase.getInstance(ctx).appDao().getAllChapters();
                roomHasContent = dbChapters != null && !dbChapters.isEmpty();
            } catch (Exception ignored) {}

            final boolean finalRoomHasContent = roomHasContent;

            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    updateCardVisibility(featuresUnlocked, paymentSubmitted, finalRoomHasContent);
                });
            }
        });
    }

    /**
     * OPTIMIZED: Centralized UI control for card visibility
     * Shows one of three cards: subscribe, download, or streak
     */
    private void updateCardVisibility(boolean featuresUnlocked, boolean paymentSubmitted, boolean roomHasContent) {
        if (subscribeCard == null || downloadCard == null || streakCard == null) return;

        if (roomHasContent) {
            // Room DB has content - show streak card
            subscribeCard.setVisibility(View.GONE);
            downloadCard.setVisibility(View.GONE);
            streakCard.setVisibility(View.VISIBLE);
        } else if (featuresUnlocked) {
            // Features unlocked but no content - show download card
            subscribeCard.setVisibility(View.GONE);
            downloadCard.setVisibility(View.VISIBLE);
            streakCard.setVisibility(View.GONE);
            
            // Reset button state
            btnDownload.setEnabled(true);
            btnDownload.setText("Download");
            downloadProgressBar.setVisibility(View.GONE);

            // FIXED: Reset purchase button state when features unlock
            SharedPreferences prefs = requireContext().getSharedPreferences("DreamPediatricsPrefs", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("payment_submitted", false).apply();
        } else {
            // Features locked - show subscribe card
            subscribeCard.setVisibility(View.VISIBLE);
            downloadCard.setVisibility(View.GONE);
            streakCard.setVisibility(View.GONE);

            if (paymentSubmitted) {
                btnSubscribe.setText("Pending");
                btnSubscribe.setEnabled(false);
            } else {
                btnSubscribe.setText("Subscribe");
                btnSubscribe.setEnabled(true);
            }
        }
    }

    /**
     * Check if features are unlocked on server (not locally)
     * This is used to determine if download button should be shown
     */
    private void checkServerFeatureStatus(final OnServerStatusChecked callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onChecked(false); // Not signed in, assume locked
            return;
        }

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference()
                .child("users").child(user.getUid()).child("featuresLocked");

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean featuresLocked = snapshot.getValue(Boolean.class);
                // If null or true, features are locked; if false, unlocked
                boolean unlocked = featuresLocked != null && !featuresLocked;
                callback.onChecked(unlocked);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("HomeFragment", "Failed to check server feature status: " + error.getMessage());
                callback.onChecked(false); // Assume locked on error
            }
        });
    }

    private interface OnServerStatusChecked {
        void onChecked(boolean serverFeaturesUnlocked);
    }

    /**
     * Legacy methods - now delegate to unified UI update method
     */
    public void updatePurchaseButtonState() {
        updateUIBasedOnVerificationState();
    }

    public void updateDownloadButtonVisibility() {
        updateUIBasedOnVerificationState();
    }

    /**
     * Called after purchase submit to set UI to pending.
     */
    public void onPurchaseSubmitted() {
        if (btnSubscribe == null) return;

        btnSubscribe.setText("Pending");
        btnSubscribe.setEnabled(false);

        SharedPreferences prefs = requireContext().getSharedPreferences("DreamPediatricsPrefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("payment_submitted", true).apply();
    }

    public void loadChaptersFromDb() {
        Context ctx = getContext();
        if (ctx == null) return;

        io.execute(() -> {
            List<ChapterEntity> dbChapters = AppDatabase.getInstance(ctx).appDao().getAllChapters();
            if (dbChapters == null) dbChapters = new ArrayList<>();
            chapters = dbChapters;

            // back to UI
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    populateChaptersWithProgress();
                    // Update UI state after loading chapters (important for payment card visibility)
                    updateUIBasedOnVerificationState();
                });
            }
        });
    }

    private void populateChaptersWithProgress() {
        if (!isAdded()) {
            return;
        }

        chaptersContainer.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();

        // Update goal pill with actual data
        updateGoalPill();

        for (ChapterEntity ch : chapters) {
            View chapterView = inflater.inflate(R.layout.item_chapter, chaptersContainer, false);

            TextView titleView = chapterView.findViewById(R.id.chapterTitle);
            TextView subtitleView = chapterView.findViewById(R.id.chapterSubtitle);
            LinearProgressIndicator progressBar = chapterView.findViewById(R.id.progressBar);
            TextView progressText = chapterView.findViewById(R.id.progressText);
            TextView badgeView = chapterView.findViewById(R.id.badge);
            ImageView chapterIcon = chapterView.findViewById(R.id.chapterIcon);
            FrameLayout chapterIconContainer = chapterView.findViewById(R.id.chapterIconContainer);
            androidx.cardview.widget.CardView cardView = (androidx.cardview.widget.CardView) chapterView;

            // Strip markdown formatting from title and description
            String plainTitle = stripMarkdownFast(ch.title != null ? ch.title : "");
            String plainDescription = stripMarkdownFast(ch.description != null ? ch.description : "");
            
            titleView.setText(plainTitle);
            subtitleView.setText(plainDescription);
            
            // Set chapter icon (use default for now, can be customized per chapter)
            chapterIcon.setImageResource(R.drawable.ic_chapter_default);
            
            badgeView.setVisibility(View.VISIBLE);
            badgeView.setText("8%");

            final String chapterId = ch.chapterId;
            progressBar.setProgress(0);
            progressText.setText("0 / 0");

            io.execute(() -> {
                if (!isAdded()) return;

                AppDao dao = AppDatabase.getInstance(requireContext()).appDao();
                int total = dao.getTopicCountForChapter(chapterId);
                int completed = dao.getCompletedCountForChapter(chapterId);
                int percent = 0;
                if (total > 0) percent = (int) Math.round((completed * 100.0) / total);

                final int finalTotal = total;
                final int finalCompleted = completed;
                final int finalPercent = percent;

                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        progressBar.setProgress(finalPercent);
                        progressText.setText(finalCompleted + " / " + finalTotal);
                        
                        // Update badge based on completion
                        if (finalPercent == 100) {
                            badgeView.setText("Done");
                            badgeView.setBackground(getResources().getDrawable(R.drawable.bg_badge_done));
                            badgeView.setTextColor(getResources().getColor(R.color.badge_done_fg));
                        } else {
                            badgeView.setText(finalPercent + "%");
                            badgeView.setBackground(getResources().getDrawable(R.drawable.bg_badge_warn));
                            badgeView.setTextColor(getResources().getColor(R.color.badge_warn_fg));
                        }
                    });
                }
            });

            cardView.setOnClickListener(v -> {
                MainActivity main = (MainActivity) getActivity();
                if (main != null) {
                    main.openChapterById(ch.chapterId, ch.title);
                }
            });

            chapterView.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100);
                        break;
                }
                return false;
            });

            chaptersContainer.addView(chapterView);
        }

        if (chapters.isEmpty()) {
            View emptyView = inflater.inflate(R.layout.empty_state, chaptersContainer, false);
            TextView emptyTitle = emptyView.findViewById(R.id.emptyTitle);
            TextView emptyMessage = emptyView.findViewById(R.id.emptyMessage);

            emptyTitle.setText("No Chapters");
            emptyMessage.setText("No resources found. Purchase amp&; Download the Book to get started.");
            chaptersContainer.addView(emptyView);
        }
    }

    private void filterChapters(String query) {
        for (int i = 0; i < chaptersContainer.getChildCount(); i++) {
            View chapterView = chaptersContainer.getChildAt(i);
            TextView titleView = chapterView.findViewById(R.id.chapterTitle);
            TextView subtitleView = chapterView.findViewById(R.id.chapterSubtitle);

            String title = titleView.getText().toString().toLowerCase();
            String subtitle = subtitleView.getText().toString().toLowerCase();

            if (query.isEmpty() || title.contains(query) || subtitle.contains(query)) {
                chapterView.setVisibility(View.VISIBLE);
                chapterView.setAlpha(1.0f);
            } else {
                chapterView.setVisibility(View.GONE);
                chapterView.setAlpha(0.0f);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh progress counts because topics may have changed
        loadChaptersFromDb();

        MainActivity main = (MainActivity) getActivity();
        long last = (main != null) ? main.getLastOpenedTopicRowId() : -1L;
        if (btnContinue != null) {
            btnContinue.setVisibility(last != -1L ? View.VISIBLE : View.INVISIBLE);
        }

        // Refresh greeting in case user changed name in settings
        refreshGreeting();

        // Update UI based on current verification state
        updateUIBasedOnVerificationState();
        
        // Update goal pill with latest data
        updateGoalPill();
    }

    /**
     * Update the goal pill with actual streak and topic completion data
     */
    private void updateGoalPill() {
        Context ctx = getContext();
        if (ctx == null || goalValue == null || goalStreak == null) return;

        io.execute(() -> {
            if (!isAdded()) return;

            AppDao dao = AppDatabase.getInstance(ctx).appDao();
            
            // Get total topics and completed topics across all chapters
            int totalTopics = dao.getTotalTopicCount();
            int completedTopics = dao.getTotalCompletedCount();
            
            // Calculate streak
            int streak = calculateStreak(dao);

            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    // Update goal value
                    if (totalTopics > 0) {
                        goalValue.setText(completedTopics + " of " + totalTopics + " topics done");
                    } else {
                        goalValue.setText("No topics available");
                    }

                    // Update streak
                    if (streak > 0) {
                        goalStreak.setText("🔥 " + streak + "-day streak");
                    } else {
                        goalStreak.setText("Start your streak!");
                    }
                });
            }
        });
    }

    /**
     * Calculate the current reading streak based on completed topics
     * A streak is maintained if the user completes at least one topic per day
     */
    private int calculateStreak(AppDao dao) {
        try {
            List<String> completedDates = dao.getCompletedDates();
            if (completedDates == null || completedDates.isEmpty()) {
                return 0;
            }

            // Get today's date in the same format
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            String today = sdf.format(new java.util.Date());

            int streak = 0;
            String expectedDate = today;

            // Check if user completed something today or yesterday (to allow for late night reading)
            if (!completedDates.contains(today)) {
                // Check yesterday
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.add(java.util.Calendar.DAY_OF_YEAR, -1);
                String yesterday = sdf.format(cal.getTime());
                
                if (!completedDates.contains(yesterday)) {
                    // No activity today or yesterday, streak is broken
                    return 0;
                }
                expectedDate = yesterday;
            }

            // Count consecutive days
            java.util.Calendar cal = java.util.Calendar.getInstance();
            for (String date : completedDates) {
                if (date.equals(expectedDate)) {
                    streak++;
                    // Move to previous day
                    cal.setTime(sdf.parse(expectedDate));
                    cal.add(java.util.Calendar.DAY_OF_YEAR, -1);
                    expectedDate = sdf.format(cal.getTime());
                } else {
                    // Gap in streak, stop counting
                    break;
                }
            }

            return streak;
        } catch (Exception e) {
            android.util.Log.e("HomeFragment", "Error calculating streak", e);
            return 0;
        }
    }

    /**
     * Refresh the greeting card: name + time-of-day text.
     */
    public void refreshGreeting() {
        Context ctx = getContext();
        if (ctx == null) return;

        SharedPreferences prefs = ctx.getSharedPreferences("DreamPediatricsPrefs", Context.MODE_PRIVATE);
        String plainName = prefs.getString("username", null);
        if (plainName == null || plainName.trim().isEmpty()) {
            plainName = "User";
        }

        // Add "Dr." prefix when showing greeting
        String displayName = plainName.trim();
        if (!displayName.toUpperCase().startsWith("DR")) {
            displayName = "Dr. " + displayName;
        }

        // Time-based greeting
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String timeGreeting;
        String greetingPrefix;
        if (hour < 12) {
            timeGreeting = "Good morning";
            greetingPrefix = "Good morning,";
        } else if (hour < 17) {
            timeGreeting = "Good afternoon";
            greetingPrefix = "Good afternoon,";
        } else {
            timeGreeting = "Good evening";
            greetingPrefix = "Good evening,";
        }

        if (greetingText != null) greetingText.setText(greetingPrefix + "\n" + displayName);
        if (greetingTimeText != null) greetingTimeText.setText("Ready for today's session?");
    }

    /**
     * OPTIMIZED: Start textbook download with server verification
     * Checks if features are unlocked on server before proceeding
     */
    private void startTextbookDownload() {
        Context ctx = getContext();
        if (ctx == null) return;
        MainActivity main = (MainActivity) getActivity();
        if (main == null) return;

        // First check server status before attempting download
        btnDownload.setEnabled(false);
        btnDownload.setText("Checking...");

        checkServerFeatureStatus(new OnServerStatusChecked() {
            @Override
            public void onChecked(boolean serverFeaturesUnlocked) {
                if (!isAdded()) return;

                if (serverFeaturesUnlocked) {
                    // Features are unlocked on server - proceed with download
                    proceedWithDownload(ctx, main);
                } else {
                    // Features still locked on server - show error and reset button
                    btnDownload.setEnabled(true);
                    btnDownload.setText("Download");

                    Toast.makeText(ctx, "Please purchase the book to download resources", Toast.LENGTH_LONG).show();

                    // Update UI to show subscribe card instead
                    updateUIBasedOnVerificationState();
                }
            }
        });
    }

    /**
     * Proceed with the actual download process
     */
    private void proceedWithDownload(Context ctx, MainActivity main) {
        // Show download progress UI
        btnDownload.setEnabled(false);
        btnDownload.setText("Downloading...");
        downloadProgressBar.setVisibility(View.VISIBLE);
        downloadProgressBar.setProgress(0);

        // Firebase Storage reference - explicitly use dream-pedi bucket
        com.google.firebase.storage.FirebaseStorage storage = com.google.firebase.storage.FirebaseStorage.getInstance("gs://dream-pedi");
        com.google.firebase.storage.StorageReference ref = storage.getReference().child("textbooks/textbook.enc");

        // Local target file
        File outDir = new File(ctx.getFilesDir(), "downloaded_textbook");
        if (!outDir.exists()) outDir.mkdirs();
        File outFile = new File(outDir, "textbook.enc");

        ref.getFile(outFile)
                .addOnProgressListener(snapshot -> {
                    double progress = (100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount();
                    int p = (int) progress;
                    downloadProgressBar.setProgress(p);
                    btnDownload.setText("Downloading... " + p + "%");
                })
                .addOnSuccessListener(taskSnapshot -> {
                    downloadProgressBar.setProgress(100);
                    btnDownload.setText("Processing...");

                    Toast.makeText(ctx, "Download completed, installing textbook...", Toast.LENGTH_SHORT).show();

                    // Call MainActivity to decrypt and populate DB
                    main.decryptAndPopulate(outFile);
                })
                .addOnFailureListener(e -> {
                    // Reset UI on failure
                    downloadProgressBar.setVisibility(View.GONE);
                    btnDownload.setEnabled(true);
                    btnDownload.setText("Download");

                    String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                    Toast.makeText(ctx, "Download failed: " + errorMsg, Toast.LENGTH_LONG).show();
                    Log.e("HomeFragment", "Download failed", e);
                });
    }

    /**
     * Called by MainActivity after successful textbook installation
     * Resets download UI and triggers UI state update
     */
    public void onTextbookInstalled() {
        downloadProgressBar.setVisibility(View.GONE);
        if (btnDownload != null) {
            btnDownload.setEnabled(true);
            btnDownload.setText("Download");
        }

        // Refresh chapters and update UI state
        loadChaptersFromDb();
        updateUIBasedOnVerificationState();
    }

    /**
     * Strip common Markdown formatting characters to show plain text.
     * This removes bold, italic, links, and other markdown syntax.
     */
    private static String stripMarkdownFast(String input) {
        if (input == null) return "";
        String result = input;
        
        // Remove bold: **text** or __text__
        result = result.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        result = result.replaceAll("__([^_]+)__", "$1");
        
        // Remove italic: *text* or _text_
        result = result.replaceAll("\\*([^*]+)\\*", "$1");
        result = result.replaceAll("_([^_]+)_", "$1");
        
        // Remove strikethrough: ~~text~~
        result = result.replaceAll("~~([^~]+)~~", "$1");
        
        // Remove inline code: `text`
        result = result.replaceAll("`([^`]+)`", "$1");
        
        // Remove links: [text](url)
        result = result.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");
        
        // Remove images: ![alt](url)
        result = result.replaceAll("!\\[([^\\]]*)\\]\\([^)]+\\)", "$1");
        
        // Remove headers: # text
        result = result.replaceAll("^#{1,6}\\s+", "");
        result = result.replaceAll("\\n#{1,6}\\s+", "\n");
        
        // Remove list markers: - or * or + or 1.
        result = result.replaceAll("^[\\-\\*\\+]\\s+", "");
        result = result.replaceAll("\\n[\\-\\*\\+]\\s+", "\n");
        result = result.replaceAll("^\\d+\\.\\s+", "");
        result = result.replaceAll("\\n\\d+\\.\\s+", "\n");
        
        return result.trim();
    }
}