package com.dreampediatrics.app;

public class UserSettings {
    private String username;
    private String email;
    private String uid;
    private boolean darkMode;
    private boolean notifications;

    public UserSettings() {
        this.username = "";
        this.email = "";
        this.uid = "";
        this.darkMode = false;
        this.notifications = true;
    }

    public UserSettings(String username, String email, String uid) {
        this.username = username;
        this.email = email;
        this.uid = uid;
        this.darkMode = false;
        this.notifications = true;
    }

    // Getters / Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public boolean isDarkMode() { return darkMode; }
    public void setDarkMode(boolean darkMode) { this.darkMode = darkMode; }

    public boolean isNotifications() { return notifications; }
    public void setNotifications(boolean notifications) { this.notifications = notifications; }
}
