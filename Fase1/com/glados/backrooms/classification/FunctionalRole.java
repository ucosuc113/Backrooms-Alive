package com.glados.backrooms.classification;

/**
 * Rol funcional de una habitacion dentro del grafo de habitaciones
 * (Documento de Diseno, seccion 6.9: LOBBY, OFICINA, ALMACEN, DORMITORIO,
 * UTILITARIO, INTERSECCION).
 *
 * NOTA DE UBICACION (ver conflicto #2 en el analisis adjunto a esta
 * entrega): la tabla 2.6 del Documento de Arquitectura asigna este enum al
 * paquete {@code graph}. Sin embargo, {@code analysis.MemoryAnalysis} y
 * {@code analysis.RoomPrototype} (seccion 10.1 y 10.5 del Documento de
 * Diseno) necesitan referenciar {@code FunctionalRole} directamente, y la
 * seccion 4 del Documento de Arquitectura prohibe explicitamente que
 * {@code analysis} dependa de {@code graph}. Colocar este enum en
 * {@code graph} crearia una dependencia circular real entre ambos paquetes.
 *
 * Se resuelve moviendo {@code FunctionalRole} a {@code classification},
 * exactamente por la misma razon que ya justifica que {@code
 * ArchitecturalFunction} viva aqui: es vocabulario consumido por varias
 * capas (analysis, graph, context, placement, degradation) sin que ninguna
 * de ellas deba depender de otra capa hermana o superior para usarlo.
 *
 * {@code WallDirection} y {@code HierarchyLevel} no tienen este problema
 * (solo los usan {@code graph} y las capas por encima de el) y permanecen
 * en {@code graph} tal como especifica el Documento de Arquitectura.
 */
public enum FunctionalRole {
    LOBBY,
    OFICINA,
    ALMACEN,
    DORMITORIO,
    UTILITARIO,
    INTERSECCION
}
