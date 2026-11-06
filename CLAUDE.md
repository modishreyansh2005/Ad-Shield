# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

AdShield: on-device Android DNS ad/tracker blocker built on `VpnService`. Pure Java 17 (Kotlin was fully removed — don't reintroduce it; the root `build.gradle.kts` still declares the Kotlin plugin with `apply false`, unused). XML layouts + ViewBinding + Material 3, no Compose. No backend, no network APIs besides upstream DNS. `minSdk 26`, `targetSdk/compileSdk 34`, AGP 8.3.2.

## Commands

Requires JDK 17 (`JAVA_HOME` must point to it).

```bash
./gradlew assembleDebug                      # APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest                  # all JVM unit tests (JUnit 4)
./gradlew testDebugUnitTest --tests "com.adshield.dns.DnsParserTest"            # one class
./gradlew testDebugUnitTest --tests "com.adshield.dns.DnsParserTest.someMethod" # one test
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

No lint/format config beyond Android defaults. No instrumented tests exist. Unit tests run with `unitTests.isReturnDefaultValues = true`, so Android framework calls (e.g. `Log`) return defaults instead of throwing — test classes in `dns/` that avoid Android deps are the testable core.

## Architecture

**Packet path** (the core; spans `service/` and `dns/`):

1. `service/AdShieldVpnService` builds the TUN with only the fake DNS server address routed (`10.0.0.1/32` IPv4, `fd00:1::1/128` IPv6). All non-DNS traffic bypasses the tunnel. Excluded apps (from `PreferencesManager.getExcludedPackages()`) are added via `addDisallowedApplication`. A single reader thread reads TUN packets and dispatches each to a 4-thread executor; writes back to TUN are `synchronized` on the output stream.
2. `dns/DnsFilteringEngine.processPacket` — parse IP/UDP (`IpPacketUtil`) → parse DNS (`DnsParser`) → `DomainBlocklist.isBlocked` → either sinkhole (`DnsResponseBuilder`: `0.0.0.0` / `::`) or `DnsCache` lookup → `CloudflareDnsForwarder` (upstream over a `protect()`ed socket to avoid VPN loops, TCP fallback on truncation; SERVFAIL on timeout). Every query is logged to SQLite and reported to a `QueryListener`, which the VPN service uses to bump counters in prefs + static `LiveData`.
3. Response is rewrapped by `IpPacketUtil.buildIpv4UdpPacket` / `buildIpv6UdpPacket` (computes checksums) with src/dst swapped.

**Block decision order** in `DomainBlocklist.isBlocked` (order matters): user whitelist (from DB, parent-domain match) → user blacklist → `YouTubeFilter` / `SpotifyFilter` / `JioHotstarFilter` (each gated by a prefs toggle; null prefs = enabled, which is how tests construct it) → essential-domain guard → `BUILT_IN_BLOCKED_DOMAINS` → `BlocklistStore` (~78k-domain StevenBlack list, `assets/blocklist.txt`, loaded async at app start and awaited by the VPN worker before the first packet; the Filters → Community tab's "Update Lists" calls `updateFromUrl`, which saves a fresh copy to `filesDir` that then takes precedence). All generic lists use hierarchical parent matching. User rule sets are cached in memory by `AdShieldDbHelper` and invalidated on rule changes. In JVM tests the store is empty unless set via `BlocklistStore.setDomainsForTest`. Each per-app filter class keeps an `*_AD_DOMAINS` set and an `ESSENTIAL_*` set that must never be blocked (e.g. `googlevideo.com`, `audio-*.spotify.com`) — blocking media hosts breaks playback, so ads served from the same host as content are intentionally not blockable at DNS level.

**Other ad-defusing layers** (outside DNS):
- No accessibility service — it was removed deliberately (user does not want that permission); don't reintroduce it.
- `service/MediaAdMuteService` — `NotificationListenerService` (user grants Notification access) that watches YouTube / YT Music / Spotify media sessions and mutes while `MediaAdDetector` says an ad is playing. YouTube ad signal (verified on a Pixel 4a): seeking is disabled during ads (`PlaybackState` actions lose `ACTION_SEEK_TO`, 8615 → 8359) while the title stays the video's. Tune from `adb logcat -s MediaAdMute` `[METADATA]` lines.
- `service/AdMuteController` — the only code that touches `STREAM_MUSIC` mute; detectors mute/unmute under their own reason key so they never unmute each other.
- `service/SpotifyAdMuter` — runtime-registered receiver (from the VPN service and the notification listener) for Spotify's `metadatachanged` / `playbackstatechanged` broadcasts; mutes `STREAM_MUSIC` while an ad track plays. Requires the user to enable Spotify's "Device Broadcast Status".

**State & data:**
- `AdShieldApplication` holds singletons (`PreferencesManager`, `AdShieldDbHelper`) and applies theme mode at startup.
- `PreferencesManager` (SharedPreferences): toggles, upstream DNS choice, excluded packages, counters (total/blocked, per-app blocked counts).
- `AdShieldDbHelper` (raw SQLiteOpenHelper singleton, all methods `synchronized`): tables `dns_logs`, `custom_rules` (whitelist/blacklist), `daily_stats`. `onUpgrade` drops all tables — bumping `DATABASE_VERSION` wipes user rules and logs.
- UI observes `AdShieldVpnService` static `LiveData` (`getVpnStateLiveData`, counts). `MainActivity` swaps fragments via bottom nav (Home/Dashboard, Apps, Block list/Filters, Settings). The Query Log screen was removed on purpose; queries are still stored in `dns_logs` for stats. UI text is written for non-technical users — avoid terms like DNS, query, sinkhole, domain, blacklist/whitelist in user-visible strings; `ui/stats/StatisticsFragment` exists but nothing navigates to it.
- `ui/player/CyberPlayerActivity` — in-app WebView "Ad-Free streaming hub" (YouTube / YT Music / Spotify web); `shouldInterceptRequest` reuses `DomainBlocklist` to drop requests. `assets/youtube_scriptlet.js` is installed with `WebViewCompat.addDocumentStartJavaScript` (must run before YouTube's scripts) and prunes `adPlacements`/`playerAds`/`adSlots` from `JSON.parse`, `Response.json()` and `ytInitialPlayerResponse`; `onPageStarted/Finished` injection is only a fallback.

**Hotstar note:** the latest commit removed Hotstar from the UI, but `JioHotstarFilter`, its prefs toggle/counter, and `DomainBlocklistTest` references still exist in the DNS layer. Check with the user before deleting or reviving it.

## Repo quirks

- `AdShield-v2.0.apk` is a committed release binary at repo root.
- README claims "20 unit tests"; treat `app/src/test` as the source of truth.
