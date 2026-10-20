package com.aurora.vpn;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Build-time public bootstrap snapshot. Fails rather than shipping invented or empty nodes. */
public class GateSeedBuilder {
    public static void main(String[] args)throws Exception{
        FeedSnapshot feed=FeedSnapshot.online(Discovery.download(),System.currentTimeMillis());
        Path path=Paths.get(args[0]);Files.createDirectories(path.getParent());
        Files.write(path,feed.encode().getBytes(StandardCharsets.UTF_8));
        System.out.println("Bundled "+feed.nodes.size()+" dated public TCP candidates (not connection-tested)");
    }
}
