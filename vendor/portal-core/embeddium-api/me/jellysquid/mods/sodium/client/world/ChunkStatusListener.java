package me.jellysquid.mods.sodium.client.world;

// Compile-time ABI only. Provided at runtime by Embeddium; never packaged in TWOH.
public interface ChunkStatusListener {
    void onChunkAdded(int x, int z);
    void onChunkRemoved(int x, int z);
}
