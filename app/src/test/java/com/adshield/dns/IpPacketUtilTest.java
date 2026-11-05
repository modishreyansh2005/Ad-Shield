package com.adshield.dns;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class IpPacketUtilTest {

    @Test
    public void testIpAndUdpPacketBuildingAndParsing() {
        byte[] srcIp = new byte[]{10, 0, 0, 1};
        byte[] dstIp = new byte[]{10, 0, 0, 2};
        int srcPort = 53;
        int dstPort = 54321;
        byte[] payload = "Hello DNS World".getBytes(StandardCharsets.UTF_8);

        // Build IPv4 UDP packet
        byte[] packet = IpPacketUtil.buildIpv4UdpPacket(
                srcIp,
                dstIp,
                srcPort,
                dstPort,
                payload,
                42
        );

        // Verify total length: 20 bytes IP header + 8 bytes UDP header + payload.length
        assertEquals(20 + 8 + payload.length, packet.length);

        // Verify IP checksum: sum of 20-byte IP header including checksum must fold to 0xFFFF
        int ipSum = 0;
        for (int i = 0; i < 20; i += 2) {
            ipSum += ((packet[i] & 0xFF) << 8) | (packet[i + 1] & 0xFF);
        }
        while ((ipSum >>> 16) > 0) {
            ipSum = (ipSum & 0xFFFF) + (ipSum >>> 16);
        }
        assertEquals("IP Header Checksum must be valid", 0xFFFF, ipSum);

        // Parse back the generated packet
        IpPacketUtil.ParsedUdpPacket parsed = IpPacketUtil.parseUdp(packet, packet.length);
        assertNotNull(parsed);
        assertArrayEquals(srcIp, parsed.getSrcIp());
        assertArrayEquals(dstIp, parsed.getDstIp());
        assertEquals(srcPort, parsed.getSrcPort());
        assertEquals(dstPort, parsed.getDstPort());
        assertEquals(42, parsed.getIpId());
        assertArrayEquals(payload, parsed.getPayload());
    }

    @Test
    public void testOddLengthPayload_udpChecksumCalculatesCorrectly() {
        byte[] srcIp = new byte[]{10, 0, 0, 1};
        byte[] dstIp = new byte[]{10, 0, 0, 2};
        // Odd length payload: 5 bytes
        byte[] oddPayload = new byte[]{1, 2, 3, 4, 5};

        byte[] packet = IpPacketUtil.buildIpv4UdpPacket(
                srcIp,
                dstIp,
                53,
                12345,
                oddPayload,
                100
        );

        IpPacketUtil.ParsedUdpPacket parsed = IpPacketUtil.parseUdp(packet, packet.length);
        assertNotNull(parsed);
        assertArrayEquals(oddPayload, parsed.getPayload());
    }

    @Test
    public void testIpv6PacketBuildingAndParsing() {
        byte[] srcIp = new byte[]{
                (byte) 0xFD, 0x00, 0, 1, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 2
        };
        byte[] dstIp = new byte[]{
                (byte) 0xFD, 0x00, 0, 1, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 1
        };
        int srcPort = 53535;
        int dstPort = 53;
        byte[] payload = "IPv6 DNS Test Query".getBytes(StandardCharsets.UTF_8);

        byte[] packet = IpPacketUtil.buildIpv6UdpPacket(srcIp, dstIp, srcPort, dstPort, payload);
        assertEquals(40 + 8 + payload.length, packet.length);

        IpPacketUtil.ParsedUdpPacket parsed = IpPacketUtil.parseUdp(packet, packet.length);
        assertNotNull(parsed);
        assertEquals(6, parsed.getIpVersion());
        assertArrayEquals(srcIp, parsed.getSrcIp());
        assertArrayEquals(dstIp, parsed.getDstIp());
        assertEquals(srcPort, parsed.getSrcPort());
        assertEquals(dstPort, parsed.getDstPort());
        assertArrayEquals(payload, parsed.getPayload());
    }
}
