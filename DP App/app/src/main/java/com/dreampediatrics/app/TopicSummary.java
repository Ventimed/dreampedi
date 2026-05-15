package com.dreampediatrics.app;

/**
 * Lightweight projection used to display the chapter's topic list quickly.
 * Room will map the SQL columns into these public fields.
 */
public class TopicSummary {
    public long rowid;         // maps to sqlite rowid
    public String chapterId;
    public String title;
    public String snippet;     // substring(content,1,300) returned by query
    public boolean completed;
}
