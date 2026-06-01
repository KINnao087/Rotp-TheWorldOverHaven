package com.inza.twoh.realitymarksys;

import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

public class RealityMarkRegistry extends WorldSavedData {
    static final String DATA_NAME = "twoh_reality_marks";

    private final Map<UUID, RealityMark> marksById = new HashMap<>();

    // owner -> markIds
    private final Map<UUID, Set<UUID>> marksByOwner = new HashMap<>();

    // target -> markIds
    private final Map<UUID, Set<UUID>> marksByTarget = new HashMap<>();

    public RealityMarkRegistry() {
        super(DATA_NAME);
    }

    public RealityMark apply(ServerWorld world, LivingEntity owner, LivingEntity target, int durationTicks, int level) {
        UUID markId = UUID.randomUUID();

        RealityMark mark = new RealityMark(
            markId,
            owner.getUUID(),
            target.getUUID(),
            world.dimension().location(),
            world.getGameTime(),
            world.getGameTime() + durationTicks,
            level,
            false
        );

        addMark(mark);

        setDirty();
        return mark;
    }

    public Optional<RealityMark> getMark(UUID ownerUuid, UUID targetUuid, long gameTime) {
        Set<UUID> ids = marksByTarget.get(targetUuid);
        if (ids == null || ids.isEmpty()) {
            return Optional.empty();
        }

        for (UUID id : ids) {
            RealityMark mark = marksById.get(id);
            if (mark == null) {
                continue;
            }

            if (mark.getOwnerUuid().equals(ownerUuid) && !mark.isExpired(gameTime)) {
                return Optional.of(mark);
            }
        }

        return Optional.empty();
    }

    public boolean hasMark(ServerWorld world, LivingEntity owner, LivingEntity target) {
        return getMark(owner.getUUID(), target.getUUID(), world.getGameTime()).isPresent();
    }

    public void consume(UUID markId) {
        RealityMark mark = marksById.remove(markId);
        if (mark == null) {
            return;
        }

        removeFromIndex(marksByOwner, mark.getOwnerUuid(), markId);
        removeFromIndex(marksByTarget, mark.getTargetUuid(), markId);

        setDirty();
    }

    public void clearMarksOnTarget(UUID targetUuid) {
        Set<UUID> ids = new HashSet<>(marksByTarget.getOrDefault(targetUuid, Collections.emptySet()));
        for (UUID id : ids) {
            consume(id);
        }
    }

    public void cleanupExpired(long gameTime) {
        List<UUID> expired = new ArrayList<>();

        for (Map.Entry<UUID, RealityMark> entry : marksById.entrySet()) {
            if (entry.getValue().isExpired(gameTime)) {
                expired.add(entry.getKey());
            }
        }

        for (UUID id : expired) {
            consume(id);
        }
    }

    private static void removeFromIndex(Map<UUID, Set<UUID>> index, UUID key, UUID markId) {
        Set<UUID> set = index.get(key);
        if (set == null) {
            return;
        }

        set.remove(markId);

        if (set.isEmpty()) {
            index.remove(key);
        }
    }

    private void addMark(RealityMark mark) {
        UUID markId = mark.getMarkId();

        marksById.put(markId, mark);
        marksByOwner.computeIfAbsent(mark.getOwnerUuid(), k -> new HashSet<>()).add(markId);
        marksByTarget.computeIfAbsent(mark.getTargetUuid(), k -> new HashSet<>()).add(markId);
    }

    @Override
    public void load(CompoundNBT nbt) {
        marksById.clear();
        marksByOwner.clear();
        marksByTarget.clear();

        ListNBT list = nbt.getList("Marks", 10);

        for (int i = 0; i < list.size(); i++) {
            CompoundNBT markNbt = list.getCompound(i);
            RealityMark mark = RealityMark.fromNbt(markNbt);

            if (mark != null) {
                addMark(mark);
            }
        }
    }

    @Override
    public CompoundNBT save(CompoundNBT nbt) {
        ListNBT list = new ListNBT();

        for (RealityMark mark : marksById.values()) {
            list.add(mark.toNbt());
        }

        nbt.put("Marks", list);
        return nbt;
    }
}
