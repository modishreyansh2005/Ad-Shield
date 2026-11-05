package com.adshield.dns;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class DomainBlocklistTest {

    private DomainBlocklist blocklist;

    @Before
    public void setUp() {
        blocklist = new DomainBlocklist();
    }

    @Test
    public void testRequiredInitialTestDomains_areBlocked() {
        assertTrue("doubleclick.net must be blocked", blocklist.isBlocked("doubleclick.net"));
        assertTrue("googlesyndication.com must be blocked", blocklist.isBlocked("googlesyndication.com"));
        assertTrue("googleadservices.com must be blocked", blocklist.isBlocked("googleadservices.com"));
        assertTrue("adservice.google.com must be blocked", blocklist.isBlocked("adservice.google.com"));
    }

    @Test
    public void testSubdomainsOfBlockedDomains_areBlocked() {
        assertTrue(blocklist.isBlocked("ad.doubleclick.net"));
        assertTrue(blocklist.isBlocked("securepubads.g.doubleclick.net"));
        assertTrue(blocklist.isBlocked("pagead2.googlesyndication.com"));
        assertTrue(blocklist.isBlocked("www.googleadservices.com"));
        assertTrue(blocklist.isBlocked("sub.adservice.google.com"));
    }

    @Test
    public void testNormalDomains_areNeverBlocked() {
        assertFalse("google.com must not be blocked", blocklist.isBlocked("google.com"));
        assertFalse("youtube.com must not be blocked", blocklist.isBlocked("youtube.com"));
        assertFalse("instagram.com must not be blocked", blocklist.isBlocked("instagram.com"));
        assertFalse("wikipedia.org must not be blocked", blocklist.isBlocked("wikipedia.org"));
        assertFalse("github.com must not be blocked", blocklist.isBlocked("github.com"));
        assertFalse("cloudflare.com must not be blocked", blocklist.isBlocked("cloudflare.com"));
    }

    @Test
    public void testCaseInsensitiveAndTrailingDotNormalization() {
        assertTrue(blocklist.isBlocked("DOUBLECLICK.NET"));
        assertTrue(blocklist.isBlocked("DoubleClick.Net."));
        assertTrue(blocklist.isBlocked("pagead2.GoogleSyndication.COM."));
        assertFalse(blocklist.isBlocked("GOOGLE.COM."));
    }

    @Test
    public void testPartialSuffixMismatch_isNotBlocked() {
        assertFalse(blocklist.isBlocked("notdoubleclick.net"));
        assertFalse(blocklist.isBlocked("fakegooglesyndication.com"));
    }

    @Test
    public void testYouTubeAdDomains_areBlockedWhileVideosPlay() {
        assertTrue(blocklist.isBlocked("ad.youtube.com"));
        assertTrue(blocklist.isBlocked("youtubeads.l.google.com"));
        assertTrue(blocklist.isBlocked("googleads.g.doubleclick.net"));

        // Video streaming domains must NOT be blocked to avoid breaking playback
        assertFalse(blocklist.isBlocked("youtube.com"));
        assertFalse(blocklist.isBlocked("www.youtube.com"));
        assertFalse(blocklist.isBlocked("googlevideo.com"));
        assertFalse(blocklist.isBlocked("rr1---sn-4g5ednss.googlevideo.com"));
    }

    @Test
    public void testJioHotstarAdDomains_areBlockedWhileStreamingWorks() {
        assertTrue(blocklist.isBlocked("ads.hotstar.com"));
        assertTrue(blocklist.isBlocked("adengine.hotstar.com"));
        assertTrue(blocklist.isBlocked("ads.jiocinema.com"));
        assertTrue(blocklist.isBlocked("jioads.com"));
        assertTrue(blocklist.isBlocked("vserve.mobi"));
        assertTrue(blocklist.isBlocked("imasdk.googleapis.com"));

        // Essential streaming domains must NOT be blocked
        assertFalse(blocklist.isBlocked("hotstar.com"));
        assertFalse(blocklist.isBlocked("www.hotstar.com"));
        assertFalse(blocklist.isBlocked("api.hotstar.com"));
        assertFalse(blocklist.isBlocked("jiocinema.com"));
        assertFalse(blocklist.isBlocked("playback.jiocinema.com"));
    }
}
