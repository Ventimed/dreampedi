package com.dreampediatrics.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SearchUtil {

    public static List<SearchResultWrapper> performSearch(String query, Map<String, SearchResult> database) {
        List<SearchResultWrapper> results = new ArrayList<>();

        if (query == null || query.trim().isEmpty()) {
            return results;
        }

        String lowerQuery = query.toLowerCase().trim();

        for (Map.Entry<String, SearchResult> entry : database.entrySet()) {
            String keyword = entry.getKey();
            SearchResult result = entry.getValue();

            if (keyword.toLowerCase().contains(lowerQuery) ||
                    result.getPreview().toLowerCase().contains(lowerQuery) ||
                    result.getChapter().toLowerCase().contains(lowerQuery) ||
                    result.getSection().toLowerCase().contains(lowerQuery)) {

                results.add(new SearchResultWrapper(keyword, result));
            }
        }

        return results;
    }

    public static String highlightSearchTerm(String text, String term) {
        if (term == null || term.trim().isEmpty()) {
            return text;
        }

        String regex = "(?i)(" + term.trim() + ")";
        return text.replaceAll(regex, "<b style='background-color:#FFF8E1; padding:2px 4px; border-radius:4px;'>$1</b>");
    }

    public static class SearchResultWrapper {
        private String keyword;
        private SearchResult result;

        public SearchResultWrapper(String keyword, SearchResult result) {
            this.keyword = keyword;
            this.result = result;
        }

        public String getKeyword() { return keyword; }
        public SearchResult getResult() { return result; }
    }
}