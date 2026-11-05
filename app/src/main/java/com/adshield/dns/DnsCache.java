package com.adshield.dns;

import java.util.LinkedHashMap;
import java.util.Map;

public class DnsCache {

    private static final int MAX_CACHE_SIZE = 1024;
    private static final long DEFAULT_TTL_MS = 60 * 1000L; // 60 seconds

    private static class CacheEntry {
        final byte[] responsePayload;
        final long expiresAt;

        CacheEntry(byte[] responsePayload, long expiresAt) {
            this.responsePayload = responsePayload;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private final Map<String, CacheEntry> cache;

    public DnsCache() {
        this.cache = new LinkedHashMap<String, CacheEntry>(128, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        };
    }

    public synchronized byte[] get(String domain, int qtype, int newQueryId) {
        String key = domain + ":" + qtype;
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }

        if (entry.isExpired()) {
            cache.remove(key);
            return null;
        }

        // Return a copy of the response payload with the transaction ID matched to the new query
        byte[] copy = entry.responsePayload.clone();
        if (copy.length >= 2) {
            copy[0] = (byte) ((newQueryId >> 8) & 0xFF);
            copy[1] = (byte) (newQueryId & 0xFF);
        }
        return copy;
    }

    public synchronized void put(String domain, int qtype, byte[] responsePayload, long ttlSeconds) {
        if (responsePayload == null || responsePayload.length < 12) return;
        long ttlMs = (ttlSeconds > 0) ? (ttlSeconds * 1000L) : DEFAULT_TTL_MS;
        String key = domain + ":" + qtype;
        cache.put(key, new CacheEntry(responsePayload.clone(), System.currentTimeMillis() + ttlMs));
    }

    public synchronized void clear() {
        cache.clear();
    }
}
