package com.dreampediatrics.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.google.android.flexbox.FlexboxLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * SearchFragment
 * - Waits for 3+ characters before firing search
 * - Runs Room FTS on background thread, resolves chapter lookup on background thread
 * - Shows topic title + snippet (with highlighted matches)
 * - Clicking result opens TopicActivity with EXTRA_HIGHLIGHT_TERM so TopicActivity
 *   scrolls & highlights the match.
 */
public class SearchFragment extends Fragment {
    private EditText searchInput;
    private LinearLayout searchResultsContainer;
    private View emptyState;
    private FlexboxLayout recentContainer;

    private final Executor bgExecutor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);

        searchInput = view.findViewById(R.id.searchInput);
        searchResultsContainer = view.findViewById(R.id.searchResultsContainer);
        emptyState = view.findViewById(R.id.emptyState);
        recentContainer = view.findViewById(R.id.recentContainer);

        setupSearch();
        showEmptyState();

        return view;
    }

    private void setupSearch() {
        searchInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) showRecentSearches();
        });
        searchInput.setOnClickListener(v -> showRecentSearches());

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString().trim();
                if (TextUtils.isEmpty(q)) {
                    showEmptyState();
                } else if (q.length() < 3) {
                    showTypeMoreChars(q.length());
                    highlightRecentSearchChips(); // Add highlighting as user types
                } else {
                    performSearch(q.toLowerCase());
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean handled = false;
            String q = searchInput.getText().toString().trim();
            if ((actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE)
                    || (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                if (!q.isEmpty() && q.length() >= 3) {
                    MainActivity ma = (MainActivity) getActivity();
                    if (ma != null) ma.saveRecentSearch(q);
                    performSearch(q.toLowerCase());
                    handled = true;
                }
            }
            return handled;
        });
    }

    private void showRecentSearches() {
        if (!isAdded()) return;
        recentContainer.removeAllViews();
        MainActivity ma = (MainActivity)getActivity();
        if (ma == null) return;

        List<String> recent = ma.getRecentSearches();
        if (recent == null || recent.isEmpty()) {
            recentContainer.setVisibility(View.GONE);
            return;
        }

        recentContainer.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(getContext());

        for (String q : recent) {
            View chip = inflater.inflate(R.layout.item_search_chip, recentContainer, false);
            TextView tv = chip.findViewById(R.id.chipText);
            tv.setText(q);
            chip.setOnClickListener(v -> {
                searchInput.setText(q);
                searchInput.setSelection(q.length());
                performSearch(q.toLowerCase());
            });

            com.google.android.flexbox.FlexboxLayout.LayoutParams lp =
                    new com.google.android.flexbox.FlexboxLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            int m = (int) (6 * getResources().getDisplayMetrics().density);
            lp.setMargins(m, m, m, m);
            recentContainer.addView(chip, lp);
        }
        // Apply highlighting to chips based on current input
        highlightRecentSearchChips();
    }

    // Enhanced highlighting for recent search chips
    private void highlightRecentSearchChips() {
        if (!isAdded()) return;

        String currentQuery = searchInput.getText().toString().trim().toLowerCase();
        if (currentQuery.length() < 1) return;

        // Highlight matching text in recent search chips
        for (int i = 0; i < recentContainer.getChildCount(); i++) {
            View chipView = recentContainer.getChildAt(i);
            TextView chipText = chipView.findViewById(R.id.chipText);
            if (chipText != null) {
                String originalText = chipText.getTag() != null ?
                        chipText.getTag().toString() : chipText.getText().toString();

                if (originalText.toLowerCase().contains(currentQuery)) {
                    String highlighted = highlightQueryInHtml(originalText, currentQuery);
                    chipText.setText(Html.fromHtml(highlighted, Html.FROM_HTML_MODE_COMPACT));
                } else {
                    chipText.setText(originalText);
                }
            }
        }
    }

    /**
     * Perform the FTS query off the UI thread. Build a snippet (context window) around the first
     * occurrence; highlight occurrences in that snippet, and show topic title + snippet in the UI.
     */
    private void performSearch(final String query) {
        if (!isAdded()) return;

        searchResultsContainer.removeAllViews();

        if (TextUtils.isEmpty(query)) {
            showEmptyState();
            return;
        }

        if (query.length() < 3) {
            showTypeMoreChars(query.length());
            return;
        }

        emptyState.setVisibility(View.GONE);
        recentContainer.setVisibility(View.GONE);

        bgExecutor.execute(() -> {
            AppDao dao = AppDatabase.getInstance(requireContext()).appDao();
            String ftsQuery = query + "*"; // prefix match

            List<TopicEntity> topics;
            try {
                topics = dao.searchTopics(ftsQuery);
            } catch (Exception e) {
                topics = null;
            }

            final List<SearchResultRow> rows = new ArrayList<>();
            if (topics != null && !topics.isEmpty()) {
                for (TopicEntity t : topics) {
                    // prepare snippet: find first occurrence and take context window
                    String content = t.content != null ? Html.fromHtml(t.content, Html.FROM_HTML_MODE_LEGACY).toString() : "";
                    String snippet = buildSnippetAroundMatch(content, query, 60, 140);
                    rows.add(new SearchResultRow(t, snippet));
                }
            }

            // Post UI update
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;

                if (rows.isEmpty()) {
                    showNoResults(query);
                    return;
                }

                LayoutInflater inflater = LayoutInflater.from(getContext());
                for (SearchResultRow row : rows) {
                    final TopicEntity topic = row.topic;
                    final String snippet = row.snippet;
                    final String highlightedSnippet = highlightQueryInHtml(snippet, query);
                    final String thisQuery = query;

                    View resultView = inflater.inflate(R.layout.item_search_result, searchResultsContainer, false);
                    TextView chapterView = resultView.findViewById(R.id.resultChapter);
                    TextView sectionView = resultView.findViewById(R.id.resultSection);
                    TextView previewView = resultView.findViewById(R.id.resultPreview);
                    CardView cardView = (CardView) resultView;

                    // Per request: first line should be topic title (replace previous chapter title)
                    chapterView.setText(topic.title != null ? topic.title : "(Untitled)");

                    // second line: show short context info (chapter title or small label)
                    ChapterEntity ch = null;
                    try {
                        ch = AppDatabase.getInstance(requireContext()).appDao().getChapterById(topic.chapterId);
                    } catch (Exception ex) {
                        ch = null;
                    }

                    // preview: snippet containing match only (with highlighted keyword)
                    previewView.setText(Html.fromHtml(highlightedSnippet, Html.FROM_HTML_MODE_COMPACT));

                    // Click -> save recent search and open TopicActivity with highlight term
                    cardView.setOnClickListener(v -> {
                        MainActivity ma = (MainActivity) getActivity();
                        if (ma != null) ma.saveRecentSearch(thisQuery);

                        Intent intent = new Intent(getActivity(), TopicActivity.class);
                        intent.putExtra(TopicActivity.EXTRA_TOPIC_ROWID, topic.rowid);
                        intent.putExtra(TopicActivity.EXTRA_HIGHLIGHT_TERM, thisQuery);
                        startActivity(intent);
                    });

                    searchResultsContainer.addView(resultView);
                }
            });
        });
    }

    // build a small snippet around first match; returns trimmed snippet (with ellipses if trimmed)
    private String buildSnippetAroundMatch(String text, String query, int before, int after) {
        if (TextUtils.isEmpty(text)) return "";
        String lower = text.toLowerCase();
        String q = query.toLowerCase();
        int idx = lower.indexOf(q);
        if (idx < 0) {
            // no match found -> return beginning truncated
            return text.length() <= before + after ? text : text.substring(0, before + after) + "...";
        }
        int start = Math.max(0, idx - before);
        int end = Math.min(text.length(), idx + q.length() + after);
        String prefix = start > 0 ? "..." : "";
        String suffix = end < text.length() ? "..." : "";
        return prefix + text.substring(start, end).trim() + suffix;
    }

    // highlight query in snippet using inline HTML (yellow background + bold)
    private String highlightQueryInHtml(String snippet, String query) {
        if (TextUtils.isEmpty(snippet) || TextUtils.isEmpty(query)) return snippet;
        try {
            String regex = "(?i)(" + java.util.regex.Pattern.quote(query) + ")";
            String replacement = "<span style='background-color:#FFEB3B; color:#000000; font-weight:bold; padding:1px 2px; border-radius:2px;'>$1</span>";
            return snippet.replaceAll(regex, replacement);
        } catch (Exception e) {
            return snippet;
        }
    }

    private String highlightQueryInText(String text, String query) {
        // fallback if needed for non-HTML usage
        return highlightQueryInHtml(text, query);
    }

    private void showTypeMoreChars(int typed) {
        if (!isAdded()) return;
        emptyState.setVisibility(View.VISIBLE);
        TextView emptyTitle = emptyState.findViewById(R.id.emptyTitle);
        TextView emptyMessage = emptyState.findViewById(R.id.emptyMessage);
        emptyTitle.setText("Type more characters");
        emptyMessage.setText("Please type at least 3 characters to start searching (currently: " + typed + ")");
        recentContainer.setVisibility(View.VISIBLE);
        showRecentSearches();
    }

    private void showEmptyState() {
        if (!isAdded()) return;
        emptyState.setVisibility(View.VISIBLE);
        TextView emptyTitle = emptyState.findViewById(R.id.emptyTitle);
        TextView emptyMessage = emptyState.findViewById(R.id.emptyMessage);
        emptyTitle.setText("Advanced Search");
        emptyMessage.setText("Enter keywords to search topic titles and content.");
        recentContainer.setVisibility(View.VISIBLE);
        showRecentSearches();
    }

    private void showNoResults(String query) {
        if (!isAdded()) return;
        View noResultsView = LayoutInflater.from(getContext()).inflate(R.layout.empty_state, searchResultsContainer, false);
        TextView emptyTitle = noResultsView.findViewById(R.id.emptyTitle);
        TextView emptyMessage = noResultsView.findViewById(R.id.emptyMessage);
        emptyTitle.setText("No Results Found");
        emptyMessage.setText("No content found for \"" + query + "\". Try different keywords or check spelling.");
        searchResultsContainer.addView(noResultsView);
    }

    // simple holder
    private static class SearchResultRow {
        final TopicEntity topic;
        final String snippet;

        SearchResultRow(TopicEntity topic, String snippet) {
            this.topic = topic;
            this.snippet = snippet;
        }
    }
}
