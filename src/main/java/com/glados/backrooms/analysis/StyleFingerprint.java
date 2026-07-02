package com.glados.backrooms.analysis;

import java.util.List;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Huella de estilo de una memoria: resumen numerico de su caracter
 * arquitectonico (Documento de Diseno, secciones 4.5 y 10.4). Es el unico
 * dato que viaja sin transformarse a traves de todas las capas superiores
 * (district, graph, placement, degradation lo leen directamente por
 * referencia; ver Documento de Arquitectura, seccion 5).
 */
public record StyleFingerprint(List<WeightedBlock> primaryBlocks, List<WeightedBlock> accentBlocks,
                                LightingStyle lightingStyle, float functionalDensity, float wallComplexity,
                                int typicalRoomWidth, int typicalRoomDepth, int typicalCorridorWidth) {

    public static final int MAX_PRIMARY_BLOCKS = 3;
    public static final int MAX_ACCENT_BLOCKS = 3;
    public static final int DEFAULT_CORRIDOR_WIDTH = 2;
    public static final float ACCENT_FREQUENCY_CEILING = 0.15f;

    public StyleFingerprint {
        if (primaryBlocks.size() > MAX_PRIMARY_BLOCKS) {
            throw new IllegalArgumentException("primaryBlocks no puede superar " + MAX_PRIMARY_BLOCKS + " entradas.");
        }
        if (accentBlocks.size() > MAX_ACCENT_BLOCKS) {
            throw new IllegalArgumentException("accentBlocks no puede superar " + MAX_ACCENT_BLOCKS + " entradas.");
        }
        if (wallComplexity < 0f || wallComplexity > 1f) {
            throw new IllegalArgumentException("wallComplexity debe estar en [0,1].");
        }
        primaryBlocks = List.copyOf(primaryBlocks);
        accentBlocks = List.copyOf(accentBlocks);
    }

    /** El bloque primario mas frecuente, o {@code null} si la memoria no tenia ninguno (caso de fallback neutral). */
    public BlockState dominantPrimaryBlock() {
        return primaryBlocks.isEmpty() ? null : primaryBlocks.get(0).state();
    }

    public BlockState dominantAccentBlock() {
        return accentBlocks.isEmpty() ? null : accentBlocks.get(0).state();
    }

    /** Par (BlockState, frecuencia relativa) tal como lo describe el Documento de Diseno, 10.4. */
    public record WeightedBlock(BlockState state, float frequency) {

        public WeightedBlock {
            if (frequency < 0f || frequency > 1f) {
                throw new IllegalArgumentException("frequency debe estar en [0,1].");
            }
        }
    }
}
