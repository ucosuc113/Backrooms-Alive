package com.glados.backrooms.placement;

import com.glados.backrooms.analysis.LightingStyle;
import com.glados.backrooms.analysis.MemoryAnalysis;
import com.glados.backrooms.analysis.RoomPrototype;
import com.glados.backrooms.analysis.StyleFingerprint;
import com.glados.backrooms.classification.ArchitecturalFunction;
import com.glados.backrooms.context.ChunkGenerationContext;
import com.glados.backrooms.graph.AxisAlignedSegment;
import com.glados.backrooms.graph.CorridorEdge;
import com.glados.backrooms.graph.RoomNode;
import com.glados.backrooms.generation.FunctionalMaterialTable;
import com.glados.backrooms.registry.ModBlocks;
import com.glados.backrooms.util.HashUtil;
import com.glados.backrooms.generation.BackroomsChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Coloca iluminacion estructural en salas y pasillos según estilos. */
final class LightingPlacer {

    private static final int ROOM_CEILING_MIN_SPACING = 4;
    private static final int ROOM_CEILING_MAX_SPACING = 8;
    private static final int ROOM_WALL_LIGHT_SPACING = 6;
    private static final int CORRIDOR_LIGHT_STEP = 8;
    private static final int CORRIDOR_SIDE_LIGHT_OFFSET = 1;
    private static final int ROOM_CENTER_LIGHT_Y = 51;
    private static final int WALL_LIGHT_Y = BackroomsChunkGenerator.CEILING_Y - 1;

    void place(ChunkGenerationContext ctx) {
        for (RoomNode room : ctx.activeRooms) {
            MemoryAnalysis mem = ctx.memoryAnalysisById.get(room.memoryAnalysisId());
            StyleFingerprint sf = mem == null ? null : mem.styleFingerprint();
            LightingStyle style = sf == null ? LightingStyle.NINGUNO : sf.lightingStyle();

            switch (style) {
                case TECHO_PLANO -> placeCeilingGrid(ctx, room, sf);
                case PUNTUAL -> placePunctual(ctx, room, mem);
                case PARED -> placeWallLights(ctx, room);
                case NINGUNO -> placeMinimalRoomLights(ctx, room);
            }
        }

        for (CorridorEdge corridor : ctx.activeCorridors) {
            RoomNode fromRoom = findRoom(ctx, corridor.fromRoomId());
            RoomNode toRoom = findRoom(ctx, corridor.toRoomId());
            RoomNode referenceRoom = fromRoom != null ? fromRoom : toRoom;
            MemoryAnalysis mem = referenceRoom == null ? null : ctx.memoryAnalysisById.get(referenceRoom.memoryAnalysisId());
            StyleFingerprint sf = mem == null ? null : mem.styleFingerprint();
            LightingStyle style = sf == null ? LightingStyle.NINGUNO : sf.lightingStyle();

            for (AxisAlignedSegment segment : corridor.segments()) {
                placeCorridorLights(ctx, segment, style);
            }
        }
    }

    private void placeCeilingGrid(ChunkGenerationContext ctx, RoomNode room, StyleFingerprint sf) {
        int spacing = ROOM_CEILING_MIN_SPACING;
        if (sf != null) {
            spacing = Math.max(ROOM_CEILING_MIN_SPACING,
                    Math.min(ROOM_CEILING_MAX_SPACING, ROOM_CEILING_MIN_SPACING + Math.round(sf.wallComplexity() * 4)));
        }

        int interiorMinX = room.minX() + 1;
        int interiorMaxX = room.maxX() - 1;
        int interiorMinZ = room.minZ() + 1;
        int interiorMaxZ = room.maxZ() - 1;
        if (interiorMinX > interiorMaxX || interiorMinZ > interiorMaxZ) {
            placeMinimalRoomLights(ctx, room);
            return;
        }

        int offsetX = Math.abs((int) HashUtil.hashCoords(room.id(), room.minX(), room.minZ(), "ceilingX")) % spacing;
        int offsetZ = Math.abs((int) HashUtil.hashCoords(room.id(), room.minX(), room.minZ(), "ceilingZ")) % spacing;

        for (int x = interiorMinX + offsetX; x <= interiorMaxX; x += spacing) {
            for (int z = interiorMinZ + offsetZ; z <= interiorMaxZ; z += spacing) {
                if (!ctx.inBounds(ctx.localX(x), ctx.localZ(z))) continue;
                if (ctx.roleMap[ctx.localX(x)][ctx.localZ(z)] != com.glados.backrooms.context.ColumnRole.INTERIOR_HABITACION) continue;
                setLight(ctx, x, ROOM_CENTER_LIGHT_Y, z, ArchitecturalFunction.LIGHT);
            }
        }
    }

