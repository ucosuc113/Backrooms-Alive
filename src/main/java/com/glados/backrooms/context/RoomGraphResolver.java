package com.glados.backrooms.context;

import com.glados.backrooms.context.DistrictOverlapResolver.ActiveDistrict;
import com.glados.backrooms.graph.RoomGraph;
import com.glados.backrooms.graph.RoomGraphCache;

import java.util.ArrayList;
import java.util.List;

/**
 * Paso 7.2: para cada distrito solapante, obtiene (o genera) su RoomGraph
 * del cache (Documento de Diseno, seccion 7.2).
 */
final class RoomGraphResolver {

    private RoomGraphResolver() {
    }

    record ActiveGraph(ActiveDistrict district, RoomGraph graph) {
    }

    static List<ActiveGraph> resolve(List<ActiveDistrict> districts, RoomGraphCache cache) {
        List<ActiveGraph> result = new ArrayList<>(districts.size());
        for (ActiveDistrict ad : districts) {
            RoomGraph graph = cache.getOrGenerate(ad.cellX(), ad.cellZ());
            result.add(new ActiveGraph(ad, graph));
        }
        return result;
    }
}
