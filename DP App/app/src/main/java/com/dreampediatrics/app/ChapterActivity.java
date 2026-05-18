package com.dreampediatrics.app;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * ChapterActivity - uses lightweight TopicSummary objects to avoid heavy HTML parsing on UI thread.
 */
public class ChapterActivity extends AppCompatActivity {
    public static final String EXTRA_CHAPTER_ID = "extra_chapter_id";
    public static final String EXTRA_CHAPTER_TITLE = "extra_chapter_title";

    private LinearLayout topicsContainer;
    private String chapterId;
    private String chapterTitle;
    private final Executor io = Executors.newSingleThreadExecutor();

    private TextView chapterCount;
    private TextView chapterProgress;
    private TextView chapterTitleText;
    private ImageView backButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chapter);

        topicsContainer = findViewById(R.id.topicsContainer);
        chapterCount = findViewById(R.id.chapterCount);
        chapterProgress = findViewById(R.id.chapterProgress);
        chapterTitleText = findViewById(R.id.chapterTitleText);
        backButton = findViewById(R.id.backButton);
        
        updateStatusBarColor();

        chapterId = getIntent().getStringExtra(EXTRA_CHAPTER_ID);
        chapterTitle = getIntent().getStringExtra(EXTRA_CHAPTER_TITLE);

        // Set chapter title
        if (chapterTitleText != null && chapterTitle != null) {
            chapterTitleText.setText(chapterTitle);
        }

        // Back button click handler
        if (backButton != null) {
            backButton.setOnClickListener(v -> onBackPressed());
        }

        // load topics using lightweight summaries
        loadTopics();
        updateChapterProgress(); // initial
    }

    /**
     * Load topic summaries (fast) from Room on a background thread,
     * then populate UI on the main thread.
     */
    private void loadTopics() {
        io.execute(() -> {
            // Use the new DAO projection for quick results
            final List<TopicSummary> summaries = AppDatabase.getInstance(this).appDao().getTopicSummariesForChapter(chapterId);
            runOnUiThread(() -> populateTopicsFromSummaries(summaries));
        });
    }

    /**
     * Inflate item views and bind data from TopicSummary.
     * Note: snippet field may contain HTML tags — we strip tags with a small regex (fast).
     */
    private void populateTopicsFromSummaries(List<TopicSummary> summaries) {
        topicsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        if (summaries == null || summaries.isEmpty()) {
            View empty = inflater.inflate(R.layout.empty_state, topicsContainer, false);
            TextView title = empty.findViewById(R.id.emptyTitle);
            TextView msg = empty.findViewById(R.id.emptyMessage);
            title.setText("No Topics");
            msg.setText("No topics available for this chapter.");
            topicsContainer.addView(empty);
            return;
        }

        for (TopicSummary s : summaries) {
            View v = inflater.inflate(R.layout.item_topic, topicsContainer, false);

            TextView titleView = v.findViewById(R.id.topicTitle);
            TextView subtitleView = v.findViewById(R.id.topicSubtitle);
            TextView topicNumber = v.findViewById(R.id.topicNumber);
            TextView topicTime = v.findViewById(R.id.topicTime);
            TextView topicStatus = v.findViewById(R.id.topicStatus);
            FrameLayout topicNumberContainer = v.findViewById(R.id.topicNumberContainer);
            ImageView topicCheckmark = v.findViewById(R.id.topicCheckmark);
            MaterialCardView cardView = (MaterialCardView) v;

            titleView.setText(s.title != null ? s.title : "Untitled");

            // Use curated description if available, otherwise fall back to snippet
            String displayText = "";
            if (s.description != null && !s.description.trim().isEmpty()) {
                // Use the curated description (already clean, no HTML/Markdown)
                displayText = s.description;
            } else {
                // Fallback to snippet (strip HTML/Markdown from content)
                String rawSnippet = s.snippet != null ? s.snippet : "";
                String plain = stripHtmlFast(rawSnippet).trim();
                displayText = makeSnippet(plain);
            }
            subtitleView.setText(displayText);

            // Set topic number from database (global number across all chapters)
            topicNumber.setText(String.valueOf(s.number));
            
            // Always show the number, never hide it
            topicNumber.setVisibility(View.VISIBLE);
            if (topicCheckmark != null) {
                topicCheckmark.setVisibility(View.GONE);
            }

            // Hide time for now (can be populated if available in TopicSummary)
            if (topicTime != null) {
                topicTime.setVisibility(View.GONE);
            }

            // Update card stroke and status based on completion
            if (s.completed) {
                // Completed topic - green background for number, "Done" status
                topicNumberContainer.setBackgroundResource(R.drawable.bg_topic_number_done);
                topicNumber.setTextColor(ContextCompat.getColor(this, R.color.badge_done_fg));
                cardView.setStrokeColor(ContextCompat.getColor(this, R.color.outline));
                
                if (topicStatus != null) {
                    topicStatus.setVisibility(View.VISIBLE);
                    topicStatus.setText("Done");
                    topicStatus.setBackgroundResource(R.drawable.bg_status_done);
                    topicStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_done_fg));
                }
            } else {
                // Not completed - check if it's in progress or new
                // For now, treat all incomplete as "New" with default stroke
                topicNumberContainer.setBackgroundResource(R.drawable.bg_topic_number);
                topicNumber.setTextColor(ContextCompat.getColor(this, R.color.on_surface));
                cardView.setStrokeColor(ContextCompat.getColor(this, R.color.outline));
                
                if (topicStatus != null) {
                    topicStatus.setVisibility(View.VISIBLE);
                    topicStatus.setText("New");
                    topicStatus.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
                    topicStatus.setBackgroundResource(R.drawable.bg_status_new);
                }
            }

            final long rowId = s.rowid;
            cardView.setOnClickListener(view -> {
                Intent i = new Intent(ChapterActivity.this, TopicActivity.class);
                i.putExtra(TopicActivity.EXTRA_TOPIC_ROWID, rowId);
                startActivity(i);
            });

            topicsContainer.addView(v);
        }
    }

    /**
     * Very small helper to strip HTML tags quickly using regex.
     * This is not a full HTML parser but it is fast and good enough for short snippets.
     */
    private static String stripHtmlFast(String input) {
        if (input == null) return "";
        // remove tags like <...>
        String result = input.replaceAll("<[^>]+>", "");
        // Strip Markdown formatting
        result = stripMarkdownFast(result);
        return result;
    }

    /**
     * Strip common Markdown formatting characters to show plain text in snippets.
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
        
        // Remove blockquote markers: >
        result = result.replaceAll("^>\\s+", "");
        result = result.replaceAll("\\n>\\s+", "\n");
        
        // Remove horizontal rules: --- or ***
        result = result.replaceAll("^[-*_]{3,}$", "");
        result = result.replaceAll("\\n[-*_]{3,}\\n", "\n");
        
        // Clean up any remaining asterisks or underscores that weren't part of formatting
        result = result.replaceAll("\\*+", "");
        result = result.replaceAll("_+", "");
        
        return result;
    }

    private String makeSnippet(String text) {
        if (text == null || text.isEmpty()) return "";
        String[] lines = text.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String ln : lines) {
            String t = ln.trim();
            if (t.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(t);
            count++;
            if (count >= 3) break;
        }
        String s = sb.toString();
        if (s.length() > 180) s = s.substring(0, 177) + "...";
        return s;
    }

    private void updateStatusBarColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            int color = ContextCompat.getColor(this, R.color.primary);
            window.setStatusBarColor(color);

            WindowInsetsControllerCompat insetsController =
                    WindowCompat.getInsetsController(window, window.getDecorView());

            boolean useDarkIcons = ColorUtils.calculateLuminance(color) > 0.5;
            insetsController.setAppearanceLightStatusBars(useDarkIcons);
        }
    }

    // Compute chapter progress from DB: percent = (completed topics / total topics) * 100
    private void updateChapterProgress() {
        io.execute(() -> {
            AppDao dao = AppDatabase.getInstance(ChapterActivity.this).appDao();
            int total = dao.getTopicCountForChapter(chapterId);
            int completed = dao.getCompletedCountForChapter(chapterId);

            final int finalTotal = total;
            final int finalCompleted = completed;
            
            runOnUiThread(() -> {
                // Update header text
                if (chapterCount != null) {
                    String countText = finalTotal == 1 ? finalTotal + " chapter" : finalTotal + " chapters";
                    chapterCount.setText(countText);
                }
                
                if (chapterProgress != null) {
                    chapterProgress.setText(finalCompleted + " of " + finalTotal + " done");
                }
            });
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();  // acts like system back
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // refresh topics (in case someone changed completion/progress) and chapter progress
        loadTopics();
        updateChapterProgress();
        updateStatusBarColor();
    }
}
