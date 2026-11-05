package com.adshield.dns;

import java.io.ByteArrayOutputStream;

public final class DnsResponseBuilder {

    private static final int DEFAULT_TTL_SECONDS = 300;

    private DnsResponseBuilder() {}

    public static byte[] buildSinkholeResponse(DnsQuery query) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int qtype = query.getQuestion().getType();

        // 1. Transaction ID
        out.write((query.getId() >> 8) & 0xFF);
        out.write(query.getId() & 0xFF);

        // 2. Flags: 0x8180 (Response, Recursion Available, No Error)
        int rdFlag = query.isRecursionDesired() ? 0x01 : 0x00;
        out.write(0x80 | rdFlag);
        out.write(0x80);

        // 3. QDCOUNT: 1
        out.write(0x00);
        out.write(0x01);

        // 4. ANCOUNT: 1 for A and AAAA, 0 for others
        boolean hasAnswer = (qtype == DnsType.A || qtype == DnsType.AAAA);
        if (hasAnswer) {
            out.write(0x00);
            out.write(0x01);
        } else {
            out.write(0x00);
            out.write(0x00);
        }

        // 5. NSCOUNT: 0, ARCOUNT: 0
        out.write(0x00);
        out.write(0x00);
        out.write(0x00);
        out.write(0x00);

        // 6. Question section copy
        byte[] questionBytes = query.getQuestion().getRawBytes();
        out.write(questionBytes, 0, questionBytes.length);

        // 7. Answer section
        if (hasAnswer) {
            // NAME pointer to offset 12 (0xC00C)
            out.write(0xC0);
            out.write(0x0C);

            if (qtype == DnsType.A) {
                // TYPE: A (1)
                out.write(0x00);
                out.write(0x01);
                // CLASS: IN (1)
                out.write(0x00);
                out.write(0x01);
                // TTL: 300s
                out.write((DEFAULT_TTL_SECONDS >> 24) & 0xFF);
                out.write((DEFAULT_TTL_SECONDS >> 16) & 0xFF);
                out.write((DEFAULT_TTL_SECONDS >> 8) & 0xFF);
                out.write(DEFAULT_TTL_SECONDS & 0xFF);
                // RDLENGTH: 4 bytes
                out.write(0x00);
                out.write(0x04);
                // RDATA: 0.0.0.0
                out.write(0x00);
                out.write(0x00);
                out.write(0x00);
                out.write(0x00);
            } else {
                // TYPE: AAAA (28 = 0x1C)
                out.write(0x00);
                out.write(0x1C);
                // CLASS: IN (1)
                out.write(0x00);
                out.write(0x01);
                // TTL: 300s
                out.write((DEFAULT_TTL_SECONDS >> 24) & 0xFF);
                out.write((DEFAULT_TTL_SECONDS >> 16) & 0xFF);
                out.write((DEFAULT_TTL_SECONDS >> 8) & 0xFF);
                out.write(DEFAULT_TTL_SECONDS & 0xFF);
                // RDLENGTH: 16 bytes
                out.write(0x00);
                out.write(0x10);
                // RDATA: :: (16 zero bytes)
                byte[] zeroes = new byte[16];
                out.write(zeroes, 0, 16);
            }
        }

        return out.toByteArray();
    }

    public static byte[] buildServFailResponse(DnsQuery query) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Transaction ID
        out.write((query.getId() >> 8) & 0xFF);
        out.write(query.getId() & 0xFF);

        // Flags: 0x8182 (QR=1, RD=1, RA=1, RCODE=2 Server Failure)
        int rdFlag = query.isRecursionDesired() ? 0x01 : 0x00;
        out.write(0x80 | rdFlag);
        out.write(0x82);

        // QDCOUNT: 1
        out.write(0x00);
        out.write(0x01);

        // ANCOUNT, NSCOUNT, ARCOUNT: 0
        out.write(0x00);
        out.write(0x00);
        out.write(0x00);
        out.write(0x00);
        out.write(0x00);
        out.write(0x00);

        // Question section
        byte[] questionBytes = query.getQuestion().getRawBytes();
        out.write(questionBytes, 0, questionBytes.length);

        return out.toByteArray();
    }
}
