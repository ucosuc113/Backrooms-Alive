package com.glados.backrooms.context;

/**
 * Rol de columna (localX, localZ) dentro de un chunk en generacion
 * (Documento de Diseno, seccion 7.4 y Documento de Arquitectura, seccion 2.7).
 *
 * El orden de prioridad al construir el mapa es:
 * ABIERTO -> PARED_HABITACION/INTERIOR_HABITACION -> ABERTURA (sobreescribe paredes)
 * -> PARED_PASILLO/INTERIOR_PASILLO.
 */
public enum ColumnRole {
    /** Espacio sin asignar a ninguna estructura. */
    ABIERTO,
    /** Interior de una habitacion (sin incluir sus paredes). */
    INTERIOR_HABITACION,
    /** Pared de una habitacion. */
    PARED_HABITACION,
    /** Interior de un pasillo (espacio transitable). */
    INTERIOR_PASILLO,
    /** Pared lateral de un pasillo. */
    PARED_PASILLO,
    /** Abertura en la pared de una habitacion; sobreescribe PARED_HABITACION. */
    ABERTURA
}
