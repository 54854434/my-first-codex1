package com.aurora.vpn;
public final class DiagnosticLogTest {
    public static void main(String[] args){
        if(DiagnosticLog.error("Options error: unknown option")==null)throw new AssertionError();
        if(DiagnosticLog.error("-----BEGIN PRIVATE KEY-----")!=null)throw new AssertionError();
        if(DiagnosticLog.error("MANAGEMENT: CMD failed secret")!=null)throw new AssertionError();
        if(DiagnosticLog.error("password authentication failed secret")!=null)throw new AssertionError();
        if(DiagnosticLog.error("Cannot load private key secret").contains("secret"))throw new AssertionError();
        if(DiagnosticLog.error("Process exited with exit value 1")==null)throw new AssertionError();
        System.out.println("Native diagnostic redaction checks passed");
    }
}
