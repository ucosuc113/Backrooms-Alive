package com.glados.backrooms.graph;

/**
 * Abertura en la pared de una habitacion — la conexion fisica entre la
 * habitacion y un pasillo o habitacion adyacente (Documento de Diseno,
 * seccion 10.10).
 *
 * La posicion esta especificada en coordenadas del mundo absolutas y es
 * compartida por todos los chunks que la vean (Invariante 1).
 *
 * @param wallDirection   cara de la habitacion en la que esta la abertura.
 * @param startWorldCoord primera coordenada de la abertura en la direccion
 *                        paralela a la pared.
 * @param endWorldCoord   ultima coordenada de la abertura (inclusive).
 * @param fixedCoord      coordenada perpendicular: la posicion exacta de
 *                        la pared que contiene la abertura. Nunca coincide
 *                        con un multiplo de 16 (restriccion de seccion 6.8).
 * @param fromRoomId      id de la habitacion que contiene esta abertura.
 * @param toCorridorId    id del CorridorEdge al que conecta, o -1 si
 *                        conecta directamente con otra habitacion.
 * @param toRoomId        id de la habitacion de destino si la abertura
 *                        conecta dos habitaciones directamente, o -1 si
 *                        conecta con un pasillo.
 */
public record Apertura(
        WallDirection wallDirection,
        int startWorldCoord,
        int endWorldCoord,
        int fixedCoord,
        int fromRoomId,
        int toCorridorId,
        int toRoomId
) {

    /** Anchura de la abertura en bloques. */
    public int width() {
        return endWorldCoord - startWorldCoord + 1;
    }
}
