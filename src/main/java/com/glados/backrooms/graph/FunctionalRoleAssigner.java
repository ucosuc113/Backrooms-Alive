package com.glados.backrooms.graph;

import com.glados.backrooms.analysis.MemoryAnalysis;
import com.glados.backrooms.analysis.MemoryAnalysisRepository;
import com.glados.backrooms.analysis.PrototypeElement;
import com.glados.backrooms.analysis.RoomPrototype;
import com.glados.backrooms.classification.FunctionalRole;
import com.glados.backrooms.graph.OpeningPlacer.PlacedOpenings;
import com.glados.backrooms.graph.RoomSizeAssigner.MutableRoom;
import com.glados.backrooms.util.HashUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Paso 8: asigna rol funcional a cada habitacion y calcula las posiciones
 * absolutas en coordenadas del mundo de cada {@link PlannedElement}
 * (Documento de Diseno, seccion 6.9).
 *
 * Las posiciones quedan fijas para siempre (Invariante 1 del documento de
 * diseno). Esta es la unica vez que se calculan: a partir de aqui,
 * {@code placement} las lee directamente sin recalcularlas.
 *
 * FLOOR_Y es 48 (primera capa de aire interior, seccion 8.5 del diseno).
 */
final class FunctionalRoleAssigner {

    /** Nivel Y del suelo del interior (seccion 8.5). */
    static final int FLOOR_Y = 48;

    /** Numero minimo de conexiones para que una habitacion sea LOBBY/INTERSECCION. */
    private static final int HUB_CONNECTION_THRESHOLD = 3;

    private FunctionalRoleAssigner() {
    }

    /**
     * Asigna roles y elementos funcionales a cada habitacion.
     *
     * @param rooms         habitaciones resueltas.
     * @param openingsPerRoom mapa roomId -> lista de aberturas (calculado de PlacedOpenings).
     * @param districtMemoryId id de la memoria principal del distrito.
     * @param repo          repositorio de analisis.
     * @param zoneSeed      semilla de la zona.
     * @return mapa roomId -> (rol, elementos).
     */
    static Map<Integer, RoomAssignment> assign(List<MutableRoom> rooms,
                                               Map<Integer, List<Apertura>> openingsPerRoom,
                                               String districtMemoryId,
                                               MemoryAnalysisRepository repo,
                                               long zoneSeed) {
        // Cargar la memoria del distrito.
        Optional<MemoryAnalysis> memOpt = repo.get(districtMemoryId);
        MemoryAnalysis memory = memOpt.orElseGet(() -> repo.allUsable().get(0));

        FunctionalRole[] roles = FunctionalRole.values();
        Map<Integer, RoomAssignment> result = new HashMap<>();

        for (MutableRoom room : rooms) {
            int connections = openingsPerRoom.getOrDefault(room.id, List.of()).size();

            FunctionalRole role;
            if (connections >= HUB_CONNECTION_THRESHOLD) {
                // Habitaciones con muchas conexiones -> LOBBY o INTERSECCION
                long hHub = HashUtil.hashCoords(zoneSeed, room.centerX(), room.centerZ(), "hubRole");
                role = HashUtil.chance(hHub, 0.5f) ? FunctionalRole.LOBBY : FunctionalRole.INTERSECCION;
            } else {
                long hRole = HashUtil.hashCoords(zoneSeed, room.centerX(), room.centerZ(), "funcRole");
                int idx = HashUtil.intInRange(hRole, 0, roles.length - 1);
                role = roles[idx];
            }

            // Calcular PlannedElements desde el RoomPrototype.
            List<PlannedElement> elements = buildElements(room, role, memory, zoneSeed);

            result.put(room.id, new RoomAssignment(role, elements, districtMemoryId));
        }
        return result;
    }

    // ── Internos ─────────────────────────────────────────────────────────────────

    private static List<PlannedElement> buildElements(MutableRoom room,
                                                      FunctionalRole role,
                                                      MemoryAnalysis memory,
                                                      long zoneSeed) {
        RoomPrototype proto = memory.roomPrototypeFor(role);
        if (proto == null || proto.elements().isEmpty()) {
            return List.of();
        }

        // Espacio interior (excluyendo las paredes de 1 bloque en cada lado).
        int interiorMinX = room.minX + 1;
        int interiorMaxX = room.maxX - 1;
        int interiorMinZ = room.minZ + 1;
        int interiorMaxZ = room.maxZ - 1;

        if (interiorMaxX <= interiorMinX || interiorMaxZ <= interiorMinZ) {
            return List.of();
        }

        int interiorW = interiorMaxX - interiorMinX;
        int interiorD = interiorMaxZ - interiorMinZ;

        List<PlannedElement> elements = new ArrayList<>();
        for (PrototypeElement pe : proto.elements()) {
            int wx = interiorMinX + Math.round(pe.normalizedX() * interiorW);
            int wz = interiorMinZ + Math.round(pe.normalizedZ() * interiorD);
            elements.add(new PlannedElement(
                    wx, wz,
                    pe.heightFromFloor(),
                    pe.function(),
                    pe.blockState(),
                    pe.blockEntityData()));
        }
        return elements;
    }

    // ── DTO ──────────────────────────────────────────────────────────────────────

    /** Resultado de la asignacion para una habitacion. */
    record RoomAssignment(
            FunctionalRole role,
            List<PlannedElement> elements,
            String memoryAnalysisId
    ) {
    }
}
