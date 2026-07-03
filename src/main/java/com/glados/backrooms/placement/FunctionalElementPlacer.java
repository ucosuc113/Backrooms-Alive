package com.glados.backrooms.placement;

import com.glados.backrooms.analysis.PrototypeElement;
import com.glados.backrooms.graph.PlannedElement;
import com.glados.backrooms.graph.RoomNode;
import com.glados.backrooms.context.ChunkGenerationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayDeque;
import java.util.Queue;

/** Coloca los PlannedElement en el chunk, con BFS de reubicacion si bloqueado. */
final class FunctionalElementPlacer {

    void place(ChunkGenerationContext ctx) {
        for (RoomNode room : ctx.activeRooms) {
            for (PlannedElement el : room.plannedElements()) {
                int wx = el.worldX();
                int wz = el.worldZ();
                int lx = ctx.localX(wx);
                int lz = ctx.localZ(wz);
                if (!ctx.inBounds(lx, lz)) continue;

                var role = ctx.roleMap[lx][lz];
                if (role == com.glados.backrooms.context.ColumnRole.PARED_HABITACION
                        || role == com.glados.backrooms.context.ColumnRole.PARED_PASILLO
                        || role == com.glados.backrooms.context.ColumnRole.ABERTURA) {
                    // buscar celda libre prox dentro del mismo room (BFS max 4)
                    int[] found = findNearestFree(ctx, room, lx, lz, 4);
                    if (found == null) continue;
                    lx = found[0]; lz = found[1];
                    wx = ctx.worldX(lx); wz = ctx.worldZ(lz);
                }

                int y = BackroomsPlacementUtil.FLOOR_Y + 1 + el.heightFromFloor();
                ctx.chunk.setBlockState(new BlockPos(wx, y, wz), el.blockState(), false);
                if (el.blockEntityData() != null) {
                    CompoundTag tag = el.blockEntityData().copy();
                    tag.putInt("x", wx); tag.putInt("y", y); tag.putInt("z", wz);
                    ctx.chunk.setBlockEntityNbt(tag);
                }
            }
        }
    }

    private int[] findNearestFree(ChunkGenerationContext ctx, RoomNode room, int startX, int startZ, int maxRadius) {
        boolean[][] visited = new boolean[16][16];
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{startX, startZ, 0});
        visited[startX][startZ] = true;

        while (!q.isEmpty()) {
            var cur = q.remove();
            int x = cur[0], z = cur[1], dist = cur[2];
            if (dist > maxRadius) break;
            if (ctx.roleMap[x][z] == com.glados.backrooms.context.ColumnRole.INTERIOR_HABITACION) {
                int wx = ctx.worldX(x), wz = ctx.worldZ(z);
                int y = BackroomsPlacementUtil.FLOOR_Y + 1;
                var state = ctx.chunk.getBlockState(new BlockPos(wx, y, wz));
                if (state.isAir()) return new int[]{x, z};
            }
            // expand
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) != 1) continue;
                int nx = x + dx, nz = z + dz;
                if (!ctx.inBounds(nx, nz) || visited[nx][nz]) continue;
                // must belong to same room
                if (ctx.roomMap[nx][nz] != room.id()) continue;
                visited[nx][nz] = true;
                q.add(new int[]{nx, nz, dist + 1});
            }
        }
        return null;
    }
}
