package com.adshield.dns;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class DnsParser {

    private static final int HEADER_SIZE = 12;

    private DnsParser() {}

    public static DnsQuery parseQuery(byte[] data, int length) {
        if (data == null || length < HEADER_SIZE) {
            return null;
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(data, 0, length);
            int id = buffer.getShort() & 0xFFFF;
            int flags = buffer.getShort() & 0xFFFF;
            int qdCount = buffer.getShort() & 0xFFFF;

            // Must have at least 1 question
            if (qdCount < 1) {
                return null;
            }

            // Skip remaining header bytes (ANCOUNT, NSCOUNT, ARCOUNT) to reach Question offset 12
            buffer.position(HEADER_SIZE);

            int questionStart = buffer.position();
            String domainName = parseDomainName(data, buffer, length);
            if (domainName == null) {
                return null;
            }

            if (buffer.remaining() < 4) {
                return null;
            }

            int qtype = buffer.getShort() & 0xFFFF;
            int qclass = buffer.getShort() & 0xFFFF;
            int questionEnd = buffer.position();

            byte[] questionRawBytes = Arrays.copyOfRange(data, questionStart, questionEnd);
            DnsQuestion question = new DnsQuestion(domainName, qtype, qclass, questionRawBytes);

            return new DnsQuery(id, flags, question, Arrays.copyOf(data, length));
        } catch (Exception e) {
            return null;
        }
    }

    public static DnsQuery parseQuery(byte[] data) {
        return parseQuery(data, data != null ? data.length : 0);
    }

    private static String parseDomainName(byte[] data, ByteBuffer buffer, int maxLength) {
        List<String> parts = new ArrayList<>();
        boolean jumped = false;
        int jumpsCount = 0;
        int maxJumps = 5;
        int currentPos = buffer.position();

        while (currentPos < maxLength) {
            int len = data[currentPos] & 0xFF;

            if (len == 0) {
                if (!jumped) {
                    buffer.position(currentPos + 1);
                }
                break;
            }

            // Compression pointer: top 2 bits set (0xC0)
            if ((len & 0xC0) == 0xC0) {
                if (currentPos + 1 >= maxLength) return null;
                int pointerOffset = ((len & 0x3F) << 8) | (data[currentPos + 1] & 0xFF);
                if (pointerOffset >= maxLength) return null;

                if (!jumped) {
                    buffer.position(currentPos + 2);
                    jumped = true;
                }

                currentPos = pointerOffset;
                jumpsCount++;
                if (jumpsCount > maxJumps) {
                    return null;
                }
                continue;
            }

            // Standard label (1 to 63 bytes)
            if (len > 63 || currentPos + 1 + len > maxLength) {
                return null;
            }

            byte[] labelBytes = new byte[len];
            System.arraycopy(data, currentPos + 1, labelBytes, 0, len);
            parts.add(new String(labelBytes, StandardCharsets.US_ASCII));

            currentPos += 1 + len;
            if (!jumped) {
                buffer.position(currentPos);
            }
        }

        if (parts.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            sb.append(parts.get(i));
            if (i < parts.size() - 1) {
                sb.append('.');
            }
        }
        return sb.toString();
    }
}
