package com.adshield.dns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.adshield.data.model.DnsLogEntry;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public class DnsFilteringEngineTest {

    private byte[] createDnsQueryPayload(int id, String domain, int qtype) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write((id >> 8) & 0xFF);
        out.write(id & 0xFF);
        out.write(0x01); // RD = 1
        out.write(0x00);
        out.write(0x00); // QDCOUNT = 1
        out.write(0x01);
        out.write(new byte[6], 0, 6); // AN, NS, AR = 0

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
        return out.toByteArray();
    }

    @Test
    public void testBlockedDomain_returnsSinkholeIpPacket() {
        AtomicReference<DnsLogEntry> reportedEntry = new AtomicReference<>();
        DomainBlocklist blocklist = new DomainBlocklist();

        DnsFilteringEngine engine = new DnsFilteringEngine(
                null,
                blocklist,
                null,
                null,
                null
        );
        engine.setQueryListener(reportedEntry::set);

        byte[] dnsQuery = createDnsQueryPayload(0x7777, "doubleclick.net", DnsType.A);
        byte[] clientIp = new byte[]{10, 0, 0, 2};
        byte[] dnsServerIp = new byte[]{10, 0, 0, 1};

        byte[] incomingIpPacket = IpPacketUtil.buildIpv4UdpPacket(
                clientIp,
                dnsServerIp,
                45000,
                53,
                dnsQuery,
                1
        );

        byte[] responseIpPacket = engine.processPacket(incomingIpPacket, incomingIpPacket.length);
        assertNotNull(responseIpPacket);
        assertNotNull(reportedEntry.get());
        assertEquals("doubleclick.net", reportedEntry.get().getDomain());
        assertEquals(true, reportedEntry.get().isBlocked());

        // Parse response packet
        IpPacketUtil.ParsedUdpPacket parsed = IpPacketUtil.parseUdp(responseIpPacket, responseIpPacket.length);
        assertNotNull(parsed);
        assertEquals(53, parsed.getSrcPort());
        assertEquals(45000, parsed.getDstPort());

        // Parse DNS response payload
        ByteBuffer dnsBuf = ByteBuffer.wrap(parsed.getPayload());
        assertEquals(0x7777, dnsBuf.getShort() & 0xFFFF);
        assertEquals(0x8180, dnsBuf.getShort() & 0xFFFF); // NoError Response
        assertEquals(1, dnsBuf.getShort() & 0xFFFF); // QDCOUNT
        assertEquals(1, dnsBuf.getShort() & 0xFFFF); // ANCOUNT
    }

    @Test
    public void testNonDnsPortPacket_isIgnored() {
        DnsFilteringEngine engine = new DnsFilteringEngine(
                null,
                new DomainBlocklist(),
                null,
                null,
                null
        );

        byte[] nonDnsPacket = IpPacketUtil.buildIpv4UdpPacket(
                new byte[]{10, 0, 0, 2},
                new byte[]{10, 0, 0, 1},
                50000,
                8080, // Not port 53
                new byte[]{1, 2, 3},
                2
        );

        byte[] response = engine.processPacket(nonDnsPacket, nonDnsPacket.length);
        assertNull("Non-DNS packets must be ignored", response);
    }
}
