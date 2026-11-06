package com.adshield.dns;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Holds the large community ad/tracker blocklist in memory.
 * Loads the downloaded copy from internal storage when present, otherwise the
 * offline copy bundled in assets/blocklist.txt.
 */
public final class BlocklistStore {

    private static final String TAG = "BlocklistStore";
    private static final String ASSET_NAME = "blocklist.txt";
    private static final String DOWNLOADED_NAME = "blocklist_downloaded.txt";
    private static final int MIN_VALID_DOMAINS = 1000;
    private static final int TIMEOUT_MS = 20000;

    public static final String DEFAULT_UPDATE_URL =
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts";

    private static final BlocklistStore INSTANCE = new BlocklistStore();

    private volatile Set<String> domains = Collections.emptySet();
    private volatile boolean loaded = false;

    private BlocklistStore() {}

    public static BlocklistStore getInstance() {
        return INSTANCE;
    }

    public int size() {
        return domains.size();
    }

    public boolean isLoaded() {
        return loaded;
    }

    /** Exact or parent-domain match against the community list. Expects a normalized domain. */
    public boolean contains(String cleanDomain) {
        Set<String> current = domains;
        if (current.isEmpty() || cleanDomain == null || cleanDomain.isEmpty()) return false;
        if (current.contains(cleanDomain)) return true;

        int dot = cleanDomain.indexOf('.');
        while (dot != -1 && dot < cleanDomain.length() - 1) {
            if (current.contains(cleanDomain.substring(dot + 1))) {
                return true;
            }
            dot = cleanDomain.indexOf('.', dot + 1);
        }
        return false;
    }

    public void loadAsync(Context context) {
        Context appContext = context.getApplicationContext();
        new Thread(() -> ensureLoaded(appContext), "AdShield-BlocklistLoader").start();
    }

    /** Blocking load; returns immediately if already loaded. */
    public synchronized void ensureLoaded(Context context) {
        if (loaded) return;

        File downloaded = new File(context.getFilesDir(), DOWNLOADED_NAME);
        if (downloaded.exists()) {
            try (InputStream in = new FileInputStream(downloaded)) {
                Set<String> parsed = parse(in);
                if (parsed.size() >= MIN_VALID_DOMAINS) {
                    setDomains(parsed);
                    Log.i(TAG, "Loaded downloaded blocklist: " + parsed.size() + " domains");
                    return;
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed to read downloaded blocklist: " + e.getMessage());
            }
        }

        try (InputStream in = context.getAssets().open(ASSET_NAME)) {
            Set<String> parsed = parse(in);
            setDomains(parsed);
            Log.i(TAG, "Loaded bundled blocklist: " + parsed.size() + " domains");
        } catch (IOException e) {
            Log.e(TAG, "Failed to read bundled blocklist: " + e.getMessage());
            loaded = true;
        }
    }

    /**
     * Downloads a fresh list, validates it, saves it to internal storage and swaps it in.
     * Must be called off the main thread. Returns the new domain count.
     */
    public int updateFromUrl(Context context, String urlString) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code);
            }
            Set<String> parsed;
            try (InputStream in = conn.getInputStream()) {
                parsed = parse(in);
            }
            if (parsed.size() < MIN_VALID_DOMAINS) {
                throw new IOException("Downloaded list too small (" + parsed.size() + " domains)");
            }

            File target = new File(context.getFilesDir(), DOWNLOADED_NAME);
            File tmp = new File(context.getFilesDir(), DOWNLOADED_NAME + ".tmp");
            try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                for (String d : parsed) {
                    w.write(d);
                    w.write('\n');
                }
            }
            if (!tmp.renameTo(target)) {
                throw new IOException("Could not save blocklist");
            }

            setDomains(parsed);
            Log.i(TAG, "Updated blocklist: " + parsed.size() + " domains");
            return parsed.size();
        } finally {
            conn.disconnect();
        }
    }

    public long getLastUpdatedMillis(Context context) {
        File downloaded = new File(context.getFilesDir(), DOWNLOADED_NAME);
        return downloaded.exists() ? downloaded.lastModified() : 0L;
    }

    private void setDomains(Set<String> parsed) {
        domains = Collections.unmodifiableSet(parsed);
        loaded = true;
    }

    /** Parses hosts files ("0.0.0.0 domain"), plain domain lists and "||domain^" adblock rules. */
    static Set<String> parse(InputStream in) throws IOException {
        Set<String> result = new HashSet<>(100_000);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            String domain = parseLine(line);
            if (domain != null) {
                result.add(domain);
            }
        }
        return result;
    }

    static String parseLine(String rawLine) {
        String line = rawLine;
        int hash = line.indexOf('#');
        if (hash >= 0) line = line.substring(0, hash);
        line = line.trim();
        if (line.isEmpty() || line.startsWith("!")) return null;

        String candidate;
        if (line.startsWith("||")) {
            int end = line.indexOf('^');
            if (end < 0 || end != line.length() - 1) return null; // skip rules with paths/options
            candidate = line.substring(2, end);
        } else {
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) {
                if (!parts[0].equals("0.0.0.0") && !parts[0].equals("127.0.0.1") && !parts[0].equals("::")) {
                    return null;
                }
                candidate = parts[1];
            } else {
                candidate = parts[0];
            }
        }

        candidate = DomainBlocklist.normalize(candidate);
        if (!isValidDomain(candidate)) return null;
        switch (candidate) {
            case "localhost":
            case "localhost.localdomain":
            case "local":
            case "broadcasthost":
            case "0.0.0.0":
                return null;
            default:
                return candidate;
        }
    }

    private static boolean isValidDomain(String d) {
        if (d.isEmpty() || d.length() > 253 || d.indexOf('.') < 0) return false;
        for (int i = 0; i < d.length(); i++) {
            char c = d.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '.' || c == '_';
            if (!ok) return false;
        }
        return !d.startsWith(".") && !d.contains("..");
    }

    /** Test hook. */
    static void setDomainsForTest(Set<String> testDomains) {
        INSTANCE.setDomains(new HashSet<>(testDomains));
    }
}
