package com.glados.backrooms.degradation;

import com.glados.backrooms.analysis.WallColumn;
import com.glados.backrooms.analysis.WallPrototype;
import com.glados.backrooms.context.ChunkGenerationContext;
import com.glados.backrooms.graph.RoomNode;
import com.glados.backrooms.util.HashUtil;
import com.glados.backrooms.util.NoiseField;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** Eje 3: duplicacion, extension y acumulacion de esquina. */
final class AdditiveEffectsApplier {

    private final long worldSeed;
    private final NoiseField noise;

    AdditiveEffectsApplier(long worldSeed) {
        this.worldSeed = worldSeed;
        this.noise = new NoiseField(worldSeed ^ 0xABCDABCDL, 4, 1);
    }

    void apply(ChunkGenerationContext ctx) {
        // Effect 1: wall duplication
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            if (ctx.roleMap[lx][lz] != com.glados.backrooms.context.ColumnRole.PARED_HABITACION) continue;
            float deg = ctx.degradationAdditive[lx][lz];
            if (deg < 0.35f) continue;
            int wx = ctx.worldX(lx), wz = ctx.worldZ(lz);
            long h = HashUtil.hashCoords(worldSeed, wx, wz, "wallDup");
            float prob = (deg - 0.35f) / 0.65f;
            if (!HashUtil.chance(h, prob)) continue;

            // determine perpendicular direction: pick any neighbor that is interior
            int dirX = 0, dirZ = 0;
            if (lx > 0 && (ctx.roleMap[lx-1][lz] == com.glados.backrooms.context.ColumnRole.INTERIOR_HABITACION || ctx.roleMap[lx-1][lz] == com.glados.backrooms.context.ColumnRole.INTERIOR_PASILLO)) dirX = -1;
            else if (lx < 15 && (ctx.roleMap[lx+1][lz] == com.glados.backrooms.context.ColumnRole.INTERIOR_HABITACION || ctx.roleMap[lx+1][lz] == com.glados.backrooms.context.ColumnRole.INTERIOR_PASILLO)) dirX = 1;
            else if (lz > 0 && (ctx.roleMap[lx][lz-1] == com.glados.backrooms.context.ColumnRole.INTERIOR_HABITACION || ctx.roleMap[lx][lz-1] == com.glados.backrooms.context.ColumnRole.INTERIOR_PASILLO)) dirZ = -1;
            else if (lz < 15 && (ctx.roleMap[lx][lz+1] == com.glados.backrooms.context.ColumnRole.INTERIOR_HABITACION || ctx.roleMap[lx][lz+1] == com.glados.backrooms.context.ColumnRole.INTERIOR_PASILLO)) dirZ = 1;
            else continue;

            // leave gap +1 as air, place duplicates at +2
            int tx = wx + dirX * 2, tz = wz + dirZ * 2;
            int gapx = wx + dirX, gapz = wz + dirZ;
            if (ctx.inBounds(ctx.localX(tx), ctx.localZ(tz))) {
                // copy existing wall blocks (if any) at source to target
                for (int y = 48; y <= 51; y++) {
                    var s = ctx.chunk.getBlockState(new BlockPos(wx, y, wz));
                    // apply structural degr on the duplicate by chance
                    ctx.chunk.setBlockState(new BlockPos(tx, y, tz), s, false);
                }
                // clear gap
                for (int y = 48; y <= 51; y++) ctx.chunk.setBlockState(new BlockPos(gapx, y, gapz), Blocks.AIR.defaultBlockState(), false);
            }
        }

        // Effect 2: erroneous extension at segment ends
        for (RoomNode room : ctx.activeRooms) {
            int[] xs = {room.minX(), room.maxX()};
            int[] zs = {room.minZ(), room.maxZ()};
            for (int ex : xs) for (int ez : zs) {
                int lx = ctx.localX(ex), lz = ctx.localZ(ez);
                if (!ctx.inBounds(lx, lz)) continue;
                float deg = ctx.degradationAdditive[lx][lz];
                if (deg < 0.5f) continue;
                long h = HashUtil.hashCoords(worldSeed, ex, ez, "wallExt");
                float prob = Math.min(1.0f, (deg - 0.5f) * 1.5f);
                if (!HashUtil.chance(h, prob)) continue;
                int len = 1 + HashUtil.intInRange(h, 0, 2);
                // direction: toward interior (simple heuristic)
                int dirX = room.minX() == ex ? 1 : (room.maxX() == ex ? -1 : 0);
                int dirZ = room.minZ() == ez ? 1 : (room.maxZ() == ez ? -1 : 0);
                for (int i = 1; i <= len; i++) {
                    int tx = ex + dirX * i, tz = ez + dirZ * i;
                    if (!ctx.inBounds(ctx.localX(tx), ctx.localZ(tz))) break;
                    var existing = ctx.chunk.getBlockState(new BlockPos(tx, 48, tz));
                    if (existing != null && existing != Blocks.AIR.defaultBlockState()) break;
                    // copy material from edge
                    for (int y = 48; y <= 51; y++) {
                        var s = ctx.chunk.getBlockState(new BlockPos(ex, y, ez));
                        ctx.chunk.setBlockState(new BlockPos(tx, y, tz), s, false);
                    }
                }
            }
        }

        // Effect 3: corner accumulation
        for (RoomNode room : ctx.activeRooms) {
            int[][] corners = {{room.minX(), room.minZ()}, {room.minX(), room.maxZ()}, {room.maxX(), room.minZ()}, {room.maxX(), room.maxZ()}};
            for (var c : corners) {
                int ex = c[0], ez = c[1];
                int lx = ctx.localX(ex), lz = ctx.localZ(ez);
                if (!ctx.inBounds(lx, lz)) continue;
                float deg = ctx.degradationAdditive[lx][lz];
                if (deg < 0.4f) continue;
                long h = HashUtil.hashCoords(worldSeed, ex, ez, "cornerAccum");
                float prob = (deg - 0.4f) / 0.6f;
                if (!HashUtil.chance(h, prob)) continue;
                int tx = ex + (room.minX() == ex ? 1 : -1);
                int tz = ez + (room.minZ() == ez ? 1 : -1);
                if (!ctx.inBounds(ctx.localX(tx), ctx.localZ(tz))) continue;
                // place extra block with same material as wall
                var s = ctx.chunk.getBlockState(new BlockPos(ex, 48, ez));
                ctx.chunk.setBlockState(new BlockPos(tx, 48, tz), s, false);
            }
        }
    }
}
