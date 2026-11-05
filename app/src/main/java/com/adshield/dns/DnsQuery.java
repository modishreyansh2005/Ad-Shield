package com.adshield.dns;

import java.util.Arrays;

public class DnsQuery {
    private final int id;
    private final int flags;
    private final DnsQuestion question;
    private final byte[] rawData;

    public DnsQuery(int id, int flags, DnsQuestion question, byte[] rawData) {
        this.id = id;
        this.flags = flags;
        this.question = question;
        this.rawData = rawData;
    }

    public int getId() {
        return id;
    }

    public int getFlags() {
        return flags;
    }

    public DnsQuestion getQuestion() {
        return question;
    }

    public byte[] getRawData() {
        return rawData;
    }

    public boolean isStandardQuery() {
        return (flags & 0x7800) == 0;
    }

    public boolean isRecursionDesired() {
        return (flags & 0x0100) != 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DnsQuery dnsQuery = (DnsQuery) o;
        if (id != dnsQuery.id) return false;
        if (flags != dnsQuery.flags) return false;
        if (question != null ? !question.equals(dnsQuery.question) : dnsQuery.question != null) return false;
        return Arrays.equals(rawData, dnsQuery.rawData);
    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + flags;
        result = 31 * result + (question != null ? question.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(rawData);
        return result;
    }
}
