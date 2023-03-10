package ac.seven.CDN.Syncher;

import ac.seven.CDN.Lock.Settings;

public class ChunkConfig {

    public static Boolean disablePhysics() {
        return !Settings.TickChunks;
    }

    public static Boolean shouldDisableChunkTicking() {
        return !Settings.TickChunks;
    }
}
