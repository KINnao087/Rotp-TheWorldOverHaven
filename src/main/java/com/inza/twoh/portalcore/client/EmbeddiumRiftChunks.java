package com.inza.twoh.portalcore.client;

import com.inza.twoh.portalcore.immersive_portals.chunk_loading.MyClientChunkManager;
import me.jellysquid.mods.sodium.client.world.ChunkStatusListener;
import me.jellysquid.mods.sodium.client.world.ChunkStatusListenerManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.world.biome.BiomeContainer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

/** One listener per client dimension, including the remote view through the rift. */
public final class EmbeddiumRiftChunks extends MyClientChunkManager implements ChunkStatusListenerManager {
    private ChunkStatusListener renderer;

    public static MyClientChunkManager create(ClientWorld world, int distance) {
        return new EmbeddiumRiftChunks(world, distance);
    }

    private EmbeddiumRiftChunks(ClientWorld world, int distance) {
        super(world, distance);
    }

    @Override
    public void setListener(ChunkStatusListener listener) {
        if (renderer == listener) return;
        renderer = listener;
        if (listener != null) {
            for (Chunk chunk : getCopiedChunkList())
                listener.onChunkAdded(chunk.getPos().x, chunk.getPos().z);
        }
    }

    @Override
    public Chunk replaceWithPacketData(int x, int z, BiomeContainer biomes, PacketBuffer data,
                                       CompoundNBT tag, int mask, boolean full) {
        Chunk chunk = super.replaceWithPacketData(x, z, biomes, data, tag, mask, full);
        if (renderer != null && chunk != null) renderer.onChunkAdded(x, z);
        return chunk;
    }

    @Override
    public void drop(int x, int z) {
        boolean loaded = getChunk(x, z, ChunkStatus.FULL, false) != null;
        super.drop(x, z);
        if (loaded && renderer != null) renderer.onChunkRemoved(x, z);
    }
}
