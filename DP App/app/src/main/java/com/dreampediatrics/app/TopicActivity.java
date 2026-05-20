package com.dreampediatrics.app;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.StyleSpan;
import android.text.style.ClickableSpan;
import android.util.DisplayMetrics;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.snackbar.Snackbar;

import io.noties.markwon.Markwon;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.ImagesPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TopicActivity extends AppCompatActivity {
    public static final String EXTRA_TOPIC_ROWID = "extra_topic_rowid";
    public static final String EXTRA_HIGHLIGHT_TERM = "extra_highlight_term";
    public static final String EXTRA_RESUMED_FROM_RESUME = "extra_resumed_from_resume"; // new

    private ImageView topicImage, bookmarkimg, backButton;
    private LinearLayout imageContainer, fontSizeControls; // new: will hold multiple images below the text
    private TextView topicContent;
    private TextView topicTitleView;
    private FrameLayout bookmarkButton, decreaseFontButton, increaseFontButton;
    private ScrollView topicScrollView;

    // Font size settings
    private static final float MIN_FONT_SIZE = 12f;
    private static final float MAX_FONT_SIZE = 24f;
    private static final float DEFAULT_FONT_SIZE = 16f;
    private static final float FONT_SIZE_STEP = 2f;
    private static final String PREF_FONT_SIZE = "content_font_size";
    private float currentFontSize = DEFAULT_FONT_SIZE;

    // Auto-hide settings
    private static final long AUTO_HIDE_DELAY = 3000; // 3 seconds
    private Runnable hideControlsRunnable;

    private final Executor io = Executors.newSingleThreadExecutor();
    private long rowid;
    private String highlightTerm;
    private Markwon markwon;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_topic);
        updateStatusBarColor();

        topicImage = findViewById(R.id.topicImage);
        topicContent = findViewById(R.id.topicContent);
        topicTitleView = findViewById(R.id.topicTitle);
        bookmarkButton = findViewById(R.id.bookmarkButton);
        topicScrollView = findViewById(R.id.topicScrollView);
        bookmarkimg = findViewById(R.id.bookmarkimg);
        imageContainer = findViewById(R.id.imageContainer);
        backButton = findViewById(R.id.backButton);
        decreaseFontButton = findViewById(R.id.decreaseFontButton);
        increaseFontButton = findViewById(R.id.increaseFontButton);
        fontSizeControls = findViewById(R.id.fontSizeControls);

        // Initialize hide controls runnable
        hideControlsRunnable = () -> hideFontControls();

        rowid = getIntent().getLongExtra(EXTRA_TOPIC_ROWID, -1);
        highlightTerm = getIntent().getStringExtra(EXTRA_HIGHLIGHT_TERM);
        boolean resumedFlag = getIntent().getBooleanExtra(EXTRA_RESUMED_FROM_RESUME, false);

        // Load saved font size preference
        loadFontSizePreference();

        if (rowid == -1) {
            finish();
            return;
        }

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // Initialize Markwon for Markdown rendering
        markwon = Markwon.builder(this)
                .usePlugin(HtmlPlugin.create())
                .usePlugin(ImagesPlugin.create())
                .usePlugin(TablePlugin.create(this))
                .usePlugin(StrikethroughPlugin.create())
                .build();

        loadAndMarkTopic();

        if (resumedFlag) {
            final View root = findViewById(android.R.id.content);
            if (root != null) {
                Snackbar sb = Snackbar.make(root,
                        "Resumed last reading",
                        Snackbar.LENGTH_LONG);

                sb.setAction("Back to Home", v -> finish());
                sb.setActionTextColor(ContextCompat.getColor(this, R.color.primary));

                View sbView = sb.getView();
                sbView.setBackgroundTintList(null);
                sbView.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_snackbar_round));
                TextView text = sbView.findViewById(com.google.android.material.R.id.snackbar_text);
                if (text != null) {
                    text.setTextColor(ContextCompat.getColor(this, R.color.outline));
                    text.setMaxLines(5);
                }
                TextView action = sbView.findViewById(com.google.android.material.R.id.snackbar_action);
                if (action != null) {
                    action.setTextColor(ContextCompat.getColor(this, R.color.primary));
                }

                sb.show();
            }
        }

        backButton.setOnClickListener(v -> finish());
        bookmarkButton.setOnClickListener(v -> toggleBookmarkForCurrentTopic());
        
        // Font size control listeners
        decreaseFontButton.setOnClickListener(v -> {
            decreaseFontSize();
            resetAutoHideTimer();
        });
        increaseFontButton.setOnClickListener(v -> {
            increaseFontSize();
            resetAutoHideTimer();
        });

        // Set up tap to show controls
        topicScrollView.setOnClickListener(v -> toggleFontControls());
        topicContent.setOnClickListener(v -> toggleFontControls());
    }

    /**
     * Show font controls and start auto-hide timer
     */
    private void showFontControls() {
        if (fontSizeControls != null) {
            fontSizeControls.setVisibility(View.VISIBLE);
            fontSizeControls.setAlpha(0f);
            fontSizeControls.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start();
        }
    }

    /**
     * Hide font controls with animation
     */
    private void hideFontControls() {
        if (fontSizeControls != null && fontSizeControls.getVisibility() == View.VISIBLE) {
            fontSizeControls.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(() -> {
                        if (fontSizeControls != null) {
                            fontSizeControls.setVisibility(View.GONE);
                        }
                    })
                    .start();
        }
    }

    /**
     * Toggle font controls visibility
     */
    private void toggleFontControls() {
        if (fontSizeControls != null) {
            if (fontSizeControls.getVisibility() == View.VISIBLE) {
                // If visible, hide immediately
                topicScrollView.removeCallbacks(hideControlsRunnable);
                hideFontControls();
            } else {
                // If hidden, show and start auto-hide timer
                showFontControls();
                resetAutoHideTimer();
            }
        }
    }

    /**
     * Reset the auto-hide timer
     */
    private void resetAutoHideTimer() {
        if (topicScrollView != null && hideControlsRunnable != null) {
            topicScrollView.removeCallbacks(hideControlsRunnable);
            topicScrollView.postDelayed(hideControlsRunnable, AUTO_HIDE_DELAY);
        }
    }

    /**
     * Show controls initially when content loads
     */
    private void showControlsInitially() {
        showFontControls();
        resetAutoHideTimer();
    }

    /**
     * Load the saved font size preference
     */
    private void loadFontSizePreference() {
        try {
            currentFontSize = getSharedPreferences("TopicPreferences", MODE_PRIVATE)
                    .getFloat(PREF_FONT_SIZE, DEFAULT_FONT_SIZE);
            // Ensure it's within bounds
            if (currentFontSize < MIN_FONT_SIZE) currentFontSize = MIN_FONT_SIZE;
            if (currentFontSize > MAX_FONT_SIZE) currentFontSize = MAX_FONT_SIZE;
        } catch (Exception e) {
            currentFontSize = DEFAULT_FONT_SIZE;
        }
    }

    /**
     * Save the font size preference
     */
    private void saveFontSizePreference() {
        try {
            getSharedPreferences("TopicPreferences", MODE_PRIVATE)
                    .edit()
                    .putFloat(PREF_FONT_SIZE, currentFontSize)
                    .apply();
        } catch (Exception ignored) {
        }
    }

    /**
     * Decrease the font size of the content text
     */
    private void decreaseFontSize() {
        if (currentFontSize > MIN_FONT_SIZE) {
            currentFontSize -= FONT_SIZE_STEP;
            if (currentFontSize < MIN_FONT_SIZE) {
                currentFontSize = MIN_FONT_SIZE;
            }
            updateContentFontSize();
            saveFontSizePreference();
        }
    }

    /**
     * Increase the font size of the content text
     */
    private void increaseFontSize() {
        if (currentFontSize < MAX_FONT_SIZE) {
            currentFontSize += FONT_SIZE_STEP;
            if (currentFontSize > MAX_FONT_SIZE) {
                currentFontSize = MAX_FONT_SIZE;
            }
            updateContentFontSize();
            saveFontSizePreference();
        }
    }

    /**
     * Update the content text view with the new font size
     */
    private void updateContentFontSize() {
        if (topicContent != null) {
            topicContent.setTextSize(currentFontSize);
        }
    }

    /**
     * Toggle bookmark: decide desired state, optimistically update UI immediately,
     * then call MainActivity to do the DB work (which updates in-memory list).
     */
    private void toggleBookmarkForCurrentTopic() {
        io.execute(() -> {
            AppDao dao = AppDatabase.getInstance(this).appDao();
            TopicEntity topic = dao.getTopicByRowId(rowid);
            if (topic == null) return;
            final String topicId = topic.topicId;
            final MainActivity main = MainActivity.getInstance();
            if (main == null) return;

            final boolean currentlyBookmarked = main.isTopicBookmarked(topicId);
            runOnUiThread(() -> setBookmarkIcon(!currentlyBookmarked));

            if (currentlyBookmarked) {
                main.removeBookmark(topicId);
            } else {
                String snippet = topic.content != null ? getSnippetPlain(topic.content) : "";
                BookmarkItem bi = new BookmarkItem(topicId, topic.title, snippet, System.currentTimeMillis());
                main.addBookmark(bi);
            }

            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            runOnUiThread(this::updateBookmarkIcon);
        });
    }

    private void updateBookmarkIcon() {
        final MainActivity main = MainActivity.getInstance();
        if (main != null) {
            io.execute(() -> {
                AppDao dao = AppDatabase.getInstance(this).appDao();
                TopicEntity t = dao.getTopicByRowId(rowid);
                if (t == null) return;
                final boolean bookmarked = main.isTopicBookmarked(t.topicId);
                runOnUiThread(() -> setBookmarkIcon(bookmarked));
            });
            return;
        }

        io.execute(() -> {
            AppDao dao = AppDatabase.getInstance(this).appDao();
            TopicEntity t = dao.getTopicByRowId(rowid);
            if (t == null) return;
            final boolean bookmarked = dao.getBookmarkByTopicId(t.topicId) != null;
            runOnUiThread(() -> setBookmarkIcon(bookmarked));
        });
    }

    private void setBookmarkIcon(boolean bookmarked) {
        if (bookmarkimg == null) return;
        if (bookmarked) {
            bookmarkimg.setImageResource(R.drawable.ic_bookmark_solid);
            try {
                bookmarkimg.setColorFilter(ContextCompat.getColor(this, R.color.primary_dark), android.graphics.PorterDuff.Mode.SRC_IN);
            } catch (Exception e) {
                bookmarkimg.clearColorFilter();
            }
        } else {
            bookmarkimg.setImageResource(R.drawable.ic_bookmark_tp);
            bookmarkimg.clearColorFilter();
        }
    }

    private void loadAndMarkTopic() {
        io.execute(() -> {
            AppDao dao = AppDatabase.getInstance(this).appDao();
            TopicEntity topic = dao.getTopicByRowId(rowid);
            if (topic == null) {
                runOnUiThread(this::finish);
                return;
            }

            long now = System.currentTimeMillis();
            if (!topic.completed) {
                dao.updateTopicCompletion(rowid, true, now);
                topic.completed = true;
                topic.lastViewed = now;
            } else {
                dao.updateTopicCompletion(rowid, true, now);
                topic.lastViewed = now;
            }

            final TopicEntity t = topic;
            runOnUiThread(() -> {
                setTitle(t.title);
                topicTitleView.setText(t.title);

                String rawContent = t.content != null ? t.content : "";
                
                // Preprocess content: Convert single \n to Markdown hard breaks (two spaces + \n)
                // This makes single newlines create actual line breaks in the rendered output
                rawContent = preprocessMarkdownLineBreaks(rawContent);

                // Render Markdown content using Markwon
                if (highlightTerm != null && !highlightTerm.trim().isEmpty()) {
                    // First render markdown, then apply highlighting
                    Spannable rendered = (Spannable) markwon.toMarkdown(rawContent);
                    SpannableStringBuilder ssb = new SpannableStringBuilder(rendered);
                    highlightOccurrences(ssb, highlightTerm);
                    topicContent.setText(ssb);
                    topicContent.post(() -> scrollToFirstOccurrence(ssb, highlightTerm));
                } else {
                    // Just render markdown
                    markwon.setMarkdown(topicContent, rawContent);
                }

                // Apply saved font size
                updateContentFontSize();

                // Ensure links work
                topicContent.setMovementMethod(LinkMovementMethod.getInstance());
                topicContent.setHighlightColor(Color.TRANSPARENT);

                // append images below content (if any) and add click-to-zoom
                appendImagesBelowContent(t);

                // legacy single topicImage load (keeps older UI)
                if (t.images != null && !t.images.isEmpty()) {
                    String[] imgs = t.images.split(",");
                    if (imgs.length > 0) {
                        String fname = imgs[0].trim();
                        if (!fname.isEmpty()) {
                            String resName = fname.contains(".") ? fname.substring(0, fname.lastIndexOf('.')) : fname;
                            int resId = getResources().getIdentifier(resName, "drawable", getPackageName());
                            if (resId != 0) {
                                topicImage.setImageResource(resId);
                                topicImage.setVisibility(View.VISIBLE);
                            } else topicImage.setVisibility(View.GONE);
                        }
                    } else topicImage.setVisibility(View.GONE);
                } else topicImage.setVisibility(View.GONE);

                updateBookmarkIcon();

                // Show font controls initially for 3 seconds
                showControlsInitially();

                MainActivity main = MainActivity.getInstance();
                if (main != null) {
                    String snippet = t.content != null ? getSnippetPlain(t.content) : "";
                    HistoryItem hi = new HistoryItem(t.topicId, t.title, snippet, "Viewed", 100, now);
                    main.recordTopicViewed(hi);
                    try { main.saveLastOpenedTopic(rowid); } catch (Exception ignored) {}
                }
            });
        });
    }

    /**
     * Parse custom content to SpannableStringBuilder and insert ImageSpans for inline <img:...> tags.
     * Also attaches a ClickableSpan to each inline image so tapping it opens a zoom dialog.
     */
    private SpannableStringBuilder parseCustomContent(String raw) {
        if (raw == null) raw = "";

        String s = raw.replace("\\r\\n", "\n")
                .replace("\\n\\n", "\n\n")
                .replace("\\n", "\n")
                .replace("\\r", "\n");

        s = s.replace("/<", "<")
                .replace("/>", ">")
                .replace("/&", "&")
                .replace("/\"", "\"")
                .replace("/'", "'");

        SpannableStringBuilder ssb = new SpannableStringBuilder();

        String startTag = "<boldin>";
        String endTag = "<boldout>";
        int pos = 0;
        while (true) {
            int idx = s.indexOf(startTag, pos);
            if (idx == -1) {
                ssb.append(s.substring(pos));
                break;
            }
            if (idx > pos) ssb.append(s.substring(pos, idx));
            int contentStart = idx + startTag.length();
            int endIdx = s.indexOf(endTag, contentStart);
            String boldText;
            if (endIdx == -1) {
                boldText = s.substring(contentStart);
                pos = s.length();
            } else {
                boldText = s.substring(contentStart, endIdx);
                pos = endIdx + endTag.length();
            }
            int spanStart = ssb.length();
            ssb.append(boldText);
            int spanEnd = ssb.length();
            if (spanEnd > spanStart) {
                ssb.setSpan(new StyleSpan(Typeface.BOLD), spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        // Find all occurrences of <italicin>...</italicout> and apply italic spans.
        String fullBefore = ssb.toString();
        Pattern pItal = Pattern.compile("<italicin>(.*?)<italicout>", Pattern.DOTALL);
        Matcher mItal = pItal.matcher(fullBefore);


        class ItalMatch { int start; int end; String inner; }
        List<ItalMatch> italMatches = new ArrayList<>();
        while (mItal.find()) {
            ItalMatch im = new ItalMatch();
            im.start = mItal.start();
            im.end = mItal.end();
            im.inner = mItal.group(1);
            italMatches.add(im);
        }
        // Replace tags from end -> start so indices remain correct
        for (int i = italMatches.size() - 1; i >= 0; i--) {
            ItalMatch im = italMatches.get(i);
            int start = im.start;
            int end = im.end;
            String inner = im.inner;

            // replace the tagged region with just the inner text
            ssb.replace(start, end, inner);
            // apply italic span
            if (inner.length() > 0) {
                ssb.setSpan(new StyleSpan(Typeface.ITALIC), start, start + inner.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        // Now handle inline <img:...> tags that may still be present
        String full = ssb.toString();
        Pattern p = Pattern.compile("<img:([^>]+)>", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(full);

        // collect matches
        class ImgMatch { int start; int end; String name; }
        List<ImgMatch> matches = new ArrayList<>();
        while (m.find()) {
            ImgMatch im = new ImgMatch();
            im.start = m.start();
            im.end = m.end();
            im.name = m.group(1).trim();
            matches.add(im);
        }

        // Replace from end -> start
        for (int i = matches.size() - 1; i >= 0; i--) {
            ImgMatch im = matches.get(i);
            int start = im.start;
            int end = im.end;
            String fname = im.name;

            // replace tag text with object replacement char
            ssb.replace(start, end, "\uFFFC");

            String resName = fname.contains(".") ? fname.substring(0, fname.lastIndexOf('.')) : fname;
            int resId = getResources().getIdentifier(resName, "drawable", getPackageName());
            Drawable d = null;
            if (resId != 0) d = ContextCompat.getDrawable(this, resId);

            if (d != null) {
                int maxW = topicContent != null && topicContent.getWidth() > 0
                        ? topicContent.getWidth()
                        : (getResources().getDisplayMetrics().widthPixels - dpToPx(32));

                int intrinsicW = d.getIntrinsicWidth();
                int intrinsicH = d.getIntrinsicHeight();
                if (intrinsicW <= 0 || intrinsicH <= 0) {
                    intrinsicW = maxW;
                    intrinsicH = maxW / 2;
                }
                int desiredW = Math.min(intrinsicW, maxW);
                int desiredH = (int) Math.round((desiredW / (float) intrinsicW) * intrinsicH);

                d.setBounds(0, 0, desiredW, desiredH);
                ImageSpan is = new ImageSpan(d, ImageSpan.ALIGN_BOTTOM);
                ssb.setSpan(is, start, start + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                // attach a ClickableSpan for zooming
                final int finalResId = resId;
                ClickableSpan clickable = new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        showImageFullscreenDialog(finalResId);
                    }
                };
                ssb.setSpan(clickable, start, start + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                ssb.replace(start, start + 1, "[image]");
            }
        }

        return ssb;
    }

    /**
     * Append images enumerated in topic.images below the text if inline images weren't used,
     * and make each appended image clickable to open the zoom dialog. Uses imageContainer when present,
     * otherwise appends directly after topicContent in its parent.
     */
    private void appendImagesBelowContent(TopicEntity topic) {
        if (topic.images == null || topic.images.trim().isEmpty()) return;

        String raw = topic.images.trim();
        String[] parts = raw.split("\\s*,\\s*");
        if (parts.length == 0) return;

        // prefer imageContainer if available
        ViewGroup container = imageContainer;
        if (container == null) {
            ViewGroup parent = (ViewGroup) topicContent.getParent();
            if (parent == null) return;
            // find index and create a simple linear container to hold appended images after content
            int insertIndex = -1;
            for (int i = 0; i < parent.getChildCount(); i++) {
                if (parent.getChildAt(i) == topicContent) { insertIndex = i + 1; break; }
            }
            if (insertIndex == -1) insertIndex = parent.getChildCount();
            // use parent itself, but we'll insert ImageViews at insertIndex
            for (String fname : parts) {
                if (fname == null || fname.trim().isEmpty()) continue;
                String resName = fname.contains(".") ? fname.substring(0, fname.lastIndexOf('.')) : fname;
                final int resId = getResources().getIdentifier(resName, "drawable", getPackageName());
                if (resId == 0) continue;
                Drawable d = ContextCompat.getDrawable(this, resId);
                if (d == null) continue;

                int maxW = topicContent.getWidth() > 0 ? topicContent.getWidth() : (getResources().getDisplayMetrics().widthPixels - dpToPx(32));
                int intrinsicW = d.getIntrinsicWidth() <= 0 ? maxW : d.getIntrinsicWidth();
                int desiredW = Math.min(intrinsicW, maxW);

                ImageView iv = new ImageView(this);
                iv.setAdjustViewBounds(true);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setImageDrawable(d);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, dpToPx(12), 0, dpToPx(12));
                iv.setLayoutParams(lp);
                iv.setOnClickListener(v -> showImageFullscreenDialog(resId));
                parent.addView(iv, insertIndex++);
            }
            return;
        }

        // if we have imageContainer (preferred)
        container.removeAllViews();
        for (String fname : parts) {
            if (fname == null || fname.trim().isEmpty()) continue;
            String resName = fname.contains(".") ? fname.substring(0, fname.lastIndexOf('.')) : fname;
            final int resId = getResources().getIdentifier(resName, "drawable", getPackageName());
            if (resId == 0) continue;

            ImageView iv = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dpToPx(12), 0, dpToPx(12));
            iv.setLayoutParams(lp);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageResource(resId);
            iv.setOnClickListener(v -> showImageFullscreenDialog(resId));
            container.addView(iv);
        }
    }

    /**
     * Show fullscreen transparent dialog that contains a PhotoView for pinch-zooming.
     */
    private void showImageFullscreenDialog(int drawableResId) {
        try {
            final Dialog dlg = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
            dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dlg.setContentView(R.layout.dialog_image_viewer);
            if (dlg.getWindow() != null) {
                dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
            PhotoView photo = dlg.findViewById(R.id.fullscreenPhoto);
            photo.setImageResource(drawableResId);
            photo.setMaximumScale(4.0f);
            photo.setMediumScale(2.0f);
            photo.setOnClickListener(v -> dlg.dismiss());
            dlg.show();
        } catch (Exception e) {
            // fallback: simple dialog with ImageView
            Dialog fallback = new Dialog(this);
            ImageView iv = new ImageView(this);
            iv.setImageResource(drawableResId);
            fallback.setContentView(iv);
            fallback.show();
        }
    }

    private int dpToPx(int dp) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return Math.round(dp * dm.density);
    }

    private void highlightOccurrences(SpannableStringBuilder ssb, String term) {
        if (term == null || term.trim().isEmpty()) return;
        String s = ssb.toString().toLowerCase(Locale.ROOT);
        String lower = term.toLowerCase(Locale.ROOT);
        int idx = s.indexOf(lower);
        int color = ContextCompat.getColor(this, android.R.color.holo_orange_light);
        while (idx >= 0) {
            int start = idx;
            int end = idx + lower.length();
            ssb.setSpan(new BackgroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            idx = s.indexOf(lower, end);
        }
    }

    private void scrollToFirstOccurrence(SpannableStringBuilder ssb, String term) {
        String s = ssb.toString().toLowerCase(Locale.ROOT);
        String lower = term.toLowerCase(Locale.ROOT);
        int idx = s.indexOf(lower);
        if (idx >= 0) {
            int start = idx;
            topicContent.post(() -> {
                if (topicContent.getLayout() == null) return;
                int line = topicContent.getLayout().getLineForOffset(start);
                int y = topicContent.getLayout().getLineTop(line);
                topicScrollView.smoothScrollTo(0, y);
            });
        }
    }

    private String getSnippetPlain(String content) {
        if (content == null) return "";
        // Strip markdown first, then convert any HTML
        String stripped = stripMarkdown(content);
        String plain = Html.fromHtml(stripped, Html.FROM_HTML_MODE_LEGACY).toString().trim();
        if (plain.length() <= 120) return plain;
        return plain.substring(0, 120) + "...";
    }

    /**
     * Preprocess Markdown content to convert single newlines to hard breaks.
     * In standard Markdown, you need two spaces + newline OR double newline for breaks.
     * This method converts single \n to double-space + \n (hard break in Markdown).
     */
    private String preprocessMarkdownLineBreaks(String content) {
        if (content == null) return "";
        
        // Replace single newlines with two spaces + newline (Markdown hard break)
        // But preserve double newlines (paragraph breaks)
        // Strategy: First protect double newlines, then convert singles, then restore doubles
        
        String placeholder = "<<<PARAGRAPH_BREAK>>>";
        
        // Step 1: Replace \n\n with placeholder
        String result = content.replace("\n\n", placeholder);
        
        // Step 2: Replace remaining single \n with two spaces + \n (hard break)
        result = result.replace("\n", "  \n");
        
        // Step 3: Restore paragraph breaks
        result = result.replace(placeholder, "\n\n");
        
        return result;
    }

    /**
     * Strip common Markdown formatting characters to show plain text in snippets.
     */
    private String stripMarkdown(String input) {
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up callbacks to prevent memory leaks
        if (topicScrollView != null && hideControlsRunnable != null) {
            topicScrollView.removeCallbacks(hideControlsRunnable);
        }
    }
}
