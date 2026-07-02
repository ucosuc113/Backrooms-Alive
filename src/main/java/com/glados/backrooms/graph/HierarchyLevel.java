package com.glados.backrooms.graph;

/**
 * Nivel jerarquico de una conexion entre habitaciones (Documento de Diseno,
 * seccion 6.5).
 *
 * PRINCIPAL: aristas del MST — pasillos de 3 o 4 bloques de ancho.
 * SECUNDARIO: aristas adicionales (+35%) — pasillos de 2 o 3 bloques de ancho.
 */
public enum HierarchyLevel {
    PRINCIPAL,
    SECUNDARIO
}
