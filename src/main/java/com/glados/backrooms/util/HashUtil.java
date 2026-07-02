package com.glados.backrooms.util;

/**
 * Unica fuente de "aleatoriedad determinista" de todo el sistema
 * (Documento de Arquitectura, seccion 2.1, tabla {@code util}).
 *
 * Todos los metodos son funciones puras: la misma combinacion de argumentos
 * siempre produce el mismo resultado, sin importar el hilo o el momento en
 * que se invoquen. Esto sostiene el Invariante 2 (determinismo total) del
 * Documento de Diseno: para una semilla de mundo dada, cualquier chunk
 * generado en cualquier orden produce exactamente el mismo resultado.
 */
public final class HashUtil {

    // Constantes de mezcla tipo splitmix64 / murmur finalizer.
    private static final long MIX_1 = 0xff51afd7ed558ccdL;
    private static final long MIX_2 = 0xc4ceb9fe1a85ec53L;
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private HashUtil() {
    }

    /**
     * Combina una semilla base con un numero arbitrario de valores {@code long}
     * en un unico hash de 64 bits bien distribuido. El orden de los valores
     * importa: {@code hash(seed, a, b) != hash(seed, b, a)} en general.
     */
    public static long hash(long seed, long... values) {
        long h = mix(seed ^ GOLDEN_GAMMA);
        for (long value : values) {
            h = mix(h ^ mix(value));
        }
        return h;
    }

    /**
     * Variante de conveniencia para el caso mas comun: combinar una semilla
     * de mundo, dos coordenadas enteras (x, z) y una "sal" textual que
     * distingue el proposito del hash. La sal evita colisiones entre, por
     * ejemplo, "elegir memoria de distrito" y "elegir densidad de distrito"
     * cuando ambos parten exactamente del mismo centro de distrito.
     */
    public static long hashCoords(long seed, int x, int z, String salt) {
        return hash(seed, x, z, saltToLong(salt));
    }

    /** Igual que {@link #hashCoords(long, int, int, String)} pero con un indice adicional (p. ej. indice de habitacion o de arista). */
    public static long hashCoords(long seed, int x, int z, int index, String salt) {
        return hash(seed, x, z, index, saltToLong(salt));
    }

    /** Convierte un hash de 64 bits en un float determinista en [0.0, 1.0). */
    public static float floatFromHash(long hash) {
        int bits = (int) (hash >>> 40) & 0xFFFFFF;
        return bits / (float) (1 << 24);
    }

    /** Float determinista en [min, max). */
    public static float floatInRange(long hash, float min, float max) {
        return min + floatFromHash(hash) * (max - min);
    }

    /** Entero determinista en [min, max] (inclusive en ambos extremos). */
    public static int intInRange(long hash, int min, int max) {
        if (max <= min) {
            return min;
        }
        long span = (long) max - (long) min + 1L;
        long unsigned = hash >>> 1; // evita el problema del signo en el modulo
        return (int) (min + (unsigned % span));
    }

    /** Booleano determinista con probabilidad {@code probability} (en [0,1]) de ser true. */
    public static boolean chance(long hash, float probability) {
        return floatFromHash(hash) < probability;
    }

    /** Deriva una semilla larga a partir de un hash, para alimentar generadores que esperan una semilla de 64 bits (p. ej. {@code RandomSource}). */
    public static long deriveSeed(long hash) {
        return mix(hash ^ GOLDEN_GAMMA);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 33)) * MIX_1;
        z = (z ^ (z >>> 33)) * MIX_2;
        return z ^ (z >>> 33);
    }

    /**
     * FNV-1a de 64 bits sobre la sal textual. Se usa una implementacion
     * propia en lugar de {@code String.hashCode()} para mantener el hash
     * completamente bajo nuestro control y documentado en este archivo, en
     * vez de depender implicitamente del contrato de otra clase del JDK.
     */
    private static long saltToLong(String salt) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < salt.length(); i++) {
            hash ^= salt.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
