package com.adshield.dns;

import android.net.VpnService;
import android.util.Log;

import com.adshield.data.PreferencesManager;
import com.adshield.data.db.AdShieldDbHelper;
import com.adshield.data.model.DnsLogEntry;

public class DnsFilteringEngine {

    private static final String TAG = "DnsFilteringEngine";
    private static final int DNS_PORT = 53;

    public interface QueryListener {
        void onQueryProcessed(DnsLogEntry entry);
    }

    private final DomainBlocklist blocklist;
    private final CloudflareDnsForwarder forwarder;
    private final DnsCache cache;
    private final AdShieldDbHelper dbHelper;
    private final PreferencesManager preferencesManager;
    private QueryListener queryListener;

    public DnsFilteringEngine(VpnService vpnService,
                              DomainBlocklist blocklist,
                              CloudflareDnsForwarder forwarder,
                              AdShieldDbHelper dbHelper,
                              PreferencesManager preferencesManager) {
        this.blocklist = (blocklist != null) ? blocklist : new DomainBlocklist();
        this.forwarder = (forwarder != null) ? forwarder : new CloudflareDnsForwarder(vpnService);
        this.cache = new DnsCache();
        this.dbHelper = dbHelper;
        this.preferencesManager = preferencesManager;
    }

    public void setQueryListener(QueryListener listener) {
        this.queryListener = listener;
    }

    public byte[] processPacket(byte[] rawPacket, int length) {
        IpPacketUtil.ParsedUdpPacket udpPacket = IpPacketUtil.parseUdp(rawPacket, length);
        if (udpPacket == null) {
            return null;
        }

        // Only process DNS queries directed to port 53
        if (udpPacket.getDstPort() != DNS_PORT) {
            return null;
        }

        DnsQuery query = DnsParser.parseQuery(udpPacket.getPayload());
        if (query == null) {
            return null;
        }

        String domain = query.getQuestion().getName();
        int qtype = query.getQuestion().getType();
        String qtypeName = DnsType.getTypeName(qtype);
        boolean isBlocked = blocklist.isBlocked(domain);

        byte[] dnsResponsePayload;
        String resolvedIp = "-";

        if (isBlocked) {
            dnsResponsePayload = DnsResponseBuilder.buildSinkholeResponse(query);
            resolvedIp = (qtype == DnsType.A) ? "0.0.0.0" : (qtype == DnsType.AAAA ? "::" : "-");

            // Track YouTube, Spotify & Hotstar specific blocked stats
            if (YouTubeFilter.isYouTubeRelated(domain) && preferencesManager != null) {
                preferencesManager.incrementYouTubeBlockedCount();
            } else if (SpotifyFilter.isSpotifyRelated(domain) && preferencesManager != null) {
                preferencesManager.incrementSpotifyBlockedCount();
            } else if (JioHotstarFilter.isHotstarRelated(domain) && preferencesManager != null) {
                preferencesManager.incrementHotstarBlockedCount();
            }

            Log.i(TAG, "[BLOCKED] " + domain + " (" + qtypeName + ")");
        } else {
            // Check in-memory DNS cache first
            byte[] cached = cache.get(domain, qtype, query.getId());
            if (cached != null) {
                dnsResponsePayload = cached;
                resolvedIp = extractIpFromAnswer(cached, qtype);
                Log.d(TAG, "[CACHE-HIT] " + domain);
            } else {
                byte[] upstreamResponse = forwarder.forward(query.getRawData());
                if (upstreamResponse != null) {
                    dnsResponsePayload = upstreamResponse;
                    resolvedIp = extractIpFromAnswer(upstreamResponse, qtype);
                    cache.put(domain, qtype, upstreamResponse, 60);
                    Log.d(TAG, "[FORWARDED] " + domain + " -> " + resolvedIp);
                } else {
                    dnsResponsePayload = DnsResponseBuilder.buildServFailResponse(query);
                    Log.w(TAG, "[SERVFAIL] " + domain + " (upstream timeout)");
                }
            }
        }

        // Record in SQLite database for Live Query Log & Statistics
        DnsLogEntry logEntry = new DnsLogEntry(
                0,
                System.currentTimeMillis(),
                domain,
                qtypeName,
                isBlocked,
                identifyApp(domain),
                resolvedIp
        );

        if (dbHelper != null) {
            dbHelper.insertLog(logEntry);
        }

        if (queryListener != null) {
            queryListener.onQueryProcessed(logEntry);
        }

        // Return complete IP UDP response packet (IPv4 or IPv6)
        if (udpPacket.getIpVersion() == 6) {
            return IpPacketUtil.buildIpv6UdpPacket(
                    udpPacket.getDstIp(),
                    udpPacket.getSrcIp(),
                    udpPacket.getDstPort(),
                    udpPacket.getSrcPort(),
                    dnsResponsePayload
            );
        } else {
            return IpPacketUtil.buildIpv4UdpPacket(
                    udpPacket.getDstIp(),
                    udpPacket.getSrcIp(),
                    udpPacket.getDstPort(),
                    udpPacket.getSrcPort(),
                    dnsResponsePayload,
                    udpPacket.getIpId() + 1
            );
        }
    }

    private String identifyApp(String domain) {
        if (YouTubeFilter.isYouTubeRelated(domain)) return "YouTube";
        if (SpotifyFilter.isSpotifyRelated(domain)) return "Spotify";
        if (JioHotstarFilter.isHotstarRelated(domain)) return "Streaming";
        if (domain.contains("instagram") || domain.contains("cdninstagram")) return "Instagram";
        if (domain.contains("facebook") || domain.contains("fbcdn")) return "Facebook";
        if (domain.contains("google") || domain.contains("gstatic") || domain.contains("chrome")) return "Google/Chrome";
        if (domain.contains("whatsapp")) return "WhatsApp";
        if (domain.contains("telegram")) return "Telegram";
        return "Unknown";
    }

    private String extractIpFromAnswer(byte[] response, int qtype) {
        if (response == null || response.length < 12) return "-";
        try {
            int anCount = ((response[6] & 0xFF) << 8) | (response[7] & 0xFF);
            if (anCount == 0) return "-";

            // If query was A (type 1), check the last 4 bytes of response for IPv4
            if (qtype == DnsType.A && response.length >= 16) {
                int len = response.length;
                return (response[len - 4] & 0xFF) + "." +
                        (response[len - 3] & 0xFF) + "." +
                        (response[len - 2] & 0xFF) + "." +
                        (response[len - 1] & 0xFF);
            }
        } catch (Exception ignored) {}
        return "-";
    }
}
