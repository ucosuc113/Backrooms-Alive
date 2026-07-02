package com.glados.backrooms.analysis;

import java.util.List;

/**
 * Patron de muro extraido de una memoria (Documento de Diseno, secciones 4.3
 * y 10.2). {@code columns} conserva el orden original a lo largo del
 * segmento de muro detectado. {@code tileableStart}/{@code tileableEnd}
 * delimitan, dentro de {@code columns}, el rango central que puede repetirse
 * (via modulo o escalado) cuando {@code placement.WallPatternMapper}
 * proyecta este prototipo sobre un muro de longitud distinta a la original.
 * Los extremos (los 2 primeros y 2 ultimos elementos de {@code columns})
 * nunca se repiten; se acceden con {@link #endColumnsLeft()} y
 * {@link #endColumnsRight()}.
 */
public record WallPrototype(WallRole role, List<WallColumn> columns, int tileableStart, int tileableEnd) {

    private static final int END_SIZE = 2;

    public WallPrototype {
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("Un WallPrototype necesita al menos una columna.");
        }
        columns = List.copyOf(columns);
        if (tileableStart < 0 || tileableEnd > columns.size() || tileableStart > tileableEnd) {
            throw new IllegalArgumentException("Rango tileable invalido para WallPrototype.");
        }
    }

    /** La longitud real (en bloques) del segmento de muro original (Documento de Diseno, 10.2). */
    public int referenceLength() {
        return columns.size();
    }

    public List<WallColumn> endColumnsLeft() {
        return columns.subList(0, Math.min(END_SIZE, columns.size()));
    }

    public List<WallColumn> endColumnsRight() {
        int size = columns.size();
        return columns.subList(Math.max(0, size - END_SIZE), size);
    }

    public List<WallColumn> tileableRange() {
        return columns.subList(tileableStart, tileableEnd);
    }
}
