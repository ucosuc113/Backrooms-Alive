package com.glados.backrooms.classification;

/**
 * Funcion arquitectonica de un bloque dentro de una memoria o del mundo
 * generado. Consumida por {@code analysis}, {@code placement} y
 * {@code degradation} — vive en {@code classification} para que ninguna
 * de esas capas dependa de {@code generation} (ver NOTA_classification_pendiente.md).
 */
public enum ArchitecturalFunction {
    WALL,
    FLOOR,
    CEILING,
    DOOR,
    WINDOW,
    STAIRS,
    STORAGE,
    BED,
    LIGHT,
    FURNITURE,
    AIR
}
