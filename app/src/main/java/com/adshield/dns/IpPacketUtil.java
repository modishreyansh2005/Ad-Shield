package com.adshield.dns;

import java.util.Arrays;

public final class IpPacketUtil {

    private static final int IP_V4 = 4;
    private static final int PROTOCOL_UDP = 17;
    private static final int MIN_IP_HEADER_LEN = 20;
    private static final int UDP_HEADER_LEN = 8;

    private IpPacketUtil() {}

    public static class ParsedUdpPacket {
        private final byte[] srcIp;
        private final byte[] dstIp;
        private final int srcPort;
        private final int dstPort;
        private final int ipId;
        private final byte[] payload;

        public ParsedUdpPacket(byte[] srcIp, byte[] dstIp, int srcPort, int dstPort, int ipId, byte[] payload) {
            this.srcIp = srcIp;
            this.dstIp = dstIp;
            this.srcPort = srcPort;
            this.dstPort = dstPort;
            this.ipId = ipId;
            this.payload = payload;
        }

        public byte[] getSrcIp() {
            return srcIp;
        }

        public byte[] getDstIp() {
            return dstIp;
        }

        public int getSrcPort() {
            return srcPort;
        }

        public int getDstPort() {
            return dstPort;
        }

        public int getIpId() {
            return ipId;
        }

        public byte[] getPayload() {
            return payload;
        }

        public int getIpVersion() {
            return (srcIp != null && srcIp.length == 16) ? 6 : 4;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ParsedUdpPacket that = (ParsedUdpPacket) o;
            if (srcPort != that.srcPort) return false;
            if (dstPort != that.dstPort) return false;
            if (ipId != that.ipId) return false;
            if (!Arrays.equals(srcIp, that.srcIp)) return false;
            if (!Arrays.equals(dstIp, that.dstIp)) return false;
            return Arrays.equals(payload, that.payload);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(srcIp);
            result = 31 * result + Arrays.hashCode(dstIp);
            result = 31 * result + srcPort;
            result = 31 * result + dstPort;
            result = 31 * result + ipId;
            result = 31 * result + Arrays.hashCode(payload);
            return result;
        }
    }

    public static ParsedUdpPacket parseUdp(byte[] data, int length) {
        if (data == null || length < MIN_IP_HEADER_LEN + UDP_HEADER_LEN) {
            return null;
        }

        int version = (data[0] >> 4) & 0x0F;
        if (version == IP_V4) {
            int ihl = (data[0] & 0x0F) * 4;
            if (ihl < MIN_IP_HEADER_LEN || length < ihl + UDP_HEADER_LEN) {
                return null;
            }

            int protocol = data[9] & 0xFF;
            if (protocol != PROTOCOL_UDP) {
                return null;
            }

            int totalLength = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
            int packetEnd = Math.min(length, totalLength);

            int ipId = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
            byte[] srcIp = Arrays.copyOfRange(data, 12, 16);
            byte[] dstIp = Arrays.copyOfRange(data, 16, 20);

            int udpOffset = ihl;
            int srcPort = ((data[udpOffset] & 0xFF) << 8) | (data[udpOffset + 1] & 0xFF);
            int dstPort = ((data[udpOffset + 2] & 0xFF) << 8) | (data[udpOffset + 3] & 0xFF);
            int udpLength = ((data[udpOffset + 4] & 0xFF) << 8) | (data[udpOffset + 5] & 0xFF);

            if (udpLength < UDP_HEADER_LEN) {
                return null;
            }

            int payloadStart = udpOffset + UDP_HEADER_LEN;
            int payloadEnd = Math.min(packetEnd, udpOffset + udpLength);
            if (payloadEnd < payloadStart) {
                return null;
            }

            byte[] payload = Arrays.copyOfRange(data, payloadStart, payloadEnd);
            return new ParsedUdpPacket(srcIp, dstIp, srcPort, dstPort, ipId, payload);
        } else if (version == 6) {
            // IPv6 UDP packet parsing (40 bytes IPv6 header + 8 bytes UDP header)
            if (length < 40 + UDP_HEADER_LEN) {
                return null;
            }

            int nextHeader = data[6] & 0xFF;
            if (nextHeader != PROTOCOL_UDP) {
                return null;
            }

            int payloadLen = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
            int packetEnd = Math.min(length, 40 + payloadLen);

            byte[] srcIp = Arrays.copyOfRange(data, 8, 24);
            byte[] dstIp = Arrays.copyOfRange(data, 24, 40);

            int udpOffset = 40;
            int srcPort = ((data[udpOffset] & 0xFF) << 8) | (data[udpOffset + 1] & 0xFF);
            int dstPort = ((data[udpOffset + 2] & 0xFF) << 8) | (data[udpOffset + 3] & 0xFF);
            int udpLength = ((data[udpOffset + 4] & 0xFF) << 8) | (data[udpOffset + 5] & 0xFF);

            if (udpLength < UDP_HEADER_LEN) {
                return null;
            }

            int payloadStart = udpOffset + UDP_HEADER_LEN;
            int payloadEnd = Math.min(packetEnd, udpOffset + udpLength);
            if (payloadEnd < payloadStart) {
                return null;
            }

            byte[] payload = Arrays.copyOfRange(data, payloadStart, payloadEnd);
            return new ParsedUdpPacket(srcIp, dstIp, srcPort, dstPort, 0, payload);
        }

        return null;
    }

