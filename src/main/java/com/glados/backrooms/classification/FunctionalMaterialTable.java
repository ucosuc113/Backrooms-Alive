package com.glados.backrooms.classification;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tabla de materiales por defecto para cada {@link ArchitecturalFunction}.
 * Usada como fallback cuando ningun {@link com.glados.backrooms.analysis.StyleFingerprint}
 * esta disponible (p.ej. durante la generacion neutral o en tests).
 * Reubicado de {@code generation} a {@code classification} por la misma
 * razon que {@link ArchitecturalFunction} y {@link MemoryFunctionClassifier}.
 */
public final class FunctionalMaterialTable {

    private static final BlockState[] WALLS = {
            Blocks.YELLOW_CONCRETE.defaultBlockState(),
            Blocks.YELLOW_TERRACOTTA.defaultBlockState(),
            Blocks.YELLOW_WOOL.defaultBlockState(),
            Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
            Blocks.STRIPPED_BIRCH_WOOD.defaultBlockState(),
            Blocks.OCHRE_FROGLIGHT.defaultBlockState()
    };

    private static final BlockState[] FLOORS = {
            Blocks.SMOOTH_STONE.defaultBlockState(),
            Blocks.POLISHED_DIORITE.defaultBlockState(),
            Blocks.BIRCH_PLANKS.defaultBlockState(),
            Blocks.CUT_SANDSTONE.defaultBlockState(),
            Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState()
    };

    private static final BlockState CEILING_STATE = Blocks.SMOOTH_SANDSTONE.defaultBlockState();

    private FunctionalMaterialTable() {
    }

    public static BlockState stateFor(ArchitecturalFunction function, RandomSource random) {
        return switch (function) {
            case WALL                -> pick(WALLS, random);
            case FLOOR, STAIRS       -> pick(FLOORS, random);
            case CEILING             -> CEILING_STATE;
            case DOOR                -> Blocks.BIRCH_DOOR.defaultBlockState();
            case WINDOW              -> random.nextInt(3) == 0
                                        ? Blocks.AIR.defaultBlockState()
                                        : Blocks.GLASS_PANE.defaultBlockState();
            case STORAGE             -> Blocks.CHEST.defaultBlockState();
            case BED                 -> Blocks.YELLOW_BED.defaultBlockState();
            case LIGHT               -> Blocks.SEA_LANTERN.defaultBlockState();
            case FURNITURE           -> Blocks.BIRCH_PLANKS.defaultBlockState();
            case AIR                 -> Blocks.AIR.defaultBlockState();
        };
    }

    private static BlockState pick(BlockState[] states, RandomSource random) {
        return states[random.nextInt(states.length)];
    }
}
