package com.glados.backrooms.degradation;

import com.glados.backrooms.context.ChunkGenerationContext;
import com.glados.backrooms.graph.PlannedElement;
import com.glados.backrooms.graph.RoomNode;
import com.glados.backrooms.util.HashUtil;
import com.glados.backrooms.generation.FunctionalMaterialTable;
import net.minecraft.core.BlockPos;

import java.util.List;

/** Eje 4: degrada elementos funcionales segun nivel funcional. */
final class FunctionalDegrader {

    private final long worldSeed;

    FunctionalDegrader(long worldSeed) { this.worldSeed = worldSeed; }

    void apply(ChunkGenerationContext ctx) {
        for (RoomNode room : ctx.activeRooms) {
            for (PlannedElement el : room.plannedElements()) {
                int wx = el.worldX(), wz = el.worldZ();
                int lx = ctx.localX(wx), lz = ctx.localZ(wz);
                if (!ctx.inBounds(lx, lz)) continue;
                float deg = ctx.degradationFunctional[lx][lz];
                if (deg <= 0.1f) continue;
                long h = HashUtil.hashCoords(worldSeed, el.worldX(), el.worldZ(), "funcDeg");

                if (deg <= 0.4f) {
                    float prob = (deg - 0.1f) / 0.3f;
                    if (HashUtil.chance(h, prob)) {
                        int shift = HashUtil.intInRange(h, 1, 2);
                        int dir = HashUtil.chance(h, 0.5f) ? 1 : -1;
                        int nx = wx + (HashUtil.chance(h, 0.5f) ? dir * shift : 0);
                        int nz = wz + (HashUtil.chance(h, 0.5f) ? 0 : dir * shift);
                        if (ctx.inBounds(ctx.localX(nx), ctx.localZ(nz)) && ctx.roleMap[ctx.localX(nx)][ctx.localZ(nz)] == com.glados.backrooms.context.ColumnRole.INTERIOR_HABITACION) {
                            // move
                            int y = 47 + 1 + el.heightFromFloor();
                            ctx.chunk.setBlockState(new BlockPos(wx, y, wz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), false);
                            ctx.chunk.setBlockState(new BlockPos(nx, y, nz), el.blockState(), false);
                            if (el.blockEntityData() != null) {
                                var tag = el.blockEntityData().copy(); tag.putInt("x", nx); tag.putInt("y", y); tag.putInt("z", nz); ctx.chunk.setBlockEntityNbt(tag);
                            }
                        }
                    }
                } else if (deg <= 0.7f) {
                    float prob = (deg - 0.4f) / 0.3f;
                    if (HashUtil.chance(h, prob)) {
                        // replace type using FunctionalMaterialTable (pick different)
                        var randSeed = HashUtil.deriveSeed(h);
                        com.glados.backrooms.generation.ArchitecturalFunction gf = com.glados.backrooms.generation.ArchitecturalFunction.valueOf(el.function().name());
                        var s = com.glados.backrooms.generation.FunctionalMaterialTable.stateFor(gf, net.minecraft.util.RandomSource.create(randSeed));
                        int y = 47 + 1 + el.heightFromFloor();
                        int lx2 = ctx.localX(wx), lz2 = ctx.localZ(wz);
                        var roleHere = ctx.roleMap[lx2][lz2];
                        if (roleHere == com.glados.backrooms.context.ColumnRole.PARED_HABITACION) {
                            // find nearest interior cell and place replacement there
                            boolean placed = false;
                            for (int r = 1; r <= 6 && !placed; r++) {
                                for (int dx = -r; dx <= r && !placed; dx++) {
                                    for (int dz = -r; dz <= r && !placed; dz++) {
                                        int nx = wx + dx, nz = wz + dz;
                                        if (!ctx.inBounds(ctx.localX(nx), ctx.localZ(nz))) continue;
                                        var role = ctx.roleMap[ctx.localX(nx)][ctx.localZ(nz)];
                                        if (role == com.glados.backrooms.context.ColumnRole.INTERIOR_HABITACION
                                                || role == com.glados.backrooms.context.ColumnRole.INTERIOR_PASILLO) {
                                            int yy = 47 + 1 + el.heightFromFloor();
                                            ctx.chunk.setBlockState(new BlockPos(nx, yy, nz), s, false);
                                            placed = true;
                                        }
                                    }
                                }
                            }
                        } else {
                            ctx.chunk.setBlockState(new BlockPos(wx, y, wz), s, false);
                        }
                    }
                } else {
                    float prob = (deg - 0.7f) / 0.3f;
                    if (HashUtil.chance(h, prob)) {
                        int choice = HashUtil.intInRange(h, 0, 2);
                        int y;
                        if (choice == 0) {
                            y = 52 - 1;
                            ctx.chunk.setBlockState(new BlockPos(wx, y, wz), el.blockState(), false);
                        } else if (choice == 1) {
                                // inside wall: do NOT carve walls. Instead find nearest interior cell and place there.
                                boolean placed = false;
                                for (int r = 1; r <= 6 && !placed; r++) {
                                    for (int dx = -r; dx <= r && !placed; dx++) {
                                        for (int dz = -r; dz <= r && !placed; dz++) {
                                            int nx = wx + dx, nz = wz + dz;
                                            if (!ctx.inBounds(ctx.localX(nx), ctx.localZ(nz))) continue;
                                            var role = ctx.roleMap[ctx.localX(nx)][ctx.localZ(nz)];
                                            if (role == com.glados.backrooms.context.ColumnRole.INTERIOR_HABITACION) {
                                                int yy = 47 + 1 + el.heightFromFloor();
                                                ctx.chunk.setBlockState(new BlockPos(nx, yy, nz), el.blockState(), false);
                                                if (el.blockEntityData() != null) { var tag = el.blockEntityData().copy(); tag.putInt("x", nx); tag.putInt("y", yy); tag.putInt("z", nz); ctx.chunk.setBlockEntityNbt(tag); }
                                                placed = true;
                                            }
                                        }
                                    }
                                }
                        } else {
                            // interior pasillo
                            boolean placed = false;
                            for (int lx2 = 0; lx2 < 16 && !placed; lx2++) for (int lz2 = 0; lz2 < 16 && !placed; lz2++) {
                                if (ctx.roleMap[lx2][lz2] == com.glados.backrooms.context.ColumnRole.INTERIOR_PASILLO) {
                                    int nx = ctx.worldX(lx2), nz = ctx.worldZ(lz2); int y2 = 48;
                                    ctx.chunk.setBlockState(new BlockPos(nx, y2, nz), el.blockState(), false);
                                    if (el.blockEntityData() != null) { var tag = el.blockEntityData().copy(); tag.putInt("x", nx); tag.putInt("y", y2); tag.putInt("z", nz); ctx.chunk.setBlockEntityNbt(tag); }
                                    placed = true;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
