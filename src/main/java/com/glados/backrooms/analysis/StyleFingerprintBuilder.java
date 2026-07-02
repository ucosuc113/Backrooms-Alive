package com.glados.backrooms.analysis;

import com.glados.backrooms.classification.ArchitecturalFunction;
import com.glados.backrooms.classification.MemoryFunctionClassifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Implementa la seccion 4.5 del Documento de Diseno: construye la
 * {@link StyleFingerprint} de una memoria a partir de los datos brutos de
 * bloque y de los volumenes detectados.
 *
 * <h3>Que produce</h3>
 * <ul>
 *   <li>{@code primaryBlocks}: hasta 3 pares (BlockState, frecuencia relativa).
 *       Los bloques mas frecuentes en paredes.</li>
 *   <li>{@code accentBlocks}: hasta 3 bloques que aparecen con menos del 15%
 *       de frecuencia pero de forma no aleatoria (agrupados o en posiciones
 *       repetidas). Aproximacion: bloques con frecuencia en [MIN_ACCENT, 15%).</li>
 *   <li>{@code lightingStyle}: se infiere del tipo de bloques de luz y su
 *       posicion (techo, pared, puntual o ninguno).</li>
 *   <li>{@code functionalDensity}: recibida directamente de
 *       {@link FunctionalDistributionAnalyzer}.</li>
 *   <li>{@code wallComplexity}: heterogeneidad del patron de pared [0,1].</li>
 *   <li>{@code typicalRoomWidth, typicalRoomDepth}: promedio de dimensiones
 *       horizontales de los volumenes de tipo ROOM.</li>
 *   <li>{@code typicalCorridorWidth}: promedio de la dimension minima de los
 *       volumenes de tipo CORRIDOR (o DEFAULT_CORRIDOR_WIDTH si no hay).</li>
 * </ul>
 *
 * <h3>Invariantes</h3>
 * <ul>
 *   <li>Sin estado entre invocaciones: {@link #build} es puro.</li>
 *   <li>El resultado es siempre un {@link StyleFingerprint} valido (nunca null,
 *       nunca lanza excepciones por datos vacios: se aplican defaults).</li>
 * </ul>
 */
public final class StyleFingerprintBuilder {

    /**
     * Frecuencia relativa minima para que un bloque sea considerado acento.
     * Por debajo de este umbral se asume ocurrencia aleatoria sin valor estético.
     */
    private static final float MIN_ACCENT_FREQUENCY = 0.02f;

    /**
     * Frecuencia relativa maxima para bloques de acento (Documento de Diseno 4.5).
     * Equivale a {@link StyleFingerprint#ACCENT_FREQUENCY_CEILING}.
     */
    private static final float MAX_ACCENT_FREQUENCY = StyleFingerprint.ACCENT_FREQUENCY_CEILING;

    /**
     * Numero minimo de bloques de pared necesarios para calcular complejidad.
     * Con menos de esto se considera complejidad 0 (un solo material repetido).
     */
    private static final int MIN_WALL_BLOCKS_FOR_COMPLEXITY = 4;

    /**
     * Nivel Y relativo a floorY+1 por debajo del cual una luz en pared se
     * clasifica como PARED (altura media = nivel 2 de 4).
     */
    private static final int WALL_LIGHT_MAX_HEIGHT_INDEX = 2;

    private StyleFingerprintBuilder() {
    }

    // ── Punto de entrada ─────────────────────────────────────────────────────────

    /**
     * Construye la {@link StyleFingerprint} completa de una memoria.
     *
     * @param statesByPos        mapa posicion-relativa → BlockState de la memoria.
     * @param volumes            volumenes detectados por {@link InteriorVolumeDetector}.
     * @param functionalDensity  densidad funcional calculada por
     *                           {@link FunctionalDistributionAnalyzer}.
     * @param floorY             nivel Y del suelo (en coordenadas relativas de la memoria).
     * @param ceilingY           nivel Y del techo.
     */
    public static StyleFingerprint build(
            Map<BlockPos, BlockState> statesByPos,
            List<InteriorVolumeDetector.DetectedVolume> volumes,
            float functionalDensity,
            int floorY, int ceilingY) {

        int interiorHeight = ceilingY - floorY - 1;

        // ── 1. Frecuencia de materiales en paredes ──────────────────────────────
        WallMaterialStats materialStats = computeWallMaterialStats(statesByPos, floorY, ceilingY);

        List<StyleFingerprint.WeightedBlock> primaryBlocks =
                buildWeightedList(materialStats.primaryCandidates, StyleFingerprint.MAX_PRIMARY_BLOCKS);
        List<StyleFingerprint.WeightedBlock> accentBlocks =
                buildWeightedList(materialStats.accentCandidates, StyleFingerprint.MAX_ACCENT_BLOCKS);

        // ── 2. Complejidad de pared ─────────────────────────────────────────────
        float wallComplexity = computeWallComplexity(materialStats);

        // ── 3. Estilo de iluminacion ────────────────────────────────────────────
        LightingStyle lightingStyle = inferLightingStyle(statesByPos, floorY, ceilingY, interiorHeight);

        // ── 4. Dimensiones tipicas de habitaciones y pasillos ──────────────────
        RoomDimensions roomDims = computeRoomDimensions(volumes);

        return new StyleFingerprint(
                primaryBlocks,
                accentBlocks,
                lightingStyle,
                functionalDensity,
                wallComplexity,
                roomDims.typicalRoomWidth(),
                roomDims.typicalRoomDepth(),
                roomDims.typicalCorridorWidth());
    }

    // ── Frecuencia de materiales ─────────────────────────────────────────────────

    /**
     * Contabiliza la frecuencia de cada BlockState que aparece en posiciones
     * de pared (entre floorY y ceilingY, funcion WALL).
     */
    private static WallMaterialStats computeWallMaterialStats(
            Map<BlockPos, BlockState> statesByPos, int floorY, int ceilingY) {

        Map<BlockState, Integer> counts = new HashMap<>();
        int total = 0;

        for (Map.Entry<BlockPos, BlockState> entry : statesByPos.entrySet()) {
            int y = entry.getKey().getY();
            if (y <= floorY || y >= ceilingY) continue;
            BlockState state = entry.getValue();
            if (state.isAir()) continue;
            if (MemoryFunctionClassifier.classify(state) != ArchitecturalFunction.WALL) continue;
            counts.merge(state, 1, Integer::sum);
            total++;
        }

        if (total == 0) {
            return new WallMaterialStats(Collections.emptyList(), Collections.emptyList(), 0);
        }

        // Convertir a frecuencias relativas y ordenar de mayor a menor.
        int finalTotal = total;
        List<Map.Entry<BlockState, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Comparator.<Map.Entry<BlockState, Integer>>comparingInt(Map.Entry::getValue).reversed());

        List<StyleFingerprint.WeightedBlock> primaryCandidates = new ArrayList<>();
        List<StyleFingerprint.WeightedBlock> accentCandidates  = new ArrayList<>();

        for (Map.Entry<BlockState, Integer> entry : sorted) {
            float freq = (float) entry.getValue() / finalTotal;
            if (freq >= MAX_ACCENT_FREQUENCY) {
                // Bloque frecuente → primario.
                primaryCandidates.add(new StyleFingerprint.WeightedBlock(entry.getKey(), freq));
            } else if (freq >= MIN_ACCENT_FREQUENCY) {
                // Frecuencia baja pero significativa → acento.
                accentCandidates.add(new StyleFingerprint.WeightedBlock(entry.getKey(), freq));
            }
            // Por debajo de MIN_ACCENT_FREQUENCY: ruido estadistico, se ignora.
        }

        return new WallMaterialStats(primaryCandidates, accentCandidates, sorted.size());
    }

    /**
     * Toma los primeros {@code maxCount} elementos de la lista de candidatos y
     * normaliza sus frecuencias para que sumen 1.0 dentro del subconjunto
     * seleccionado (la frecuencia relativa dentro del grupo, no del total de
     * bloques de pared).
     */
    private static List<StyleFingerprint.WeightedBlock> buildWeightedList(
            List<StyleFingerprint.WeightedBlock> candidates, int maxCount) {

        if (candidates.isEmpty()) return Collections.emptyList();

        List<StyleFingerprint.WeightedBlock> top = candidates.subList(
                0, Math.min(maxCount, candidates.size()));

        // Renormalizar: la suma de frecuencias del subconjunto puede ser < 1.
        float sum = 0f;
        for (StyleFingerprint.WeightedBlock wb : top) {
            sum += wb.frequency();
        }
        if (sum <= 0f) return Collections.emptyList();

        List<StyleFingerprint.WeightedBlock> normalized = new ArrayList<>(top.size());
        for (StyleFingerprint.WeightedBlock wb : top) {
            normalized.add(new StyleFingerprint.WeightedBlock(wb.state(), wb.frequency() / sum));
        }
        return Collections.unmodifiableList(normalized);
    }

    // ── Complejidad de pared ─────────────────────────────────────────────────────

    /**
     * Calcula {@code wallComplexity} [0,1] como la fraccion de tipos de bloque
     * distintos respecto al total de bloques de pared contabilizados.
     *
     * <ul>
     *   <li>0 = todos los bloques de pared son identicos (un solo material).</li>
     *   <li>1 = cada bloque de pared es de un tipo diferente (caos total).</li>
     * </ul>
     *
     * En la practica la mayoria de memorias produce un valor entre 0.05 y 0.4.
     * Valores por encima de 0.5 indican memorias con mucha variedad de materiales.
     */
    private static float computeWallComplexity(WallMaterialStats stats) {
        if (stats.totalUniqueTypes < MIN_WALL_BLOCKS_FOR_COMPLEXITY) {
            return 0f;
        }
        // Cantidad de tipos primarios + acento como indicador de diversidad.
        int significantTypes = stats.primaryCandidates.size() + stats.accentCandidates.size();
        // Normalizamos contra un maximo razonable de 10 tipos distintos significativos.
        return Math.min(1f, significantTypes / 10f);
    }

    // ── Estilo de iluminacion ────────────────────────────────────────────────────

    /**
     * Infiere el {@link LightingStyle} examinando la posicion relativa de los
     * bloques con funcion LIGHT dentro de la memoria:
     * <ul>
     *   <li>Si la mayoria de las luces estan en Y = ceilingY-1 (justo bajo el
     *       techo): {@link LightingStyle#TECHO_PLANO}.</li>
     *   <li>Si la mayoria estan en el nivel medio interior (heightIndex <= 2):
     *       {@link LightingStyle#PARED}.</li>
     *   <li>Si hay luces pero no predomina ninguna posicion: {@link LightingStyle#PUNTUAL}.</li>
     *   <li>Si no hay luces: {@link LightingStyle#NINGUNO}.</li>
     * </ul>
     */
    private static LightingStyle inferLightingStyle(
            Map<BlockPos, BlockState> statesByPos,
            int floorY, int ceilingY, int interiorHeight) {

        int totalLights   = 0;
        int ceilingLights = 0; // Y == ceilingY - 1
        int wallLights    = 0; // heightIndex <= WALL_LIGHT_MAX_HEIGHT_INDEX (< techo, > suelo bajo)

        for (Map.Entry<BlockPos, BlockState> entry : statesByPos.entrySet()) {
            int y = entry.getKey().getY();
            BlockState state = entry.getValue();
            if (y <= floorY || y >= ceilingY) continue;
            if (MemoryFunctionClassifier.classify(state) != ArchitecturalFunction.LIGHT) continue;

            totalLights++;
            int heightIndex = interiorHeight <= 1 ? 0
                    : (y - floorY - 1) * (WallColumn.HEIGHT_LEVELS - 1) / Math.max(1, interiorHeight - 1);

            if (y == ceilingY - 1) {
                ceilingLights++;
            } else if (heightIndex <= WALL_LIGHT_MAX_HEIGHT_INDEX) {
                wallLights++;
            }
        }

        if (totalLights == 0) {
            return LightingStyle.NINGUNO;
        }

        // Para ser clasificado como TECHO_PLANO o PARED necesitamos al menos el
        // 50% de las luces en esa categoria.
        float ceilingRatio = (float) ceilingLights / totalLights;
        float wallRatio    = (float) wallLights    / totalLights;

        if (ceilingRatio >= 0.5f) {
            return LightingStyle.TECHO_PLANO;
        }
        if (wallRatio >= 0.5f) {
            return LightingStyle.PARED;
        }
        return LightingStyle.PUNTUAL;
    }

    // ── Dimensiones tipicas de habitaciones y pasillos ──────────────────────────

    /**
     * Calcula las dimensiones tipicas de habitaciones y pasillos como el promedio
     * de las dimensiones horizontales de todos los volumenes del tipo correspondiente.
     *
     * <p>Para habitaciones: promedio de spanX y spanZ de todos los volumenes ROOM.</p>
     * <p>Para pasillos: promedio de la dimension minima (la anchura) de los CORRIDOR.</p>
     * <p>Si no hay pasillos: {@link StyleFingerprint#DEFAULT_CORRIDOR_WIDTH}.</p>
     */
    private static RoomDimensions computeRoomDimensions(
            List<InteriorVolumeDetector.DetectedVolume> volumes) {

        int roomWidthSum  = 0;
        int roomDepthSum  = 0;
        int roomCount     = 0;

        int corridorWidthSum = 0;
        int corridorCount    = 0;

        for (InteriorVolumeDetector.DetectedVolume vol : volumes) {
            if (vol.shape() == InteriorVolumeDetector.VolumeShape.ROOM) {
                roomWidthSum += vol.horizontalSpanX();
                roomDepthSum += vol.horizontalSpanZ();
                roomCount++;
            } else if (vol.shape() == InteriorVolumeDetector.VolumeShape.CORRIDOR) {
                // La anchura de un pasillo es su dimension horizontal minima.
                corridorWidthSum += Math.min(vol.horizontalSpanX(), vol.horizontalSpanZ());
                corridorCount++;
            }
        }

        int typicalRoomWidth  = roomCount > 0
                ? Math.max(1, roomWidthSum / roomCount) : 10;
        int typicalRoomDepth  = roomCount > 0
                ? Math.max(1, roomDepthSum / roomCount) : 10;
        int typicalCorridorWidth = corridorCount > 0
                ? Math.max(1, corridorWidthSum / corridorCount)
                : StyleFingerprint.DEFAULT_CORRIDOR_WIDTH;

        return new RoomDimensions(typicalRoomWidth, typicalRoomDepth, typicalCorridorWidth);
    }

    // ── Estructuras auxiliares internas ─────────────────────────────────────────

    /**
     * Resultados intermedios del calculo de frecuencia de materiales de pared,
     * usados tanto para construir primaryBlocks/accentBlocks como para calcular
     * wallComplexity sin releer el mapa de estados.
     */
    private record WallMaterialStats(
            List<StyleFingerprint.WeightedBlock> primaryCandidates,
            List<StyleFingerprint.WeightedBlock> accentCandidates,
            int totalUniqueTypes) {
    }

    /**
     * Dimensiones tipicas calculadas en {@link #computeRoomDimensions}.
     */
    private record RoomDimensions(int typicalRoomWidth, int typicalRoomDepth, int typicalCorridorWidth) {
    }
}
