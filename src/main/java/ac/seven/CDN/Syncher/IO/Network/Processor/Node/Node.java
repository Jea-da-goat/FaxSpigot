package ac.seven.CDN.Syncher.IO.Network.Processor.Node;

import ac.seven.CDN.Syncher.Chunk.ChunkSyncher;
import ac.seven.CDN.Syncher.Chunk.ChunkTicker;
import ac.seven.CDN.Syncher.ChunkSyncManager;

import java.util.HashMap;
import java.util.Map;

public class Node {

    public static void read(HashMap<String, Object> messages) {
        for(Map.Entry<String, Object> message : messages.entrySet()) {
            switch (message.getKey()) {
                case "blockUpdate":
                    HashMap<String, String> blockupdates = (HashMap<String, String>) message.getValue();
                    blockupdates.entrySet().forEach(set -> {
                        ChunkSyncManager.registerBlockUpdate(set.getKey(), set.getValue());
                    });
                    break;
                case "chunkUpdate":
                    HashMap<String, HashMap<String, String>> chunkupdates = (HashMap<String, HashMap<String, String>>) message.getValue();
                    chunkupdates.entrySet().forEach(set -> {
                        //ChunkSyncher.registerChunkUpdate(set.getKey(), set.getValue());
                        ChunkTicker.loadChunk(set.getKey(), set.getValue());
                    });
                    break;
                case "unloadChunk":
                    HashMap<String, String> chunkUnloads = (HashMap<String, String>) message.getValue();
                    chunkUnloads.keySet().forEach(ChunkTicker::unloadChunk);
                    break;
                default:
                    break;
            }
        }
    }
}
