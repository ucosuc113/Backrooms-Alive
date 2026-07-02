package com.glados.backrooms.graph;

import com.glados.backrooms.util.GeometryUtil;

import java.util.List;
import java.util.Map;

/**
 * Grafo completo de habitaciones y pasillos de una zona del mundo
 * (Documento de Diseno, seccion 10.7). Inmutable desde que
 * {@link RoomGraphGenerator} termina de construirlo (Invariante 6).
 *
 * @param zoneId    identificador de la zona.
 * @param semilla   semilla con la que fue generado este grafo.
 * @param bounds    rectangulo de la zona entera en coordenadas mundo.
 * @param rooms     lista de nodos de habitacion.
 * @param corridors lista de aristas de pasillo.
 * @param roomById  mapa id -> RoomNode para lookups rapidos.
 */
public record RoomGraph(
        ZoneId zoneId,
        long semilla,
        GeometryUtil.IntRect bounds,
        List<RoomNode> rooms,
        List<CorridorEdge> corridors,
        Map<Integer, RoomNode> roomById
) {

    public RoomGraph {
        rooms     = List.copyOf(rooms);
        corridors = List.copyOf(corridors);
        roomById  = Map.copyOf(roomById);
    }

    /** Busca una habitacion por su id, lanzando excepcion si no existe. */
    public RoomNode requireRoom(int id) {
        RoomNode node = roomById.get(id);
        if (node == null) {
            throw new IllegalArgumentException("RoomNode con id=" + id + " no encontrado en el grafo.");
        }
        return node;
    }
}
