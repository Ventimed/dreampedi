package com.dreampediatrics.app;

public class SearchResult {
    private String chapter;
    private String section;
    private String preview;
    private String page;

    public SearchResult(String chapter, String section, String preview, String page) {
        this.chapter = chapter;
        this.section = section;
        this.preview = preview;
        this.page = page;
    }

    // Getters
    public String getChapter() { return chapter; }
    public String getSection() { return section; }
    public String getPreview() { return preview; }
    public String getPage() { return page; }
}
