package com.glados.backrooms.graph;

/**
 * Direccion de una pared de habitacion (Documento de Diseno, seccion 10.10).
 * NORTE = cara Z- (minZ), SUR = cara Z+ (maxZ),
 * OESTE = cara X- (minX), ESTE = cara X+ (maxX).
 */
public enum WallDirection {
    NORTE,
    SUR,
    ESTE,
    OESTE
}
