package com.adshield;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.adshield.data.model.ShieldStatistics;
import com.adshield.data.model.VpnState;
import com.adshield.service.AdShieldVpnService;

import org.junit.Test;

public class ProtectionStateTest {

    @Test
    public void testVpnState_isProtectedProperty() {
        assertFalse(VpnState.DISCONNECTED.isProtected());
        assertFalse(VpnState.CONNECTING.isProtected());
        assertTrue(VpnState.CONNECTED.isProtected());
        assertFalse(VpnState.DISCONNECTING.isProtected());
    }

    @Test
    public void testShieldStatistics_defaultValuesAreZero() {
        ShieldStatistics stats = new ShieldStatistics();
        assertEquals(0L, stats.getBlockedRequests());
        assertEquals(0L, stats.getTotalQueries());
        assertNull(stats.getProtectionStartTimeMillis());
    }

    @Test
    public void testServiceConstants() {
        assertEquals("com.adshield.action.START", AdShieldVpnService.ACTION_START);
        assertEquals("com.adshield.action.STOP", AdShieldVpnService.ACTION_STOP);
        assertEquals(VpnState.DISCONNECTED, AdShieldVpnService.getVpnStateLiveData().getValue());
    }
}
