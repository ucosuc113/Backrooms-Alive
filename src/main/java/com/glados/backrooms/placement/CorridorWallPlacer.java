package com.glados.backrooms.placement;

import com.glados.backrooms.analysis.WallColumn;
import com.glados.backrooms.analysis.WallPrototype;
import com.glados.backrooms.analysis.WallRole;
import com.glados.backrooms.context.ColumnRole;import com.glados.backrooms.context.ColumnRole;import com.glados.backrooms.context.ChunkGenerationContext;
import com.glados.backrooms.graph.AxisAlignedSegment;
import com.glados.backrooms.graph.CorridorEdge;
import com.glados.backrooms.graph.RoomNode;
import com.glados.backrooms.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Coloca las paredes laterales de pasillos. */
final class CorridorWallPlacer {

    void place(ChunkGenerationContext ctx) {
        Map<Integer, RoomNode> roomById = new HashMap<>();
        for (RoomNode rn : ctx.activeRooms) roomById.put(rn.id(), rn);

        for (CorridorEdge edge : ctx.activeCorridors) {
            for (AxisAlignedSegment seg : edge.segments()) {
                if (seg.axis() == AxisAlignedSegment.Axis.X) {
                    for (int x = seg.startCoord(); x <= seg.endCoord(); x++) {
                        placeWallLine(ctx, x, seg.wallCoordA(), roomById, edge, seg);
                        placeWallLine(ctx, x, seg.wallCoordB(), roomById, edge, seg);
                    }
                } else {
                    for (int z = seg.startCoord(); z <= seg.endCoord(); z++) {
                        placeWallLine(ctx, seg.wallCoordA(), z, roomById, edge, seg);
                        placeWallLine(ctx, seg.wallCoordB(), z, roomById, edge, seg);
                    }
                }
            }
        }
    }

    private void placeWallLine(ChunkGenerationContext ctx, int wx, int wz,
                               Map<Integer, RoomNode> roomById, CorridorEdge edge,
                               AxisAlignedSegment seg) {
        int lx = ctx.localX(wx), lz = ctx.localZ(wz);
        if (!ctx.inBounds(lx, lz)) return;
        if (ctx.roleMap[lx][lz] == ColumnRole.ABERTURA) {
            clearColumn(ctx, wx, wz);
            return;
        }

        // elegir habitacion mas cercana (fromRoom, toRoom)
        RoomNode a = roomById.get(edge.fromRoomId());
        RoomNode b = roomById.get(edge.toRoomId());
        RoomNode chosen = chooseNearest(wx, wz, a, b);

        WallPrototype proto = null;
        if (chosen != null) proto = ctx.memoryAnalysisById.get(chosen.memoryAnalysisId())
                .wallPrototypeFor(WallRole.PASILLO);
        if (proto == null) {
            // fallback: dedicated back wall
            BlockState s = ModBlocks.BACK_WALL.get().defaultBlockState();
            for (int y = 48; y <= 51; y++) ctx.chunk.setBlockState(new BlockPos(wx, y, wz), s, false);
            return;
        }

        int segLen = Math.max(1, seg.length());
        int pos = seg.axis() == AxisAlignedSegment.Axis.X
                ? wx - seg.startCoord()
                : wz - seg.startCoord();
        WallColumn col = WallPatternMapper.mapColumn(proto, pos, segLen);
        if (col == null) col = WallPatternMapper.neutralColumn();
        for (int h = 0; h < WallColumn.HEIGHT_LEVELS; h++) {
            ctx.chunk.setBlockState(new BlockPos(wx, 48 + h, wz), ModBlocks.BACK_WALL.get().defaultBlockState(), false);
        }
    }

    private void clearColumn(ChunkGenerationContext ctx, int wx, int wz) {
        for (int y = 48; y <= 51; y++) {
            ctx.chunk.setBlockState(new BlockPos(wx, y, wz), Blocks.AIR.defaultBlockState(), false);
        }
    }

    private RoomNode chooseNearest(int wx, int wz, RoomNode a, RoomNode b) {
        if (a == null) return b;
        if (b == null) return a;
        double da = Math.hypot(a.centerX() - wx, a.centerZ() - wz);
        double db = Math.hypot(b.centerX() - wx, b.centerZ() - wz);
        return da <= db ? a : b;
    }
}
