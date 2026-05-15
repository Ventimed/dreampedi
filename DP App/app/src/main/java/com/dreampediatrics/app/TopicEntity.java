    package com.dreampediatrics.app;

    import androidx.room.ColumnInfo;
    import androidx.room.Entity;
    import androidx.room.PrimaryKey;

    @Entity(tableName = "topics")
    public class TopicEntity {
        @PrimaryKey(autoGenerate = true)
        @ColumnInfo(name = "rowid")
        public long rowid;

        public String topicId;
        public String chapterId;
        public int number;
        public String title;
        public String description;  // Short curated description for card views
        public String content;
        public String images;

        // 0 = not completed, 1 = completed
        public boolean completed; // true if user has opened/viewed the topic
        public long lastViewed;   // timestamp in millis when last viewed

        public TopicEntity(String topicId, String chapterId, int number, String title, String description, String content, String images, boolean completed, long lastViewed) {
            this.topicId = topicId;
            this.chapterId = chapterId;
            this.number = number;
            this.title = title;
            this.description = description;
            this.content = content;
            this.images = images;
            this.completed = completed;
            this.lastViewed = lastViewed;
        }
    }
