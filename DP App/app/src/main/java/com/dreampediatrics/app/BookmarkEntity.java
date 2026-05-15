package com.dreampediatrics.app;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Bookmark stored in Room.
 * Primary key is topicId (string) to prevent duplicates.
 */
@Entity(tableName = "bookmarks")
public class BookmarkEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "topicId")
    public String topicId;

    public String title;
    public String snippet;
    public long timestamp;

    public BookmarkEntity(@NonNull String topicId, String title, String snippet, long timestamp) {
        this.topicId = topicId;
        this.title = title;
        this.snippet = snippet;
        this.timestamp = timestamp;
    }
}
