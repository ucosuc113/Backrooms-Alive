package com.glados.backrooms.graph;

import com.glados.backrooms.classification.FunctionalRole;
import com.glados.backrooms.util.GeometryUtil;

import java.util.List;

/**
 * Nodo del RoomGraph que representa una habitacion con su posicion y
 * propiedades en coordenadas del mundo (Documento de Diseno, seccion 10.8).
 * Inmutable desde que {@link RoomGraphGenerator} termina de construirlo
 * (Invariante 1 y 6).
 *
 * @param id               indice unico en el grafo.
 * @param minX             bound minimo X en coords mundo.
 * @param minZ             bound minimo Z en coords mundo.
 * @param maxX             bound maximo X en coords mundo.
 * @param maxZ             bound maximo Z en coords mundo.
 * @param memoryAnalysisId id del MemoryAnalysis asignado a esta habitacion.
 * @param functionalRole   rol funcional asignado en el paso 8.
 * @param plannedElements  elementos funcionales con posicion absoluta.
 * @param openings         aberturas de esta habitacion hacia pasillos/vecinas.
 * @param degradationLevel override local de degradacion; 0.0 = usar el campo global.
 */
public record RoomNode(
        int id,
        int minX,
        int minZ,
        int maxX,
        int maxZ,
        String memoryAnalysisId,
        FunctionalRole functionalRole,
        List<PlannedElement> plannedElements,
        List<Apertura> openings,
        float degradationLevel
) {

    public RoomNode {
        plannedElements = List.copyOf(plannedElements);
        openings        = List.copyOf(openings);
    }

    /** Bounds como IntRect para usar con GeometryUtil. */
    public GeometryUtil.IntRect bounds() {
        return new GeometryUtil.IntRect(minX, minZ, maxX, maxZ);
    }

    /** Ancho de la habitacion en bloques (incluye paredes). */
    public int width() {
        return maxX - minX + 1;
    }

    /** Profundidad de la habitacion en bloques (incluye paredes). */
    public int depth() {
        return maxZ - minZ + 1;
    }

    /** Centro X de la habitacion. */
    public double centerX() {
        return (minX + maxX) / 2.0;
    }

    /** Centro Z de la habitacion. */
    public double centerZ() {
        return (minZ + maxZ) / 2.0;
    }
}
