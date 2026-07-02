package com.glados.backrooms.graph;

import com.glados.backrooms.graph.ConnectionSelector.ClassifiedEdge;
import com.glados.backrooms.graph.RoomSizeAssigner.MutableRoom;
import com.glados.backrooms.util.HashUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Paso 6: calcula la ruta axis-aligned de cada arista y resuelve
 * intersecciones entre pasillos (Documento de Diseno, seccion 6.7).
 *
 * Los pasillos son siempre axis-aligned (uno o dos segmentos rectos). Si
 * dos segmentos se cruzan, la zona de cruce ya queda cubierta por ambos
 * rectangulos — la Capa 4 lo colocara limpiamente.
 */
final class CorridorRouter {

    /** Si la diferencia en un eje es menor a este valor, el pasillo es recto. */
    private static final int ALIGNMENT_THRESHOLD = 8;

    private CorridorRouter() {
    }

    /**
     * Resultado de enrutar una arista: lista de {@link AxisAlignedSegment}
     * (uno o dos) que forman el pasillo.
     */
    record RoutedCorridor(int edgeIdx, List<AxisAlignedSegment> segments) {
    }

    /**
     * Enruta todas las aristas clasificadas.
     *
     * @param edges    aristas con jerarquia y ancho.
     * @param rooms    habitaciones con bounds resueltos.
     * @param zoneSeed semilla de la zona para la eleccion del codo.
     * @return lista de {@link RoutedCorridor} en el mismo orden que {@code edges}.
     */
    static List<RoutedCorridor> route(List<ClassifiedEdge> edges,
                                      List<MutableRoom> rooms,
                                      long zoneSeed) {
        List<RoutedCorridor> result = new ArrayList<>(edges.size());
        for (int i = 0; i < edges.size(); i++) {
            ClassifiedEdge edge = edges.get(i);
            MutableRoom from = rooms.get(edge.fromId());
            MutableRoom to   = rooms.get(edge.toId());
            List<AxisAlignedSegment> segs = buildSegments(from, to, edge.width(), zoneSeed, i);
            result.add(new RoutedCorridor(i, segs));
        }
        return result;
    }

    // ── Internos ─────────────────────────────────────────────────────────────────

    private static List<AxisAlignedSegment> buildSegments(MutableRoom from, MutableRoom to,
                                                          int width, long seed, int edgeIdx) {
        int ax = from.centerX(), az = from.centerZ();
        int bx = to.centerX(),   bz = to.centerZ();
        int dx = Math.abs(bx - ax);
        int dz = Math.abs(bz - az);

        List<AxisAlignedSegment> segs = new ArrayList<>(2);

        if (dz < ALIGNMENT_THRESHOLD) {
            // Casi alineados en Z -> pasillo recto en X.
            int minX = Math.min(ax, bx);
            int maxX = Math.max(ax, bx);
            segs.add(AxisAlignedSegment.alongX(az, minX, maxX, width));

        } else if (dx < ALIGNMENT_THRESHOLD) {
            // Casi alineados en X -> pasillo recto en Z.
            int minZ = Math.min(az, bz);
            int maxZ = Math.max(az, bz);
            segs.add(AxisAlignedSegment.alongZ(ax, minZ, maxZ, width));

        } else {
            // Codo: determinar si primero X luego Z, o viceversa.
            long h = HashUtil.hashCoords(seed, ax + bx, az + bz, edgeIdx, "corridorElbow");
            boolean xFirst = HashUtil.chance(h, 0.5f);

            if (xFirst) {
                // Primer segmento en X hasta bx, segundo en Z hasta bz.
                int minX = Math.min(ax, bx), maxX = Math.max(ax, bx);
                int minZ = Math.min(az, bz), maxZ = Math.max(az, bz);
                segs.add(AxisAlignedSegment.alongX(az, minX, maxX, width));
                segs.add(AxisAlignedSegment.alongZ(bx, minZ, maxZ, width));
            } else {
                // Primer segmento en Z hasta bz, segundo en X hasta bx.
                int minZ = Math.min(az, bz), maxZ = Math.max(az, bz);
                int minX = Math.min(ax, bx), maxX = Math.max(ax, bx);
                segs.add(AxisAlignedSegment.alongZ(ax, minZ, maxZ, width));
                segs.add(AxisAlignedSegment.alongX(bz, minX, maxX, width));
            }
        }
        return segs;
    }
}
