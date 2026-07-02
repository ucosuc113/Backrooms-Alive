package com.glados.backrooms.district;

import com.glados.backrooms.analysis.MemoryAnalysis;
import com.glados.backrooms.analysis.MemoryAnalysisRepository;
import com.glados.backrooms.util.HashUtil;

import java.util.List;

/**
 * Implementa la derivacion de propiedades de distrito (Documento de Diseno,
 * seccion 5.2). Dado el centro de un distrito en coordenadas de mundo, deriva
 * deterministicamente todos sus campos a partir de hashes.
 *
 * Sin estado mutable: cada invocacion de {@link #derive} es una funcion pura
 * (Documento de Arquitectura, seccion 2.5: "DistrictPropertyDeriver").
 */
final class DistrictPropertyDeriver {

    private DistrictPropertyDeriver() {
    }

    /**
     * Deriva las propiedades completas del distrito cuyo centro esta en
     * ({@code centerX}, {@code centerZ}).
     *
     * @param worldSeed semilla del mundo.
     * @param centerX   coordenada X del centro del distrito (ya calculada por
     *                  {@link DistrictGrid}).
     * @param centerZ   coordenada Z del centro del distrito.
     * @param cellX     indice de celda X del distrito.
     * @param cellZ     indice de celda Z del distrito.
     * @param repo      repositorio de analisis de memoria; debe estar ya
     *                  construido ({@link MemoryAnalysisRepository#build} ya
     *                  llamado).
     * @return el {@link District} con todos sus campos fijos.
     */
    static District derive(long worldSeed,
                           double centerX, double centerZ,
                           int cellX, int cellZ,
                           MemoryAnalysisRepository repo) {

        int cx = (int) Math.round(centerX);
        int cz = (int) Math.round(centerZ);

        // ── Seleccion de memoria primaria ────────────────────────────────────────
        List<MemoryAnalysis> usable = repo.allUsable();
        long hPrimary = HashUtil.hashCoords(worldSeed, cx, cz, "primaryMemory");
        int primaryIndex = HashUtil.intInRange(hPrimary, 0, usable.size() - 1);
        String primaryMemoryId = usable.get(primaryIndex).id();

        // ── Seleccion de memoria secundaria (30% de probabilidad) ────────────────
        String secondaryMemoryId = null;
        long hSecChance = HashUtil.hashCoords(worldSeed, cx, cz, "secondaryChance");
        if (HashUtil.chance(hSecChance, 0.30f) && usable.size() > 1) {
            long hSec = HashUtil.hashCoords(worldSeed, cx, cz, "secondaryMemory");
            // Elegir un indice distinto al primario.
            int secondaryIndex = HashUtil.intInRange(hSec, 0, usable.size() - 2);
            if (secondaryIndex >= primaryIndex) {
                secondaryIndex++;
            }
            secondaryMemoryId = usable.get(secondaryIndex).id();
        }

        // ── densidadBase [0.3, 0.9] ──────────────────────────────────────────────
        long hDensidad = HashUtil.hashCoords(worldSeed, cx, cz, "densidadBase");
        float densidadBase = HashUtil.floatInRange(hDensidad, 0.3f, 0.9f);

        // ── degradacionBase [0.0, 0.8], sesgada hacia valores bajos ─────────────
        // Se elevan dos muestras uniform y se toma el minimo para sesgar a bajo.
        long hDeg1 = HashUtil.hashCoords(worldSeed, cx, cz, "degradacionBase1");
        long hDeg2 = HashUtil.hashCoords(worldSeed, cx, cz, "degradacionBase2");
        float raw1 = HashUtil.floatInRange(hDeg1, 0.0f, 0.8f);
        float raw2 = HashUtil.floatInRange(hDeg2, 0.0f, 0.8f);
        float degradacionBase = Math.min(raw1, raw2);

        // ── semilla del distrito ─────────────────────────────────────────────────
        long semilla = HashUtil.hashCoords(worldSeed, cx, cz, "districtSeed");

        // ── esEspacioAbierto (seccion 5.5) ───────────────────────────────────────
        boolean esEspacioAbierto = densidadBase < 0.4f && degradacionBase > 0.6f;

        return new District(
                centerX, centerZ,
                cellX, cellZ,
                primaryMemoryId, secondaryMemoryId,
                densidadBase, degradacionBase,
                semilla, esEspacioAbierto
        );
    }
}
