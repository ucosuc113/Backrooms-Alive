package com.glados.backrooms.graph;

import com.glados.backrooms.graph.RoomSizeAssigner.MutableRoom;
import com.glados.backrooms.util.HashUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Paso 5: seleccion de conexiones finales y clasificacion jerarquica
 * (Documento de Diseno, seccion 6.5 y 6.6).
 *
 * Parte del MST (aristas PRINCIPAL) y anade el 35% adicional de las aristas
 * de Delaunay que no estan en el MST (aristas SECUNDARIO). Asigna el ancho
 * del pasillo segun la jerarquia.
 */
final class ConnectionSelector {

    /** Fraccion de aristas no-MST que se anade como secundarias. */
    private static final float SECONDARY_FRACTION = 0.35f;

    // Anchos segun seccion 6.6.
    private static final int[] PRINCIPAL_WIDTHS  = {3, 4};
    private static final int[] SECONDARY_WIDTHS  = {2, 3};

    private ConnectionSelector() {
    }

    /** Arista clasificada con jerarquia y ancho. */
    record ClassifiedEdge(int fromId, int toId, HierarchyLevel level, int width) {
    }

    /**
     * Selecciona y clasifica todas las aristas del grafo de conectividad.
     *
     * @param mstEdges      aristas del MST.
     * @param delaunayEdges aristas totales de Delaunay (superset del MST).
     * @param rooms         habitaciones del grafo.
     * @param zoneSeed      semilla de la zona para determinismo.
     * @return lista de {@link ClassifiedEdge}.
     */
    static List<ClassifiedEdge> select(List<int[]> mstEdges,
                                       List<int[]> delaunayEdges,
                                       List<MutableRoom> rooms,
                                       long zoneSeed) {
        List<ClassifiedEdge> result = new ArrayList<>();

        // Todas las aristas del MST son PRINCIPAL.
        Set<String> mstKeys = new HashSet<>();
        for (int[] e : mstEdges) {
            int a = Math.min(e[0], e[1]);
            int b = Math.max(e[0], e[1]);
            mstKeys.add(a + "," + b);
            long hw = HashUtil.hashCoords(zoneSeed, a, b, "corridorWidth");
            int width = PRINCIPAL_WIDTHS[HashUtil.intInRange(hw, 0, 1)];
            result.add(new ClassifiedEdge(e[0], e[1], HierarchyLevel.PRINCIPAL, width));
        }

        // Calcular cuantas secundarias agregar.
        List<int[]> extras = new ArrayList<>();
        for (int[] e : delaunayEdges) {
            int a = Math.min(e[0], e[1]);
            int b = Math.max(e[0], e[1]);
            if (!mstKeys.contains(a + "," + b)) {
                extras.add(e);
            }
        }
        int numSecondary = (int) Math.round(extras.size() * SECONDARY_FRACTION);

        // Mezclar deterministicamente y tomar los primeros numSecondary.
        shuffleDeterministic(extras, zoneSeed);
        for (int i = 0; i < Math.min(numSecondary, extras.size()); i++) {
            int[] e = extras.get(i);
            int a = Math.min(e[0], e[1]);
            int b = Math.max(e[0], e[1]);
            long hw = HashUtil.hashCoords(zoneSeed, a, b, "secWidth");
            int width = SECONDARY_WIDTHS[HashUtil.intInRange(hw, 0, 1)];
            result.add(new ClassifiedEdge(e[0], e[1], HierarchyLevel.SECUNDARIO, width));
        }
        return result;
    }

    // ── Internos ─────────────────────────────────────────────────────────────────

    private static void shuffleDeterministic(List<int[]> list, long seed) {
        for (int i = list.size() - 1; i > 0; i--) {
            long h = HashUtil.hash(seed, i, 0xDEAD);
            int j = HashUtil.intInRange(h, 0, i);
            int[] tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }
}
