package com.dreampediatrics.app;

public class HistoryItem {
    private String topicId;
    private String title;
    private String snippet;
    private String info;
    private int progress;
    private long timestamp;

    public HistoryItem(String topicid, String title, String snippet, String info, int progress, long timestamp) {
        this.topicId = topicid;
        this.title = title;
        this.snippet = snippet;
        this.info = info;
        this.progress = progress;
        this.timestamp = timestamp;
    }

    public String getTopicId() { return topicId; }
    public String getTitle() { return title; }
    public String getSnippet() { return snippet; }
    public String getInfo() { return info; }
    public int getProgress() { return progress; }
    public long getTimestamp() { return timestamp; }
}
