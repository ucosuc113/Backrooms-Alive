package com.glados.backrooms.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Recuerdo persistente capturado desde una region del Overworld.
 */
public final class MemoryRegion {

    private static final String TAG_UUID = "uuid";
    private static final String TAG_NAME = "name";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_CREATED_AT = "created_at";
    private static final String TAG_UPDATED_AT = "updated_at";
    private static final String TAG_MIN_X = "min_x";
    private static final String TAG_MIN_Y = "min_y";
    private static final String TAG_MIN_Z = "min_z";
    private static final String TAG_MAX_X = "max_x";
    private static final String TAG_MAX_Y = "max_y";
    private static final String TAG_MAX_Z = "max_z";
    private static final String TAG_BLOCKS = "blocks";

    private final UUID uuid;
    private final String name;
    private final String dimension;
    private final BoundingBox bounds;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final List<MemoryBlockSnapshot> blocks;

    public MemoryRegion(
            UUID uuid,
            String name,
            String dimension,
            BoundingBox bounds,
            Instant createdAt,
            Instant updatedAt,
            List<MemoryBlockSnapshot> blocks
    ) {
        this.uuid = uuid;
        this.name = name;
        this.dimension = dimension;
        this.bounds = bounds;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.blocks = List.copyOf(blocks);
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public String dimension() {
        return dimension;
    }

    public BoundingBox bounds() {
        return bounds;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public List<MemoryBlockSnapshot> blocks() {
        return blocks;
    }

    public int width() {
        return bounds.maxX() - bounds.minX() + 1;
    }

    public int height() {
        return bounds.maxY() - bounds.minY() + 1;
    }

    public int depth() {
        return bounds.maxZ() - bounds.minZ() + 1;
    }

    public BlockPos minCorner() {
        return new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());
    }

    public BlockPos maxCorner() {
        return new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_UUID, uuid);
        tag.putString(TAG_NAME, name);
        tag.putString(TAG_DIMENSION, dimension);
        tag.putString(TAG_CREATED_AT, createdAt.toString());
        tag.putString(TAG_UPDATED_AT, updatedAt.toString());
        tag.putInt(TAG_MIN_X, bounds.minX());
        tag.putInt(TAG_MIN_Y, bounds.minY());
        tag.putInt(TAG_MIN_Z, bounds.minZ());
        tag.putInt(TAG_MAX_X, bounds.maxX());
        tag.putInt(TAG_MAX_Y, bounds.maxY());
        tag.putInt(TAG_MAX_Z, bounds.maxZ());

        ListTag blockList = new ListTag();
        for (MemoryBlockSnapshot block : blocks) {
            blockList.add(block.save());
        }
        tag.put(TAG_BLOCKS, blockList);
        return tag;
    }

    public static MemoryRegion load(CompoundTag tag) {
        List<MemoryBlockSnapshot> blocks = new ArrayList<>();
        ListTag blockList = tag.getList(TAG_BLOCKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < blockList.size(); i++) {
            blocks.add(MemoryBlockSnapshot.load(blockList.getCompound(i)));
        }

        return new MemoryRegion(
                tag.getUUID(TAG_UUID),
                tag.getString(TAG_NAME),
                tag.getString(TAG_DIMENSION),
                new BoundingBox(
                        tag.getInt(TAG_MIN_X),
                        tag.getInt(TAG_MIN_Y),
                        tag.getInt(TAG_MIN_Z),
                        tag.getInt(TAG_MAX_X),
                        tag.getInt(TAG_MAX_Y),
                        tag.getInt(TAG_MAX_Z)
                ),
                Instant.parse(tag.getString(TAG_CREATED_AT)),
                Instant.parse(tag.getString(TAG_UPDATED_AT)),
                blocks
        );
    }
}
