package com.aurora.vpn;
public class NodeSearchTest {
    public static void main(String[] args){
        Node japan=new Node("8.8.4.4","Japan","JP",443,1,"");
        for(String query:new String[]{"日本","Japan","JAPAN","jp","8.8.4"," 日本 ",""})
            if(!japan.matches(query))throw new AssertionError(query);
        if(japan.matches("美国"))throw new AssertionError("Wrong country");
        if(!new Node("8.8.8.8","United States","US",443,1,"").matches("美国"))throw new AssertionError("US alias");
        System.out.println("Passed 9 Chinese/English/IP node search checks");
    }
}
