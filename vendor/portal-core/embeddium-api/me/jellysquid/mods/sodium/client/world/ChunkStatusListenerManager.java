package me.jellysquid.mods.sodium.client.world;

// Compile-time ABI only. Provided at runtime by Embeddium; never packaged in TWOH.
public interface ChunkStatusListenerManager {
    void setListener(ChunkStatusListener listener);
}
