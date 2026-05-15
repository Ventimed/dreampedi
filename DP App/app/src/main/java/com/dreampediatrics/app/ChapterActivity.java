package com.dreampediatrics.app;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

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

    private Toolbar toolbar;
    private LinearLayout topicsContainer;
    private String chapterId;
    private String chapterTitle;
    private final Executor io = Executors.newSingleThreadExecutor();

    private LinearProgressIndicator chapterProgressBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chapter);

        topicsContainer = findViewById(R.id.topicsContainer);
        chapterProgressBar = findViewById(R.id.chapterProgressBar);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        updateStatusBarColor();

        chapterId = getIntent().getStringExtra(EXTRA_CHAPTER_ID);
        chapterTitle = getIntent().getStringExtra(EXTRA_CHAPTER_TITLE);

        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }

        // Ensure action bar exists and show Up arrow; set the title to the selected chapter title
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(chapterTitle != null ? chapterTitle : "Chapter");
        }

        // Keep toolbar title color consistent
        toolbar.setTitle(chapterTitle != null ? chapterTitle : "Chapter");
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.on_surface));

        // tint navigation icon to on_surface
        final int navTint = ContextCompat.getColor(this, R.color.on_surface);
        Drawable navDrawable = toolbar.getNavigationIcon();
        if (navDrawable != null) {
            navDrawable.setTint(navTint);
            toolbar.setNavigationIcon(navDrawable);
        } else {
            toolbar.post(() -> {
                Drawable d = toolbar.getNavigationIcon();
                if (d != null) {
                    d.setTint(navTint);
                    toolbar.setNavigationIcon(d);
                }
            });
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
            View v = inflater.inflate(R.layout.item_chapter, topicsContainer, false);

            TextView titleView = v.findViewById(R.id.chapterTitle);
            TextView subtitleView = v.findViewById(R.id.chapterSubtitle);
            TextView infoView = v.findViewById(R.id.chapterInfo);
            LinearProgressIndicator progressBar = v.findViewById(R.id.progressBar);
            View badgeView = v.findViewById(R.id.badge);
            CardView cardView = (CardView) v;

            titleView.setText(s.title != null ? s.title : "Untitled");

            // Fast strip of HTML tags from snippet (avoids Html.fromHtml on UI thread)
            String rawSnippet = s.snippet != null ? s.snippet : "";
            String plain = stripHtmlFast(rawSnippet).trim();
            subtitleView.setText(makeSnippet(plain));

            infoView.setVisibility(View.GONE);

            if (progressBar != null) {
                int p = s.completed ? 100 : 0;
                progressBar.setProgress(p);
                progressBar.setVisibility(s.completed ? View.VISIBLE : View.GONE);
            }

            if (badgeView != null) badgeView.setVisibility(View.GONE);

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
            int color = ContextCompat.getColor(this, R.color.toolbar_bg);
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
            int percent = 0;
            if (total > 0) percent = (int) Math.round((completed * 100.0) / total);

            final int finalPercent = percent;
            runOnUiThread(() -> {
                if (chapterProgressBar != null) {
                    chapterProgressBar.setProgress(finalPercent);
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
