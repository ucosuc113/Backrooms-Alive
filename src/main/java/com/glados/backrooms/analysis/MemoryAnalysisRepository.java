package com.glados.backrooms.analysis;

import com.glados.backrooms.BackroomsMod;
import com.glados.backrooms.memory.MemoryRegion;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Punto de acceso inmutable al conjunto de {@link MemoryAnalysis} producidos
 * por {@link MemoryAnalyzer} al arrancar el servidor (Documento de Diseno,
 * seccion 11.1 y 4.1).
 *
 * <h3>Ciclo de vida</h3>
 * <ol>
 *   <li>{@link #build} se llama <b>una unica vez</b> desde
 *       {@code CommonSetup} (o el hook de servidor equivalente), antes de
 *       que se genere cualquier chunk.</li>
 *   <li>Desde ese momento el repositorio es inmutable. Cualquier capa
 *       superior (district, graph, context, placement, degradation) lo
 *       consulta via {@link #get} o {@link #allUsable} sin necesidad de
 *       sincronizacion.</li>
 * </ol>
 *
 * <h3>Fallback neutral</h3>
 * Si ninguna memoria supera el umbral de calidad ({@code isUsableAsPrimary}),
 * {@link #allUsable()} retorna una lista con el analisis neutral de
 * {@link NeutralAnalysisProvider}, garantizando que el sistema de distritos
 * siempre tenga al menos un analisis con el que trabajar (seccion 13.1).
 *
 * <h3>Thread safety</h3>
 * El mapa interno es un {@link Collections#unmodifiableMap} sobre un
 * {@link LinkedHashMap} construido completamente antes de la primera
 * consulta. No requiere sincronizacion post-construccion (Invariante 2 del
 * Documento de Diseno: determinismo total).
 */
public final class MemoryAnalysisRepository {

    /** Instancia singleton; null hasta que {@link #build} sea invocado. */
    private static volatile MemoryAnalysisRepository instance;

    /**
     * Mapa id (nombre de la memoria) → MemoryAnalysis. Inmutable tras
     * la construccion. Incluye siempre la entrada neutral con clave
     * {@link NeutralAnalysisProvider#NEUTRAL_ID}.
     */
    private final Map<String, MemoryAnalysis> byId;

    /** Cache de la lista de analisis usables como primarios (pre-filtrada). */
    private final List<MemoryAnalysis> usable;

    private MemoryAnalysisRepository(Map<String, MemoryAnalysis> byId,
                                      List<MemoryAnalysis> usable) {
        this.byId   = Collections.unmodifiableMap(byId);
        this.usable = Collections.unmodifiableList(usable);
    }

    // ── Construccion (una sola vez) ──────────────────────────────────────────────

    /**
     * Construye el repositorio analizando todas las {@code regions} recibidas.
     * Debe llamarse exactamente una vez al arrancar el servidor, antes de que
     * cualquier chunk solicite datos de distrito o grafo.
     *
     * <p>Si el repositorio ya fue construido, este metodo loguea una
     * advertencia y retorna la instancia existente sin reconstruirla.
     *
     * @param regions     todas las {@link MemoryRegion} cargadas por
     *                    {@link com.glados.backrooms.memory.MemoryLibrary}.
     * @param blockLookup getter de bloques del servidor, necesario para
     *                    que {@link MemoryAnalyzer} deserialice los estados.
     * @return la instancia del repositorio, lista para usar.
     */
    public static MemoryAnalysisRepository build(
            Collection<MemoryRegion> regions,
            net.minecraft.core.HolderGetter<net.minecraft.world.level.block.Block> blockLookup) {

        if (instance != null) {
            BackroomsMod.LOGGER.warn(
                    "[MemoryAnalysisRepository] build() llamado mas de una vez; "
                    + "se retorna la instancia existente.");
            return instance;
        }

        // Analizar cada region. Los nulos (fallo de analisis) se descartan.
        Map<String, MemoryAnalysis> byId = new LinkedHashMap<>();

        for (MemoryRegion region : regions) {
            MemoryAnalysis analysis = MemoryAnalyzer.analyze(region, blockLookup);
            if (analysis == null) {
                // El propio MemoryAnalyzer ya loguo el motivo.
                continue;
            }
            if (byId.containsKey(analysis.id())) {
                BackroomsMod.LOGGER.warn(
                        "[MemoryAnalysisRepository] Dos memorias tienen el mismo nombre '{}'; "
                        + "se conserva la primera.",
                        analysis.id());
                continue;
            }
            byId.put(analysis.id(), analysis);
        }

        // Siempre incluir el analisis neutral (nunca sobreescribe una memoria real
        // a menos que una memoria se llame literalmente "neutral", lo cual seria
        // un error de nomenclatura del usuario).
        byId.putIfAbsent(NeutralAnalysisProvider.NEUTRAL_ID, NeutralAnalysisProvider.get());

        // Precalcular lista de usables.
        List<MemoryAnalysis> usable = new ArrayList<>();
        for (MemoryAnalysis a : byId.values()) {
            if (a.isUsableAsPrimary()) {
                usable.add(a);
            }
        }

        // Si absolutamente ninguna memoria (incluyendo las reales) es usable,
        // la lista solo contendra el neutral (que siempre tiene isUsableAsPrimary=true).
        if (usable.isEmpty()) {
            BackroomsMod.LOGGER.warn(
                    "[MemoryAnalysisRepository] Ninguna memoria paso el umbral de calidad. "
                    + "Solo se usara el analisis neutral.");
            usable.add(NeutralAnalysisProvider.get());
        }

        BackroomsMod.LOGGER.info(
                "[MemoryAnalysisRepository] Construido con {} memorias totales, {} usables como primarias.",
                byId.size(), usable.size());

        instance = new MemoryAnalysisRepository(byId, usable);
        return instance;
    }

    /**
     * Retorna la instancia activa del repositorio.
     *
     * @throws IllegalStateException si {@link #build} no ha sido llamado aun.
     */
    public static MemoryAnalysisRepository getInstance() {
        MemoryAnalysisRepository repo = instance;
        if (repo == null) {
            throw new IllegalStateException(
                    "MemoryAnalysisRepository no ha sido construido. "
                    + "Llama a build() desde CommonSetup antes de generar chunks.");
        }
        return repo;
    }

    /**
     * Resetea la instancia singleton. Util exclusivamente para tests o para
     * recargas de servidor en entornos de desarrollo. No llamar en produccion.
     */
    public static void reset() {
        instance = null;
    }

    // ── Consulta ─────────────────────────────────────────────────────────────────

    /**
     * Busca un {@link MemoryAnalysis} por su identificador (nombre de la
     * memoria tal como fue capturada en {@link MemoryRegion#name()}).
     *
     * @param id nombre de la memoria; nunca null.
     * @return el analisis, o {@link Optional#empty()} si no existe.
     */
    public Optional<MemoryAnalysis> get(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * Lista de todos los {@link MemoryAnalysis} con {@code isUsableAsPrimary = true},
     * ordenada segun el orden de insercion (orden de carga de memorias).
     * Nunca vacia: siempre contiene al menos el analisis neutral.
     */
    public List<MemoryAnalysis> allUsable() {
        return usable;
    }

    /**
     * Lista de todos los {@link MemoryAnalysis} cargados, incluyendo los
     * que no superaron el umbral de calidad. Util para el sistema de
     * degradacion, que puede usar cualquier memoria como fuente de
     * fragmentos (Documento de Diseno, seccion 4.7).
     */
    public Collection<MemoryAnalysis> all() {
        return byId.values();
    }

    /** Numero total de memorias cargadas (incluye neutral e insuficientes). */
    public int size() {
        return byId.size();
    }
}
