package com.glados.backrooms.graph;

import com.glados.backrooms.analysis.MemoryAnalysisRepository;
import com.glados.backrooms.analysis.StyleFingerprint;
import com.glados.backrooms.district.District;
import com.glados.backrooms.graph.ConnectionSelector.ClassifiedEdge;
import com.glados.backrooms.graph.CorridorRouter.RoutedCorridor;
import com.glados.backrooms.graph.FunctionalRoleAssigner.RoomAssignment;
import com.glados.backrooms.graph.OpeningPlacer.PlacedOpenings;
import com.glados.backrooms.graph.RoomSizeAssigner.MutableRoom;
import com.glados.backrooms.util.GeometryUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orquesta los ocho pasos de generacion del {@link RoomGraph} en orden fijo
 * (Documento de Diseno, secciones 6.2-6.9 y Documento de Arquitectura,
 * seccion 2.6).
 *
 * Sin estado entre invocaciones: todo el estado de trabajo es local a cada
 * llamada a {@link #generate}. Usado exclusivamente por {@link RoomGraphCache}.
 */
final class RoomGraphGenerator {

    private final MemoryAnalysisRepository repo;

    RoomGraphGenerator(MemoryAnalysisRepository repo) {
        this.repo = repo;
    }

    /**
     * Genera el {@link RoomGraph} completo para la zona especificada.
     *
     * @param zoneId   identificador de la zona.
     * @param district propiedades del distrito al que pertenece la zona.
     * @return el grafo inmutable.
     */
    RoomGraph generate(ZoneId zoneId, District district) {
        long zoneSeed = district.semilla();

        // Bounds de la zona (celda de 192x192 en coords mundo).
        int cellSize = 192;
        int minX = (int) zoneId.cellX() * cellSize;
        int minZ = (int) zoneId.cellZ() * cellSize;
        GeometryUtil.IntRect zoneBounds = new GeometryUtil.IntRect(
                minX, minZ, minX + cellSize - 1, minZ + cellSize - 1);

        // ── Paso 1: centros de habitacion ─────────────────────────────────────────
        StyleFingerprint fingerprint = resolveFingerprint(district.primaryMemoryId());
        List<int[]> centers = RoomCenterDistributor.distribute(
                zoneBounds, zoneSeed, district.densidadBase());

        // ── Paso 2: tamanos ───────────────────────────────────────────────────────
        List<MutableRoom> rooms = RoomSizeAssigner.assign(centers, fingerprint, zoneSeed);

        // ── Paso 3: colisiones ────────────────────────────────────────────────────
        rooms = CollisionResolver.resolve(rooms);

        if (rooms.isEmpty()) {
            return emptyGraph(zoneId, zoneSeed, zoneBounds);
        }

        // ── Paso 4a: Delaunay ─────────────────────────────────────────────────────
        List<int[]> delaunayEdges = DelaunayTriangulator.triangulate(rooms);

        // ── Paso 4b: MST ──────────────────────────────────────────────────────────
        List<int[]> mstEdges = MinimumSpanningTreeBuilder.buildMST(delaunayEdges, rooms);

        // ── Paso 5: clasificacion de conexiones ───────────────────────────────────
        List<ClassifiedEdge> classified = ConnectionSelector.select(
                mstEdges, delaunayEdges, rooms, zoneSeed);

        // ── Paso 6: routing de pasillos ───────────────────────────────────────────
        List<RoutedCorridor> routed = CorridorRouter.route(classified, rooms, zoneSeed);

        // ── Paso 7: aberturas ─────────────────────────────────────────────────────
        List<PlacedOpenings> allOpenings = OpeningPlacer.place(classified, routed, rooms);

        // Construir mapa roomId -> aberturas para el paso 8.
        Map<Integer, List<Apertura>> openingsPerRoom = new HashMap<>();
        for (int i = 0; i < classified.size(); i++) {
            ClassifiedEdge edge = classified.get(i);
            PlacedOpenings po   = allOpenings.get(i);
            openingsPerRoom.computeIfAbsent(edge.fromId(), k -> new ArrayList<>())
                    .add(po.fromApertura());
            openingsPerRoom.computeIfAbsent(edge.toId(), k -> new ArrayList<>())
                    .add(po.toApertura());
        }

        // ── Paso 8: rol funcional y PlannedElements ───────────────────────────────
        Map<Integer, RoomAssignment> assignments = FunctionalRoleAssigner.assign(
                rooms, openingsPerRoom, district.primaryMemoryId(), repo, zoneSeed);

        // ── Ensamblaje del RoomGraph ──────────────────────────────────────────────
        List<RoomNode> roomNodes = new ArrayList<>(rooms.size());
        Map<Integer, RoomNode> roomById = new HashMap<>();

        for (MutableRoom mr : rooms) {
            RoomAssignment asgn = assignments.getOrDefault(mr.id,
                    new RoomAssignment(com.glados.backrooms.classification.FunctionalRole.UTILITARIO,
                            List.of(), district.primaryMemoryId()));
            List<Apertura> roomOpenings = openingsPerRoom.getOrDefault(mr.id, List.of());

            RoomNode node = new RoomNode(
                    mr.id, mr.minX, mr.minZ, mr.maxX, mr.maxZ,
                    asgn.memoryAnalysisId(),
                    asgn.role(),
                    asgn.elements(),
                    roomOpenings,
                    0.0f);
            roomNodes.add(node);
            roomById.put(node.id(), node);
        }

        List<CorridorEdge> corridors = new ArrayList<>(classified.size());
        for (int i = 0; i < classified.size(); i++) {
            ClassifiedEdge edge = classified.get(i);
            PlacedOpenings po   = allOpenings.get(i);
            RoutedCorridor rc   = routed.get(i);
            corridors.add(new CorridorEdge(
                    i,
                    edge.fromId(), edge.toId(),
                    edge.level(), edge.width(),
                    rc.segments(),
                    po.fromApertura(), po.toApertura()));
        }

        return new RoomGraph(zoneId, zoneSeed, zoneBounds, roomNodes, corridors, roomById);
    }

    // ── Internos ─────────────────────────────────────────────────────────────────

    private StyleFingerprint resolveFingerprint(String memoryId) {
        Optional<com.glados.backrooms.analysis.MemoryAnalysis> opt = repo.get(memoryId);
        if (opt.isPresent()) {
            return opt.get().styleFingerprint();
        }
        return repo.allUsable().get(0).styleFingerprint();
    }

    private static RoomGraph emptyGraph(ZoneId zoneId, long seed, GeometryUtil.IntRect bounds) {
        return new RoomGraph(zoneId, seed, bounds, List.of(), List.of(), Map.of());
    }
}