    public static byte[] buildIpv4UdpPacket(byte[] srcIp, byte[] dstIp, int srcPort, int dstPort, byte[] payload, int ipId) {
        int ipHeaderLen = MIN_IP_HEADER_LEN;
        int udpTotalLen = UDP_HEADER_LEN + payload.length;
        int ipTotalLen = ipHeaderLen + udpTotalLen;
        byte[] packet = new byte[ipTotalLen];

        // 1. IP Header
        packet[0] = 0x45; // Version 4, IHL 5
        packet[1] = 0x00;
        packet[2] = (byte) ((ipTotalLen >> 8) & 0xFF);
        packet[3] = (byte) (ipTotalLen & 0xFF);
        packet[4] = (byte) ((ipId >> 8) & 0xFF);
        packet[5] = (byte) (ipId & 0xFF);
        packet[6] = 0x40; // Don't fragment
        packet[7] = 0x00;
        packet[8] = 64;   // TTL
        packet[9] = PROTOCOL_UDP;
        packet[10] = 0x00;
        packet[11] = 0x00;

        System.arraycopy(srcIp, 0, packet, 12, 4);
        System.arraycopy(dstIp, 0, packet, 16, 4);

        int ipChecksum = calculateIpChecksum(packet, 0, ipHeaderLen);
        packet[10] = (byte) ((ipChecksum >> 8) & 0xFF);
        packet[11] = (byte) (ipChecksum & 0xFF);

        // 2. UDP Header
        int udpOffset = ipHeaderLen;
        packet[udpOffset] = (byte) ((srcPort >> 8) & 0xFF);
        packet[udpOffset + 1] = (byte) (srcPort & 0xFF);
        packet[udpOffset + 2] = (byte) ((dstPort >> 8) & 0xFF);
        packet[udpOffset + 3] = (byte) (dstPort & 0xFF);
        packet[udpOffset + 4] = (byte) ((udpTotalLen >> 8) & 0xFF);
        packet[udpOffset + 5] = (byte) (udpTotalLen & 0xFF);
        packet[udpOffset + 6] = 0x00;
        packet[udpOffset + 7] = 0x00;

        // 3. Payload
        System.arraycopy(payload, 0, packet, udpOffset + UDP_HEADER_LEN, payload.length);

        // 4. UDP Checksum
        int udpChecksum = calculateUdpChecksum(srcIp, dstIp, packet, udpOffset, udpTotalLen);
        packet[udpOffset + 6] = (byte) ((udpChecksum >> 8) & 0xFF);
        packet[udpOffset + 7] = (byte) (udpChecksum & 0xFF);

        return packet;
    }

    public static byte[] buildIpv4UdpPacket(byte[] srcIp, byte[] dstIp, int srcPort, int dstPort, byte[] payload) {
        return buildIpv4UdpPacket(srcIp, dstIp, srcPort, dstPort, payload, 0);
    }

    public static int calculateIpChecksum(byte[] data, int offset, int length) {
        int sum = 0;
        int i = offset;
        int end = offset + length;

        while (i < end) {
            int high = data[i] & 0xFF;
            int low = (i + 1 < end) ? (data[i + 1] & 0xFF) : 0;
            sum += (high << 8) | low;
            i += 2;
        }

        while ((sum >> 16) > 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }

        return (~sum) & 0xFFFF;
    }

