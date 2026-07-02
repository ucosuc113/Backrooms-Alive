package com.glados.backrooms.graph;

import com.glados.backrooms.graph.RoomSizeAssigner.MutableRoom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Paso 3: resolucion de colisiones entre habitaciones mediante repulsion
 * iterativa (Documento de Diseno, seccion 6.4).
 *
 * Para cada par de habitaciones que solapa (con margen de 4 bloques) se
 * aplica un vector de empuje desde el centro de una hacia el de la otra.
 * Maximo 50 iteraciones. Si al terminar siguen existiendo solapamientos, se
 * eliminan las habitaciones mas problematicas hasta que no haya ninguno.
 */
final class CollisionResolver {

    /** Margen adicional entre habitaciones (bloques libres entre bounds). */
    private static final int MARGIN  = 4;
    /** Iteraciones maximas de repulsion. */
    private static final int MAX_ITER = 50;
    /** Fuerza de empuje por iteracion, en bloques. */
    private static final float PUSH_STRENGTH = 1.5f;

    private CollisionResolver() {
    }

    /**
     * Resuelve colisiones in-place sobre la lista de {@link MutableRoom}.
     * Las habitaciones eliminadas se marcan con {@code eliminated = true}
     * y no se incluyen en el resultado.
     *
     * @param rooms lista mutable de habitaciones (modificada en el proceso).
     * @return la misma lista, con las eliminadas removidas.
     */
    static List<MutableRoom> resolve(List<MutableRoom> rooms) {
        for (int iter = 0; iter < MAX_ITER; iter++) {
            boolean anyOverlap = false;
            for (int i = 0; i < rooms.size(); i++) {
                MutableRoom a = rooms.get(i);
                for (int j = i + 1; j < rooms.size(); j++) {
                    MutableRoom b = rooms.get(j);
                    if (overlaps(a, b)) {
                        anyOverlap = true;
                        push(a, b);
                    }
                }
            }
            if (!anyOverlap) break;
        }

        // Si aun hay solapamientos, eliminar las mas problematicas.
        boolean dirty = true;
        while (dirty) {
            dirty = false;
            MutableRoom worst = findMostOverlapping(rooms);
            if (worst != null) {
                rooms.remove(worst);
                dirty = true;
            }
        }

        // Re-numerar ids para que sean continuos.
        for (int i = 0; i < rooms.size(); i++) {
            rooms.get(i).id = i;
        }
        return rooms;
    }

    // ── Internos ─────────────────────────────────────────────────────────────────

    private static boolean overlaps(MutableRoom a, MutableRoom b) {
        return a.minX - MARGIN <= b.maxX && a.maxX + MARGIN >= b.minX
                && a.minZ - MARGIN <= b.maxZ && a.maxZ + MARGIN >= b.minZ;
    }

    private static void push(MutableRoom a, MutableRoom b) {
        int dX = b.centerX() - a.centerX();
        int dZ = b.centerZ() - a.centerZ();

        // Evitar division por cero con centros coincidentes.
        if (dX == 0 && dZ == 0) dX = 1;

        double len = Math.hypot(dX, dZ);
        int pushX = (int) Math.round(PUSH_STRENGTH * dX / len);
        int pushZ = (int) Math.round(PUSH_STRENGTH * dZ / len);

        // Mover cada habitacion en direcciones opuestas.
        a.minX -= pushX; a.maxX -= pushX;
        a.minZ -= pushZ; a.maxZ -= pushZ;
        b.minX += pushX; b.maxX += pushX;
        b.minZ += pushZ; b.maxZ += pushZ;
    }

    /** Habitacion con mas solapamientos activos, o null si no hay ninguno. */
    private static MutableRoom findMostOverlapping(List<MutableRoom> rooms) {
        int maxCount = 0;
        MutableRoom worst = null;
        for (MutableRoom a : rooms) {
            int count = 0;
            for (MutableRoom b : rooms) {
                if (a != b && overlaps(a, b)) count++;
            }
            if (count > maxCount) {
                maxCount = count;
                worst = a;
            }
        }
        return (maxCount > 0) ? worst : null;
    }
}
