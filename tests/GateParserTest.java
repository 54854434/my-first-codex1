package com.aurora.vpn;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class GateParserTest {
    static int checks;
    static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Check "+checks);}
    static void rejects(Runnable call){try{call.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException expected){checks++;}}
    static String config="client\ndev tun\nproto tcp\nremote demo.opengw.net 443\nremote-cert-tls server\n<ca>\nTEST CERTIFICATE\n</ca>\n";
    public static void main(String[] args){
        check(GateParser.csv("a,\"South Korea, Republic of\",\"a\"\"b\"").equals(Arrays.asList("a","South Korea, Republic of","a\"b")));
        rejects(()->GateParser.csv("\"unclosed"));
        for(String ip:Arrays.asList("127.0.0.1","10.0.0.5","172.16.1.1","192.168.1.1","169.254.169.254","100.64.0.1","::1","8.8.8.999","8.8.8.8.example"))check(!GateParser.publicIp(ip));
        Node n=GateParser.parseConfig("8.8.4.4","Japan","JP",100000000,config);
        check(n.port==443 && n.config.contains("remote 8.8.4.4 443"));
        rejects(()->GateParser.parseConfig("8.8.4.4","Japan","JP",1,config+"up /tmp/evil\n"));
        rejects(()->GateParser.parseConfig("8.8.4.4","Japan","JP",1,config+"auth-user-pass /etc/passwd\n"));
        rejects(()->GateParser.parseConfig("8.8.4.4","Japan","JP",1,config.replace("proto tcp","proto udp")));
        rejects(()->GateParser.parseConfig("8.8.4.4","Japan","JP",1,config+"remote 8.8.8.8 443\n"));
        rejects(()->GateParser.parseConfig("8.8.4.4","Japan","JP",1,config.replace("</ca>","")));
        String header="*vpn_servers\n#HostName,IP,CountryLong,CountryShort,Speed,OpenVPN_ConfigData_Base64\n";
        String encoded=Base64.getEncoder().encodeToString(config.getBytes(StandardCharsets.UTF_8));
        String row="host,8.8.4.4,Japan,JP,100000000,"+encoded+"\n";
        check(GateParser.parse(header+row+row+"bad,row\n*").size()==1);
        rejects(()->GateParser.parse("<html>blocked</html>"));
        check(GateParser.parseConfig("8.8.4.4","Japan","JP",1,config+"auth-user-pass\n").config.contains("<auth-user-pass>\nvpn\nvpn\n</auth-user-pass>"));
        System.out.println("Passed "+checks+" parser/configuration checks");
    }
}
