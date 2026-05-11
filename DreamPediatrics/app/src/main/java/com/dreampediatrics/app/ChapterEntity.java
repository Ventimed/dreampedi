package com.dreampediatrics.app;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "chapters")
public class ChapterEntity {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid")
    public long rowid; // internal sqlite rowid

    public String chapterId; // original JSON id (e.g. "ch01")
    public int number;
    public String title;
    public String description;

    public ChapterEntity(String chapterId, int number, String title, String description) {
        this.chapterId = chapterId;
        this.number = number;
        this.title = title;
        this.description = description;
    }
}
