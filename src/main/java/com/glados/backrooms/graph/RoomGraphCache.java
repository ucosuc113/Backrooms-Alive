package com.glados.backrooms.graph;

import com.glados.backrooms.analysis.MemoryAnalysisRepository;
import com.glados.backrooms.district.District;
import com.glados.backrooms.district.DistrictLookup;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Unico punto de entrada publico del paquete {@code graph}
 * (Documento de Arquitectura, seccion 2.6, fachada [F]).
 *
 * Almacena y produce {@link RoomGraph} bajo demanda mediante
 * {@link ConcurrentHashMap#computeIfAbsent}, garantizando que cada zona se
 * genera exactamente una vez aunque varios hilos la soliciten a la vez
 * (Documento de Diseno, seccion 6.1 e Invariante 2).
 *
 * Una instancia por servidor. El mapa interno crece con el area explorada y
 * nunca se invalida mientras el servidor este vivo (ver riesgos en el
 * Documento de Arquitectura, seccion 9).
 */
public final class RoomGraphCache {

    private final ConcurrentHashMap<ZoneId, RoomGraph> cache = new ConcurrentHashMap<>();
    private final RoomGraphGenerator generator;
    private final DistrictLookup districtLookup;
    private final MemoryAnalysisRepository repo;

    /**
     * @param districtLookup fachada del paquete district, para derivar las
     *                       propiedades del distrito de cada zona.
     * @param repo           repositorio de analisis de memoria, ya construido.
     */
    public RoomGraphCache(DistrictLookup districtLookup, MemoryAnalysisRepository repo) {
        this.districtLookup = districtLookup;
        this.repo           = repo;
        this.generator      = new RoomGraphGenerator(repo);
    }

    /**
     * Obtiene el {@link RoomGraph} de la zona identificada por
     * ({@code cellX}, {@code cellZ}), generandolo si es la primera vez que
     * se solicita.
     *
     * @param cellX indice de celda X en la rejilla de 192x192 (long).
     * @param cellZ indice de celda Z en la rejilla de 192x192 (long).
     * @return el grafo inmutable de esa zona.
     */
    public RoomGraph getOrGenerate(long cellX, long cellZ) {
        ZoneId id = new ZoneId(cellX, cellZ);
        return cache.computeIfAbsent(id, zid -> {
            District district = districtLookup.propertiesOfCell(
                    (int) zid.cellX(), (int) zid.cellZ(), repo);
            return generator.generate(zid, district);
        });
    }

    /**
     * Numero de zonas actualmente cacheadas. Util para diagnostico.
     */
    public int cachedZoneCount() {
        return cache.size();
    }

    /**
     * Vacia el cache. Util exclusivamente para tests o recargas de servidor
     * en entornos de desarrollo. No llamar en produccion.
     */
    public void clear() {
        cache.clear();
    }
}
