package com.adshield.data.model;

public class FilterRule {
    public enum Type {
        BLACKLIST,
        WHITELIST
    }

    private final long id;
    private final String domain;
    private final Type type;
    private boolean enabled;
    private final long createdAt;

    public FilterRule(long id, String domain, Type type, boolean enabled, long createdAt) {
        this.id = id;
        this.domain = domain;
        this.type = type;
        this.enabled = enabled;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public String getDomain() {
        return domain;
    }

    public Type getType() {
        return type;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
