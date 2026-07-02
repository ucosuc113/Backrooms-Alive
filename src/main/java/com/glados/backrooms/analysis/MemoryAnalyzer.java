package com.glados.backrooms.analysis;

import com.glados.backrooms.BackroomsMod;
import com.glados.backrooms.classification.ArchitecturalFunction;
import com.glados.backrooms.classification.MemoryFunctionClassifier;
import com.glados.backrooms.memory.MemoryBlockSnapshot;
import com.glados.backrooms.memory.MemoryRegion;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Orquestador de la Capa 0 del Documento de Diseno (seccion 4.1): recibe
 * una {@link MemoryRegion} cruda y produce el {@link MemoryAnalysis}
 * completo invocando los analizadores especializados en el orden correcto.
 *
 * <h3>Orden de ejecucion (seccion 4.1)</h3>
 * <ol>
 *   <li>Deserializar los {@link MemoryBlockSnapshot} a
 *       {@code Map<BlockPos, BlockState>} usando
 *       {@link NbtUtils#readBlockState}.</li>
 *   <li>Detectar {@code floorY} y {@code ceilingY} relativos a la memoria
 *       (NO los constantes del chunk generator).</li>
 *   <li>{@link InteriorVolumeDetector#detectVolumes} → volumenes de aire.</li>
 *   <li>{@link WallSegmentAnalyzer#analyze} → prototipos de muro, esquina,
 *       abertura.</li>
 *   <li>{@link FunctionalDistributionAnalyzer#analyze} → prototipos de
 *       habitacion y densidad funcional.</li>
 *   <li>{@link StyleFingerprintBuilder#build} → huella de estilo.</li>
 *   <li>{@link QualityThresholdEvaluator#isUsableAsPrimary} → marca de
 *       calidad.</li>
 *   <li>Ensamblar y retornar {@link MemoryAnalysis}.</li>
 * </ol>
 *
 * <h3>Manejo de errores</h3>
 * Si el analisis lanza cualquier excepcion inesperada, el metodo loguea el
 * error y retorna {@code null}. {@link MemoryAnalysisRepository} filtra los
 * nulos y sustituye por el analisis neutral cuando no queda ninguno usable.
 *
 * <h3>Thread safety</h3>
 * Sin estado mutable: {@link #analyze} es reentrante. Cada llamada opera
 * exclusivamente con variables locales y datos inmutables de la region.
 */
public final class MemoryAnalyzer {

    private MemoryAnalyzer() {
    }

    // ── Punto de entrada ─────────────────────────────────────────────────────────

    /**
     * Analiza una memoria y retorna su {@link MemoryAnalysis}, o {@code null}
     * si la memoria no pudo analizarse (se logua el motivo).
     *
     * @param region      la memoria a analizar.
     * @param blockLookup getter de bloques del servidor, necesario para
     *                    deserializar {@link net.minecraft.nbt.CompoundTag}
     *                    de bloque a {@link BlockState}. Se obtiene via
     *                    {@code registryAccess.lookup(Registries.BLOCK)}.
     */
    public static MemoryAnalysis analyze(
            MemoryRegion region,
            net.minecraft.core.HolderGetter<net.minecraft.world.level.block.Block> blockLookup) {

        try {
            return doAnalyze(region, blockLookup);
        } catch (Exception ex) {
            BackroomsMod.LOGGER.warn(
                    "[MemoryAnalyzer] Fallo al analizar memoria '{}': {}",
                    region.name(), ex.getMessage(), ex);
            return null;
        }
    }

    // ── Implementacion interna ────────────────────────────────────────────────────

    private static MemoryAnalysis doAnalyze(
            MemoryRegion region,
            net.minecraft.core.HolderGetter<net.minecraft.world.level.block.Block> blockLookup) {

        List<MemoryBlockSnapshot> snapshots = region.blocks();
        if (snapshots.isEmpty()) {
            BackroomsMod.LOGGER.warn(
                    "[MemoryAnalyzer] Memoria '{}' no tiene bloques, se omite.",
                    region.name());
            return null;
        }

        // ── Paso 1: deserializar snapshots a Map<BlockPos, BlockState> ──────────
        Map<BlockPos, BlockState> statesByPos = new HashMap<>(snapshots.size());
        for (MemoryBlockSnapshot snapshot : snapshots) {
            BlockState state = NbtUtils.readBlockState(blockLookup, snapshot.blockState());
            statesByPos.put(snapshot.relativePos().immutable(), state);
        }

        // ── Paso 2: detectar floorY y ceilingY relativos a la memoria ───────────
        // floorY = nivel Y mas bajo donde existen bloques solidos horizontales
        // (el suelo de la estructura capturada).
        // ceilingY = nivel Y mas alto analogo (el techo).
        // Se usan los bounds de la BoundingBox relativa de la region como
        // referencia inicial y se refinan buscando el primer y ultimo nivel Y
        // con bloques solidos WALL/FLOOR/CEILING.
        int[] floorCeiling = detectFloorCeiling(statesByPos, region.bounds());
        int floorY    = floorCeiling[0];
        int ceilingY  = floorCeiling[1];
        int interiorHeight = ceilingY - floorY - 1;

        if (interiorHeight <= 0) {
            BackroomsMod.LOGGER.warn(
                    "[MemoryAnalyzer] Memoria '{}' tiene altura interior <= 0 (floor={}, ceiling={}), se omite.",
                    region.name(), floorY, ceilingY);
            return null;
        }

        // ── Paso 3: volumenes de aire ────────────────────────────────────────────
        BoundingBox relativeBounds = computeRelativeBounds(statesByPos);
        List<InteriorVolumeDetector.DetectedVolume> volumes =
                InteriorVolumeDetector.detectVolumes(statesByPos, relativeBounds);

        // ── Paso 4: segmentos de muro, esquinas, aberturas ───────────────────────
        WallSegmentAnalyzer.AnalysisResult wallResult =
                WallSegmentAnalyzer.analyze(statesByPos, floorY, ceilingY, interiorHeight, volumes);

        // ── Paso 5: distribucion funcional y densidad ────────────────────────────
        FunctionalDistributionAnalyzer.AnalysisResult functionalResult =
                FunctionalDistributionAnalyzer.analyze(statesByPos, volumes, floorY, ceilingY);

        // ── Paso 6: huella de estilo ─────────────────────────────────────────────
        StyleFingerprint styleFingerprint = StyleFingerprintBuilder.build(
                statesByPos,
                volumes,
                functionalResult.functionalDensity(),
                floorY,
                ceilingY);

        // ── Paso 7: umbral de calidad ────────────────────────────────────────────
        boolean usable = QualityThresholdEvaluator.isUsableAsPrimary(
                wallResult.wallPrototypes(), volumes);

        if (!usable) {
            BackroomsMod.LOGGER.info(
                    "[MemoryAnalyzer] Memoria '{}' no cumple el umbral de calidad "
                    + "(isUsableAsPrimary=false); se usara solo como fuente de degradacion.",
                    region.name());
        }

        // ── Paso 8: ensamblar MemoryAnalysis ────────────────────────────────────
        return new MemoryAnalysis(
                region.name(),
                wallResult.wallPrototypes(),
                wallResult.cornerPrototype(),
                wallResult.openingPrototype(),
                functionalResult.roomPrototypes(),
                styleFingerprint,
                usable);
    }

    // ── Deteccion de floor/ceiling relativos ─────────────────────────────────────

    /**
     * Detecta los niveles Y del suelo y el techo dentro de la memoria usando
     * los bloques con funcion estructural (WALL, FLOOR, CEILING).
     *
     * <p>Estrategia: el nivel Y mas bajo con al menos 4 bloques solidos
     * contiguos horizontalmente se toma como {@code floorY}; el nivel Y mas
     * alto analogo como {@code ceilingY}. Si no se encuentra esa densidad,
     * se usan el minY y maxY absolutos de los bounds de la region.
     *
     * @return array de dos elementos: [floorY, ceilingY], en coordenadas
     *         relativas de la memoria.
     */
    private static int[] detectFloorCeiling(Map<BlockPos, BlockState> statesByPos,
            net.minecraft.world.level.levelgen.structure.BoundingBox bounds) {

        // Contar bloques solidos por nivel Y.
        Map<Integer, Integer> solidCountByY = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : statesByPos.entrySet()) {
            BlockState state = entry.getValue();
            if (state.isAir()) continue;
            ArchitecturalFunction fn = MemoryFunctionClassifier.classify(state);
            if (fn == ArchitecturalFunction.WALL
                    || fn == ArchitecturalFunction.FLOOR
                    || fn == ArchitecturalFunction.CEILING) {
                int y = entry.getKey().getY();
                solidCountByY.merge(y, 1, Integer::sum);
            }
        }

        // Nivel mas bajo con >= 4 bloques solidos = suelo.
        // Nivel mas alto con >= 4 bloques solidos = techo.
        final int MIN_SOLID_FOR_SURFACE = 4;
        int floorY   = bounds.minY();
        int ceilingY = bounds.maxY();

        int lowestDense  = Integer.MAX_VALUE;
        int highestDense = Integer.MIN_VALUE;

        for (Map.Entry<Integer, Integer> entry : solidCountByY.entrySet()) {
            if (entry.getValue() >= MIN_SOLID_FOR_SURFACE) {
                int y = entry.getKey();
                if (y < lowestDense)  lowestDense  = y;
                if (y > highestDense) highestDense = y;
            }
        }

        if (lowestDense != Integer.MAX_VALUE)  floorY   = lowestDense;
        if (highestDense != Integer.MIN_VALUE) ceilingY = highestDense;

        // Garantizar que floor < ceiling.
        if (floorY >= ceilingY) {
            floorY   = bounds.minY();
            ceilingY = bounds.maxY();
        }

        return new int[]{floorY, ceilingY};
    }

    /**
     * Calcula los bounds minimos que contienen todas las posiciones del mapa,
     * en el sistema de coordenadas relativo de la memoria (origen en 0,0,0).
     */
    private static BoundingBox computeRelativeBounds(Map<BlockPos, BlockState> statesByPos) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : statesByPos.keySet()) {
            if (pos.getX() < minX) minX = pos.getX();
            if (pos.getX() > maxX) maxX = pos.getX();
            if (pos.getY() < minY) minY = pos.getY();
            if (pos.getY() > maxY) maxY = pos.getY();
            if (pos.getZ() < minZ) minZ = pos.getZ();
            if (pos.getZ() > maxZ) maxZ = pos.getZ();
        }

        // Fallback si el mapa esta vacio (no deberia llegar aqui, pero por seguridad).
        if (minX == Integer.MAX_VALUE) {
            return new BoundingBox(0, 0, 0, 0, 0, 0);
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
