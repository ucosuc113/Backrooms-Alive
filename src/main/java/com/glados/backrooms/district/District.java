package com.glados.backrooms.district;

/**
 * Contenedor de datos inmutable que representa las propiedades derivadas de
 * un unico distrito (Documento de Diseno, seccion 5.2 y Documento de
 * Arquitectura, seccion 2.5, tabla {@code District}).
 *
 * Una instancia de {@code District} se produce cada vez que
 * {@link DistrictPropertyDeriver} evalua un punto del mundo; no se cachea
 * (es barata de reconstruir: solo hashes y una evaluacion de ruido).
 *
 * @param centerX           coordenada X del centro del distrito en el mundo.
 * @param centerZ           coordenada Z del centro del distrito en el mundo.
 * @param cellX             indice de celda X en la rejilla de 192x192.
 * @param cellZ             indice de celda Z en la rejilla de 192x192.
 * @param primaryMemoryId   id de la {@code MemoryAnalysis} primaria asignada.
 * @param secondaryMemoryId id de la {@code MemoryAnalysis} secundaria, o null
 *                          si este distrito no tiene memoria secundaria (70%
 *                          de los distritos; seccion 5.2).
 * @param densidadBase      densidad base del grafo de habitaciones [0.3, 0.9].
 * @param degradacionBase   nivel minimo de degradacion de la zona [0.0, 0.8].
 * @param semilla           semilla especifica del distrito para su RoomGraph.
 * @param esEspacioAbierto  true si densidadBase < 0.4 y degradacionBase > 0.6
 *                          (seccion 5.5): solo suelo y techo, sin habitaciones.
 */
public record District(
        double centerX,
        double centerZ,
        int cellX,
        int cellZ,
        String primaryMemoryId,
        String secondaryMemoryId,
        float densidadBase,
        float degradacionBase,
        long semilla,
        boolean esEspacioAbierto
) {
}
