package com.glados.backrooms.placement;

import com.glados.backrooms.context.ChunkGenerationContext;
import com.glados.backrooms.graph.AxisAlignedSegment;
import com.glados.backrooms.graph.CorridorEdge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** Limpia el interior de los pasillos (pone aire en Y=48..51). */
final class CorridorInteriorCleaner {

    void place(ChunkGenerationContext ctx) {
        for (CorridorEdge edge : ctx.activeCorridors) {
            for (AxisAlignedSegment seg : edge.segments()) {
                if (seg.axis() == AxisAlignedSegment.Axis.X) {
                    for (int x = seg.startCoord(); x <= seg.endCoord(); x++) {
                        for (int z = seg.wallCoordA() + 1; z <= seg.wallCoordB() - 1; z++) {
                            int lx = ctx.localX(x), lz = ctx.localZ(z);
                            if (!ctx.inBounds(lx, lz)) continue;
                            for (int y = 48; y <= 51; y++) ctx.chunk.setBlockState(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), false);
                        }
                    }
                } else {
                    for (int z = seg.startCoord(); z <= seg.endCoord(); z++) {
                        for (int x = seg.wallCoordA() + 1; x <= seg.wallCoordB() - 1; x++) {
                            int lx = ctx.localX(x), lz = ctx.localZ(z);
                            if (!ctx.inBounds(lx, lz)) continue;
                            for (int y = 48; y <= 51; y++) ctx.chunk.setBlockState(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), false);
                        }
                    }
                }
            }
        }
    }
}
