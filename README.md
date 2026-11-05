# AdShield – Complete Android Application Modernization (v2.0.0)

AdShield is a **100% native Java**, zero-cost, privacy-first Android system-wide ad and tracker blocker powered by the Android `VpnService` API. It executes entirely on-device with **zero external servers, no paid APIs, no logins, and zero data collection**.

---

## 🚀 Key Highlights & Milestone 3 Modernization

### 1. 100% Native Java Migration (Phase 1)
- Converted **100% of Kotlin source files and tests to native Java 17**.
- Removed all Kotlin dependencies and Kotlin compiler plugins from Gradle.
- Replaced Coroutines and Flow with native Java concurrency (`ExecutorService`, `Handler`, `Thread`, `AtomicReference`, `LiveData`).
- Replaced Jetpack Compose with modern Material Design 3 XML layouts and Android ViewBinding for faster compilation and reliable DayNight theming.

### 2. Modern Material Design 3 Interface (Phase 2 & 3)
Built with a sleek **Cybersecurity** theme supporting **Light Mode, Dark Mode, and System Default**:
1. **Dashboard Screen**:
   - Modern AdShield status branding and logo.
   - Large interactive power switch with glowing status halo.
   - Measured metrics: Ads Blocked, Total Queries, Block Rate %, Today's Blocked, Protection Uptime.
   - Dedicated YouTube & Spotify Shield quick-status cards.
   - Real-time recent blocked threats list.
2. **App Protection / Selection Screen**:
   - Lists installed applications with app icon, title, and package name.
   - Highlights essential apps: **YouTube, Spotify, Instagram, Facebook, and Chrome**.
   - Per-app toggle to exclude/bypass apps from VPN filtering (`VpnService.Builder.addDisallowedApplication`).
   - Search bar and filter chips (*All*, *Protected*, *Excluded*).
3. **Live DNS Query Log**:
   - Real-time feed of all incoming DNS queries.
   - Domain name, application name, query type badge (`A`, `AAAA`), status dot (`🔴 Blocked`, `🟢 Allowed`), resolved IP, and timestamp.
   - Live search and filter chips (*All*, *Blocked*, *Allowed*).
   - Clear logs action.
4. **Statistics & Analytics**:
   - Measured actual statistics: Overall Block Rate %, Total queries, Blocked vs Allowed count.
   - Visual proportional ratio bar.
   - Top Blocked Domains list with counts and visual progress indicators.
   - Strictly real data—no simulated or fake numbers.
5. **Filter Management**:
   - Tabbed layout: **Blacklist**, **Whitelist**, and **Built-in Community Lists**.
   - Add custom domain rule dialog with domain validation.
   - Per-rule toggle switch and delete button.
   - Built-in community filter definitions included offline.
6. **Settings Screen**:
   - **Theme Selector**: System Default, Light Mode, Dark Mode (Cybersecurity).
   - **Upstream DNS Provider**: Cloudflare (`1.1.1.1`), Google (`8.8.8.8`), Quad9 (`9.9.9.9`), AdGuard (`94.140.14.14`).
   - **Specialized Shield Toggles**: Dedicated controls for YouTube and Spotify protection.
   - **System Reliability**: Start on device boot (`RECEIVE_BOOT_COMPLETED`), Battery Optimization exemption shortcut.
   - Reset statistics and clear data.

### 3. YouTube & Spotify Ad-Blocking Improvements (Phase 4)
#### Technical Transparency & Engineering Reality:
DNS-based filtering operates at layer 3/4. Ads delivered through the identical media hostnames as actual video/audio streams (e.g. `googlevideo.com`) cannot be separated by domain-name without breaking video playback or causing endless buffering loops.
- **YouTube Strategy**: Intercepts known telemetry, advertising, and companion banner domains (`doubleclick.net`, `googleads.g.doubleclick.net`, `adservice.google.com`, `youtubeads.l.google.com`, `s.youtube.com`).
- **YouTube Media Guarantee**: Strictly preserves `googlevideo.com`, `ytimg.com`, `youtubei.googleapis.com` so video playback and authentication remain 100% smooth.
- **Spotify Strategy**: Intercepts companion banners and audio ad telemetry domains (`spclient.wg.spotify.com/ads`, `adeventtracker.spotify.com`, `adstudio.spotify.com`).
- **Spotify Media Guarantee**: Whitelists `audio-*.spotify.com` and core streaming endpoints to avoid music playback freezes.

