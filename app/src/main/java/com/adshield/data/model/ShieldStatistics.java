package com.adshield.data.model;

public class ShieldStatistics {
    private final long totalQueries;
    private final long blockedRequests;
    private final long allowedRequests;
    private final long todayBlocked;
    private final long todayTotal;
    private final Long protectionStartTimeMillis;

    public ShieldStatistics() {
        this(0L, 0L, 0L, 0L, 0L, null);
    }

    public ShieldStatistics(long totalQueries, long blockedRequests, long allowedRequests,
                            long todayBlocked, long todayTotal, Long protectionStartTimeMillis) {
        this.totalQueries = totalQueries;
        this.blockedRequests = blockedRequests;
        this.allowedRequests = allowedRequests;
        this.todayBlocked = todayBlocked;
        this.todayTotal = todayTotal;
        this.protectionStartTimeMillis = protectionStartTimeMillis;
    }

    public long getTotalQueries() {
        return totalQueries;
    }

    public long getBlockedRequests() {
        return blockedRequests;
    }

    public long getAllowedRequests() {
        return allowedRequests;
    }

    public long getTodayBlocked() {
        return todayBlocked;
    }

    public long getTodayTotal() {
        return todayTotal;
    }

    public Long getProtectionStartTimeMillis() {
        return protectionStartTimeMillis;
    }

    public int getBlockedPercentage() {
        if (totalQueries == 0) return 0;
        return (int) Math.round(((double) blockedRequests / totalQueries) * 100.0);
    }
}
