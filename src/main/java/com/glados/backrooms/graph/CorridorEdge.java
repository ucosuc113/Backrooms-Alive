package com.glados.backrooms.graph;

import java.util.List;

/**
 * Arista del RoomGraph que representa un pasillo entre dos habitaciones
 * (Documento de Diseno, seccion 10.11). Inmutable.
 *
 * @param id             indice unico en el grafo.
 * @param fromRoomId     id de la habitacion de origen.
 * @param toRoomId       id de la habitacion de destino.
 * @param hierarchyLevel PRINCIPAL (MST) o SECUNDARIO (+35% adicional).
 * @param width          anchura del pasillo en bloques (2, 3 o 4).
 * @param segments       uno o dos segmentos axis-aligned que forman la ruta.
 * @param openingAtFrom  abertura en la pared de la habitacion de origen.
 * @param openingAtTo    abertura en la pared de la habitacion de destino.
 */
public record CorridorEdge(
        int id,
        int fromRoomId,
        int toRoomId,
        HierarchyLevel hierarchyLevel,
        int width,
        List<AxisAlignedSegment> segments,
        Apertura openingAtFrom,
        Apertura openingAtTo
) {

    public CorridorEdge {
        segments = List.copyOf(segments);
    }
}
