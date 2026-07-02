package com.glados.backrooms.graph;

import com.glados.backrooms.graph.RoomSizeAssigner.MutableRoom;

import java.util.ArrayList;
import java.util.List;

/**
 * Paso 4b: arbol de expansion minima (MST) sobre las aristas de Delaunay,
 * usando el algoritmo de Kruskal (Documento de Diseno, seccion 6.5).
 *
 * El MST garantiza que cada habitacion sea alcanzable desde cualquier otra
 * con el minimo total de longitud de pasillo.
 */
final class MinimumSpanningTreeBuilder {

    private MinimumSpanningTreeBuilder() {
    }

    /**
     * Calcula el MST sobre las aristas de Delaunay y devuelve la lista de
     * aristas que lo forman (subconjunto de {@code delaunayEdges}).
     *
     * @param delaunayEdges aristas producidas por {@link DelaunayTriangulator}.
     * @param rooms         habitaciones cuyos centros son los nodos del grafo.
     * @return lista de aristas del MST ({@code int[2]: {idA, idB}}).
     */
    static List<int[]> buildMST(List<int[]> delaunayEdges, List<MutableRoom> rooms) {
        int n = rooms.size();
        if (n < 2 || delaunayEdges.isEmpty()) return new ArrayList<>();

        // Ordenar aristas por longitud (distancia euclidiana entre centros).
        List<int[]> sorted = new ArrayList<>(delaunayEdges);
        sorted.sort((e1, e2) -> {
            double d1 = dist(rooms.get(e1[0]), rooms.get(e1[1]));
            double d2 = dist(rooms.get(e2[0]), rooms.get(e2[1]));
            return Double.compare(d1, d2);
        });

        // Union-Find para deteccion de ciclos.
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        List<int[]> mst = new ArrayList<>();
        for (int[] edge : sorted) {
            int a = edge[0], b = edge[1];
            int pa = find(parent, a);
            int pb = find(parent, b);
            if (pa != pb) {
                parent[pa] = pb;
                mst.add(edge);
                if (mst.size() == n - 1) break;
            }
        }
        return mst;
    }

    // ── Internos ─────────────────────────────────────────────────────────────────

    private static double dist(MutableRoom a, MutableRoom b) {
        int dx = a.centerX() - b.centerX();
        int dz = a.centerZ() - b.centerZ();
        return Math.sqrt(dx * (double) dx + dz * (double) dz);
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]]; // path compression
            i = parent[i];
        }
        return i;
    }
}
