package com.adshield.dns;

import java.util.Arrays;

public class DnsQuestion {
    private final String name;
    private final int type;
    private final int clazz;
    private final byte[] rawBytes;

    public DnsQuestion(String name, int type, int clazz, byte[] rawBytes) {
        this.name = name;
        this.type = type;
        this.clazz = clazz;
        this.rawBytes = rawBytes;
    }

    public String getName() {
        return name;
    }

    public int getType() {
        return type;
    }

    public int getClazz() {
        return clazz;
    }

    public byte[] getRawBytes() {
        return rawBytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DnsQuestion that = (DnsQuestion) o;
        if (type != that.type) return false;
        if (clazz != that.clazz) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        return Arrays.equals(rawBytes, that.rawBytes);
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + type;
        result = 31 * result + clazz;
        result = 31 * result + Arrays.hashCode(rawBytes);
        return result;
    }
}
