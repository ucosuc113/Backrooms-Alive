package com.glados.backrooms.context;

import com.glados.backrooms.graph.Apertura;
import com.glados.backrooms.graph.AxisAlignedSegment;
import com.glados.backrooms.graph.CorridorEdge;
import com.glados.backrooms.graph.RoomNode;
import com.glados.backrooms.graph.WallDirection;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.List;

/**
 * Paso 7.4: construye roleMap, roomMap y corridorMap de 16x16 segun el
 * orden de prioridad (Documento de Diseno, seccion 7.4):
 * ABIERTO -> INTERIOR/PARED_HABITACION -> ABERTURA sobreescribe paredes
 * -> INTERIOR/PARED_PASILLO.
 */
final class RoleMapBuilder {

    private RoleMapBuilder() {
    }

    record RoleMaps(ColumnRole[][] roleMap, int[][] roomMap, int[][] corridorMap) {
    }

    static RoleMaps build(List<RoomNode> rooms,
                          List<CorridorEdge> corridors,
                          List<Apertura> openings,
                          ChunkAccess chunk) {
        ColumnRole[][] roleMap   = new ColumnRole[16][16];
        int[][]        roomMap   = new int[16][16];
        int[][]        corrMap   = new int[16][16];

        int chunkMinX = chunk.getPos().getMinBlockX();
        int chunkMinZ = chunk.getPos().getMinBlockZ();

        // Inicializar.
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                roleMap[lx][lz] = ColumnRole.ABIERTO;
                roomMap[lx][lz] = -1;
                corrMap[lx][lz] = -1;
            }
        }

        // 1. Habitaciones: interior y paredes.
        for (RoomNode room : rooms) {
            for (int lx = 0; lx < 16; lx++) {
                int wx = chunkMinX + lx;
                if (wx < room.minX() || wx > room.maxX()) continue;
                for (int lz = 0; lz < 16; lz++) {
                    int wz = chunkMinZ + lz;
                    if (wz < room.minZ() || wz > room.maxZ()) continue;

                    boolean onEdge = (wx == room.minX() || wx == room.maxX()
                            || wz == room.minZ() || wz == room.maxZ());
                    ColumnRole role = onEdge ? ColumnRole.PARED_HABITACION
                                             : ColumnRole.INTERIOR_HABITACION;
                    roleMap[lx][lz] = role;
                    roomMap[lx][lz] = room.id();
                }
            }
        }

        // 2. Aberturas sobreescriben paredes.
        for (Apertura ap : openings) {
            WallDirection dir = ap.wallDirection();
            int fixed = ap.fixedCoord();
            for (int coord = ap.startWorldCoord(); coord <= ap.endWorldCoord(); coord++) {
                int wx, wz;
                if (dir == WallDirection.NORTE || dir == WallDirection.SUR) {
                    wx = coord; wz = fixed;
                } else {
                    wx = fixed; wz = coord;
                }
                int lx = wx - chunkMinX, lz = wz - chunkMinZ;
                if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16) continue;
                roleMap[lx][lz] = ColumnRole.ABERTURA;
            }
        }

        // 3. Pasillos: interior y paredes laterales.
        for (CorridorEdge corr : corridors) {
            for (AxisAlignedSegment seg : corr.segments()) {
                // Interior del segmento.
                for (int lx = 0; lx < 16; lx++) {
                    int wx = chunkMinX + lx;
                    if (wx < seg.minX() || wx > seg.maxX()) continue;
                    for (int lz = 0; lz < 16; lz++) {
                        int wz = chunkMinZ + lz;
                        if (wz < seg.minZ() || wz > seg.maxZ()) continue;

                        boolean onWall = (wx == seg.wallCoordA() || wx == seg.wallCoordB()
                                || wz == seg.wallCoordA() || wz == seg.wallCoordB());

                        // Paredes de pasillo solo en las coordenadas de pared del segmento.
                        boolean isWallCol;
                        if (seg.axis() == AxisAlignedSegment.Axis.X) {
                            isWallCol = (wz == seg.wallCoordA() || wz == seg.wallCoordB());
                        } else {
                            isWallCol = (wx == seg.wallCoordA() || wx == seg.wallCoordB());
                        }

                        ColumnRole existing = roleMap[lx][lz];
                        if (existing == ColumnRole.ABERTURA) continue; // aberturas no se sobreescriben

                        if (isWallCol) {
                            if (existing == ColumnRole.ABIERTO) {
                                roleMap[lx][lz] = ColumnRole.PARED_PASILLO;
                                corrMap[lx][lz] = corr.id();
                            }
                        } else {
                            if (existing == ColumnRole.ABIERTO || existing == ColumnRole.PARED_PASILLO) {
                                roleMap[lx][lz] = ColumnRole.INTERIOR_PASILLO;
                                corrMap[lx][lz] = corr.id();
                            }
                        }
                    }
                }
            }
        }

        return new RoleMaps(roleMap, roomMap, corrMap);
    }
}
