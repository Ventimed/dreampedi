package com.dreampediatrics.app;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Fts4;

/**
 * Full-text search table for TopicEntity (external content).
 * We index title and content. contentEntity points to TopicEntity so we can use MATCH.
 */
@Fts4(contentEntity = TopicEntity.class)
@Entity(tableName = "TopicFts")
public class TopicFts {
    @ColumnInfo(name = "title")
    public String title;

    @ColumnInfo(name = "content")
    public String content;
}
