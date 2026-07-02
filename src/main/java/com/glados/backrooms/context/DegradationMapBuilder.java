package com.glados.backrooms.context;

import com.glados.backrooms.context.DistrictOverlapResolver.ActiveDistrict;
import com.glados.backrooms.district.District;
import com.glados.backrooms.district.DistrictLookup;
import com.glados.backrooms.util.NoiseField;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.List;

/**
 * Paso 7.5: calcula los cuatro arrays de degradacion de 16x16 para el chunk
 * (Documento de Diseno, seccion 7.5).
 *
 * Combina:
 * - Campo global Simplex (via DistrictLookup).
 * - degradacionBase del distrito dominante.
 * - Boost de transicion entre distritos.
 * - Ruido local de periodo 8 (structural) y 4 (material).
 * - degradacionFuncional: varía por habitacion mas que por posicion (igual
 *   al valor de transicion total del distrito dominante para toda la habitacion).
 * - degradacionAditiva: correlaciona con structural pero no es identico.
 */
final class DegradationMapBuilder {

    private final NoiseField localStructural;
    private final NoiseField localMaterial;
    private final NoiseField localAdditive;

    DegradationMapBuilder(long worldSeed) {
        this.localStructural = new NoiseField(worldSeed ^ 0x1111L, 8, 2);
        this.localMaterial   = new NoiseField(worldSeed ^ 0x2222L, 4, 2);
        this.localAdditive   = new NoiseField(worldSeed ^ 0x3333L, 8, 2);
    }

    record DegradationMaps(
            float[][] structural,
            float[][] material,
            float[][] functional,
            float[][] additive
    ) {
    }

    DegradationMaps build(ChunkAccess chunk,
                          List<ActiveDistrict> districts,
                          DistrictLookup lookup) {
        float[][] structural = new float[16][16];
        float[][] material   = new float[16][16];
        float[][] functional = new float[16][16];
        float[][] additive   = new float[16][16];

        int chunkMinX = chunk.getPos().getMinBlockX();
        int chunkMinZ = chunk.getPos().getMinBlockZ();

        // Distrito dominante: el primero de la lista (mayor cobertura o unico).
        District dominant = districts.isEmpty() ? null : districts.get(0).properties();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                double wx = chunkMinX + lx;
                double wz = chunkMinZ + lz;

                float globalNoise  = lookup.globalDegradation(wx, wz);
                float distBase     = (dominant != null) ? dominant.degradacionBase() : 0.0f;
                float transition   = lookup.transitionBoost(wx, wz);
                float combined     = clamp(Math.max(distBase, globalNoise) + transition);

                float localS = (float) localStructural.evaluate(wx, wz);
                float localM = (float) localMaterial.evaluate(wx, wz);
                float localA = (float) localAdditive.evaluate(wx, wz);

                // degradacionEstructural: campo combinado + ruido local periodo 8.
                structural[lx][lz] = clamp(combined * 0.7f + localS * 0.3f);

                // degradacionMaterial: variacion mas fina con periodo 4.
                material[lx][lz] = clamp(combined * 0.6f + localM * 0.4f);

                // degradacionFuncional: varía por habitacion (usamos solo el
                // valor de distrito, sin ruido local fino, segun doc 7.5).
                functional[lx][lz] = combined;

                // degradacionAditiva: correlaciona con structural pero distinto.
                additive[lx][lz] = clamp(combined * 0.5f + localA * 0.5f);
            }
        }
        return new DegradationMaps(structural, material, functional, additive);
    }

    private static float clamp(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }
}
