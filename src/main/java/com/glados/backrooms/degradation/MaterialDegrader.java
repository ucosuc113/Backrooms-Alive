package com.glados.backrooms.degradation;

import com.glados.backrooms.analysis.MemoryAnalysis;
import com.glados.backrooms.context.ChunkGenerationContext;
import com.glados.backrooms.util.HashUtil;
import com.glados.backrooms.generation.FunctionalMaterialTable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/** Eje 2: sustituye materiales segun degradacionMaterial. */
final class MaterialDegrader {

    private final long worldSeed;

    MaterialDegrader(long worldSeed) {
        this.worldSeed = worldSeed;
    }

    void apply(ChunkGenerationContext ctx) {
        // pick a primary memory from context if available
        Optional<MemoryAnalysis> primary = ctx.memoryAnalysisById.values().stream().findFirst();
        Optional<MemoryAnalysis> secondary = ctx.memoryAnalysisById.values().stream().skip(1).findFirst();

        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            var role = ctx.roleMap[lx][lz];
            if (role != com.glados.backrooms.context.ColumnRole.PARED_HABITACION
                    && role != com.glados.backrooms.context.ColumnRole.PARED_PASILLO) continue;

            float deg = ctx.degradationMaterial[lx][lz];
            if (deg <= 0.05f) continue;
            int wx = ctx.worldX(lx), wz = ctx.worldZ(lz);

            for (int y = 48; y <= 51; y++) {
                BlockState current = ctx.chunk.getBlockState(new BlockPos(wx, y, wz));
                if (current.isAir()) continue;
                long h = HashUtil.hashCoords(worldSeed, wx, wz, y, "matDeg");
                float r = HashUtil.floatFromHash(h);
                if (r > deg) continue; // no sustituir

                BlockState substitute = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
                MemoryAnalysis primaryMem = primary.orElse(null);
                if (primaryMem != null) {
                    var sf = primaryMem.styleFingerprint();
                    if (deg < 0.6f) {
                        substitute = sf.dominantAccentBlock() != null ? sf.dominantAccentBlock() : (sf.dominantPrimaryBlock() == null ? substitute : sf.dominantPrimaryBlock());
                    } else {
                        // chance to pick secondary
                        MemoryAnalysis secondaryMem = secondary.orElse(null);
                        long h2 = HashUtil.hashCoords(worldSeed, wx, wz, y, "matDegSecondary");
                        if (secondaryMem != null && HashUtil.chance(h2, 0.3f)) {
                            var sfs = secondaryMem.styleFingerprint();
                            substitute = sfs.dominantPrimaryBlock() != null ? sfs.dominantPrimaryBlock() : substitute;
                        } else {
                            substitute = sf.dominantAccentBlock() != null ? sf.dominantAccentBlock() : (sf.dominantPrimaryBlock() == null ? substitute : sf.dominantPrimaryBlock());
                        }
                    }
                }
                ctx.chunk.setBlockState(new BlockPos(wx, y, wz), substitute, false);
            }
        }
    }
}
