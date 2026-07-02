package com.glados.backrooms.context;

import com.glados.backrooms.context.RoomGraphResolver.ActiveGraph;
import com.glados.backrooms.graph.Apertura;
import com.glados.backrooms.graph.AxisAlignedSegment;
import com.glados.backrooms.graph.CorridorEdge;
import com.glados.backrooms.graph.RoomNode;
import com.glados.backrooms.util.GeometryUtil;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.ArrayList;
import java.util.List;

/**
 * Paso 7.3: filtra RoomNode y segmentos de CorridorEdge que intersectan
 * con el chunk, con un margen de 1 bloque extra (Documento de Diseno,
 * seccion 7.3).
 */
final class ActiveElementFilter {

    /** Margen de interseccion extra en cada direccion. */
    private static final int MARGIN = 1;

    private ActiveElementFilter() {
    }

    record FilteredElements(
            List<RoomNode> activeRooms,
            List<CorridorEdge> activeCorridors,
            List<Apertura> activeOpenings
    ) {
    }

    static FilteredElements filter(List<ActiveGraph> graphs, ChunkAccess chunk) {
        int minX = chunk.getPos().getMinBlockX() - MARGIN;
        int minZ = chunk.getPos().getMinBlockZ() - MARGIN;
        int maxX = chunk.getPos().getMinBlockX() + 15 + MARGIN;
        int maxZ = chunk.getPos().getMinBlockZ() + 15 + MARGIN;
        GeometryUtil.IntRect chunkBounds = new GeometryUtil.IntRect(minX, minZ, maxX, maxZ);

        List<RoomNode> rooms     = new ArrayList<>();
        List<CorridorEdge> corrs = new ArrayList<>();
        List<Apertura> openings  = new ArrayList<>();

        for (ActiveGraph ag : graphs) {
            for (RoomNode node : ag.graph().rooms()) {
                if (node.bounds().intersects(chunkBounds)) {
                    rooms.add(node);
                    openings.addAll(node.openings());
                }
            }
            for (CorridorEdge edge : ag.graph().corridors()) {
                boolean anySegInChunk = false;
                for (AxisAlignedSegment seg : edge.segments()) {
                    GeometryUtil.IntRect segRect =
                            new GeometryUtil.IntRect(seg.minX(), seg.minZ(), seg.maxX(), seg.maxZ());
                    if (segRect.intersects(chunkBounds)) {
                        anySegInChunk = true;
                        break;
                    }
                }
                if (anySegInChunk) {
                    corrs.add(edge);
                }
            }
        }
        return new FilteredElements(rooms, corrs, openings);
    }
}