---

## 🛠️ Architecture & Network Engine (Phase 5 & 6)

```
                 ┌──────────────────────────────────────────────┐
                 │                Android Device                │
                 └──────────────────────┬───────────────────────┘
                                        │ DNS Query (UDP Port 53)
                                        ▼
                 ┌──────────────────────────────────────────────┐
                 │           VpnService TUN Interface           │
                 │                 (10.0.0.1:53)                │
                 └──────────────────────┬───────────────────────┘
                                        │
                 ┌──────────────────────▼───────────────────────┐
                 │             DnsFilteringEngine               │
                 │  - Parses IPv4 UDP header (RFC 768 / 791)    │
                 │  - Extracts QNAME & QTYPE                    │
                 │  - Checks In-Memory LRU DNS Cache            │
                 └──────────────┬───────────────────────────────┘
                                │
          Domain Blocked? ──────┴──────────────────────┐
          │ Yes                                        │ No (Allowed)
          ▼                                            ▼
┌───────────────────────────────┐            ┌──────────────────────────────┐
│     DnsResponseBuilder        │            │    CloudflareDnsForwarder    │
│  - 0.0.0.0 for IPv4 (A)       │            │  - Protected UDP Socket      │
│  - :: for IPv6 (AAAA)         │            │  - TCP Fallback on Truncation│
│  - TTL = 300s                 │            │  - Cloudflare / Google / Quad9│
└──────────────┬────────────────┘            └──────────────┬───────────────┘
               │                                            │
               └──────────────────────┬─────────────────────┘
                                      │
                         ┌────────────▼────────────┐
                         │   SQLite Logging & Stats│
                         │ (dns_logs, daily_stats) │
                         └────────────┬────────────┘
                                      │
                         ┌────────────▼────────────┐
                         │   IpPacketUtil Builder  │
                         │  Calculates Checksums   │
                         └────────────┬────────────┘
                                      │
                                      ▼
                         ┌─────────────────────────┐
                         │  TUN FileDescriptor     │
                         │(Delivered to client app)│
                         └─────────────────────────┘
```

1. **Selective DNS Routing**: `VpnService.Builder` routes *only* `10.0.0.1/32` into the TUN interface. Regular HTTPS traffic bypasses the VPN tunnel entirely and flows at full native hardware speeds.
2. **Loop Prevention**: Forwarder sockets call `VpnService.protect(socket)` to ensure upstream traffic bypasses the VPN interface.
3. **Android 14+ FGS Compliance**: Service declares `android:foregroundServiceType="specialUse"` with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` metadata.
4. **In-Memory LRU Cache**: Accelerates response times for repeated allowed queries with automated TTL eviction.

---

## 🧪 Testing & Verification

All 20 unit tests are written in pure Java (JUnit 4) and pass with 0 errors:

```bash
JAVA_HOME="<path-to-jdk-17>" ./gradlew testDebugUnitTest
```

### Test Suite Summary:
- `DnsParserTest`: Standard A queries, Subdomain AAAA queries, Cyclic pointer protection, Malformed packets.
- `DnsResponseBuilderTest`: Sinkhole A responses (`0.0.0.0`), AAAA responses (`::`), SERVFAIL responses.
- `DomainBlocklistTest`: Initial test domains (`doubleclick.net`, `googlesyndication.com`, etc.), Hierarchical subdomain matching, Whitelisting precedence, Essential streaming protection.
- `IpPacketUtilTest`: IPv4 header checksum verification (sum folds to `0xFFFF`), UDP checksum calculation (including odd-length payloads).
- `DnsFilteringEngineTest`: End-to-end packet processing pipeline, Sinkholing verification, Non-DNS packet bypassing.
- `ProtectionStateTest`: VpnState transition integrity, service constants, statistics defaults.

---

## 📱 Build & Installation Instructions

### 1. Compile the Debug APK
```bash
JAVA_HOME="<path-to-jdk-17>" ./gradlew clean assembleDebug
```
The APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### 2. Install on Device
With your device connected via USB or Wireless ADB:
```bash
adb install -r "app/build/outputs/apk/debug/app-debug.apk"
```

### 3. Launch AdShield
```bash
adb shell am start -n com.adshield/.MainActivity
```
