package com.glados.backrooms.graph;

import com.glados.backrooms.graph.ConnectionSelector.ClassifiedEdge;
import com.glados.backrooms.graph.CorridorRouter.RoutedCorridor;
import com.glados.backrooms.graph.RoomSizeAssigner.MutableRoom;

import java.util.ArrayList;
import java.util.List;

/**
 * Paso 7: calcula la posicion exacta de cada abertura en la pared de la
 * habitacion correspondiente (Documento de Diseno, seccion 6.8).
 *
 * Reglas:
 * - La abertura va en la cara de la habitacion mas cercana al extremo del
 *   pasillo que la toca.
 * - La posicion a lo largo de la pared es el centro del segmento disponible.
 * - Los 2 primeros y 2 ultimos bloques de la pared no pueden ser abertura.
 * - La anchura de la abertura = min(ancho del pasillo, longitud_pared - 4).
 * - La fixedCoord no puede ser multiplo de 16 (restriccion anti chunk-border):
 *   si lo es, se desplaza 1 bloque hacia el interior.
 */
final class OpeningPlacer {

    /** Bloques de margen en cada extremo de la pared que no pueden ser abertura. */
    private static final int WALL_MARGIN = 2;

    private OpeningPlacer() {
    }

    /**
     * Calcula las aberturas para todos los corredores.
     *
     * @param edges   aristas clasificadas con jerarquia y ancho.
     * @param routed  segmentos de pasillo ya enrutados.
     * @param rooms   habitaciones con bounds resueltos.
     * @return lista de {@link PlacedOpenings}, una por arista.
     */
    static List<PlacedOpenings> place(List<ClassifiedEdge> edges,
                                      List<RoutedCorridor> routed,
                                      List<MutableRoom> rooms) {
        List<PlacedOpenings> result = new ArrayList<>(edges.size());
        for (int i = 0; i < edges.size(); i++) {
            ClassifiedEdge edge = edges.get(i);
            MutableRoom fromRoom = rooms.get(edge.fromId());
            MutableRoom toRoom   = rooms.get(edge.toId());
            RoutedCorridor rc    = routed.get(i);
            int width            = edge.width();

            Apertura fromApertura = calcApertura(fromRoom, toRoom, width, rc, edge.fromId(), i, true);
            Apertura toApertura   = calcApertura(toRoom, fromRoom, width, rc, edge.toId(),   i, false);

            result.add(new PlacedOpenings(fromApertura, toApertura));
        }
        return result;
    }

    // ── Internos ─────────────────────────────────────────────────────────────────

    private static Apertura calcApertura(MutableRoom room, MutableRoom other,
                                         int corridorWidth, RoutedCorridor rc,
                                         int roomId, int corridorId,
                                         boolean isFrom) {
        // Determinar la cara mas cercana al centro del otro cuarto.
        WallDirection face = closestFace(room, other.centerX(), other.centerZ());
        int fixedCoord     = wallCoord(room, face);

        // Evitar que fixedCoord sea multiplo de 16 (restriccion seccion 6.8).
        if (fixedCoord % 16 == 0) {
            fixedCoord += isInward(face) ? 1 : -1;
        }

        // Longitud de la pared segun la cara.
        int wallStart, wallEnd;
        if (face == WallDirection.NORTE || face == WallDirection.SUR) {
            wallStart = room.minX + WALL_MARGIN;
            wallEnd   = room.maxX - WALL_MARGIN;
        } else {
            wallStart = room.minZ + WALL_MARGIN;
            wallEnd   = room.maxZ - WALL_MARGIN;
        }
        int wallLen = wallEnd - wallStart + 1;

        // Anchura de la abertura: min(corridorWidth, longitud_pared - 4) segun seccion 6.8.
        int aperturaWidth = Math.max(1, Math.min(corridorWidth, wallLen - 4));

        // Centrar la abertura en la pared disponible.
        int center = wallStart + wallLen / 2;
        int startCoord = center - aperturaWidth / 2;
        int endCoord   = startCoord + aperturaWidth - 1;

        return new Apertura(face, startCoord, endCoord, fixedCoord,
                roomId, corridorId, -1);
    }

    private static WallDirection closestFace(MutableRoom room, double otherCX, double otherCZ) {
        int cx = room.centerX(), cz = room.centerZ();
        double dNorth = cz - room.minZ; // distancia al borde norte (minZ)
        double dSouth = room.maxZ - cz;
        double dWest  = cx - room.minX;
        double dEast  = room.maxX - cx;

        // La cara que mira hacia el otro cuarto es la que tiene menor distancia
        // en la direccion del otro cuarto.
        double toNorth = cz - otherCZ; // positivo si el otro esta al norte
        double toSouth = otherCZ - cz;
        double toWest  = cx - otherCX;
        double toEast  = otherCX - cx;

        // Elegir la cara dominante segun el eje de mayor diferencia.
        if (Math.abs(otherCX - cx) > Math.abs(otherCZ - cz)) {
            return (otherCX > cx) ? WallDirection.ESTE : WallDirection.OESTE;
        } else {
            return (otherCZ > cz) ? WallDirection.SUR : WallDirection.NORTE;
        }
    }

    private static int wallCoord(MutableRoom room, WallDirection face) {
        return switch (face) {
            case NORTE -> room.minZ;
            case SUR   -> room.maxZ;
            case OESTE -> room.minX;
            case ESTE  -> room.maxX;
        };
    }

    /** Si la cara mira "hacia adentro" al desplazarse positivamente. */
    private static boolean isInward(WallDirection face) {
        return face == WallDirection.NORTE || face == WallDirection.OESTE;
    }

    // ── DTO ──────────────────────────────────────────────────────────────────────

    /** Par de aberturas para una arista (una en cada extremo del pasillo). */
    record PlacedOpenings(Apertura fromApertura, Apertura toApertura) {
    }
}
