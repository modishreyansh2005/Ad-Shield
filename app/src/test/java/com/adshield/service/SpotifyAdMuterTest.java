package com.adshield.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpotifyAdMuterTest {

    @Test
    public void testIsAdTrack_detectsAdMetadata() {
        assertTrue(SpotifyAdMuter.isAdTrack("spotify:ad:000000012c603a6600000020316a17a1", "", ""));
        assertTrue(SpotifyAdMuter.isAdTrack("SPOTIFY:AD:abc", "Some Brand", "Listen now"));
        assertTrue(SpotifyAdMuter.isAdTrack("", "Spotify", "Advertisement"));
        assertTrue(SpotifyAdMuter.isAdTrack(null, null, null));
    }

    @Test
    public void testIsAdTrack_ignoresRealMusicAndPodcasts() {
        assertFalse(SpotifyAdMuter.isAdTrack("spotify:track:4uLU6hMCjMI75M1A2tKUQC", "Rick Astley", "Never Gonna Give You Up"));
        assertFalse(SpotifyAdMuter.isAdTrack("spotify:episode:512ojhOuo1ktJprKbVcKyQ", "Some Podcast", "Episode 1"));
        assertFalse(SpotifyAdMuter.isAdTrack("spotify:track:abc", "Advertisement Band Tribute", "Song"));
    }

    @Test
    public void testAdSafetyDelay_handlesSecondsMillisAndUnknown() {
        assertEquals(33_000L, SpotifyAdMuter.adSafetyDelayMs(30));
        assertEquals(33_000L, SpotifyAdMuter.adSafetyDelayMs(30_000));
        assertEquals(45_000L, SpotifyAdMuter.adSafetyDelayMs(0));
        assertEquals(123_000L, SpotifyAdMuter.adSafetyDelayMs(600));
    }
}
