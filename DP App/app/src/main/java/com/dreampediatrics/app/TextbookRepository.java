package com.dreampediatrics.app;

import android.content.Context;
import android.content.res.AssetManager;
import android.text.Html;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class TextbookRepository {
    private static final String TAG = "TextbookRepo";
    private static TextbookRepository INSTANCE;
    private final AppDatabase db;
    private final Executor io = Executors.newSingleThreadExecutor();

    private TextbookRepository(Context ctx) {
        this.db = AppDatabase.getInstance(ctx);
    }

    public static synchronized TextbookRepository getInstance(Context ctx) {
        if (INSTANCE == null) INSTANCE = new TextbookRepository(ctx.getApplicationContext());
        return INSTANCE;
    }

    /**
     * Populate DB from a JSON string (used after decrypting downloaded JSON).
     * Runs on the repository's internal executor.
     */
    public void populateFromJsonString(Context ctx, String json) {
        if (json == null) return;
        io.execute(() -> {
            try {
                // The JSON can be either an array directly or an object with "chapters" key
                JSONArray chaptersArray;
                
                // Try to parse as array first
                if (json.trim().startsWith("[")) {
                    chaptersArray = new JSONArray(json);
                } else {
                    // Parse as object with "chapters" key
                    JSONObject root = new JSONObject(json);
                    chaptersArray = root.optJSONArray("chapters");
                }
                
                if (chaptersArray == null || chaptersArray.length() == 0) {
                    Log.e(TAG, "No chapters found in JSON");
                    return;
                }

                List<ChapterEntity> chapters = new ArrayList<>();
                List<TopicEntity> topics = new ArrayList<>();

                for (int i = 0; i < chaptersArray.length(); i++) {
                    JSONObject ch = chaptersArray.getJSONObject(i);
                    String chapterId = ch.optString("id", "ch" + (i + 1));
                    int number = ch.optInt("number", i + 1);
                    String title = ch.optString("title", "Untitled");
                    String description = ch.optString("description", "");

                    ChapterEntity chapterEntity = new ChapterEntity(chapterId, number, title, description);
                    chapters.add(chapterEntity);

                    JSONArray topicsArray = ch.optJSONArray("topics");
                    if (topicsArray != null) {
                        for (int t = 0; t < topicsArray.length(); t++) {
                            JSONObject top = topicsArray.getJSONObject(t);
                            String topicId = top.optString("id", chapterId + "_t" + (t + 1));
                            int tnum = top.optInt("number", t + 1);
                            String ttitle = top.optString("title", "Topic " + (t + 1));
                            String contentHtml = top.optString("content", "");
                            JSONArray imagesArr = top.optJSONArray("images");
                            String imagesCsv = null;
                            if (imagesArr != null && imagesArr.length() > 0) {
                                StringBuilder sb = new StringBuilder();
                                for (int k = 0; k < imagesArr.length(); k++) {
                                    if (k > 0) sb.append(",");
                                    sb.append(imagesArr.optString(k));
                                }
                                imagesCsv = sb.toString();
                            }
                            TopicEntity topicEntity = new TopicEntity(topicId, chapterId, tnum, ttitle, contentHtml, imagesCsv, false, 0L);
                            topics.add(topicEntity);
                        }
                    }
                }

                // Insert into DB in a single transaction if you have DAO methods for that (or just call insert lists)
                db.appDao().insertChapters(chapters);
                db.appDao().insertTopics(topics);

                Log.d(TAG, "Populated DB from decrypted JSON: chapters=" + chapters.size() + " topics=" + topics.size());
            } catch (Exception ex) {
                Log.e(TAG, "populateFromJsonString error", ex);
            }
        });
    }

}