    public static int calculateUdpChecksum(byte[] srcIp, byte[] dstIp, byte[] packet, int udpOffset, int udpLength) {
        int sum = 0;

        // Pseudo Header: Source IP
        for (int i = 0; i < 4; i += 2) {
            sum += ((srcIp[i] & 0xFF) << 8) | (srcIp[i + 1] & 0xFF);
        }

        // Pseudo Header: Destination IP
        for (int i = 0; i < 4; i += 2) {
            sum += ((dstIp[i] & 0xFF) << 8) | (dstIp[i + 1] & 0xFF);
        }

        sum += PROTOCOL_UDP;
        sum += udpLength;

        // UDP Header & Payload (skip bytes udpOffset+6..7)
        int i = udpOffset;
        int end = udpOffset + udpLength;
        while (i < end) {
            if (i == udpOffset + 6) {
                i += 2;
                continue;
            }
            int high = packet[i] & 0xFF;
            int low = (i + 1 < end) ? (packet[i + 1] & 0xFF) : 0;
            sum += (high << 8) | low;
            i += 2;
        }

        while ((sum >> 16) > 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }

        int checksum = (~sum) & 0xFFFF;
        if (checksum == 0) {
            checksum = 0xFFFF;
        }
        return checksum;
    }

    public static byte[] buildIpv6UdpPacket(byte[] srcIp, byte[] dstIp, int srcPort, int dstPort, byte[] payload) {
        int ipHeaderLen = 40;
        int udpTotalLen = UDP_HEADER_LEN + payload.length;
        int ipTotalLen = ipHeaderLen + udpTotalLen;
        byte[] packet = new byte[ipTotalLen];

        // IPv6 Header (40 bytes)
        packet[0] = 0x60; // Version 6, Traffic Class 0
        packet[1] = 0x00;
        packet[2] = 0x00;
        packet[3] = 0x00;
        packet[4] = (byte) ((udpTotalLen >> 8) & 0xFF); // Payload Length
        packet[5] = (byte) (udpTotalLen & 0xFF);
        packet[6] = (byte) PROTOCOL_UDP; // Next Header: UDP (17)
        packet[7] = 64;                  // Hop Limit

        System.arraycopy(srcIp, 0, packet, 8, 16);
        System.arraycopy(dstIp, 0, packet, 24, 16);

        // UDP Header
        int udpOffset = 40;
        packet[udpOffset] = (byte) ((srcPort >> 8) & 0xFF);
        packet[udpOffset + 1] = (byte) (srcPort & 0xFF);
        packet[udpOffset + 2] = (byte) ((dstPort >> 8) & 0xFF);
        packet[udpOffset + 3] = (byte) (dstPort & 0xFF);
        packet[udpOffset + 4] = (byte) ((udpTotalLen >> 8) & 0xFF);
        packet[udpOffset + 5] = (byte) (udpTotalLen & 0xFF);
        packet[udpOffset + 6] = 0x00;
        packet[udpOffset + 7] = 0x00;

        // Payload
        System.arraycopy(payload, 0, packet, udpOffset + UDP_HEADER_LEN, payload.length);

        // Calculate IPv6 UDP Checksum
        int checksum = calculateIpv6UdpChecksum(srcIp, dstIp, packet, udpOffset, udpTotalLen);
        if (checksum == 0) checksum = 0xFFFF;
        packet[udpOffset + 6] = (byte) ((checksum >> 8) & 0xFF);
        packet[udpOffset + 7] = (byte) (checksum & 0xFF);

        return packet;
    }

    private static int calculateIpv6UdpChecksum(byte[] srcIp, byte[] dstIp, byte[] packet, int udpOffset, int udpLength) {
        long sum = 0;
        for (int i = 0; i < 16; i += 2) {
            sum += ((srcIp[i] & 0xFF) << 8) | (srcIp[i + 1] & 0xFF);
            sum += ((dstIp[i] & 0xFF) << 8) | (dstIp[i + 1] & 0xFF);
        }
        sum += udpLength;
        sum += PROTOCOL_UDP;

        for (int i = 0; i < udpLength; i += 2) {
            if (i == 6) continue;
            if (i + 1 < udpLength) {
                sum += ((packet[udpOffset + i] & 0xFF) << 8) | (packet[udpOffset + i + 1] & 0xFF);
            } else {
                sum += (packet[udpOffset + i] & 0xFF) << 8;
            }
        }

        while ((sum >> 16) > 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }

        return (int) (~sum & 0xFFFF);
    }
}
