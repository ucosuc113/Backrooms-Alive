package com.glados.backrooms.district;

import com.glados.backrooms.util.NoiseField;

/**
 * Evalua el campo de degradacion global generado con ruido Simplex de tres
 * octavas (periodos 64/32/16), independiente de los distritos individuales
 * (Documento de Diseno, seccion 5.3).
 *
 * El campo tiene un rango efectivo de 0.0 a 0.7 (seccion 5.3: "el campo de
 * degradacion global tiene un rango efectivo de 0.0 a 0.7"). El valor bruto
 * de {@link NoiseField#evaluate} esta en [0.0, 1.0]; se escala a [0.0, 0.7]
 * aqui.
 *
 * Una instancia por servidor. Inmutable tras la construccion (Documento de
 * Arquitectura, seccion 6: "NoiseField — una por servidor").
 */
final class GlobalDegradationField {

    /** Factor de escala: el ruido normalizado [0,1] se reduce a [0, 0.7]. */
    private static final double GLOBAL_SCALE = 0.7;

    private final NoiseField noiseField;

    /**
     * @param worldSeed semilla del mundo; determina completamente la
     *                  disposicion del campo de degradacion global.
     */
    GlobalDegradationField(long worldSeed) {
        // Tres octavas con periodo base 64 -> octavas en 64, 32 y 16 bloques.
        this.noiseField = new NoiseField(worldSeed, 64, 3);
    }

    /**
     * Valor de degradacion global en el punto ({@code x}, {@code z}),
     * en el rango [0.0, 0.7].
     */
    float evaluate(double x, double z) {
        return (float) (noiseField.evaluate(x, z) * GLOBAL_SCALE);
    }
}
