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
    private MaterialCardView btnContinue, paymentCard;
    private MaterialButton btnPurchaseCard, downloadButton;
    private ProgressBar downloadProgress;

    private final Executor io = Executors.newSingleThreadExecutor();
    private List<ChapterEntity> chapters = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        chaptersContainer = view.findViewById(R.id.chaptersContainer);
        greetingText = view.findViewById(R.id.greetingText);
        greetingTimeText = view.findViewById(R.id.greetingTimeText);
        btnContinue = view.findViewById(R.id.btnContinue);
        downloadProgress = view.findViewById(R.id.downloadProgress);
        downloadButton = view.findViewById(R.id.downloadButton);
        btnPurchaseCard = view.findViewById(R.id.btnPurchaseCard);
        paymentCard = view.findViewById(R.id.payment_card);

        // load chapters & progress from DB
        loadChaptersFromDb();

        // --- Payment card purchase button wiring ---
        btnPurchaseCard.setOnClickListener(v -> {
            if (!isAdded()) return;
            PaymentDialogFragment dialog = new PaymentDialogFragment();
            dialog.show(getParentFragmentManager(), "payment_dialog");
        });

        downloadButton.setOnClickListener(v -> {
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
     * Fixed logic: Payment card stays visible until Room DB is populated
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
                    updatePaymentCardAndButtonsVisibility(featuresUnlocked, paymentSubmitted, finalRoomHasContent);
                });
            }
        });
    }

    /**
     * OPTIMIZED: Centralized UI control for payment card and buttons
     * FIXED: Properly handle transition from pending to download when server unlocks features
     */
    private void updatePaymentCardAndButtonsVisibility(boolean featuresUnlocked, boolean paymentSubmitted, boolean roomHasContent) {
        if (paymentCard == null || btnPurchaseCard == null || downloadButton == null) return;

        if (roomHasContent) {
            // Room DB has content - hide payment card completely
            paymentCard.setVisibility(View.GONE);
            downloadButton.setVisibility(View.GONE);
        } else {
            // Room DB is empty - show payment card
            paymentCard.setVisibility(View.VISIBLE);

            if (featuresUnlocked) {
                // Features unlocked - show download button, hide purchase button
                btnPurchaseCard.setVisibility(View.GONE);
                downloadButton.setVisibility(View.VISIBLE);

                // FIXED: Reset purchase button state when features unlock
                SharedPreferences prefs = requireContext().getSharedPreferences("DreamPediatricsPrefs", Context.MODE_PRIVATE);
                prefs.edit().putBoolean("payment_submitted", false).apply();
            } else {
                // Features locked - show purchase button based on payment state, hide download
                downloadButton.setVisibility(View.GONE);
                btnPurchaseCard.setVisibility(View.VISIBLE);

                if (paymentSubmitted) {
                    btnPurchaseCard.setText("Pending");
                    btnPurchaseCard.setEnabled(false);
                } else {
                    btnPurchaseCard.setText("Purchase");
                    btnPurchaseCard.setEnabled(true);
                }
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
        if (btnPurchaseCard == null) return;

        btnPurchaseCard.setText("Pending");
        btnPurchaseCard.setEnabled(false);

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

        for (ChapterEntity ch : chapters) {
            View chapterView = inflater.inflate(R.layout.item_chapter, chaptersContainer, false);

            TextView titleView = chapterView.findViewById(R.id.chapterTitle);
            TextView subtitleView = chapterView.findViewById(R.id.chapterSubtitle);
            TextView infoView = chapterView.findViewById(R.id.chapterInfo);
            LinearProgressIndicator progressBar = chapterView.findViewById(R.id.progressBar);
            TextView badgeView = chapterView.findViewById(R.id.badge);
            androidx.cardview.widget.CardView cardView = (androidx.cardview.widget.CardView) chapterView;

            titleView.setText(ch.title);
            subtitleView.setText(ch.description != null ? ch.description : "");
            badgeView.setVisibility(View.GONE);

            final String chapterId = ch.chapterId;
            progressBar.setProgress(0);
            infoView.setText("Loading...");

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
                        infoView.setText(
                                finalCompleted + " of " + finalTotal + " Topics    •    " + finalPercent + "% Complete"
                        );
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

        if (greetingText != null) greetingText.setText("Hi, " + displayName);

        // Time-based greeting
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String timeGreeting;
        if (hour < 12) {
            timeGreeting = "Good morning";
        } else if (hour < 17) {
            timeGreeting = "Good afternoon";
        } else {
            timeGreeting = "Good evening";
        }

        if (greetingTimeText != null) greetingTimeText.setText(timeGreeting);
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
        downloadButton.setEnabled(false);
        downloadButton.setText("Checking...");

        checkServerFeatureStatus(new OnServerStatusChecked() {
            @Override
            public void onChecked(boolean serverFeaturesUnlocked) {
                if (!isAdded()) return;

                if (serverFeaturesUnlocked) {
                    // Features are unlocked on server - proceed with download
                    proceedWithDownload(ctx, main);
                } else {
                    // Features still locked on server - show error and reset button
                    downloadButton.setEnabled(true);
                    downloadButton.setText("Download");

                    Toast.makeText(ctx, "Please purchase the book to download resources", Toast.LENGTH_LONG).show();

                    // Update UI to show purchase button instead
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
        downloadButton.setEnabled(false);
        downloadButton.setText("Downloading...");
        downloadProgress.setVisibility(View.VISIBLE);
        downloadProgress.setProgress(0);

        // Firebase Storage reference
        com.google.firebase.storage.FirebaseStorage storage = com.google.firebase.storage.FirebaseStorage.getInstance();
        com.google.firebase.storage.StorageReference ref = storage.getReference().child("textbook.enc");

        // Local target file
        File outDir = new File(ctx.getFilesDir(), "downloaded_textbook");
        if (!outDir.exists()) outDir.mkdirs();
        File outFile = new File(outDir, "textbook.enc");

        ref.getFile(outFile)
                .addOnProgressListener(snapshot -> {
                    double progress = (100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount();
                    int p = (int) progress;
                    downloadProgress.setProgress(p);
                    downloadButton.setText("Downloading... " + p + "%");
                })
                .addOnSuccessListener(taskSnapshot -> {
                    downloadProgress.setProgress(100);
                    downloadButton.setText("Processing...");

                    Toast.makeText(ctx, "Download completed, installing textbook...", Toast.LENGTH_SHORT).show();

                    // Call MainActivity to decrypt and populate DB
                    main.decryptAndPopulate(outFile);
                })
                .addOnFailureListener(e -> {
                    // Reset UI on failure
                    downloadProgress.setVisibility(View.GONE);
                    downloadButton.setEnabled(true);
                    downloadButton.setText("Download");

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
        downloadProgress.setVisibility(View.GONE);
        paymentCard.setVisibility(View.GONE);
        if (downloadButton != null) {
            downloadButton.setEnabled(true);
            downloadButton.setText("Download");

        }

        // Refresh chapters and update UI state
        loadChaptersFromDb();
        updateUIBasedOnVerificationState();
    }
}