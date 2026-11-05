package com.adshield.data.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.adshield.data.model.DnsLogEntry;
import com.adshield.data.model.FilterRule;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AdShieldDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "adshield.db";
    private static final int DATABASE_VERSION = 1;
    private static final int MAX_LOG_RECORDS = 1000;

    private static volatile AdShieldDbHelper instance;

    public static synchronized AdShieldDbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new AdShieldDbHelper(context.getApplicationContext());
        }
        return instance;
    }

    private AdShieldDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Table for Live DNS Query Logs
        db.execSQL("CREATE TABLE dns_logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "timestamp INTEGER, " +
                "domain TEXT, " +
                "qtype TEXT, " +
                "is_blocked INTEGER, " +
                "app_name TEXT, " +
                "resolved_ip TEXT)");

        db.execSQL("CREATE INDEX idx_logs_timestamp ON dns_logs(timestamp DESC)");
        db.execSQL("CREATE INDEX idx_logs_domain ON dns_logs(domain)");

        // Table for Custom Whitelist and Blacklist Rules
        db.execSQL("CREATE TABLE custom_rules (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "domain TEXT UNIQUE, " +
                "type TEXT, " +
                "is_enabled INTEGER, " +
                "created_at INTEGER)");

        // Table for Daily Aggregated Statistics
        db.execSQL("CREATE TABLE daily_stats (" +
                "date_str TEXT PRIMARY KEY, " +
                "total_queries INTEGER, " +
                "blocked_queries INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS dns_logs");
        db.execSQL("DROP TABLE IF EXISTS custom_rules");
        db.execSQL("DROP TABLE IF EXISTS daily_stats");
        onCreate(db);
    }

    // ==========================================
    // DNS Logs Operations
    // ==========================================

    public synchronized void insertLog(DnsLogEntry entry) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("timestamp", entry.getTimestamp());
        cv.put("domain", entry.getDomain());
        cv.put("qtype", entry.getQtype());
        cv.put("is_blocked", entry.isBlocked() ? 1 : 0);
        cv.put("app_name", entry.getAppName());
        cv.put("resolved_ip", entry.getResolvedIp());

        db.insert("dns_logs", null, cv);

        // Keep maximum log entries bounded to preserve performance
        db.execSQL("DELETE FROM dns_logs WHERE id NOT IN " +
                "(SELECT id FROM dns_logs ORDER BY id DESC LIMIT " + MAX_LOG_RECORDS + ")");

        // Update daily stats aggregation
        recordDailyStat(entry.isBlocked());
    }

    public synchronized List<DnsLogEntry> getRecentLogs(int limit, String filter, Boolean blockedOnly) {
        List<DnsLogEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        StringBuilder query = new StringBuilder("SELECT id, timestamp, domain, qtype, is_blocked, app_name, resolved_ip FROM dns_logs WHERE 1=1 ");
        List<String> args = new ArrayList<>();

        if (filter != null && !filter.trim().isEmpty()) {
            query.append("AND domain LIKE ? ");
            args.add("%" + filter.trim() + "%");
        }

        if (blockedOnly != null) {
            query.append("AND is_blocked = ? ");
            args.add(blockedOnly ? "1" : "0");
        }

        query.append("ORDER BY id DESC LIMIT ").append(limit);

        try (Cursor cursor = db.rawQuery(query.toString(), args.toArray(new String[0]))) {
            while (cursor.moveToNext()) {
                list.add(new DnsLogEntry(
                        cursor.getLong(0),
                        cursor.getLong(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getInt(4) == 1,
                        cursor.getString(5),
                        cursor.getString(6)
                ));
            }
        }
        return list;
    }

    public synchronized void clearLogs() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("dns_logs", null, null);
    }

    // ==========================================
    // Custom Rules (Whitelist & Blacklist)
    // ==========================================

    public synchronized boolean addOrUpdateRule(String domain, FilterRule.Type type) {
        if (domain == null || domain.trim().isEmpty()) return false;
        String cleanDomain = domain.trim().toLowerCase(Locale.US);

        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("domain", cleanDomain);
        cv.put("type", type.name());
        cv.put("is_enabled", 1);
        cv.put("created_at", System.currentTimeMillis());

        long res = db.insertWithOnConflict("custom_rules", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        return res != -1;
    }

    public synchronized boolean deleteRule(String domain) {
        if (domain == null) return false;
        SQLiteDatabase db = getWritableDatabase();
        return db.delete("custom_rules", "domain = ?", new String[]{domain.trim().toLowerCase(Locale.US)}) > 0;
    }

    public synchronized void setRuleEnabled(String domain, boolean enabled) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("is_enabled", enabled ? 1 : 0);
        db.update("custom_rules", cv, "domain = ?", new String[]{domain.trim().toLowerCase(Locale.US)});
    }

    public synchronized List<FilterRule> getRules(FilterRule.Type type) {
        List<FilterRule> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query("custom_rules",
                new String[]{"id", "domain", "type", "is_enabled", "created_at"},
                "type = ?", new String[]{type.name()},
                null, null, "created_at DESC")) {
            while (cursor.moveToNext()) {
                list.add(new FilterRule(
                        cursor.getLong(0),
                        cursor.getString(1),
                        FilterRule.Type.valueOf(cursor.getString(2)),
                        cursor.getInt(3) == 1,
                        cursor.getLong(4)
                ));
            }
        }
        return list;
    }

    public synchronized Set<String> getActiveRuleDomains(FilterRule.Type type) {
        Set<String> set = new HashSet<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query("custom_rules",
                new String[]{"domain"},
                "type = ? AND is_enabled = 1", new String[]{type.name()},
                null, null, null)) {
            while (cursor.moveToNext()) {
                set.add(cursor.getString(0));
            }
        }
        return set;
    }

    // ==========================================
    // Aggregated Statistics
    // ==========================================

    private synchronized void recordDailyStat(boolean isBlocked) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("INSERT INTO daily_stats (date_str, total_queries, blocked_queries) " +
                "VALUES ('" + today + "', 1, " + (isBlocked ? 1 : 0) + ") " +
                "ON CONFLICT(date_str) DO UPDATE SET " +
                "total_queries = total_queries + 1, " +
                "blocked_queries = blocked_queries + " + (isBlocked ? 1 : 0));
    }

    public synchronized Map<String, Long> getTopBlockedDomains(int limit) {
        Map<String, Long> map = new HashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT domain, COUNT(*) as cnt FROM dns_logs WHERE is_blocked = 1 GROUP BY domain ORDER BY cnt DESC LIMIT " + limit;
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                map.put(cursor.getString(0), cursor.getLong(1));
            }
        }
        return map;
    }

    public synchronized long[] getTodayCounts() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        SQLiteDatabase db = getReadableDatabase();
        long total = 0;
        long blocked = 0;
        try (Cursor cursor = db.query("daily_stats", new String[]{"total_queries", "blocked_queries"},
                "date_str = ?", new String[]{today}, null, null, null)) {
            if (cursor.moveToFirst()) {
                total = cursor.getLong(0);
                blocked = cursor.getLong(1);
            }
        }
        return new long[]{total, blocked};
    }
}
