package com.glados.backrooms.generation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class MemoryFunctionClassifier {

    private MemoryFunctionClassifier() {
    }

    public static ArchitecturalFunction classify(BlockState state) {
        if (state.isAir()) {
            return ArchitecturalFunction.AIR;
        }

        Block block = state.getBlock();
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        String path = key == null ? "" : key.getPath();

        if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || path.contains("barrel") || path.contains("shulker")) {
            return ArchitecturalFunction.STORAGE;
        }
        if (path.contains("bed")) {
            return ArchitecturalFunction.BED;
        }
        if (path.contains("door") || path.contains("gate")) {
            return ArchitecturalFunction.DOOR;
        }
        if (path.contains("glass") || path.contains("pane")) {
            return ArchitecturalFunction.WINDOW;
        }
        if (path.contains("stairs") || path.contains("ladder")) {
            return ArchitecturalFunction.STAIRS;
        }
        if (state.getLightEmission() > 0 || path.contains("lamp") || path.contains("lantern") || path.contains("torch")) {
            return ArchitecturalFunction.LIGHT;
        }
        if (path.contains("slab") || path.contains("table") || path.contains("chair") || path.contains("fence")) {
            return ArchitecturalFunction.FURNITURE;
        }
        return ArchitecturalFunction.WALL;
    }
}
