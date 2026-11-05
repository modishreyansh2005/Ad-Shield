package com.adshield.dns;

public final class DnsType {
    public static final int A = 1;
    public static final int NS = 2;
    public static final int CNAME = 5;
    public static final int SOA = 6;
    public static final int PTR = 12;
    public static final int MX = 15;
    public static final int TXT = 16;
    public static final int AAAA = 28;
    public static final int SRV = 33;
    public static final int HTTPS = 65;
    public static final int ANY = 255;

    private DnsType() {}

    public static String getTypeName(int type) {
        switch (type) {
            case A: return "A";
            case AAAA: return "AAAA";
            case CNAME: return "CNAME";
            case NS: return "NS";
            case SOA: return "SOA";
            case PTR: return "PTR";
            case MX: return "MX";
            case TXT: return "TXT";
            case HTTPS: return "HTTPS";
            case ANY: return "ANY";
            default: return "TYPE_" + type;
        }
    }
}