    private void placePunctual(ChunkGenerationContext ctx, RoomNode room, MemoryAnalysis mem) {
        RoomPrototype prototype = mem == null ? null : mem.roomPrototypeFor(room.functionalRole());
        if (prototype != null) {
            int interiorW = Math.max(1, room.width() - 2);
            int interiorD = Math.max(1, room.depth() - 2);
            boolean placed = false;
            for (var element : prototype.elements()) {
                if (element.function() != ArchitecturalFunction.LIGHT) continue;
                int rx = room.minX() + 1 + Math.round(element.normalizedX() * (interiorW - 1));
                int rz = room.minZ() + 1 + Math.round(element.normalizedZ() * (interiorD - 1));
                if (!ctx.inBounds(ctx.localX(rx), ctx.localZ(rz))) continue;
                if (ctx.roleMap[ctx.localX(rx)][ctx.localZ(rz)] != com.glados.backrooms.context.ColumnRole.INTERIOR_HABITACION) continue;
                ctx.chunk.setBlockState(new BlockPos(rx, ROOM_CENTER_LIGHT_Y, rz), element.blockState(), false);
                placed = true;
            }
            if (placed) {
                return;
            }
        }
        placeMinimalRoomLights(ctx, room);
    }

    private void placeWallLights(ChunkGenerationContext ctx, RoomNode room) {
        List<Integer> positionsX = new ArrayList<>();
        List<Integer> positionsZ = new ArrayList<>();

        int interiorMinX = room.minX() + 1;
        int interiorMaxX = room.maxX() - 1;
        int interiorMinZ = room.minZ() + 1;
        int interiorMaxZ = room.maxZ() - 1;

        // Add positions along the four walls, with deterministic spacing.
        addWallPositions(room.minX(), room.minZ(), room.maxZ(), positionsX, positionsZ, true);
        addWallPositions(room.maxX(), room.minZ(), room.maxZ(), positionsX, positionsZ, true);
        addWallPositions(room.minZ(), room.minX(), room.maxX(), positionsX, positionsZ, false);
        addWallPositions(room.maxZ(), room.minX(), room.maxX(), positionsX, positionsZ, false);

        for (int i = 0; i < positionsX.size(); i++) {
            int wx = positionsX.get(i);
            int wz = positionsZ.get(i);
            int lx = ctx.localX(wx);
            int lz = ctx.localZ(wz);
            if (!ctx.inBounds(lx, lz)) continue;
            if (ctx.roleMap[lx][lz] != com.glados.backrooms.context.ColumnRole.PARED_HABITACION) continue;
            if (ctx.roleMap[lx][lz] == com.glados.backrooms.context.ColumnRole.ABERTURA) continue;
            setLight(ctx, wx, WALL_LIGHT_Y, wz, ArchitecturalFunction.LIGHT);
        }
    }

    private void addWallPositions(int fixed, int start, int end, List<Integer> xs, List<Integer> zs, boolean vertical) {
        int spacing = ROOM_WALL_LIGHT_SPACING;
        for (int coord = start; coord <= end; coord += spacing) {
            if (vertical) {
                xs.add(fixed);
                zs.add(coord);
            } else {
                xs.add(coord);
                zs.add(fixed);
            }
        }
        if (end >= start && ((end - start) % spacing != 0)) {
            if (vertical) {
                xs.add(fixed);
                zs.add(end);
            } else {
                xs.add(end);
                zs.add(fixed);
            }
        }
    }

