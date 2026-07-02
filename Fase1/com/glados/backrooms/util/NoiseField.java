package com.glados.backrooms.util;

import java.util.Random;

/**
 * Envuelve una implementacion de ruido Simplex 2D multi-octava configurable
 * (periodo base, numero de octavas). Una instancia = una configuracion de
 * ruido reutilizable (Documento de Arquitectura, seccion 2.1).
 *
 * Usada por {@code district} (campo de degradacion global, periodos
 * 64/32/16) y por {@code context} (variacion local de degradacion de grano
 * fino, periodos 8 y 4).
 *
 * Cada instancia es inmutable y segura para compartir entre hilos: toda su
 * configuracion (permutacion, periodos, amplitudes) se fija en el
 * constructor y nunca cambia despues.
 */
public final class NoiseField {

    private static final double[][] GRADIENTS_2D = {
            {1, 1}, {-1, 1}, {1, -1}, {-1, -1},
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;

    private final int[] permutation;
    private final double[] periods;
    private final double[] amplitudes;
    private final double totalAmplitude;

    /**
     * @param seed        semilla determinista para la tabla de permutacion interna.
     * @param basePeriod  periodo (en bloques) de la octava mas gruesa.
     * @param octaveCount numero de octavas; cada octava sucesiva duplica la
     *                    frecuencia (periodo mitad) y reduce la amplitud a la mitad
     *                    (persistencia 0.5, esquema fractal estandar).
     */
    public NoiseField(long seed, int basePeriod, int octaveCount) {
        if (basePeriod <= 0) {
            throw new IllegalArgumentException("basePeriod debe ser positivo.");
        }
        if (octaveCount <= 0) {
            throw new IllegalArgumentException("octaveCount debe ser positivo.");
        }
        this.permutation = buildPermutation(seed);
        this.periods = new double[octaveCount];
        this.amplitudes = new double[octaveCount];

        double period = basePeriod;
        double amplitude = 1.0;
        double sum = 0.0;
        for (int i = 0; i < octaveCount; i++) {
            this.periods[i] = period;
            this.amplitudes[i] = amplitude;
            sum += amplitude;
            period *= 0.5;
            amplitude *= 0.5;
        }
        this.totalAmplitude = sum;
    }

    /**
     * Evalua el campo de ruido fractal en (x, z) y normaliza el resultado a
     * [0.0, 1.0]. La escala efectiva final que cada consumidor necesita
     * (por ejemplo, "0.0 a 0.7" para el campo de degradacion global del
     * Documento de Diseno, seccion 5.3) es responsabilidad de quien consume
     * este valor, no de esta clase.
     */
    public double evaluate(double x, double z) {
        double sum = 0.0;
        for (int i = 0; i < periods.length; i++) {
            double frequency = 1.0 / periods[i];
            sum += amplitudes[i] * simplex(x * frequency, z * frequency);
        }
        double normalized = sum / totalAmplitude; // en [-1, 1] aproximadamente
        return clamp01((normalized + 1.0) * 0.5);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double simplex(double x, double z) {
        double s = (x + z) * F2;
        int i = fastFloor(x + s);
        int j = fastFloor(z + s);
        double t = (i + j) * G2;
        double x0 = x - (i - t);
        double y0 = z - (j - t);

        int i1;
        int j1;
        if (x0 > y0) {
            i1 = 1;
            j1 = 0;
        } else {
            i1 = 0;
            j1 = 1;
        }

        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double y2 = y0 - 1.0 + 2.0 * G2;

        int ii = i & 255;
        int jj = j & 255;

        double n0 = contribution(x0, y0, gradientIndex(ii, jj));
        double n1 = contribution(x1, y1, gradientIndex(ii + i1, jj + j1));
        double n2 = contribution(x2, y2, gradientIndex(ii + 1, jj + 1));

        return 70.0 * (n0 + n1 + n2);
    }

    private double contribution(double x, double y, int gradientIndex) {
        double t = 0.5 - x * x - y * y;
        if (t < 0) {
            return 0.0;
        }
        double[] gradient = GRADIENTS_2D[gradientIndex];
        t *= t;
        return t * t * (gradient[0] * x + gradient[1] * y);
    }

    private int gradientIndex(int i, int j) {
        int a = permutation[i & 255];
        int b = permutation[(a + (j & 255)) & 255];
        return b & 7;
    }

    private static int fastFloor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static int[] buildPermutation(long seed) {
        int[] table = new int[256];
        for (int i = 0; i < 256; i++) {
            table[i] = i;
        }
        // java.util.Random con semilla fija produce siempre la misma
        // secuencia para la misma semilla (contrato especificado por el
        // JDK), por lo que esto sigue siendo determinista entre ejecuciones.
        Random random = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int swapIndex = random.nextInt(i + 1);
            int temp = table[i];
            table[i] = table[swapIndex];
            table[swapIndex] = temp;
        }
        return table;
    }
}
