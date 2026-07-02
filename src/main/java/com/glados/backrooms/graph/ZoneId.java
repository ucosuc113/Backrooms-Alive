package com.glados.backrooms.graph;

/**
 * Identificador inmutable de una zona del RoomGraph, indexado por las
 * coordenadas del centro del distrito redondeadas a la cuadricula de 192
 * (Documento de Diseno, seccion 6.1 y Documento de Arquitectura, seccion 2.6).
 *
 * Usado como clave del {@link RoomGraphCache}. Dos chunks que consultan la
 * misma zona obtienen exactamente el mismo ZoneId porque el calculo es
 * deterministico desde la cuadricula.
 *
 * @param cellX indice de celda X en la rejilla de 192x192 (long para
 *              cubrir el rango completo de coordenadas de mundo).
 * @param cellZ indice de celda Z en la rejilla de 192x192.
 */
public record ZoneId(long cellX, long cellZ) {
}
