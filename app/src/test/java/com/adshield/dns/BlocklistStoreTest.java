package com.adshield.dns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class BlocklistStoreTest {

    @After
    public void tearDown() {
        BlocklistStore.setDomainsForTest(Collections.emptySet());
    }

    @Test
    public void testParseLine_supportsHostsPlainAndAdblockFormats() {
        assertEquals("ads.example.com", BlocklistStore.parseLine("0.0.0.0 ads.example.com"));
        assertEquals("ads.example.com", BlocklistStore.parseLine("127.0.0.1\tads.example.com  # comment"));
        assertEquals("tracker.example.net", BlocklistStore.parseLine("tracker.example.net"));
        assertEquals("ads.example.org", BlocklistStore.parseLine("||ads.example.org^"));
        assertEquals("upper.example.com", BlocklistStore.parseLine("0.0.0.0 UPPER.Example.com."));
    }

    @Test
    public void testParseLine_skipsCommentsLocalhostAndInvalidEntries() {
        assertNull(BlocklistStore.parseLine("# Title: StevenBlack"));
        assertNull(BlocklistStore.parseLine("! adblock comment"));
        assertNull(BlocklistStore.parseLine(""));
        assertNull(BlocklistStore.parseLine("127.0.0.1 localhost"));
        assertNull(BlocklistStore.parseLine("0.0.0.0 0.0.0.0"));
        assertNull(BlocklistStore.parseLine("192.168.1.1 router.example.com"));
        assertNull(BlocklistStore.parseLine("||ads.example.org^$third-party"));
        assertNull(BlocklistStore.parseLine("||example.org/ads/*"));
        assertNull(BlocklistStore.parseLine("nodot"));
    }

    @Test
    public void testParse_readsWholeFile() throws Exception {
        String hosts = "# header\n0.0.0.0 a.example.com\n0.0.0.0 b.example.com\n\n127.0.0.1 localhost\n";
        Set<String> parsed = BlocklistStore.parse(new ByteArrayInputStream(hosts.getBytes(StandardCharsets.UTF_8)));
        assertEquals(new HashSet<>(Arrays.asList("a.example.com", "b.example.com")), parsed);
    }

    @Test
    public void testContains_matchesSubdomainsOnly() {
        BlocklistStore.setDomainsForTest(new HashSet<>(Collections.singletonList("adnetwork.com")));
        BlocklistStore store = BlocklistStore.getInstance();

        assertTrue(store.contains("adnetwork.com"));
        assertTrue(store.contains("cdn.eu.adnetwork.com"));
        assertFalse(store.contains("notadnetwork.com"));
        assertFalse(store.contains("adnetwork.com.example.org"));
    }

    @Test
    public void testDomainBlocklist_usesCommunityListButKeepsEssentialDomains() {
        BlocklistStore.setDomainsForTest(new HashSet<>(Arrays.asList(
                "adnetwork.com", "googlevideo.com", "audio-ak.spotify.com")));
        DomainBlocklist blocklist = new DomainBlocklist();

        assertTrue(blocklist.isBlocked("banner.adnetwork.com"));
        // Essential playback hosts are protected even if a community list contains them
        assertFalse(blocklist.isBlocked("rr3---sn-abc.googlevideo.com"));
        assertFalse(blocklist.isBlocked("audio-ak.spotify.com"));
    }
}
