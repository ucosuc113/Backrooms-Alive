package com.glados.backrooms.degradation;

import com.glados.backrooms.context.ChunkGenerationContext;
import com.glados.backrooms.util.HashUtil;
import com.glados.backrooms.util.NoiseField;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** Eje 1: elimina bloques estructurales segun degradacion estructural y ruido local. */
final class StructuralDegrader {

    private final long worldSeed;
    private final NoiseField noise;

    StructuralDegrader(long worldSeed) {
        this.worldSeed = worldSeed;
        this.noise = new NoiseField(worldSeed ^ 0xABCDABCDL, 4, 1);
    }

    void apply(ChunkGenerationContext ctx) {
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            var role = ctx.roleMap[lx][lz];
            if (role != com.glados.backrooms.context.ColumnRole.PARED_HABITACION
                    && role != com.glados.backrooms.context.ColumnRole.PARED_PASILLO) continue;
            float deg = ctx.degradationStructural[lx][lz];
            if (deg < 0.10f) continue;

            float base = 1.0f - 0.5f * deg;
            int wx = ctx.worldX(lx), wz = ctx.worldZ(lz);
            double n = noise.evaluate(wx, wz);
            float umbral = clamp(base + (float) n * 0.10f - 0.05f, 0.10f, 0.85f);

            for (int y = 48; y <= 51; y++) {
                long h = HashUtil.hashCoords(worldSeed, wx, wz, y, "structElim");
                float r = HashUtil.floatFromHash(h);
                if (r > umbral) ctx.chunk.setBlockState(new BlockPos(wx, y, wz), Blocks.AIR.defaultBlockState(), false);
            }
        }
    }

    private static float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }
}
