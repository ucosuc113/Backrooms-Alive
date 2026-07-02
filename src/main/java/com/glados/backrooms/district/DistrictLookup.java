package com.glados.backrooms.district;

import com.glados.backrooms.analysis.MemoryAnalysisRepository;
import com.glados.backrooms.util.VoronoiLookup;

import java.util.List;

/**
 * Unico punto de entrada publico del paquete {@code district}
 * (Documento de Arquitectura, seccion 2.5, fachada [F]).
 *
 * Dado cualquier punto ({@code x}, {@code z}), responde:
 * <ul>
 *   <li>Que distrito domina ese punto ({@link #nearestDistrict}).</li>
 *   <li>Los dos distritos mas cercanos, para calcular transiciones
 *       ({@link #twoNearestDistricts}).</li>
 *   <li>Las propiedades completas del distrito dominante
 *       ({@link #propertiesAt}).</li>
 *   <li>La degradacion total en ese punto, combinando campo global, base
 *       del distrito y boost de transicion ({@link #totalDegradation}).</li>
 * </ul>
 *
 * Sin estado mutable: todos los metodos son funciones puras dada la semilla
 * del mundo. Una instancia por servidor (Documento de Arquitectura,
 * seccion 6).
 */
public final class DistrictLookup {

    private final long worldSeed;
    private final DistrictGrid grid;
    private final GlobalDegradationField degradationField;

    /**
     * @param worldSeed semilla del mundo; fija completamente la distribucion
     *                  de distritos y el campo de degradacion global.
     */
    public DistrictLookup(long worldSeed) {
        this.worldSeed       = worldSeed;
        this.grid            = new DistrictGrid(worldSeed);
        this.degradationField = new GlobalDegradationField(worldSeed);
    }

    // ── Voronoi ──────────────────────────────────────────────────────────────────

    /**
     * Celda del distrito mas cercano al punto ({@code x}, {@code z}).
     *
     * @return {@link VoronoiLookup.CellDistance} con los indices de celda y la
     *         distancia al centro del distrito.
     */
    public VoronoiLookup.CellDistance nearestDistrict(double x, double z) {
        return VoronoiLookup.nearestCell(x, z, DistrictGrid.CELL_SIZE, grid);
    }

    /**
     * Los dos distritos mas cercanos al punto ({@code x}, {@code z}),
     * ordenados por distancia ascendente. Necesario para calcular zonas de
     * transicion (Documento de Diseno, seccion 5.4).
     *
     * @return lista de 1 o 2 elementos (en condiciones normales siempre 2).
     */
    public List<VoronoiLookup.CellDistance> twoNearestDistricts(double x, double z) {
        return VoronoiLookup.twoNearestCells(x, z, DistrictGrid.CELL_SIZE, grid);
    }

    // ── Propiedades de distrito ──────────────────────────────────────────────────

    /**
     * Propiedades del distrito dominante en el punto ({@code x}, {@code z}).
     *
     * @param repo repositorio de analisis de memoria, ya construido.
     * @return el {@link District} que domina ese punto.
     */
    public District propertiesAt(double x, double z, MemoryAnalysisRepository repo) {
        VoronoiLookup.CellDistance nearest = nearestDistrict(x, z);
        double cx = grid.centerX(nearest.cellX(), nearest.cellZ());
        double cz = grid.centerZ(nearest.cellX(), nearest.cellZ());
        return DistrictPropertyDeriver.derive(
                worldSeed, cx, cz,
                nearest.cellX(), nearest.cellZ(),
                repo);
    }

    /**
     * Propiedades del distrito de la celda especificada directamente por
     * sus indices ({@code cellX}, {@code cellZ}). Util cuando la celda ya
     * es conocida (por ejemplo, en {@link #totalDegradation}).
     *
     * @param repo repositorio de analisis de memoria, ya construido.
     */
    public District propertiesOfCell(int cellX, int cellZ, MemoryAnalysisRepository repo) {
        double cx = grid.centerX(cellX, cellZ);
        double cz = grid.centerZ(cellX, cellZ);
        return DistrictPropertyDeriver.derive(worldSeed, cx, cz, cellX, cellZ, repo);
    }

    // ── Degradacion ──────────────────────────────────────────────────────────────

    /**
     * Valor de degradacion del campo global en ({@code x}, {@code z}),
     * en [0.0, 0.7].
     */
    public float globalDegradation(double x, double z) {
        return degradationField.evaluate(x, z);
    }

    /**
     * Boost de degradacion por proximidad al limite entre distritos, en
     * [0.0, 0.6] (Documento de Diseno, seccion 5.4).
     */
    public float transitionBoost(double x, double z) {
        return TransitionZoneCalculator.transitionBoost(x, z, grid);
    }

    /**
     * Degradacion total en ({@code x}, {@code z}), combinando:
     * <ol>
     *   <li>Campo global Simplex (3 octavas, periodos 64/32/16).</li>
     *   <li>Base de degradacion del distrito dominante.</li>
     *   <li>Boost de transicion si el punto esta cerca de un limite.</li>
     * </ol>
     *
     * Formula (Documento de Diseno, seccion 5.3):
     * {@code clamp01(max(degradacionBase, globalNoise) + transitionBoost)}
     *
     * @param props propiedades del distrito dominante (ya calculadas).
     * @return valor en [0.0, 1.0].
     */
    public float totalDegradation(double x, double z, District props) {
        float globalNoise    = degradationField.evaluate(x, z);
        float distritalBase  = props.degradacionBase();
        float transition     = TransitionZoneCalculator.transitionBoost(x, z, grid);
        float combined       = Math.max(distritalBase, globalNoise) + transition;
        return Math.max(0.0f, Math.min(1.0f, combined));
    }
}
