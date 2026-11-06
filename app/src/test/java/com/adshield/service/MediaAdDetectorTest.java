package com.adshield.service;

import static com.adshield.service.MediaAdDetector.PKG_SPOTIFY;
import static com.adshield.service.MediaAdDetector.PKG_YOUTUBE;
import static com.adshield.service.MediaAdDetector.PKG_YT_MUSIC;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MediaAdDetectorTest {

    @Test
    public void testSpotifyAds_areDetected() {
        assertTrue(MediaAdDetector.isAd(PKG_SPOTIFY, "Advertisement", "Spotify", null, 30_000, -1));
        assertTrue(MediaAdDetector.isAd(PKG_SPOTIFY, "Try Premium", "Spotify", null, 15_000, -1));
        assertTrue(MediaAdDetector.isAd(PKG_SPOTIFY, "Brand Jingle", "Brand", "spotify:ad:0001", 30_000, -1));
    }

    @Test
    public void testSpotifyMusicAndPodcasts_areNotAds() {
        assertFalse(MediaAdDetector.isAd(PKG_SPOTIFY, "Blinding Lights", "The Weeknd", "spotify:track:0VjI", 200_000, -1));
        // Long Spotify-published content is not an ad
        assertFalse(MediaAdDetector.isAd(PKG_SPOTIFY, "Wrapped Special", "Spotify", null, 1_800_000, -1));
        assertFalse(MediaAdDetector.isAd(PKG_SPOTIFY, null, null, null, 0, -1));
    }

    @Test
    public void testYouTubeAdLabels_areDetected() {
        assertTrue(MediaAdDetector.isAd(PKG_YOUTUBE, "Ad · 0:15", "", null, 15_000, -1));
        assertTrue(MediaAdDetector.isAd(PKG_YOUTUBE, "Video will play after ad", "", null, 0, -1));
        assertTrue(MediaAdDetector.isAd(PKG_YT_MUSIC, "Advertisement", null, null, 30_000, -1));
    }

    @Test
    public void testYouTubeSeekDisabledWhilePlaying_isAd() {
        // Real values captured from a Pixel 4a: 8615 during the video, 8359 during a skippable ad
        assertTrue(MediaAdDetector.isAd(PKG_YOUTUBE, "Wiz Khalifa - See You Again", "Wiz Khalifa", null, 15_000, 8359));
        assertFalse(MediaAdDetector.isAd(PKG_YOUTUBE, "Wiz Khalifa - See You Again", "Wiz Khalifa", null, 237_000, 8615));
        // Rule is YouTube-only until verified for YT Music
        assertFalse(MediaAdDetector.isAd(PKG_YT_MUSIC, "Song", "Artist", null, 200_000, 8359));
    }

    @Test
    public void testYouTubeVideos_areNotAds() {
        assertFalse(MediaAdDetector.isAd(PKG_YOUTUBE, "Adele - Hello", "AdeleVEVO", null, 366_000, -1));
        assertFalse(MediaAdDetector.isAd(PKG_YOUTUBE, "Advertising explained", "Channel", null, 600_000, -1));
        assertFalse(MediaAdDetector.isAd(PKG_YT_MUSIC, "Ad Astra", "Composer", null, 240_000, -1));
    }
}
