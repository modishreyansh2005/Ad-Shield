package com.adshield.data.model;

import android.graphics.drawable.Drawable;

public class AppItem {
    private final String packageName;
    private final String appName;
    private final Drawable icon;
    private boolean isProtected;
    private final boolean isHighlighted;

    public AppItem(String packageName, String appName, Drawable icon, boolean isProtected, boolean isHighlighted) {
        this.packageName = packageName;
        this.appName = appName;
        this.icon = icon;
        this.isProtected = isProtected;
        this.isHighlighted = isHighlighted;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getAppName() {
        return appName;
    }

    public Drawable getIcon() {
        return icon;
    }

    public boolean isProtected() {
        return isProtected;
    }

    public void setProtected(boolean aProtected) {
        isProtected = aProtected;
    }

    public boolean isHighlighted() {
        return isHighlighted;
    }
}
