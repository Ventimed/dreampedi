# Future Enhancements - Reading Status Implementation

## Overview
This document describes how to implement the "Reading" status for topics that are currently in progress, including the yellow stroke indicator shown in the design mockups.

## Current Implementation
Currently, topics have two states:
- **New** - Not started (default stroke, white number background)
- **Done** - Completed (default stroke, green number background)

## Target Implementation
Add a third state:
- **Reading** - In progress (yellow stroke, white number background, "Reading" badge)

---

## Backend Changes Required

### 1. Database Schema Update
Add a field to track reading progress in the `TopicEntity` or create a separate progress tracking table:

```java
// Option 1: Add to TopicEntity
@Entity(tableName = "topics")
public class TopicEntity {
    // ... existing fields ...
    
    @ColumnInfo(name = "reading_status")
    public String readingStatus; // "new", "reading", "completed"
    
    @ColumnInfo(name = "last_read_timestamp")
    public long lastReadTimestamp;
    
    @ColumnInfo(name = "reading_progress_percent")
    public int readingProgressPercent; // 0-100
}
```

### 2. Update TopicSummary
Add reading status to the projection:

```java
public class TopicSummary {
    public long rowid;
    public String title;
    public String description;
    public String snippet;
    public boolean completed;
    public String readingStatus; // NEW FIELD
    public int readingProgressPercent; // NEW FIELD
}
```

### 3. Update DAO Query
Modify the query to include reading status:

```java
@Query("SELECT rowid, title, description, snippet, completed, reading_status as readingStatus, " +
       "reading_progress_percent as readingProgressPercent " +
       "FROM topics WHERE chapter_id = :chapterId ORDER BY rowid ASC")
List<TopicSummary> getTopicSummariesForChapter(String chapterId);
```

---

## Frontend Changes Required

### 1. Update ChapterActivity.java

Replace the current status logic with:

```java
// Update card stroke and status based on reading state
if (s.completed) {
    // Completed topic - green background for number, "Done" status
    topicNumberContainer.setBackgroundResource(R.drawable.bg_topic_number_done);
    topicNumber.setTextColor(ContextCompat.getColor(this, R.color.badge_done_fg));
    cardView.setStrokeColor(ContextCompat.getColor(this, R.color.outline));
    
    if (topicStatus != null) {
        topicStatus.setVisibility(View.VISIBLE);
        topicStatus.setText("Done");
        topicStatus.setBackgroundResource(R.drawable.bg_status_done);
        topicStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_done_fg));
    }
} else if ("reading".equals(s.readingStatus)) {
    // In progress - yellow stroke, white number background, "Reading" badge
    topicNumberContainer.setBackgroundResource(R.drawable.bg_topic_number);
    topicNumber.setTextColor(ContextCompat.getColor(this, R.color.on_surface));
    cardView.setStrokeColor(ContextCompat.getColor(this, R.color.stroke_in_progress)); // YELLOW STROKE
    
    if (topicStatus != null) {
        topicStatus.setVisibility(View.VISIBLE);
        topicStatus.setText("Reading");
        topicStatus.setBackgroundResource(R.drawable.bg_status_reading);
        topicStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_warn_fg));
    }
} else {
    // New topic - default stroke, white number background, "New" badge
    topicNumberContainer.setBackgroundResource(R.drawable.bg_topic_number);
    topicNumber.setTextColor(ContextCompat.getColor(this, R.color.on_surface));
    cardView.setStrokeColor(ContextCompat.getColor(this, R.color.outline));
    
    if (topicStatus != null) {
        topicStatus.setVisibility(View.VISIBLE);
        topicStatus.setText("New");
        topicStatus.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
        topicStatus.setBackgroundResource(R.drawable.bg_status_new);
    }
}
```

### 2. Update TopicActivity.java

Add logic to mark a topic as "reading" when the user opens it:

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // ... existing code ...
    
    // Mark topic as "reading" if not completed
    markTopicAsReading();
}

private void markTopicAsReading() {
    io.execute(() -> {
        AppDao dao = AppDatabase.getInstance(this).appDao();
        TopicEntity topic = dao.getTopicByRowId(topicRowId);
        
        if (topic != null && !topic.completed) {
            topic.readingStatus = "reading";
            topic.lastReadTimestamp = System.currentTimeMillis();
            dao.updateTopic(topic);
        }
    });
}
```

### 3. Update Progress Tracking

When marking a topic as completed, update the status:

```java
private void markCompleted() {
    io.execute(() -> {
        AppDao dao = AppDatabase.getInstance(this).appDao();
        TopicEntity topic = dao.getTopicByRowId(topicRowId);
        
        if (topic != null) {
            topic.completed = true;
            topic.readingStatus = "completed";
            topic.readingProgressPercent = 100;
            dao.updateTopic(topic);
        }
        
        // ... rest of existing code ...
    });
}
```

---

## Visual States Summary

| State | Number BG | Number Text | Card Stroke | Badge Text | Badge BG |
|-------|-----------|-------------|-------------|------------|----------|
| New | White | Dark | Light Gray | "New" | Transparent |
| Reading | White | Dark | **Yellow** | "Reading" | Light Amber |
| Done | Light Green | Dark Green | Light Gray | "Done" | Light Green |

---

## Testing Checklist

When implementing this feature, test:

- [ ] New topics show "New" badge with default stroke
- [ ] Opening a topic marks it as "Reading"
- [ ] "Reading" topics show yellow stroke and "Reading" badge
- [ ] Completing a topic changes status to "Done"
- [ ] "Done" topics show green number background and "Done" badge
- [ ] Reopening a completed topic doesn't change its status
- [ ] Status persists across app restarts
- [ ] Chapter progress bar updates correctly with all three states

---

## Additional Enhancements

### Reading Progress Indicator
Consider adding a subtle progress indicator for "Reading" topics:

```xml
<!-- Add to item_topic.xml inside the topic number container -->
<ProgressBar
    android:id="@+id/topicReadingProgress"
    style="?android:attr/progressBarStyleHorizontal"
    android:layout_width="match_parent"
    android:layout_height="3dp"
    android:layout_gravity="bottom"
    android:progressTint="@color/progress_fill"
    android:visibility="gone" />
```

### Time Estimate Population
If time estimates are available in the database:

```java
// In ChapterActivity.java
if (s.estimatedMinutes > 0) {
    topicTime.setVisibility(View.VISIBLE);
    topicTime.setText("⏱ " + s.estimatedMinutes + " min");
} else {
    topicTime.setVisibility(View.GONE);
}
```

---

## Migration Strategy

1. **Phase 1:** Add database fields (with default values for existing data)
2. **Phase 2:** Update DAO queries and models
3. **Phase 3:** Implement reading status tracking in TopicActivity
4. **Phase 4:** Update UI in ChapterActivity to show reading status
5. **Phase 5:** Test thoroughly with existing and new data
6. **Phase 6:** Deploy and monitor

---

## Notes

- All drawable resources and colors are already created and ready to use
- The yellow stroke color (`stroke_in_progress`) is already defined in `colors.xml`
- The "Reading" badge background (`bg_status_reading.xml`) is already created
- Only backend tracking and UI logic updates are needed
