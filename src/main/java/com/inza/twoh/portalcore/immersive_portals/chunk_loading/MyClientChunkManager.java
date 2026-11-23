// Modified for TWOH from Immersive Portals Forge, copyright 2020 qouteall, Apache-2.0.
package com.inza.twoh.portalcore.immersive_portals.chunk_loading;

import com.inza.twoh.portalcore.hiding_in_the_bushes.O_O;
import com.inza.twoh.portalcore.immersive_portals.ClientWorldLoader;
import com.inza.twoh.portalcore.immersive_portals.my_util.SignalArged;
import com.inza.twoh.portalcore.immersive_portals.render.context_management.RenderDimensionRedirect;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.client.multiplayer.ClientChunkProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.LightType;
import net.minecraft.world.biome.BiomeContainer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.lighting.WorldLightManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;

// allow storing chunks that are far away from the player
@OnlyIn(Dist.CLIENT)
public class MyClientChunkManager extends ClientChunkProvider {
    private static final Logger LOGGER = LogManager.getLogger();
    protected final Chunk emptyChunk;
    protected final WorldLightManager lightingProvider;
    protected final ClientWorld level;
    
    protected final Long2ObjectLinkedOpenHashMap<Chunk> chunkMap =
        new Long2ObjectLinkedOpenHashMap<>();
    
    public static final SignalArged<Chunk> clientChunkLoadSignal = new SignalArged<>();
    public static final SignalArged<Chunk> clientChunkUnloadSignal = new SignalArged<>();
    
    public MyClientChunkManager(ClientWorld clientWorld, int loadDistance) {
        super(clientWorld, loadDistance);
        this.level = clientWorld;
        this.emptyChunk = new EmptyChunk(clientWorld, new ChunkPos(0, 0));
        this.lightingProvider = new WorldLightManager(
            this,
            true,
            RenderDimensionRedirect.hasSkylight(clientWorld)
        );
        
    }
    
    @Override
    public WorldLightManager getLightEngine() {
        return this.lightingProvider;
    }
    
    @Override
    public void drop(int x, int z) {
        synchronized (chunkMap) {
            
            ChunkPos chunkPos = new ChunkPos(x, z);
            Chunk chunk = chunkMap.get(chunkPos.toLong());
            if (positionEquals(chunk, x, z)) {
                chunkMap.remove(chunkPos.toLong());
                O_O.postClientChunkUnloadEvent(chunk);
                level.unload(chunk);
                clientChunkUnloadSignal.emit(chunk);
            }
        }
    }
    
    @Override
    public Chunk getChunk(int x, int z, ChunkStatus chunkStatus, boolean create) {
        // the profiler shows that this is not a hot spot
        synchronized (chunkMap) {
            Chunk chunk = chunkMap.get(ChunkPos.asLong(x, z));
            if (positionEquals(chunk, x, z)) {
                return chunk;
            }
            
            return create ? this.emptyChunk : null;
        }
    }
    
    @Override
    public IBlockReader getLevel() {
        return this.level;
    }
    
    @Override
    public Chunk replaceWithPacketData(
        int x,
        int z,
        BiomeContainer biomeArray,
        PacketBuffer packetByteBuf,
        CompoundNBT compoundTag,
        int mask,
        boolean forceCreate
    ) {
        long chunkPosLong = ChunkPos.asLong(x, z);
        
        Chunk worldChunk;
        synchronized (chunkMap) {
            worldChunk = (Chunk) this.chunkMap.get(chunkPosLong);
            if (!forceCreate && positionEquals(worldChunk, x, z)) {
                worldChunk.replaceWithPacketData(biomeArray, packetByteBuf, compoundTag, mask);
            }
            else {
                if (biomeArray == null) {
                    LOGGER.error(
                        "Missing Biome Array: {} {} {} Client Biome May be Incorrect",
                        level.dimension().location(), x, z
                    );
                    throw new RuntimeException("Null biome array");
                }
                
                worldChunk = new Chunk(this.level, new ChunkPos(x, z), biomeArray);
                worldChunk.replaceWithPacketData(biomeArray, packetByteBuf, compoundTag, mask);
                chunkMap.put(chunkPosLong, worldChunk);
            }
        }
        
        ChunkSection[] chunkSections = worldChunk.getSections();
        WorldLightManager lightingProvider = this.getLightEngine();
        lightingProvider.enableLightSources(new ChunkPos(x, z), true);
        
        for (int cy = 0; cy < chunkSections.length; ++cy) {
            ChunkSection chunkSection = chunkSections[cy];
            lightingProvider.updateSectionStatus(
                SectionPos.of(x, cy, z),
                ChunkSection.isEmpty(chunkSection)
            );
        }
        
        this.level.onChunkLoaded(x, z);
        
        O_O.postClientChunkLoadEvent(worldChunk);
        clientChunkLoadSignal.emit(worldChunk);
        
        return worldChunk;
    }
    
    public static void updateLightStatus(Chunk chunk) {
        WorldLightManager lightingProvider = chunk.getLevel().getLightEngine();
        ChunkSection[] chunkSections = chunk.getSections();
        for (int cy = 0; cy < chunkSections.length; ++cy) {
            ChunkSection chunkSection = chunkSections[cy];
            lightingProvider.updateSectionStatus(
                SectionPos.of(chunk.getPos().x, cy, chunk.getPos().z),
                ChunkSection.isEmpty(chunkSection)
            );
        }
    }
    
    public List<Chunk> getCopiedChunkList() {
        synchronized (chunkMap) {
            return Arrays.asList(chunkMap.values().toArray(new Chunk[0]));
        }
    }
    
    @Override
    public void updateViewCenter(int x, int z) {
        //do nothing
    }
    
    @Override
    public void updateViewRadius(int r) {
        //do nothing
    }
    
    @Override
    public String gatherStats() {
        return "Client chunks (TWOH) " + getLoadedChunksCount();
    }
    
    @Override
    public int getLoadedChunksCount() {
        synchronized (chunkMap) {
            return chunkMap.size();
        }
    }
    
    @Override
    public void onLightUpdate(LightType lightType, SectionPos chunkSectionPos) {
        ClientWorldLoader.getWorldRenderer(
            level.dimension()
        ).setSectionDirty(
            chunkSectionPos.x(),
            chunkSectionPos.y(),
            chunkSectionPos.z()
        );
    }
    
    @Override
    public boolean isTickingChunk(BlockPos blockPos) {
        return this.hasChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4);
    }
    
    @Override
    public boolean isEntityTickingChunk(ChunkPos chunkPos) {
        return this.hasChunk(chunkPos.x, chunkPos.z);
    }
    
    @Override
    public boolean isEntityTickingChunk(Entity entity) {
        return this.hasChunk(
            MathHelper.floor(entity.getX()) >> 4,
            MathHelper.floor(entity.getZ()) >> 4
        );
    }
    
    protected static boolean positionEquals(Chunk worldChunk, int x, int z) {
        if (worldChunk == null) {
            return false;
        }
        else {
            ChunkPos chunkPos = worldChunk.getPos();
            return chunkPos.x == x && chunkPos.z == z;
        }
    }
    
}
