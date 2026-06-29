package com.glados.backrooms.memory;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Snapshot de un bloque dentro de un recuerdo.
 * <p>
 * La posicion siempre es relativa al minimo de la Bounding Box capturada.
 */
public record MemoryBlockSnapshot(BlockPos relativePos, CompoundTag blockState, CompoundTag blockEntity) {

    private static final String TAG_X = "x";
    private static final String TAG_Y = "y";
    private static final String TAG_Z = "z";
    private static final String TAG_STATE = "state";
    private static final String TAG_BLOCK_ENTITY = "block_entity";

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_X, relativePos.getX());
        tag.putInt(TAG_Y, relativePos.getY());
        tag.putInt(TAG_Z, relativePos.getZ());
        tag.put(TAG_STATE, blockState.copy());
        if (blockEntity != null) {
            tag.put(TAG_BLOCK_ENTITY, blockEntity.copy());
        }
        return tag;
    }

    public static MemoryBlockSnapshot load(CompoundTag tag) {
        CompoundTag blockEntity = tag.contains(TAG_BLOCK_ENTITY) ? tag.getCompound(TAG_BLOCK_ENTITY) : null;
        return new MemoryBlockSnapshot(
                new BlockPos(tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z)),
                tag.getCompound(TAG_STATE),
                blockEntity
        );
    }
}
