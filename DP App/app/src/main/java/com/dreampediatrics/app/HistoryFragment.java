package com.dreampediatrics.app;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryFragment extends Fragment {
    private LinearLayout historyContainer;
    private MaterialCardView btnClearHistory;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);
        historyContainer = view.findViewById(R.id.historyContainer);
        btnClearHistory = view.findViewById(R.id.btnClearHistory);

        btnClearHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Clear reading history")
                        .setMessage("Are you sure you want to clear your entire reading history? This cannot be undone.")
                        .setPositiveButton("Clear", (d, which) -> {
                            MainActivity main = (MainActivity) getActivity();
                            if (main != null) {
                                // call MainActivity to handle DB deletion + in-memory clearing
                                main.clearAllHistory();
                                btnClearHistory.setVisibility(INVISIBLE);

                                // immediate UI feedback: clear view and show empty state
                                showEmptyState();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        populateHistory();
    }

    private void populateHistory() {
        historyContainer.removeAllViews();
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity == null) return;

        List<HistoryItem> historyItems = mainActivity.getReadingHistoryLimited(20); // show last 20
        LayoutInflater inflater = LayoutInflater.from(getContext());

        if (historyItems.isEmpty()) {
            View emptyView = inflater.inflate(R.layout.empty_state, historyContainer, false);
            TextView emptyTitle = emptyView.findViewById(R.id.emptyTitle);
            TextView emptyMessage = emptyView.findViewById(R.id.emptyMessage);
            ImageView Img = emptyView.findViewById(R.id.fragicon);
            Img.setImageResource(R.drawable.ic_history);
            emptyTitle.setText("No Reading History");
            emptyMessage.setText("Start reading chapters to see your progress here");
            historyContainer.addView(emptyView);
            btnClearHistory.setVisibility(INVISIBLE);
            return;
        }

        for (HistoryItem item : historyItems) {
            View historyView = inflater.inflate(R.layout.item_history, historyContainer, false);

            TextView chapterView = historyView.findViewById(R.id.historyChapter);
            TextView sectionView = historyView.findViewById(R.id.historySection);
            TextView infoView = historyView.findViewById(R.id.historyInfo);
            LinearProgressIndicator progressBar = historyView.findViewById(R.id.historyProgress);
            CardView cardView = (CardView) historyView;

            chapterView.setText(item.getTitle()); // use topic title
            sectionView.setText(item.getSnippet()); // snippet of content
            progressBar.setVisibility(GONE);
            btnClearHistory.setVisibility(VISIBLE);

            // Format timestamp into readable date/time
            String dateTime = formatTimestamp(item.getTimestamp());
            // Compose info text: existing info (e.g. "Viewed") + " • " + date/time
            String infoText = (item.getInfo() != null && !item.getInfo().isEmpty())
                    ? item.getInfo() + "   •   " + dateTime
                    : dateTime;
            infoView.setText(infoText);


            cardView.setOnClickListener(v -> {
                // Open the topic by id stored in history item
                mainActivity.openTopic(item.getTopicId());
            });


            // Touch effect
            cardView.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100);
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100);
                        break;
                }
                return false;
            });

            historyContainer.addView(historyView);
        }
    }
    private void showEmptyState() {
        historyContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View emptyView = inflater.inflate(R.layout.empty_state, historyContainer, false);
        TextView title = emptyView.findViewById(R.id.emptyTitle);
        TextView msg = emptyView.findViewById(R.id.emptyMessage);
        ImageView Img = emptyView.findViewById(R.id.fragicon);
        Img.setImageResource(R.drawable.ic_history);
        title.setText("No Reading History");
        msg.setText("Your recent reads will appear here. Start reading topics to build your history.");
        historyContainer.addView(emptyView);
    }

    private String formatTimestamp(long tsMillis) {
        if (tsMillis <= 0) return "";
        // Use device locale. Example format: "Sep 6, 2025 • 10:32 AM"
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy \u2022 h:mm a", Locale.getDefault());
        return sdf.format(new Date(tsMillis));
    }
}
