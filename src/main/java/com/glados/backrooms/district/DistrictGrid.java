package com.glados.backrooms.district;

import com.glados.backrooms.util.HashUtil;
import com.glados.backrooms.util.VoronoiLookup;

/**
 * Implementa la rejilla de distritos de 192x192 bloques (Documento de Diseno,
 * seccion 5.1). Define la estructura de celdas y calcula, para cada celda de
 * la rejilla, el centro de distrito desplazado deterministicamente.
 *
 * Sin estado mutable: todas sus operaciones son funciones puras parametrizadas
 * por la semilla del mundo (Documento de Arquitectura, seccion 2.5).
 *
 * Implementa {@link VoronoiLookup.CellCenterProvider} para que
 * {@link VoronoiLookup} pueda usar la rejilla de distritos directamente.
 */
final class DistrictGrid implements VoronoiLookup.CellCenterProvider {

    /** Tamano de celda de la rejilla en bloques. */
    static final int CELL_SIZE = 192;

    /**
     * Desplazamiento maximo del centro dentro de la celda, en bloques.
     * El centro puede desplazarse entre -CENTER_OFFSET_MAX y +CENTER_OFFSET_MAX
     * en ambos ejes, para evitar patrones regulares visibles en el mundo.
     */
    private static final int CENTER_OFFSET_MAX = 64;

    private final long worldSeed;

    DistrictGrid(long worldSeed) {
        this.worldSeed = worldSeed;
    }

    /**
     * Coordenada X del centro del distrito en la celda (cellX, cellZ),
     * en coordenadas de mundo.
     */
    @Override
    public double centerX(int cellX, int cellZ) {
        int base = cellX * CELL_SIZE + (CELL_SIZE / 2);
        long h = HashUtil.hashCoords(worldSeed, cellX, cellZ, "districtOffsetX");
        int offset = HashUtil.intInRange(h, -CENTER_OFFSET_MAX, CENTER_OFFSET_MAX);
        return base + offset;
    }

    /**
     * Coordenada Z del centro del distrito en la celda (cellX, cellZ),
     * en coordenadas de mundo.
     */
    @Override
    public double centerZ(int cellX, int cellZ) {
        int base = cellZ * CELL_SIZE + (CELL_SIZE / 2);
        long h = HashUtil.hashCoords(worldSeed, cellX, cellZ, "districtOffsetZ");
        int offset = HashUtil.intInRange(h, -CENTER_OFFSET_MAX, CENTER_OFFSET_MAX);
        return base + offset;
    }

    /**
     * Semilla especifica del distrito de la celda (cellX, cellZ), derivada
     * deterministicamente de la semilla del mundo y los indices de celda.
     * Usada por {@link DistrictPropertyDeriver} para construir el grafo de
     * habitaciones del distrito.
     */
    long districtSeed(int cellX, int cellZ) {
        return HashUtil.hashCoords(worldSeed, cellX, cellZ, "districtSeed");
    }
}
