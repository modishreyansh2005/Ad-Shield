package com.adshield.dns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class DnsParserTest {

    private byte[] createDnsQueryPacket(int id, String domain, int qtype) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // ID
        out.write((id >> 8) & 0xFF);
        out.write(id & 0xFF);
        // Flags: 0x0100 (Standard query, RD = 1)
        out.write(0x01);
        out.write(0x00);
        // QDCOUNT: 1
        out.write(0x00);
        out.write(0x01);
        // ANCOUNT, NSCOUNT, ARCOUNT: 0
        out.write(new byte[6], 0, 6);

        // QNAME: length-prefixed labels
        for (String part : domain.split("\\.")) {
            if (!part.isEmpty()) {
                out.write(part.length());
                byte[] bytes = part.getBytes(StandardCharsets.US_ASCII);
                out.write(bytes, 0, bytes.length);
            }
        }
        out.write(0x00); // Root label

        // QTYPE
        out.write((qtype >> 8) & 0xFF);
        out.write(qtype & 0xFF);
        // QCLASS: 1 (IN)
        out.write(0x00);
        out.write(0x01);

        return out.toByteArray();
    }

    @Test
    public void testParseStandardAQuery() {
        byte[] raw = createDnsQueryPacket(0x1234, "doubleclick.net", DnsType.A);
        DnsQuery query = DnsParser.parseQuery(raw);

        assertNotNull(query);
        assertEquals(0x1234, query.getId());
        assertTrue(query.isStandardQuery());
        assertTrue(query.isRecursionDesired());
        assertEquals("doubleclick.net", query.getQuestion().getName());
        assertEquals(DnsType.A, query.getQuestion().getType());
        assertEquals(1, query.getQuestion().getClazz());
    }

    @Test
    public void testParseSubdomainAAAAQuery() {
        byte[] raw = createDnsQueryPacket(0xABCD, "pagead2.googlesyndication.com", DnsType.AAAA);
        DnsQuery query = DnsParser.parseQuery(raw);

        assertNotNull(query);
        assertEquals(0xABCD, query.getId());
        assertEquals("pagead2.googlesyndication.com", query.getQuestion().getName());
        assertEquals(DnsType.AAAA, query.getQuestion().getType());
    }

    @Test
    public void testParseMalformedPacket_returnsNullWithoutCrashing() {
        // Less than 12-byte header
        assertNull(DnsParser.parseQuery(new byte[6]));

        // Zero length
        assertNull(DnsParser.parseQuery(new byte[0]));

        // Truncated question section
        byte[] truncated = new byte[15];
        for (int i = 0; i < 15; i++) truncated[i] = 0x01;
        assertNull(DnsParser.parseQuery(truncated));

        // Packet with label length pointing out of bounds
        byte[] outOfBounds = new byte[20];
        outOfBounds[4] = 0x00;
        outOfBounds[5] = 0x01; // QDCOUNT = 1
        outOfBounds[12] = 50;  // Label length 50, but packet is only 20 bytes!
        assertNull(DnsParser.parseQuery(outOfBounds));
    }

    @Test
    public void testParseZeroQuestionsPacket_returnsNull() {
        byte[] noQuestions = new byte[12];
        // QDCOUNT is 0
        assertNull(DnsParser.parseQuery(noQuestions));
    }
}
