package com.glados.backrooms.memory;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class MemoryConnectorAnalyzer {

    private MemoryConnectorAnalyzer() {
    }

    public static List<MemoryConnector> analyze(MemoryRegion region, HolderGetter<Block> blockLookup) {
        List<MemoryConnector> connectors = new ArrayList<>();
        for (MemoryBlockSnapshot snapshot : region.blocks()) {
            BlockState state = NbtUtils.readBlockState(blockLookup, snapshot.blockState());
            BlockPos relativePos = snapshot.relativePos();
            Direction boundaryDirection = boundaryDirection(region, relativePos);

            if (isDoor(state)) {
                connectors.add(new MemoryConnector(MemoryConnectorType.DOOR, relativePos, boundaryDirection));
            } else if (state.isAir() && boundaryDirection != null && relativePos.getY() > 0) {
                connectors.add(new MemoryConnector(MemoryConnectorType.OPENING, relativePos, boundaryDirection));
                connectors.add(new MemoryConnector(MemoryConnectorType.CONNECTION_POINT, relativePos, boundaryDirection));
            } else if (isStairs(state)) {
                connectors.add(new MemoryConnector(MemoryConnectorType.STAIRS, relativePos, boundaryDirection));
            }
        }
        return connectors;
    }

    private static Direction boundaryDirection(MemoryRegion region, BlockPos relativePos) {
        if (relativePos.getX() == 0) {
            return Direction.WEST;
        }
        if (relativePos.getX() == region.width() - 1) {
            return Direction.EAST;
        }
        if (relativePos.getZ() == 0) {
            return Direction.NORTH;
        }
        if (relativePos.getZ() == region.depth() - 1) {
            return Direction.SOUTH;
        }
        return null;
    }

    private static boolean isDoor(BlockState state) {
        return blockPath(state).contains("door") || blockPath(state).contains("gate");
    }

    private static boolean isStairs(BlockState state) {
        String path = blockPath(state);
        return path.contains("stairs") || path.contains("ladder");
    }

    private static String blockPath(BlockState state) {
        ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key == null ? "" : key.getPath();
    }
}
