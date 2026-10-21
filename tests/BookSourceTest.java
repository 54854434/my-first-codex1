package com.aurora.vpn;
import java.nio.file.*;
import java.util.*;
public final class BookSourceTest {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args)throws Exception{
        String page="VPN Credentials<code>vpnbook</code><code>example123</code>"+
            "{\"hostname\":\"de20.vpnbook.com\",\"ipAddress\":\"8.8.8.8\",\"countryCode\":\"DE\",\"countryName\":\"Germany\"}";
        String[] login=BookSource.credentials(page);check(login[0].equals("vpnbook")&&login[1].equals("example123"),"Dynamic public credential extraction");
        check(BookSource.servers(page).size()==1,"Server metadata extraction");
        check(BookSource.servers(page.replace("8.8.8.8","127.0.0.1")).isEmpty(),"Private endpoints rejected");
        String raw="client\ndev tun1\nproto tcp\nremote de20.vpnbook.com 443\nauth-user-pass\nfast-io\n<ca>\nfixture\n</ca>\n";
        Node n=BookSource.profile(BookSource.servers(page).get(0),raw,login);
        check(n.source.equals("VPNBook")&&n.config.contains("dev tun\n"),"Android tun normalization");
        check(n.config.contains("<auth-user-pass>\nvpnbook\nexample123\n</auth-user-pass>"),"Public provider credentials retained");
        List<Node> restored=GateParser.parse(Discovery.encodeNodes(Arrays.asList(n,n)));
        check(restored.size()==1&&restored.get(0).source.equals("VPNBook")&&restored.get(0).config.equals(n.config),"Snapshot source/auth round-trip and dedup");
        List<Node> all=new ArrayList<>();for(int i=1;i<45;i++)all.add(new Node("9.0.0."+i,"Japan","JP",443,1000,"fixture"));all.add(n);
        check(Discovery.balanced(all,40).contains(n),"Additional provider must not be excluded by advertised bandwidth");
        if(args.length==2){String actual=Files.readString(Path.of(args[0]));String config=Files.readString(Path.of(args[1]));List<BookSource.Server> servers=BookSource.servers(actual);check(!servers.isEmpty(),"Real provider metadata");BookSource.profile(servers.get(0),config,BookSource.credentials(actual));}
        System.out.println("Public provider auth, normalization, source balancing and snapshot checks passed");
    }
}
