package com.glados.backrooms.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Dado un punto y una funcion que produce un centro por celda de rejilla,
 * determina el centro mas cercano entre las 9 celdas vecinas (Documento de
 * Diseno, seccion 5.1: "definicion estandar de un diagrama de Voronoi
 * discreto"). Pura geometria: no sabe que es un "distrito", solo trabaja con
 * celdas y centros abstractos provistos por el llamador.
 *
 * Usada por {@code district} (DistrictLookup, DistrictGrid,
 * TransitionZoneCalculator) con {@code cellSize = 192} y un
 * {@link CellCenterProvider} que deriva el centro de cada celda con
 * {@link HashUtil} a partir de la semilla del mundo.
 */
public final class VoronoiLookup {

    private VoronoiLookup() {
    }

    /** Produce el centro (en coordenadas de mundo) de una celda de la rejilla, dados sus indices de celda. */
    @FunctionalInterface
    public interface CellCenterProvider {
        double centerX(int cellX, int cellZ);

        double centerZ(int cellX, int cellZ);
    }

    /** Una celda candidata con su distancia al punto consultado. */
    public record CellDistance(int cellX, int cellZ, double distance) {
    }

    /** El centro de celda mas cercano a (x, z), entre las 9 celdas vecinas de la rejilla. */
    public static CellDistance nearestCell(double x, double z, int cellSize, CellCenterProvider provider) {
        return rankNearbyCells(x, z, cellSize, provider).get(0);
    }

    /**
     * Los dos centros de celda mas cercanos a (x, z), ordenados por
     * distancia ascendente. Usado por la deteccion de zonas de transicion
     * entre distritos (Documento de Diseno, seccion 5.4), que necesita saber
     * cual es el *segundo* distrito mas cercano y a que distancia esta.
     */
    public static List<CellDistance> twoNearestCells(double x, double z, int cellSize, CellCenterProvider provider) {
        List<CellDistance> ranked = rankNearbyCells(x, z, cellSize, provider);
        return ranked.subList(0, Math.min(2, ranked.size()));
    }

    private static List<CellDistance> rankNearbyCells(double x, double z, int cellSize, CellCenterProvider provider) {
        int cellX = Math.floorDiv((int) Math.floor(x), cellSize);
        int cellZ = Math.floorDiv((int) Math.floor(z), cellSize);

        List<CellDistance> candidates = new ArrayList<>(9);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int neighborCellX = cellX + dx;
                int neighborCellZ = cellZ + dz;
                double centerX = provider.centerX(neighborCellX, neighborCellZ);
                double centerZ = provider.centerZ(neighborCellX, neighborCellZ);
                double distance = Math.hypot(x - centerX, z - centerZ);
                candidates.add(new CellDistance(neighborCellX, neighborCellZ, distance));
            }
        }
        candidates.sort(Comparator.comparingDouble(CellDistance::distance));
        return candidates;
    }
}
