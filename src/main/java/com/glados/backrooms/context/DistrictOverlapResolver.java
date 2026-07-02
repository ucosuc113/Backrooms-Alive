package com.glados.backrooms.context;

import com.glados.backrooms.analysis.MemoryAnalysisRepository;
import com.glados.backrooms.district.District;
import com.glados.backrooms.district.DistrictLookup;
import com.glados.backrooms.graph.RoomGraphCache;
import com.glados.backrooms.util.VoronoiLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.ArrayList;
import java.util.List;

/**
 * Paso 7.1: evalua DistrictLookup en los cuatro vertices del chunk y
 * determina uno o dos distritos solapantes (Documento de Diseno, seccion 7.1).
 */
final class DistrictOverlapResolver {

    private DistrictOverlapResolver() {
    }

    /** Un distrito activo con su celda y propiedades. */
    record ActiveDistrict(int cellX, int cellZ, District properties) {
    }

    /**
     * Determina los distritos que solapan con el chunk evaluando sus cuatro
     * esquinas. Si todos los vertices apuntan al mismo distrito, devuelve
     * una lista de uno. Si hay mas de uno, devuelve hasta dos.
     */
    static List<ActiveDistrict> resolve(ChunkAccess chunk,
                                        DistrictLookup lookup,
                                        MemoryAnalysisRepository repo) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        // Evaluar los 4 vertices.
        double[][] corners = {
                {minX, minZ}, {maxX, minZ}, {minX, maxZ}, {maxX, maxZ}
        };

        List<ActiveDistrict> found = new ArrayList<>(2);
        for (double[] corner : corners) {
            VoronoiLookup.CellDistance cell = lookup.nearestDistrict(corner[0], corner[1]);
            int cx = cell.cellX(), cz = cell.cellZ();
            boolean already = false;
            for (ActiveDistrict ad : found) {
                if (ad.cellX() == cx && ad.cellZ() == cz) {
                    already = true;
                    break;
                }
            }
            if (!already) {
                District props = lookup.propertiesOfCell(cx, cz, repo);
                found.add(new ActiveDistrict(cx, cz, props));
                if (found.size() == 2) break; // maximo dos
            }
        }
        return found;
    }
}