    private void placeMinimalRoomLights(ChunkGenerationContext ctx, RoomNode room) {
        int cx = (room.minX() + room.maxX()) / 2;
        int cz = (room.minZ() + room.maxZ()) / 2;
        if (ctx.inBounds(ctx.localX(cx), ctx.localZ(cz))
                && ctx.roleMap[ctx.localX(cx)][ctx.localZ(cz)] == com.glados.backrooms.context.ColumnRole.INTERIOR_HABITACION) {
            setLight(ctx, cx, ROOM_CENTER_LIGHT_Y, cz, ArchitecturalFunction.LIGHT);
        }
    }

    private void placeCorridorLights(ChunkGenerationContext ctx, AxisAlignedSegment seg, LightingStyle style) {
        if (style == LightingStyle.NINGUNO) return;

        int offset = Math.abs((int) HashUtil.hashCoords(seg.fixedCoord(), seg.startCoord(), seg.endCoord(), "corridor")) % CORRIDOR_LIGHT_STEP;
        if (seg.axis() == AxisAlignedSegment.Axis.X) {
            for (int x = seg.startCoord() + offset; x <= seg.endCoord(); x += CORRIDOR_LIGHT_STEP) {
                if (style == LightingStyle.PARED) {
                    placeCorridorSideLight(ctx, x, seg.wallCoordA() + CORRIDOR_SIDE_LIGHT_OFFSET, x, seg.wallCoordB() - CORRIDOR_SIDE_LIGHT_OFFSET, seg, true);
                } else {
                    int z = seg.fixedCoord();
                    placeCorridorCeilingLight(ctx, x, z);
                }
            }
        } else {
            for (int z = seg.startCoord() + offset; z <= seg.endCoord(); z += CORRIDOR_LIGHT_STEP) {
                if (style == LightingStyle.PARED) {
                    placeCorridorSideLight(ctx, seg.wallCoordA() + CORRIDOR_SIDE_LIGHT_OFFSET, z, seg.wallCoordB() - CORRIDOR_SIDE_LIGHT_OFFSET, z, seg, false);
                } else {
                    int x = seg.fixedCoord();
                    placeCorridorCeilingLight(ctx, x, z);
                }
            }
        }
    }

    private void placeCorridorSideLight(ChunkGenerationContext ctx,
                                        int xA, int zA,
                                        int xB, int zB,
                                        AxisAlignedSegment seg,
                                        boolean horizontal) {
        int index = Math.abs((int) HashUtil.hashCoords(seg.fixedCoord(), seg.startCoord(), seg.endCoord(), "side")) % 2;
        int x = index == 0 ? xA : xB;
        int z = index == 0 ? zA : zB;
        if (!ctx.inBounds(ctx.localX(x), ctx.localZ(z))) {
            x = index == 0 ? xB : xA;
            z = index == 0 ? zB : zA;
        }
        if (!ctx.inBounds(ctx.localX(x), ctx.localZ(z))) return;
        setLight(ctx, x, WALL_LIGHT_Y, z, ArchitecturalFunction.LIGHT);
    }

    private void placeCorridorCeilingLight(ChunkGenerationContext ctx, int x, int z) {
        if (!ctx.inBounds(ctx.localX(x), ctx.localZ(z))) return;
        setLight(ctx, x, ROOM_CENTER_LIGHT_Y, z, ArchitecturalFunction.LIGHT);
    }

    private void setLight(ChunkGenerationContext ctx, int x, int y, int z, ArchitecturalFunction function) {
        int lx = ctx.localX(x);
        int lz = ctx.localZ(z);
        if (!ctx.inBounds(lx, lz)) return;
        if (y < ctx.chunk.getMinBuildHeight() || y >= ctx.chunk.getMaxBuildHeight()) return;
        // use dedicated backrooms light block for structural illumination
        BlockState lightState = ModBlocks.BACK_LIGHT.get().defaultBlockState();
        ctx.chunk.setBlockState(new BlockPos(x, y, z), lightState, false);
    }

    private RoomNode findRoom(ChunkGenerationContext ctx, int id) {
        for (RoomNode room : ctx.activeRooms) {
            if (room.id() == id) return room;
        }
        return null;
    }
}
