package com.aurora.vpn;
import java.io.*;
import java.util.zip.*;
public class ApkSeedCheck {
    public static void main(String[] args)throws Exception{
        try(ZipFile apk=new ZipFile(args[0])){
            ZipEntry entry=apk.getEntry("assets/gate-seed.csv");
            if(entry==null)throw new AssertionError("APK is missing bootstrap snapshot");
            try(InputStream input=apk.getInputStream(entry);ByteArrayOutputStream output=new ByteArrayOutputStream()){
                byte[] b=new byte[8192];int n;
                while((n=input.read(b))!=-1){if(output.size()+n>5*1024*1024)throw new AssertionError("Oversize snapshot");output.write(b,0,n);}
                FeedSnapshot snapshot=FeedSnapshot.decode(output.toString("UTF-8"),System.currentTimeMillis());
                System.out.println("Verified APK bootstrap: "+snapshot.nodes.size()+" real, dated public candidates");
            }
        }
    }
}
