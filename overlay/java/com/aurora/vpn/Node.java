package com.aurora.vpn;

public final class Node {
    public final String ip, country, code, config;
    public final int port;
    public final long advertisedBps;
    public volatile int latencyMs = -1;
    public volatile long checkedAt;
    public Node(String ip, String country, String code, int port, long bps, String config) {
        this.ip=ip; this.country=country; this.code=code; this.port=port;
        this.advertisedBps=bps; this.config=config;
    }
    public String flag() {
        if (!code.matches("[A-Z]{2}")) return "🌐";
        return new String(Character.toChars(0x1F1E6+code.charAt(0)-'A'))
            + new String(Character.toChars(0x1F1E6+code.charAt(1)-'A'));
    }
}
