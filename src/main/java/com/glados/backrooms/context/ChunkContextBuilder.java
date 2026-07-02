package com.glados.backrooms.context;

import com.glados.backrooms.analysis.MemoryAnalysis;
import com.glados.backrooms.analysis.MemoryAnalysisRepository;
import com.glados.backrooms.context.ActiveElementFilter.FilteredElements;
import com.glados.backrooms.context.DegradationMapBuilder.DegradationMaps;
import com.glados.backrooms.context.DistrictOverlapResolver.ActiveDistrict;
import com.glados.backrooms.context.RoleMapBuilder.RoleMaps;
import com.glados.backrooms.context.RoomGraphResolver.ActiveGraph;
import com.glados.backrooms.district.DistrictLookup;
import com.glados.backrooms.graph.RoomGraphCache;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unico punto de entrada publico del paquete {@code context}
 * (Documento de Arquitectura, seccion 2.7, fachada [F]).
 *
 * Ejecuta los pasos 7.1-7.6 del Documento de Diseno en orden fijo y
 * produce un {@link ChunkGenerationContext} completo para el chunk en
 * curso. Sin estado entre invocaciones.
 */
public final class ChunkContextBuilder {

    private final DistrictLookup districtLookup;
    private final RoomGraphCache graphCache;
    private final MemoryAnalysisRepository repo;
    private final DegradationMapBuilder degradationMapBuilder;

    /**
     * @param worldSeed      semilla del mundo; usada para construir los
     *                       campos de ruido locales de degradacion.
     * @param districtLookup fachada del paquete district.
     * @param graphCache     fachada del paquete graph.
     * @param repo           repositorio de analisis de memoria.
     */
    public ChunkContextBuilder(long worldSeed,
                               DistrictLookup districtLookup,
                               RoomGraphCache graphCache,
                               MemoryAnalysisRepository repo) {
        this.districtLookup       = districtLookup;
        this.graphCache           = graphCache;
        this.repo                 = repo;
        this.degradationMapBuilder = new DegradationMapBuilder(worldSeed);
    }

    /**
     * Construye el contexto completo para el chunk dado.
     *
     * @param chunk  el chunk en generacion.
     * @param region la region del mundo (para leer bloques vecinos si fuera necesario).
     * @param random fuente de aleatoriedad sembrada por Minecraft para este chunk.
     * @return el {@link ChunkGenerationContext} listo para las capas 4 y 5.
     */
    public ChunkGenerationContext build(ChunkAccess chunk,
                                        WorldGenRegion region,
                                        RandomSource random) {
        // ── Paso 7.1: distritos solapantes ───────────────────────────────────────
        List<ActiveDistrict> districts =
                DistrictOverlapResolver.resolve(chunk, districtLookup, repo);

        // ── Paso 7.2: grafos de habitaciones ──────────────────────────────────────
        List<ActiveGraph> graphs = RoomGraphResolver.resolve(districts, graphCache);

        // ── Paso 7.3: filtrar elementos activos ───────────────────────────────────
        FilteredElements filtered = ActiveElementFilter.filter(graphs, chunk);

        // ── Paso 7.4: mapas de rol ────────────────────────────────────────────────
        RoleMaps roleMaps = RoleMapBuilder.build(
                filtered.activeRooms(),
                filtered.activeCorridors(),
                filtered.activeOpenings(),
                chunk);

        // ── Paso 7.5: mapas de degradacion ────────────────────────────────────────
        DegradationMaps degradation =
                degradationMapBuilder.build(chunk, districts, districtLookup);

        // ── Paso 7.6: aberturas activas ───────────────────────────────────────────
        List<com.glados.backrooms.graph.Apertura> openings = OpeningCollector.collect(
                filtered.activeRooms(),
                filtered.activeCorridors(),
                chunk);

        // ── Mapa de MemoryAnalysis para acceso rapido ─────────────────────────────
        Map<String, MemoryAnalysis> memById = buildMemoryMap(filtered, districts);

        return new ChunkGenerationContext(
                chunk, region, random,
                roleMaps.roleMap(), roleMaps.roomMap(), roleMaps.corridorMap(),
                degradation.structural(), degradation.material(),
                degradation.functional(), degradation.additive(),
                filtered.activeRooms(),
                filtered.activeCorridors(),
                openings,
                memById);
    }

    // ── Internos ─────────────────────────────────────────────────────────────────

    private Map<String, MemoryAnalysis> buildMemoryMap(FilteredElements filtered,
                                                        List<ActiveDistrict> districts) {
        Map<String, MemoryAnalysis> map = new HashMap<>();
        // Agregar las memorias de los distritos activos.
        for (ActiveDistrict ad : districts) {
            addMemory(map, ad.properties().primaryMemoryId());
            if (ad.properties().secondaryMemoryId() != null) {
                addMemory(map, ad.properties().secondaryMemoryId());
            }
        }
        // Agregar las memorias de las habitaciones activas (pueden diferir).
        for (com.glados.backrooms.graph.RoomNode node : filtered.activeRooms()) {
            addMemory(map, node.memoryAnalysisId());
        }
        return map;
    }

    private void addMemory(Map<String, MemoryAnalysis> map, String id) {
        if (id != null && !map.containsKey(id)) {
            repo.get(id).ifPresent(a -> map.put(id, a));
        }
    }
}
