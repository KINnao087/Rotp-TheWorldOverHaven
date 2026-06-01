package com.inza.twoh.realitymarksys;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;

import java.util.UUID;

public class RealityMark {
    private UUID markId;
    private UUID ownerId;
    private UUID targetId;

    private ResourceLocation dimensionId;

    private long creationTime;
    private long expireGameTick;

    private int level;
    private boolean empowered;

    public RealityMark(UUID markId, UUID ownerId, UUID targetId, ResourceLocation dimensionId, long creationTime, long expireGameTick, int level, boolean empowered) {
        this.markId = markId;
        this.ownerId = ownerId;
        this.targetId = targetId;
        this.dimensionId = dimensionId;
        this.creationTime = creationTime;
        this.expireGameTick = expireGameTick;
        this.level = level;
        this.empowered = empowered;
    }

    public boolean isExpired(long gameTick) {
        return gameTick >= expireGameTick;
    }

    public CompoundNBT toNbt() {
        CompoundNBT nbt = new CompoundNBT();

        nbt.putUUID("MarkId", markId);
        nbt.putUUID("OwnerUuid", ownerId);
        nbt.putUUID("TargetUuid", targetId);
        nbt.putString("DimensionId", dimensionId.toString());
        nbt.putLong("CreatedGameTime", creationTime);
        nbt.putLong("ExpireGameTime", expireGameTick);
        nbt.putInt("Level", level);
        nbt.putBoolean("Empowered", empowered);

        return nbt;
    }

    public static RealityMark fromNbt(CompoundNBT nbt) {
        if (!nbt.hasUUID("MarkId") || !nbt.hasUUID("OwnerUuid") || !nbt.hasUUID("TargetUuid")) {
            return null;
        }

        return new RealityMark(
            nbt.getUUID("MarkId"),
            nbt.getUUID("OwnerUuid"),
            nbt.getUUID("TargetUuid"),
            new ResourceLocation(nbt.getString("DimensionId")),
            nbt.getLong("CreatedGameTime"),
            nbt.getLong("ExpireGameTime"),
            nbt.getInt("Level"),
            nbt.getBoolean("Empowered")
        );
    }

    public UUID getMarkId() {
        return markId;
    }

    public UUID getOwnerUuid() {
        return ownerId;
    }

    public UUID getTargetUuid() {
        return targetId;
    }

    public ResourceLocation getDimensionId() {
        return dimensionId;
    }

    public long getCreatedGameTime() {
        return creationTime;
    }

    public long getExpireGameTime() {
        return expireGameTick;
    }

    public int getLevel() {
        return level;
    }

    public boolean isEmpowered() {
        return empowered;
    }
}
