package com.glados.backrooms.graph;

/**
 * Un segmento de pasillo axis-aligned en coordenadas del mundo
 * (Documento de Diseno, seccion 10.12).
 *
 * Cada {@link CorridorEdge} se compone de uno o dos de estos segmentos
 * (recto o con codo). Los bounds precomputados (minX/minZ/maxX/maxZ)
 * permiten lookup rapido de interseccion con un chunk.
 *
 * @param axis       X si el segmento avanza en la direccion X, Z si avanza
 *                   en la direccion Z.
 * @param fixedCoord coordenada perpendicular al movimiento (Z si axis=X,
 *                   X si axis=Z); es el centro del pasillo en ese eje.
 * @param startCoord inicio del segmento en la direccion del movimiento.
 * @param endCoord   fin del segmento (siempre >= startCoord).
 * @param wallCoordA coordenada de la primera pared lateral (fixedCoord - halfWidth).
 * @param wallCoordB coordenada de la segunda pared lateral (fixedCoord + halfWidth).
 * @param minX       bound minimo X precomputado.
 * @param minZ       bound minimo Z precomputado.
 * @param maxX       bound maximo X precomputado.
 * @param maxZ       bound maximo Z precomputado.
 */
public record AxisAlignedSegment(
        Axis axis,
        int fixedCoord,
        int startCoord,
        int endCoord,
        int wallCoordA,
        int wallCoordB,
        int minX,
        int minZ,
        int maxX,
        int maxZ
) {

    /** Eje de avance del segmento. */
    public enum Axis { X, Z }

    /**
     * Construye un segmento en el eje X (avanza en X, fijo en Z).
     *
     * @param fixedZ coordenada Z central del pasillo.
     * @param startX coordenada X de inicio (menor).
     * @param endX   coordenada X de fin (mayor).
     * @param width  anchura del pasillo en bloques (numero de bloques
     *               interiores, sin contar las paredes).
     */
    public static AxisAlignedSegment alongX(int fixedZ, int startX, int endX, int width) {
        int halfInterior = width / 2;
        int wA = fixedZ - halfInterior - 1;
        int wB = fixedZ + halfInterior + 1;
        return new AxisAlignedSegment(
                Axis.X, fixedZ, startX, endX,
                wA, wB,
                startX, wA, endX, wB);
    }

    /**
     * Construye un segmento en el eje Z (avanza en Z, fijo en X).
     *
     * @param fixedX coordenada X central del pasillo.
     * @param startZ coordenada Z de inicio (menor).
     * @param endZ   coordenada Z de fin (mayor).
     * @param width  anchura del pasillo en bloques.
     */
    public static AxisAlignedSegment alongZ(int fixedX, int startZ, int endZ, int width) {
        int halfInterior = width / 2;
        int wA = fixedX - halfInterior - 1;
        int wB = fixedX + halfInterior + 1;
        return new AxisAlignedSegment(
                Axis.Z, fixedX, startZ, endZ,
                wA, wB,
                wA, startZ, wB, endZ);
    }

    /** Longitud del segmento en bloques. */
    public int length() {
        return endCoord - startCoord + 1;
    }
}
