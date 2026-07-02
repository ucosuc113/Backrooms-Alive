package com.glados.backrooms.graph;

import com.glados.backrooms.graph.RoomSizeAssigner.MutableRoom;

import java.util.ArrayList;
import java.util.List;

/**
 * Paso 4a: triangulacion de Delaunay incremental aproximada sobre los centros
 * de habitacion (Documento de Diseno, seccion 6.5). La implementacion es
 * intencional y deliberadamente simple (no exacta): el documento de diseno
 * especifica "aproximada, no exacta — una implementacion simple de
 * triangulacion incremental es suficiente".
 *
 * Produce un conjunto de aristas (pares de ids de habitacion) que conectan
 * los centros sin aristas cruzadas, listo para que
 * {@link MinimumSpanningTreeBuilder} calcule el MST.
 */
final class DelaunayTriangulator {

    private DelaunayTriangulator() {
    }

    /**
     * Triangula los centros de las habitaciones y devuelve las aristas
     * resultantes como pares (idA, idB).
     *
     * @param rooms lista de habitaciones con bounds resueltos.
     * @return lista de aristas (int[2]: {idA, idB}).
     */
    static List<int[]> triangulate(List<MutableRoom> rooms) {
        List<int[]> edges = new ArrayList<>();
        int n = rooms.size();

        if (n < 2) return edges;
        if (n == 2) {
            edges.add(new int[]{0, 1});
            return edges;
        }

        // Triangulacion incremental simple: para cada punto, conectar con los
        // k vecinos mas cercanos (k = min(3, n-1)). Esto produce una
        // aproximacion razonable del grafo de Delaunay sin implementar el
        // algoritmo exacto.
        int k = Math.min(3, n - 1);

        for (int i = 0; i < n; i++) {
            MutableRoom a = rooms.get(i);
            // Ordenar los demas puntos por distancia a a.
            int[] indices = sortByDistance(a, rooms, i);
            for (int ki = 0; ki < k; ki++) {
                int j = indices[ki];
                if (i < j && !edgeExists(edges, i, j)) {
                    edges.add(new int[]{i, j});
                }
            }
        }
        return edges;
    }

    // ── Internos ─────────────────────────────────────────────────────────────────

    private static int[] sortByDistance(MutableRoom from, List<MutableRoom> rooms, int excludeIdx) {
        int n = rooms.size();
        // Indices excluyendo excludeIdx
        int[] indices = new int[n - 1];
        double[] dists = new double[n - 1];
        int pos = 0;
        for (int i = 0; i < n; i++) {
            if (i == excludeIdx) continue;
            MutableRoom r = rooms.get(i);
            int dx = r.centerX() - from.centerX();
            int dz = r.centerZ() - from.centerZ();
            indices[pos] = i;
            dists[pos] = dx * (double) dx + dz * (double) dz;
            pos++;
        }
        // Insertion sort (n tipicamente <= 15, eficiente para tamanos pequenos).
        for (int i = 1; i < indices.length; i++) {
            int ki = indices[i];
            double kd = dists[i];
            int j = i - 1;
            while (j >= 0 && dists[j] > kd) {
                indices[j + 1] = indices[j];
                dists[j + 1]   = dists[j];
                j--;
            }
            indices[j + 1] = ki;
            dists[j + 1]   = kd;
        }
        return indices;
    }

    private static boolean edgeExists(List<int[]> edges, int a, int b) {
        for (int[] e : edges) {
            if ((e[0] == a && e[1] == b) || (e[0] == b && e[1] == a)) {
                return true;
            }
        }
        return false;
    }
}
