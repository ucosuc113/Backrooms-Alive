package com.glados.backrooms.graph;

import com.glados.backrooms.analysis.StyleFingerprint;
import com.glados.backrooms.util.GeometryUtil;
import com.glados.backrooms.util.HashUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Paso 2: asigna tamanos de habitacion a cada centro distribuido
 * (Documento de Diseno, seccion 6.3).
 *
 * El tamano base es (typicalRoomWidth, typicalRoomDepth) de la
 * StyleFingerprint con una variacion de +-40%, redondeada al par mas
 * cercano. Bounds absolutos: minimo 6x6, maximo 20x20, siempre pares.
 */
final class RoomSizeAssigner {

    private static final int MIN_SIZE  = 6;
    private static final int MAX_SIZE  = 20;
    private static final float VARIATION = 0.40f; // +-40%

    private RoomSizeAssigner() {
    }

    /**
     * Construye las habitaciones con sus bounds definitivos a partir de los
     * centros provistos por {@link RoomCenterDistributor}.
     *
     * @param centers    lista de centros [x, z].
     * @param fingerprint huella de estilo del distrito.
     * @param zoneSeed   semilla de la zona.
     * @return lista de {@link MutableRoom} con bounds iniciales asignados.
     */
    static List<MutableRoom> assign(List<int[]> centers,
                                    StyleFingerprint fingerprint,
                                    long zoneSeed) {
        int baseW = Math.max(MIN_SIZE, fingerprint.typicalRoomWidth());
        int baseD = Math.max(MIN_SIZE, fingerprint.typicalRoomDepth());

        List<MutableRoom> rooms = new ArrayList<>(centers.size());
        for (int i = 0; i < centers.size(); i++) {
            int cx = centers.get(i)[0];
            int cz = centers.get(i)[1];

            long hw = HashUtil.hashCoords(zoneSeed, cx, cz, i, "roomWidth");
            long hd = HashUtil.hashCoords(zoneSeed, cx, cz, i, "roomDepth");

            int w = roundToEven(applyVariation(baseW, hw));
            int d = roundToEven(applyVariation(baseD, hd));

            w = clamp(w);
            d = clamp(d);

            int minX = cx - w / 2;
            int maxX = minX + w - 1;
            int minZ = cz - d / 2;
            int maxZ = minZ + d - 1;

            rooms.add(new MutableRoom(i, minX, minZ, maxX, maxZ));
        }
        return rooms;
    }

    private static int applyVariation(int base, long hash) {
        float delta = HashUtil.floatInRange(hash, -VARIATION, VARIATION);
        return Math.round(base * (1f + delta));
    }

    private static int roundToEven(int value) {
        return (value % 2 == 0) ? value : value + 1;
    }

    private static int clamp(int size) {
        int clamped = Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
        return roundToEven(clamped);
    }

    // ── MutableRoom (temporal, solo usado durante la generacion del grafo) ───────

    /**
     * Representacion mutable de una habitacion durante los pasos 1-3.
     * Se convierte en {@link RoomNode} al final del paso 3.
     */
    static final class MutableRoom {
        int id;
        int minX, minZ, maxX, maxZ;
        boolean eliminated = false;

        MutableRoom(int id, int minX, int minZ, int maxX, int maxZ) {
            this.id   = id;
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
        }

        int centerX() { return (minX + maxX) / 2; }
        int centerZ() { return (minZ + maxZ) / 2; }
        int width()   { return maxX - minX + 1; }
        int depth()   { return maxZ - minZ + 1; }

        GeometryUtil.IntRect bounds() {
            return new GeometryUtil.IntRect(minX, minZ, maxX, maxZ);
        }
    }
}
