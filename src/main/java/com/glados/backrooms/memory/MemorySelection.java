package com.glados.backrooms.memory;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class MemorySelection {

    private static final int MAX_SIZE = 64;

    private final BlockPos firstCorner;
    private final BlockPos secondCorner;

    public MemorySelection(BlockPos firstCorner, BlockPos secondCorner) {
        this.firstCorner = firstCorner;
        this.secondCorner = secondCorner;
    }

    public BlockPos firstCorner() {
        return firstCorner;
    }

    public BlockPos secondCorner() {
        return secondCorner;
    }

    public boolean isComplete() {
        return firstCorner != null && secondCorner != null;
    }

    public MemorySelection withFirst(BlockPos pos) {
        return new MemorySelection(pos, secondCorner);
    }

    public MemorySelection withSecond(BlockPos pos) {
        return new MemorySelection(firstCorner, pos);
    }

    public BoundingBox bounds() {
        if (!isComplete()) {
            throw new IllegalStateException("Selection must have two corners before building bounds.");
        }
        return BoundingBox.fromCorners(firstCorner, secondCorner);
    }

    public static boolean isWithinLimit(BoundingBox bounds) {
        return sizeX(bounds) <= MAX_SIZE && sizeY(bounds) <= MAX_SIZE && sizeZ(bounds) <= MAX_SIZE;
    }

    public static int sizeX(BoundingBox bounds) {
        return bounds.maxX() - bounds.minX() + 1;
    }

    public static int sizeY(BoundingBox bounds) {
        return bounds.maxY() - bounds.minY() + 1;
    }

    public static int sizeZ(BoundingBox bounds) {
        return bounds.maxZ() - bounds.minZ() + 1;
    }
}
