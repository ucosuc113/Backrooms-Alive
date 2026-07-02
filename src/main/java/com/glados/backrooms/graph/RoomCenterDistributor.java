package com.glados.backrooms.graph;

import com.glados.backrooms.util.GeometryUtil;
import com.glados.backrooms.util.HashUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Paso 1: distribuye centros de habitacion dentro del area del distrito
 * usando una variante simplificada de Poisson-disk sampling
 * (Documento de Diseno, seccion 6.2).
 *
 * Garantiza que ningun par de centros este a menos de MIN_DISTANCE bloques
 * entre si. Intenta colocar entre 5 y 15 centros deterministicamente.
 */
final class RoomCenterDistributor {

    /** Distancia minima entre centros de habitacion, en bloques. */
    private static final int MIN_DISTANCE = 20;

    /** Intentos maximos antes de aceptar los centros ya colocados. */
    private static final int MAX_ATTEMPTS_PER_ROOM = 30;

    /** Margen desde el borde del bounds de la zona, en bloques. */
    private static final int EDGE_MARGIN = 16;

    private RoomCenterDistributor() {
    }

    /**
     * Genera los centros de habitacion dentro de {@code zoneBounds}.
     *
     * @param zoneBounds  area de la zona (192x192 tipicamente).
     * @param zoneSeed    semilla de la zona para determinismo.
     * @param densidadBase [0.3, 0.9] — controla cuantas habitaciones se intentan.
     * @return lista de posiciones (x, z) como int[] de 2 elementos.
     */
    static List<int[]> distribute(GeometryUtil.IntRect zoneBounds,
                                  long zoneSeed,
                                  float densidadBase) {
        // N entre 5 y 15: base escalada por densidadBase mas variacion determinista
        // de hasta +-1 habitacion via hash, para que zonas con identica densidadBase
        // puedan diferir entre si.
        long hN = HashUtil.hash(zoneSeed, 0x1A);
        int nBase = (int) Math.round(5 + (densidadBase - 0.3f) / 0.6f * 10);
        int nVariation = HashUtil.intInRange(hN, -1, 1);
        int n = Math.max(5, Math.min(15, nBase + nVariation));

        int xMin = zoneBounds.minX() + EDGE_MARGIN;
        int xMax = zoneBounds.maxX() - EDGE_MARGIN;
        int zMin = zoneBounds.minZ() + EDGE_MARGIN;
        int zMax = zoneBounds.maxZ() - EDGE_MARGIN;

        if (xMax <= xMin || zMax <= zMin) {
            return new ArrayList<>();
        }

        List<int[]> centers = new ArrayList<>(n);
        long h = HashUtil.hash(zoneSeed, 0x2B);

        for (int i = 0; i < n; i++) {
            boolean placed = false;
            for (int attempt = 0; attempt < MAX_ATTEMPTS_PER_ROOM; attempt++) {
                h = HashUtil.hash(h, i, attempt);
                int x = HashUtil.intInRange(h, xMin, xMax);
                h = HashUtil.hash(h, i, attempt, 0x3C);
                int z = HashUtil.intInRange(h, zMin, zMax);

                if (isFarEnough(x, z, centers)) {
                    centers.add(new int[]{x, z});
                    placed = true;
                    break;
                }
            }
            // Si no se pudo colocar tras MAX_ATTEMPTS, se acepta lo que hay.
            if (!placed) {
                break;
            }
        }
        return centers;
    }

    private static boolean isFarEnough(int x, int z, List<int[]> existing) {
        int minDistSq = MIN_DISTANCE * MIN_DISTANCE;
        for (int[] c : existing) {
            int dx = x - c[0];
            int dz = z - c[1];
            if (dx * dx + dz * dz < minDistSq) {
                return false;
            }
        }
        return true;
    }
}
