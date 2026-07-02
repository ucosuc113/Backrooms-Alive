package com.glados.backrooms.analysis;

/**
 * Patron de los 2x2 bloques horizontales que forman una esquina de
 * habitacion, en toda su altura (Documento de Diseno, secciones 4.3 y 10.2).
 * Puede ser nulo en {@link MemoryAnalysis} si la memoria no produjo ninguna
 * esquina clara.
 *
 * Las posiciones se indexan por desplazamiento desde la esquina exacta hacia
 * el interior de la habitacion: {@code (0,0)} es el bloque de esquina,
 * {@code (1,0)} y {@code (0,1)} son sus vecinos a lo largo de cada uno de
 * los dos muros que se encuentran ahi, y {@code (1,1)} es el bloque
 * diagonal, ya dentro del interior.
 */
public record CornerPrototype(WallColumn[][] columns) {

    private static final int GRID_SIZE = 2;

    public CornerPrototype {
        if (columns.length != GRID_SIZE || columns[0].length != GRID_SIZE || columns[1].length != GRID_SIZE) {
            throw new IllegalArgumentException("CornerPrototype requiere una rejilla de exactamente 2x2 columnas.");
        }
    }

    public WallColumn at(int alongWallA, int alongWallB) {
        return columns[alongWallA][alongWallB];
    }
}
