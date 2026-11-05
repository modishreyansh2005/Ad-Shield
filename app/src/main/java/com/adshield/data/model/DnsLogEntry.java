package com.adshield.data.model;

public class DnsLogEntry {
    private final long id;
    private final long timestamp;
    private final String domain;
    private final String qtype;
    private final boolean isBlocked;
    private final String appName;
    private final String resolvedIp;

    public DnsLogEntry(long id, long timestamp, String domain, String qtype,
                       boolean isBlocked, String appName, String resolvedIp) {
        this.id = id;
        this.timestamp = timestamp;
        this.domain = domain;
        this.qtype = qtype;
        this.isBlocked = isBlocked;
        this.appName = (appName != null && !appName.isEmpty()) ? appName : "Unknown";
        this.resolvedIp = (resolvedIp != null && !resolvedIp.isEmpty()) ? resolvedIp : "-";
    }

    public long getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getDomain() {
        return domain;
    }

    public String getQtype() {
        return qtype;
    }

    public boolean isBlocked() {
        return isBlocked;
    }

    public String getAppName() {
        return appName;
    }

    public String getResolvedIp() {
        return resolvedIp;
    }
}
