package com.dreampediatrics.app;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * RoomDatabase including chapters, topics and FTS.
 * Increase version if you change schema.
 */
@Database(entities = {
        ChapterEntity.class,
        TopicEntity.class,
        BookmarkEntity.class,
        HistoryEntity.class,
        TopicFts.class
      },
      version = 1,
        exportSchema = false)

public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    public abstract AppDao appDao();

    public static AppDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "dreampedi_db")
                            .fallbackToDestructiveMigration() // change if you need migration instead
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
