package com.dreampediatrics.app;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface AppDao {
    // Chapters
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertChapters(List<ChapterEntity> chapters);

    @Query("SELECT * FROM chapters ORDER BY number")
    List<ChapterEntity> getAllChapters();

    @Query("SELECT rowid AS rowid, chapterId, title, substr(content, 1, 300) AS snippet, completed FROM topics WHERE chapterId = :chapterId ORDER BY rowid")
    List<TopicSummary> getTopicSummariesForChapter(String chapterId);

    @Query("SELECT * FROM chapters WHERE chapterId = :chapterId LIMIT 1")
    ChapterEntity getChapterById(String chapterId);

    @Query("DELETE FROM chapters")
    void deleteAllChapters();

    // Topics
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTopics(List<TopicEntity> topics);

    @Query("SELECT * FROM topics WHERE chapterId = :chapterId ORDER BY number")
    List<TopicEntity> getTopicsForChapter(String chapterId);

    @Query("SELECT * FROM topics WHERE rowid = :rowid LIMIT 1")
    TopicEntity getTopicByRowId(long rowid);

    // Update topic completion
    @Query("UPDATE topics SET completed = :completed, lastViewed = :lastViewed WHERE rowid = :rowid")
    void updateTopicCompletion(long rowid, boolean completed, long lastViewed);

    // Counts and progress for a chapter
    @Query("SELECT COUNT(*) FROM topics WHERE chapterId = :chapterId")
    int getTopicCountForChapter(String chapterId);

    @Query("SELECT COUNT(*) FROM topics WHERE chapterId = :chapterId AND completed = 1")
    int getCompletedCountForChapter(String chapterId);

    @Query("DELETE FROM topics")
    void deleteAllTopics();

    // --- utility to lookup topics by topicId (if not already present) ---
    @Query("SELECT * FROM topics WHERE topicId = :topicId LIMIT 1")
    TopicEntity getTopicByTopicId(String topicId);

    // FTS search: returns matching topics (join on topic_fts)
    /**
     * FTS-backed search — returns TopicEntity rows whose title/content match the FTS query.
     * The caller should pass a valid FTS MATCH expression; for simple prefix matching we use "term*".
     */
    @Query("SELECT topics.* FROM topics JOIN TopicFts ON topics.rowid = TopicFts.rowid WHERE TopicFts MATCH :match")
    List<TopicEntity> searchTopics(String match);

    // --- Bookmarks ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertBookmark(BookmarkEntity bookmark);

    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    List<BookmarkEntity> getAllBookmarks();

    @Query("SELECT * FROM bookmarks WHERE topicId = :topicId LIMIT 1")
    BookmarkEntity getBookmarkByTopicId(String topicId);

    @Query("DELETE FROM bookmarks WHERE topicId = :topicId")
    void deleteBookmarkByTopicId(String topicId);

    @Query("DELETE FROM bookmarks")
    void deleteAllBookmarks();

    // --- History ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertHistory(HistoryEntity history); // returns row id (if you use Room return value)

    @Update
    void updateHistory(HistoryEntity history);

    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT :limit")
    List<HistoryEntity> getHistoryLimited(int limit);

    @Query("SELECT * FROM history WHERE topicId = :topicId LIMIT 1")
    HistoryEntity getHistoryByTopicId(String topicId);

    @Query("DELETE FROM history WHERE topicId = :topicId")
    void deleteHistoryByTopicId(String topicId);

    @Query("DELETE FROM history")
    void deleteAllHistory();
}