package com.glados.backrooms.context;

import com.glados.backrooms.graph.Apertura;
import com.glados.backrooms.graph.CorridorEdge;
import com.glados.backrooms.graph.RoomNode;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.ArrayList;
import java.util.List;

/**
 * Paso 7.6: compila la lista de Aperturas activas que intersectan el chunk,
 * usada por la salvaguarda de conectividad en la Capa 5
 * (Documento de Diseno, seccion 7.6).
 */
final class OpeningCollector {

    private OpeningCollector() {
    }

    /**
     * Recoge todas las Apertura de las habitaciones y corredores activos
     * cuya fixedCoord o rango (start/end) cae dentro del chunk.
     */
    static List<Apertura> collect(List<RoomNode> rooms,
                                  List<CorridorEdge> corridors,
                                  ChunkAccess chunk) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        List<Apertura> result = new ArrayList<>();

        // Aberturas de habitaciones.
        for (RoomNode room : rooms) {
            for (Apertura ap : room.openings()) {
                if (aperturaInChunk(ap, minX, maxX, minZ, maxZ)) {
                    result.add(ap);
                }
            }
        }

        // Aberturas de los extremos de los pasillos (pueden estar en chunks distintos).
        for (CorridorEdge corr : corridors) {
            if (aperturaInChunk(corr.openingAtFrom(), minX, maxX, minZ, maxZ)) {
                result.add(corr.openingAtFrom());
            }
            if (aperturaInChunk(corr.openingAtTo(), minX, maxX, minZ, maxZ)) {
                result.add(corr.openingAtTo());
            }
        }
        return result;
    }

    private static boolean aperturaInChunk(Apertura ap,
                                           int minX, int maxX, int minZ, int maxZ) {
        // fixedCoord pertenece al chunk, o al menos parte del rango start-end cae en el.
        switch (ap.wallDirection()) {
            case NORTE: case SUR:
                return ap.fixedCoord() >= minZ && ap.fixedCoord() <= maxZ
                        && ap.endWorldCoord() >= minX && ap.startWorldCoord() <= maxX;
            case ESTE: case OESTE:
                return ap.fixedCoord() >= minX && ap.fixedCoord() <= maxX
                        && ap.endWorldCoord() >= minZ && ap.startWorldCoord() <= maxZ;
            default:
                return false;
        }
    }
}
