package com.glados.backrooms.placement;

import com.glados.backrooms.analysis.CornerPrototype;
import com.glados.backrooms.analysis.MemoryAnalysis;
import com.glados.backrooms.analysis.OpeningPrototype;
import com.glados.backrooms.analysis.WallColumn;
import com.glados.backrooms.analysis.WallPrototype;
import com.glados.backrooms.analysis.WallRole;
import com.glados.backrooms.context.ChunkGenerationContext;
import com.glados.backrooms.graph.RoomNode;
import com.glados.backrooms.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Coloca paredes de habitacion dentro del chunk, siguiendo el prototipo de memoria. */
final class RoomWallPlacer {

    void place(ChunkGenerationContext ctx) {
        for (RoomNode room : ctx.activeRooms) {
            MemoryAnalysis mem = ctx.memoryAnalysisById.get(room.memoryAnalysisId());
            WallPrototype proto = null;
            if (mem != null) proto = mem.wallPrototypeFor(WallRole.EXTERIOR);
            if (proto == null && mem != null) proto = mem.wallPrototypeFor(WallRole.INTERIOR);

            CornerPrototype corner = mem == null ? null : mem.cornerPrototype();
            OpeningPrototype openingProto = mem == null ? null : mem.openingPrototype();

            // cuatro segmentos: norte (minZ), sur (maxZ), oeste (minX), este (maxX)
            // norte/sur: iterate x; oeste/este: iterate z
            int minX = room.minX(); int maxX = room.maxX();
            int minZ = room.minZ(); int maxZ = room.maxZ();

            // Norte
            placeHorizontal(room, minX, maxX, minZ, true, proto, corner, openingProto, ctx);
            // Sur
            placeHorizontal(room, minX, maxX, maxZ, false, proto, corner, openingProto, ctx);
            // Oeste
            placeVertical(room, minZ, maxZ, minX, true, proto, corner, openingProto, ctx);
            // Este
            placeVertical(room, minZ, maxZ, maxX, false, proto, corner, openingProto, ctx);
        }
    }

    private void placeHorizontal(RoomNode room, int minX, int maxX, int fixedZ, boolean isNorth,
                                 WallPrototype proto, CornerPrototype corner, OpeningPrototype openingProto,
                                 ChunkGenerationContext ctx) {
        int segLen = maxX - minX + 1;
        for (int x = minX; x <= maxX; x++) {
            int localX = ctx.localX(x);
            int localZ = ctx.localZ(fixedZ);
            if (!ctx.inBounds(localX, localZ)) continue;

            if (ctx.roleMap[localX][localZ] == com.glados.backrooms.context.ColumnRole.ABERTURA) {
                clearColumn(ctx, x, fixedZ);
                continue;
            }

            int pos = x - minX;
            WallColumn col = WallPatternMapper.mapColumn(proto, pos, segLen);
            if (col == null) col = WallPatternMapper.neutralColumn();
            boolean isCorner = (x == minX || x == maxX) && (fixedZ == room.minZ() || fixedZ == room.maxZ());
            if (isCorner) {
                // corners are treated as solid back wall columns
                for (int h = 0; h < WallColumn.HEIGHT_LEVELS; h++) {
                    setIfInChunk(ctx, x, 48 + h, fixedZ, ModBlocks.BACK_WALL.get().defaultBlockState());
                }
                continue;
            }

            // all non-opening wall columns are the dedicated back wall
            for (int h = 0; h < WallColumn.HEIGHT_LEVELS; h++) {
                setIfInChunk(ctx, x, 48 + h, fixedZ, ModBlocks.BACK_WALL.get().defaultBlockState());
            }

            // if this wall has an adjacent opening and a framed opening material, place the frame over the back wall
            if (openingProto != null && openingProto.hasFrame() && hasAdjacentOpening(ctx, localX, localZ)) {
                for (int h = 0; h < WallColumn.HEIGHT_LEVELS; h++) {
                    setIfInChunk(ctx, x, 48 + h, fixedZ, openingProto.frameMaterial());
                }
            }
        }
    }

    private void placeVertical(RoomNode room, int minZ, int maxZ, int fixedX, boolean isWest,
                               WallPrototype proto, CornerPrototype corner, OpeningPrototype openingProto,
                               ChunkGenerationContext ctx) {
        int segLen = maxZ - minZ + 1;
        for (int z = minZ; z <= maxZ; z++) {
            int localX = ctx.localX(fixedX);
            int localZ = ctx.localZ(z);
            if (!ctx.inBounds(localX, localZ)) continue;

            if (ctx.roleMap[localX][localZ] == com.glados.backrooms.context.ColumnRole.ABERTURA) {
                clearColumn(ctx, fixedX, z);
                continue;
            }

            int pos = z - minZ;
            WallColumn col = WallPatternMapper.mapColumn(proto, pos, segLen);
            if (col == null) col = WallPatternMapper.neutralColumn();
            boolean isCorner = (z == minZ || z == maxZ) && (fixedX == room.minX() || fixedX == room.maxX());
            if (isCorner && corner != null) {
                placeCornerPrototype(ctx, room, fixedX, z, corner);
                continue;
            }

            for (int h = 0; h < WallColumn.HEIGHT_LEVELS; h++) {
                setIfInChunk(ctx, fixedX, 48 + h, z, col.blockAt(h));
            }

            if (openingProto != null && openingProto.hasFrame() && hasAdjacentOpening(ctx, localX, localZ)) {
                for (int h = 0; h < WallColumn.HEIGHT_LEVELS; h++) {
                    setIfInChunk(ctx, fixedX, 48 + h, z, openingProto.frameMaterial());
                }
            }
        }
    }

    private void placeCornerPrototype(ChunkGenerationContext ctx, RoomNode room, int cornerX, int cornerZ,
                                      CornerPrototype corner) {
        int dx = cornerX == room.minX() ? 1 : -1;
        int dz = cornerZ == room.minZ() ? 1 : -1;
        for (int ox = 0; ox < 2; ox++) {
            for (int oz = 0; oz < 2; oz++) {
                int wx = cornerX + ox * dx;
                int wz = cornerZ + oz * dz;
                int localX = ctx.localX(wx);
                int localZ = ctx.localZ(wz);
                if (!ctx.inBounds(localX, localZ)) continue;
                var column = corner.at(ox, oz);
                for (int h = 0; h < WallColumn.HEIGHT_LEVELS; h++) {
                    setIfInChunk(ctx, wx, 48 + h, wz, column.blockAt(h));
                }
            }
        }
    }

    private boolean hasAdjacentOpening(ChunkGenerationContext ctx, int lx, int lz) {
        for (int[] delta : new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
            int nx = lx + delta[0];
            int nz = lz + delta[1];
            if (!ctx.inBounds(nx, nz)) continue;
            if (ctx.roleMap[nx][nz] == com.glados.backrooms.context.ColumnRole.ABERTURA) return true;
        }
        return false;
    }

    private void clearColumn(ChunkGenerationContext ctx, int wx, int wz) {
        for (int y = 48; y <= 51; y++) setIfInChunk(ctx, wx, y, wz, Blocks.AIR.defaultBlockState());
    }

    private void setIfInChunk(ChunkGenerationContext ctx, int wx, int y, int wz, BlockState state) {
        int lx = ctx.localX(wx), lz = ctx.localZ(wz);
        if (!ctx.inBounds(lx, lz)) return;
        if (y < ctx.chunk.getMinBuildHeight() || y >= ctx.chunk.getMaxBuildHeight()) return;
        ctx.chunk.setBlockState(new BlockPos(wx, y, wz), state, false);
    }
}
