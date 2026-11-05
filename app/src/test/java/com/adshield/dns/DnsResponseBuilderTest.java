package com.adshield.dns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class DnsResponseBuilderTest {

    private DnsQuery createDummyQuery(int id, String domain, int qtype) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String part : domain.split("\\.")) {
            if (!part.isEmpty()) {
                out.write(part.length());
                byte[] bytes = part.getBytes(StandardCharsets.US_ASCII);
                out.write(bytes, 0, bytes.length);
            }
        }
        out.write(0x00);
        out.write((qtype >> 8) & 0xFF);
        out.write(qtype & 0xFF);
        out.write(0x00);
        out.write(0x01); // Class IN

        byte[] qBytes = out.toByteArray();
        DnsQuestion question = new DnsQuestion(domain, qtype, 1, qBytes);
        return new DnsQuery(id, 0x0100, question, new byte[0]);
    }

    @Test
    public void testBuildSinkholeAResponse() {
        DnsQuery query = createDummyQuery(0x5678, "doubleclick.net", DnsType.A);
        byte[] responseBytes = DnsResponseBuilder.buildSinkholeResponse(query);

        ByteBuffer buffer = ByteBuffer.wrap(responseBytes);
        int id = buffer.getShort() & 0xFFFF;
        int flags = buffer.getShort() & 0xFFFF;
        int qdCount = buffer.getShort() & 0xFFFF;
        int anCount = buffer.getShort() & 0xFFFF;

        assertEquals(0x5678, id);
        // 0x8180: QR = 1, RD = 1, RA = 1, RCODE = 0
        assertEquals(0x8180, flags);
        assertEquals(1, qdCount);
        assertEquals(1, anCount);

        // Skip question section
        buffer.position(12 + query.getQuestion().getRawBytes().length);

        // Read answer
        int namePointer = buffer.getShort() & 0xFFFF;
        assertEquals(0xC00C, namePointer); // Pointer to byte 12

        int answerType = buffer.getShort() & 0xFFFF;
        int answerClass = buffer.getShort() & 0xFFFF;
        int ttl = buffer.getInt();
        int rdLength = buffer.getShort() & 0xFFFF;
        int ip1 = buffer.get() & 0xFF;
        int ip2 = buffer.get() & 0xFF;
        int ip3 = buffer.get() & 0xFF;
        int ip4 = buffer.get() & 0xFF;

        assertEquals(DnsType.A, answerType);
        assertEquals(1, answerClass);
        assertEquals(300, ttl);
        assertEquals(4, rdLength);
        assertEquals(0, ip1);
        assertEquals(0, ip2);
        assertEquals(0, ip3);
        assertEquals(0, ip4); // 0.0.0.0
    }

    @Test
    public void testBuildSinkholeAAAAResponse() {
        DnsQuery query = createDummyQuery(0x9999, "adservice.google.com", DnsType.AAAA);
        byte[] responseBytes = DnsResponseBuilder.buildSinkholeResponse(query);

        ByteBuffer buffer = ByteBuffer.wrap(responseBytes);
        assertEquals(0x9999, buffer.getShort() & 0xFFFF);
        assertEquals(0x8180, buffer.getShort() & 0xFFFF);
        buffer.position(12 + query.getQuestion().getRawBytes().length);

        assertEquals(0xC00C, buffer.getShort() & 0xFFFF);
        assertEquals(DnsType.AAAA, buffer.getShort() & 0xFFFF);
        assertEquals(1, buffer.getShort() & 0xFFFF);
        assertEquals(300, buffer.getInt());
        assertEquals(16, buffer.getShort() & 0xFFFF); // 16 bytes for IPv6

        // Check 16 zero bytes
        byte[] ipv6Bytes = new byte[16];
        buffer.get(ipv6Bytes);
        for (byte b : ipv6Bytes) {
            assertEquals(0, b);
        }
    }

    @Test
    public void testBuildServFailResponse() {
        DnsQuery query = createDummyQuery(0x1111, "example.com", DnsType.A);
        byte[] responseBytes = DnsResponseBuilder.buildServFailResponse(query);

        ByteBuffer buffer = ByteBuffer.wrap(responseBytes);
        assertEquals(0x1111, buffer.getShort() & 0xFFFF);
        int flags = buffer.getShort() & 0xFFFF;
        // RCODE is the last 4 bits. 0x0002 is SERVFAIL
        assertEquals(2, flags & 0x000F);
    }
}
