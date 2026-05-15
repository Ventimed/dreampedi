package com.dreampediatrics.app;

public class ChapterItem {
    private String chapterId;    // e.g. "ch01" — helpful when opening ChapterActivity
    private String title;
    private String subtitle;     // description
    private String sectionInfo;  // e.g. "5 Topics"
    private int progress;
    private boolean hasUpdate;
    private boolean isEssential;
    private int chapterNumber;

    public ChapterItem(String chapterId, String title, String subtitle, String sectionInfo,
                       int progress, int chapterNumber) {
        this.chapterId = chapterId;
        this.title = title;
        this.subtitle = subtitle;
        this.sectionInfo = sectionInfo;
        this.progress = progress;
        this.chapterNumber = chapterNumber;
        this.hasUpdate = false;
        this.isEssential = false;
    }

    // Getters / setters
    public String getChapterId() { return chapterId; }
    public void setChapterId(String chapterId) { this.chapterId = chapterId; }

    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getSectionInfo() { return sectionInfo; }

    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }

    public int getChapterNumber() { return chapterNumber; }

    public boolean hasUpdate() { return hasUpdate; }
    public void setHasUpdate(boolean hasUpdate) { this.hasUpdate = hasUpdate; }

    public boolean isEssential() { return isEssential; }
    public void setIsEssential(boolean essential) { this.isEssential = essential; }
}
