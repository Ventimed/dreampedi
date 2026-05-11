package com.dreampediatrics.app;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * History row stored in Room.
 * Auto-generated primary key `id` keeps order and uniqueness.
 * We also store the topicId so we can dedupe/move existing history entries by topic.
 */
@Entity(tableName = "history")
public class HistoryEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "topicId")
    public String topicId;

    public String title;
    public String snippet;
    public String info;
    public int progress;
    public long timestamp;

    public HistoryEntity(String topicId, String title, String snippet, String info, int progress, long timestamp) {
        this.topicId = topicId;
        this.title = title;
        this.snippet = snippet;
        this.info = info;
        this.progress = progress;
        this.timestamp = timestamp;
    }
}
