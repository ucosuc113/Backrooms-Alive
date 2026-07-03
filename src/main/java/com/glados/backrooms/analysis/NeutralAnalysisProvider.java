package com.glados.backrooms.analysis;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.glados.backrooms.registry.ModBlocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Implementa el caso limite de la seccion 13.1 del Documento de Diseno:
 * cuando no existe ninguna memoria usable como primaria, el sistema de
 * distritos necesita igualmente un {@link MemoryAnalysis} valido para poder
 * generar el grafo de habitaciones con valores por defecto razonables.
 *
 * <p>Esta clase produce ese analisis ficticio una unica vez (singleton
 * estatico). Sus caracteristicas exactas estan fijadas por la seccion 13.1:
 * <ul>
 *   <li>Paredes de {@code smooth_sandstone} en todas las columnas.</li>
 *   <li>Sin acento, sin prototipos funcionales, sin esquina, abertura = aire.</li>
 *   <li>{@code wallComplexity = 0} — un solo material repetido.</li>
 *   <li>{@code lightingStyle = PUNTUAL} — un unico bloque de luz cada 8 bloques.</li>
 *   <li>Dimensiones por defecto: habitaciones 10x10, pasillos 2 bloques.</li>
 *   <li>{@code isUsableAsPrimary = true} — este analisis siempre se acepta como
 *       primario, es el fallback garantizado de ultimo recurso.</li>
 * </ul>
 *
 * <p>No tiene estado mutable: {@link #get()} siempre retorna la misma
 * instancia pre-construida.
 */
public final class NeutralAnalysisProvider {

    /** Identificador del analisis neutral, usado como clave en {@link MemoryAnalysisRepository}. */
    public static final String NEUTRAL_ID = "neutral";

    /** Longitud de referencia del WallPrototype neutral (Documento de Diseno, 4.7: minimo 4). */
    private static final int NEUTRAL_WALL_LENGTH = 4;

    private NeutralAnalysisProvider() {
    }

    /**
     * Retorna el {@link MemoryAnalysis} neutral pre-construido. Siempre la
     * misma instancia, nunca null.
     */
    public static MemoryAnalysis get() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final MemoryAnalysis INSTANCE = buildNeutral();
    }

    // ── Construccion ────────────────────────────────────────────────────────────

    private static MemoryAnalysis buildNeutral() {
        BlockState sandstone = ModBlocks.BACK_WALL.get().defaultBlockState();

        // ── WallPrototype EXTERIOR: 4 columnas identicas de smooth_sandstone ───
        WallColumn neutralColumn = buildSolidColumn(sandstone);
        List<WallColumn> columns = List.of(
                neutralColumn, neutralColumn, neutralColumn, neutralColumn);
        // Con 4 columnas: END_SIZE=2 → tileableStart=2, tileableEnd=2 (rango vacio).
        // Esto es correcto: un prototipo neutral no tiene rango tileable significativo;
        // placement lo tratara como repeticion de la unica columna disponible.
        WallPrototype neutralWall = new WallPrototype(WallRole.EXTERIOR, columns, 2, 2);

        Map<WallRole, WallPrototype> wallPrototypes = Map.of(WallRole.EXTERIOR, neutralWall);

        // ── StyleFingerprint neutral ────────────────────────────────────────────
        StyleFingerprint.WeightedBlock primaryBlock =
                new StyleFingerprint.WeightedBlock(sandstone, 1.0f);

        StyleFingerprint fingerprint = new StyleFingerprint(
                List.of(primaryBlock),          // primaryBlocks
                Collections.emptyList(),         // accentBlocks
                LightingStyle.PUNTUAL,           // lightingStyle  (13.1: luz puntual cada 8)
                0f,                              // functionalDensity
                0f,                              // wallComplexity  (13.1: un solo material)
                10,                              // typicalRoomWidth  (13.1: default 10x10)
                10,                              // typicalRoomDepth
                StyleFingerprint.DEFAULT_CORRIDOR_WIDTH); // typicalCorridorWidth (=2)

        return new MemoryAnalysis(
                NEUTRAL_ID,
                wallPrototypes,
                null,                            // cornerPrototype  (13.1: sin esquina)
                OpeningPrototype.bare(),         // openingPrototype (13.1: abertura = aire)
                Collections.emptyMap(),          // roomPrototypes   (13.1: sin prototipos func.)
                fingerprint,
                true);                           // isUsableAsPrimary = true (fallback garantizado)
    }

    /**
     * Construye una {@link WallColumn} de {@link WallColumn#HEIGHT_LEVELS} niveles
     * todos con el mismo {@code state} solido (sin abertura).
     */
    private static WallColumn buildSolidColumn(BlockState state) {
        BlockState[] blocks = new BlockState[WallColumn.HEIGHT_LEVELS];
        for (int i = 0; i < WallColumn.HEIGHT_LEVELS; i++) {
            blocks[i] = state;
        }
        return new WallColumn(blocks, false);
    }
}
