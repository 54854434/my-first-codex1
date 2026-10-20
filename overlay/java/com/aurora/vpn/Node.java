package com.aurora.vpn;

import java.util.Locale;

public final class Node {
    public final String ip, country, code, config, source;
    public final int port;
    public final long advertisedBps;
    public volatile int latencyMs = -1;
    public volatile long checkedAt;
    public Node(String ip, String country, String code, int port, long bps, String config) {
        this(ip,country,code,port,bps,config,"VPN Gate");
    }
    public Node(String ip,String country,String code,int port,long bps,String config,String source){
        this.source=source;this.ip=ip; this.country=country; this.code=code; this.port=port;
        this.advertisedBps=bps; this.config=config;
    }
    public boolean matches(String query){
        String aliases;
        switch(code){
            case "JP": aliases="日本";break;case "KR": aliases="韩国 南韩";break;
            case "US": aliases="美国 美利坚";break;case "CA": aliases="加拿大";break;
            case "GB": case "UK": aliases="英国 英格兰";break;case "DE": aliases="德国";break;
            case "NL": aliases="荷兰";break;case "SG": aliases="新加坡";break;
            case "HK": aliases="香港 中国香港";break;case "TW": aliases="台湾 中国台湾";break;
            case "FR": aliases="法国";break;case "AU": aliases="澳大利亚 澳洲";break;
            case "RU": aliases="俄罗斯";break;case "TH": aliases="泰国";break;
            case "VN": aliases="越南";break;case "IN": aliases="印度";break;
            case "BE": aliases="比利时";break;case "CH": aliases="瑞士";break;
            case "FI": aliases="芬兰";break;case "ES": aliases="西班牙";break;
            case "TR": aliases="土耳其";break;case "AR": aliases="阿根廷";break;
            case "IE": aliases="爱尔兰";break;case "CN": aliases="中国 大陆";break;
            default: aliases="";
        }
        return (country+" "+code+" "+ip+" "+aliases).toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));
    }
    public String flag() {
        if (!code.matches("[A-Z]{2}")) return "🌐";
        return new String(Character.toChars(0x1F1E6+code.charAt(0)-'A'))
            + new String(Character.toChars(0x1F1E6+code.charAt(1)-'A'));
    }
}

