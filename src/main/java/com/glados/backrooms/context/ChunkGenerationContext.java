package com.glados.backrooms.context;

import com.glados.backrooms.analysis.MemoryAnalysis;
import com.glados.backrooms.graph.Apertura;
import com.glados.backrooms.graph.CorridorEdge;
import com.glados.backrooms.graph.RoomNode;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.List;
import java.util.Map;

/**
 * Instantanea de todos los datos necesarios para generar un chunk concreto
 * (Documento de Diseno, seccion 10.13). Producido por {@link ChunkContextBuilder}
 * al inicio de {@code buildSurface}; consumido por las capas 4 y 5.
 *
 * Es el unico objeto que cruza la frontera entre context, placement y
 * degradation (Documento de Arquitectura, seccion 2.7).
 */
public final class ChunkGenerationContext {

    // ── Minecraft ────────────────────────────────────────────────────────────────
    public final ChunkAccess chunk;
    public final WorldGenRegion region;
    public final RandomSource random;

    // ── Mapas de rol y habitacion (arrays [localX][localZ], 16x16) ───────────────
    /** Rol de cada columna del chunk. */
    public final ColumnRole[][] roleMap;
    /** Id de la habitacion en cada columna, o -1 si ninguna. */
    public final int[][] roomMap;
    /** Id del pasillo en cada columna, o -1 si ninguno. */
    public final int[][] corridorMap;

    // ── Mapas de degradacion ([localX][localZ]) ──────────────────────────────────
    public final float[][] degradationStructural;
    public final float[][] degradationMaterial;
    public final float[][] degradationFunctional;
    public final float[][] degradationAdditive;

    // ── Elementos activos ─────────────────────────────────────────────────────────
    public final List<RoomNode> activeRooms;
    public final List<CorridorEdge> activeCorridors;
    public final List<Apertura> activeOpenings;

    /** Mapa id -> MemoryAnalysis para acceso rapido desde placement/degradation. */
    public final Map<String, MemoryAnalysis> memoryAnalysisById;

    // ── Coordenadas de mundo del chunk ───────────────────────────────────────────
    public final int chunkWorldMinX;
    public final int chunkWorldMinZ;

    ChunkGenerationContext(
            ChunkAccess chunk,
            WorldGenRegion region,
            RandomSource random,
            ColumnRole[][] roleMap,
            int[][] roomMap,
            int[][] corridorMap,
            float[][] degradationStructural,
            float[][] degradationMaterial,
            float[][] degradationFunctional,
            float[][] degradationAdditive,
            List<RoomNode> activeRooms,
            List<CorridorEdge> activeCorridors,
            List<Apertura> activeOpenings,
            Map<String, MemoryAnalysis> memoryAnalysisById) {

        this.chunk = chunk;
        this.region = region;
        this.random = random;
        this.roleMap = roleMap;
        this.roomMap = roomMap;
        this.corridorMap = corridorMap;
        this.degradationStructural = degradationStructural;
        this.degradationMaterial = degradationMaterial;
        this.degradationFunctional = degradationFunctional;
        this.degradationAdditive = degradationAdditive;
        this.activeRooms = List.copyOf(activeRooms);
        this.activeCorridors = List.copyOf(activeCorridors);
        this.activeOpenings = List.copyOf(activeOpenings);
        this.memoryAnalysisById = Map.copyOf(memoryAnalysisById);
        this.chunkWorldMinX = chunk.getPos().getMinBlockX();
        this.chunkWorldMinZ = chunk.getPos().getMinBlockZ();
    }

    /** Convierte localX (0-15) a coordenada X de mundo. */
    public int worldX(int localX) {
        return chunkWorldMinX + localX;
    }

    /** Convierte localZ (0-15) a coordenada Z de mundo. */
    public int worldZ(int localZ) {
        return chunkWorldMinZ + localZ;
    }

    /** Convierte coordenada X de mundo a localX, sin verificar bounds. */
    public int localX(int worldX) {
        return worldX - chunkWorldMinX;
    }

    /** Convierte coordenada Z de mundo a localZ, sin verificar bounds. */
    public int localZ(int worldZ) {
        return worldZ - chunkWorldMinZ;
    }

    /** True si (localX, localZ) esta dentro del chunk. */
    public boolean inBounds(int localX, int localZ) {
        return localX >= 0 && localX < 16 && localZ >= 0 && localZ < 16;
    }
}
