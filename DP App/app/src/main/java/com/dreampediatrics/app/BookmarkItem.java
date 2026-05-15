package com.dreampediatrics.app;

public class BookmarkItem {
    private String topicId;
    private String title;
    private String snippet;
    private long timestamp;

    public BookmarkItem(String topicId, String title, String snippet, long timestamp) {
        this.topicId = topicId;
        this.title = title;
        this.snippet = snippet;
        this.timestamp = timestamp;
    }

    public String getTopicId() { return topicId; }
    public String getTitle() { return title; }
    public String getSnippet() { return snippet; }
    public long getTimestamp() { return timestamp; }
}
