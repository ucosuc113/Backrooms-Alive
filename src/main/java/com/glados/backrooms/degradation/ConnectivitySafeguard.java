package com.glados.backrooms.degradation;

import com.glados.backrooms.context.ChunkGenerationContext;
import com.glados.backrooms.graph.Apertura;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** Paso final: fuerza aire en el volumen de todas las aberturas activas dentro del chunk. */
final class ConnectivitySafeguard {

    void apply(ChunkGenerationContext ctx) {
        for (Apertura a : ctx.activeOpenings) {
            int start = a.startWorldCoord();
            int end = a.endWorldCoord();
            int fixed = a.fixedCoord();
            for (int coord = start; coord <= end; coord++) {
                for (int y = 48; y <= 51; y++) {
                    int wx;
                    int wz;
                    switch (a.wallDirection()) {
                        case NORTE, SUR -> {
                            wx = coord;
                            wz = fixed;
                        }
                        case ESTE, OESTE -> {
                            wx = fixed;
                            wz = coord;
                        }
                        default -> {
                            wx = coord;
                            wz = fixed;
                        }
                    }
                    int lx = ctx.localX(wx), lz = ctx.localZ(wz);
                    if (!ctx.inBounds(lx, lz)) continue;
                    ctx.chunk.setBlockState(new BlockPos(wx, y, wz), Blocks.AIR.defaultBlockState(), false);
                }
            }
        }
    }
}
