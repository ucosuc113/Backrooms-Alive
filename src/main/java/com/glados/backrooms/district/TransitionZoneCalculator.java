package com.glados.backrooms.district;

import com.glados.backrooms.util.VoronoiLookup;

import java.util.List;

/**
 * Calcula el boost de degradacion por proximidad al limite entre distritos
 * (Documento de Diseno, seccion 5.4). Dado un punto, encuentra el segundo
 * distrito mas cercano y la distancia al borde de Voronoi, y produce el
 * boost de degradacion correspondiente.
 *
 * Sin estado mutable (Documento de Arquitectura, seccion 2.5).
 */
final class TransitionZoneCalculator {

    /**
     * Distancia al borde de Voronoi a partir de la cual se aplica boost
     * maximo pleno (centro de la zona de transicion segun seccion 5.4).
     */
    private static final double INNER_DIST = 16.0;

    /**
     * Distancia al borde de Voronoi a partir de la cual comienza la zona
     * de transicion parcial (seccion 5.4: "16-32 bloques de ancho").
     */
    private static final double OUTER_DIST = 32.0;

    /** Boost maximo de degradacion en el centro de la transicion. */
    private static final float TRANSITION_BOOST_MAX = 0.6f;

    private TransitionZoneCalculator() {
    }

    /**
     * Boost de degradacion en ({@code x}, {@code z}) derivado de la
     * proximidad al borde de Voronoi entre el distrito dominante y el
     * segundo mas cercano (Documento de Diseno, seccion 5.4).
     *
     * La distancia al borde de Voronoi entre dos puntos A y B cuyas
     * distancias al punto de consulta son d1 y d2 es aproximadamente
     * {@code (d2 - d1) / 2}. Cuando esta distancia es 0 estamos en el
     * borde exacto.
     *
     * Formulas:
     * <ul>
     *   <li>{@code dist < 16}: boost = {@code (1 - dist/16) * 0.6}</li>
     *   <li>{@code 16 <= dist < 32}: boost = {@code (1 - (dist-16)/16) * 0.6}</li>
     *   <li>{@code dist >= 32}: 0.0</li>
     * </ul>
     *
     * @param grid instancia de DistrictGrid para calcular los centros.
     * @return boost en [0.0, 0.6].
     */
    static float transitionBoost(double x, double z, DistrictGrid grid) {
        List<VoronoiLookup.CellDistance> two =
                VoronoiLookup.twoNearestCells(x, z, DistrictGrid.CELL_SIZE, grid);

        if (two.size() < 2) {
            return 0.0f;
        }

        double d1 = two.get(0).distance();
        double d2 = two.get(1).distance();

        // Distancia aproximada al borde de Voronoi: punto equidistante entre
        // los dos centros mas cercanos.
        double distToBorder = (d2 - d1) / 2.0;

        if (distToBorder >= OUTER_DIST) {
            return 0.0f;
        }

        float boost;
        if (distToBorder < INNER_DIST) {
            // Zona interna [0, 16): boost maximo proporcional.
            boost = (float) ((1.0 - distToBorder / INNER_DIST) * TRANSITION_BOOST_MAX);
        } else {
            // Zona parcial [16, 32): boost decae linealmente a 0.
            boost = (float) ((1.0 - (distToBorder - INNER_DIST) / INNER_DIST) * TRANSITION_BOOST_MAX);
        }
        return Math.max(0.0f, Math.min(TRANSITION_BOOST_MAX, boost));
    }
}
