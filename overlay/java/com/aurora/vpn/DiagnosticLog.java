package com.aurora.vpn;
import java.util.Locale;
/** Only error milestones are retained; profile bodies, keys, credentials and management commands are excluded. */
public final class DiagnosticLog {
    public static String error(String raw){
        if(raw==null)return null;
        String lower=raw.toLowerCase(Locale.ROOT);
        if(raw.contains("-----")||raw.contains("<")||lower.contains("management: cmd")||lower.contains("password")||lower.contains("auth-token"))return null;
        if(!(lower.contains("options error")||lower.contains("cannot ")||lower.contains("failed")||lower.contains("fatal")||lower.contains("error:")||lower.contains("exited with")||lower.contains("permission denied")||lower.contains("linker")||lower.contains("dlopen")))return null;
        if(lower.contains("private key"))return "Private key load error (details redacted)";
        String single=raw.replace('\n',' ').replace('\r',' ');
        return single.substring(0,Math.min(single.length(),420));
    }
}
