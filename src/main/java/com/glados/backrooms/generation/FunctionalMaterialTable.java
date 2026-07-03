package com.glados.backrooms.generation;

import com.glados.backrooms.registry.ModBlocks;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class FunctionalMaterialTable {

        // Structural roles should use the dedicated backrooms blocks.
        private static final BlockState BACK_WALL = ModBlocks.BACK_WALL.get().defaultBlockState();
        private static final BlockState BACK_FLOOR = ModBlocks.BACK_FLOOR.get().defaultBlockState();
        private static final BlockState BACK_CEILING = ModBlocks.BACK_CEILING.get().defaultBlockState();

    private FunctionalMaterialTable() {
    }

    public static BlockState stateFor(ArchitecturalFunction function, RandomSource random) {
        return switch (function) {
            case WALL -> BACK_WALL;
            case FLOOR, STAIRS -> BACK_FLOOR;
            case CEILING -> BACK_CEILING;
            case DOOR -> Blocks.BIRCH_DOOR.defaultBlockState();
            case WINDOW -> random.nextInt(3) == 0 ? Blocks.AIR.defaultBlockState() : Blocks.GLASS_PANE.defaultBlockState();
            case STORAGE -> Blocks.CHEST.defaultBlockState();
            case BED -> Blocks.YELLOW_BED.defaultBlockState();
            case LIGHT -> ModBlocks.BACK_LIGHT.get().defaultBlockState();
            case FURNITURE -> Blocks.BIRCH_PLANKS.defaultBlockState();
            case AIR -> Blocks.AIR.defaultBlockState();
        };
    }

    private static BlockState pick(BlockState[] states, RandomSource random) {
        return states[random.nextInt(states.length)];
    }
}
