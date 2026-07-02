package com.glados.backrooms.analysis;

import com.glados.backrooms.classification.FunctionalRole;
import com.glados.backrooms.classification.ArchitecturalFunction;
import com.glados.backrooms.classification.MemoryFunctionClassifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Implementa la seccion 4.4 del Documento de Diseno: para cada habitacion
 * detectada en una memoria, registra la posicion relativa normalizada de
 * cada bloque funcional y extrae las metricas de distribucion que alimentan
 * los {@link RoomPrototype}.
 *
 * <h3>Que produce</h3>
 * <ul>
 *   <li>Un {@link RoomPrototype} por cada {@link FunctionalRole} detectado.</li>
 *   <li>La {@code functionalDensity} que necesita {@link StyleFingerprintBuilder}:
 *       numero de bloques funcionales por metro cuadrado de interior
 *       (Documento de Diseno, 4.5).</li>
 * </ul>
 *
 * <h3>Invariantes de esta clase</h3>
 * <ul>
 *   <li>Sin estado entre invocaciones: {@link #analyze} es el unico punto de
 *       entrada y no lee ni escribe ningun campo estatico.</li>
 *   <li>No escribe bloques en ninguna parte del mundo: solo lee
 *       {@code statesByPos} y produce objetos de datos inmutables.</li>
 * </ul>
 */
public final class FunctionalDistributionAnalyzer {

    /**
     * Umbral de frecuencia relativa por encima del cual se considera que un
     * {@link ArchitecturalFunction} esta presente de forma significativa en una
     * habitacion (evita prototipos llenos de bloques irrelevantes con una sola
     * ocurrencia accidental).
     */
    private static final int MIN_OCCURRENCES_FOR_PROTOTYPE = 1;

    private FunctionalDistributionAnalyzer() {
    }

    // ── Resultado ────────────────────────────────────────────────────────────────

    /**
     * Resultado completo del analisis funcional de una memoria.
     *
     * @param roomPrototypes    mapa FunctionalRole → RoomPrototype con los elementos
     *                          funcionales tipicos de ese rol.
     * @param functionalDensity numero de bloques funcionales por metro cuadrado de
     *                          interior (suma de todas las habitaciones detectadas).
     */
    public record AnalysisResult(Map<FunctionalRole, RoomPrototype> roomPrototypes,
                                  float functionalDensity) {
    }

    // ── Punto de entrada ─────────────────────────────────────────────────────────

    /**
     * Analiza la distribucion funcional de todos los volumenes de tipo ROOM
     * presentes en la memoria.
     *
     * @param statesByPos mapa posicion-relativa → BlockState de la memoria completa.
     * @param volumes     volumenes de aire detectados por {@link InteriorVolumeDetector}.
     * @param floorY      nivel Y del suelo de la memoria (el mismo que se uso en
     *                    {@link WallSegmentAnalyzer#analyze}).
     * @param ceilingY    nivel Y del techo de la memoria.
     */
    public static AnalysisResult analyze(
            Map<BlockPos, BlockState> statesByPos,
            List<InteriorVolumeDetector.DetectedVolume> volumes,
            int floorY, int ceilingY) {

        // Solo procesamos habitaciones (no pasillos ni volumenes descartados).
        List<InteriorVolumeDetector.DetectedVolume> rooms = volumes.stream()
                .filter(v -> v.shape() == InteriorVolumeDetector.VolumeShape.ROOM)
                .toList();

        if (rooms.isEmpty()) {
            return new AnalysisResult(Collections.emptyMap(), 0f);
        }

        // Acumulador de elementos funcionales por habitacion.
        // Para el prototipo usamos todos los elementos de todas las habitaciones
        // agrupados por su ArchitecturalFunction, luego asignamos FunctionalRole.
        List<RawFunctionalElement> allElements = new ArrayList<>();
        int totalInteriorArea = 0;

        for (InteriorVolumeDetector.DetectedVolume room : rooms) {
            RoomBounds bounds = computeRoomBounds(room);
            int roomArea = Math.max(1, bounds.spanX() * bounds.spanZ());
            totalInteriorArea += roomArea;

            collectFunctionalElements(room, bounds, statesByPos, floorY, ceilingY, allElements);
        }

        // Densidad funcional global: total de bloques funcionales / total de area interior.
        float functionalDensity = totalInteriorArea > 0
                ? (float) allElements.size() / totalInteriorArea
                : 0f;

        // Agrupar elementos por ArchitecturalFunction y derivar FunctionalRole
        // para construir los RoomPrototypes.
        Map<FunctionalRole, List<PrototypeElement>> elementsByRole = groupByFunctionalRole(allElements);

        Map<FunctionalRole, RoomPrototype> roomPrototypes = new EnumMap<>(FunctionalRole.class);
        for (Map.Entry<FunctionalRole, List<PrototypeElement>> entry : elementsByRole.entrySet()) {
            FunctionalRole role = entry.getKey();
            List<PrototypeElement> elements = entry.getValue();
            if (elements.size() >= MIN_OCCURRENCES_FOR_PROTOTYPE) {
                roomPrototypes.put(role, new RoomPrototype(role, elements));
            }
        }

        return new AnalysisResult(Collections.unmodifiableMap(roomPrototypes), functionalDensity);
    }

    // ── Recoleccion de elementos funcionales ────────────────────────────────────

    /**
     * Recorre todas las celdas de una habitacion y extrae los bloques cuya
     * {@link ArchitecturalFunction} es funcional (no WALL, FLOOR, CEILING ni AIR).
     * Las posiciones se normalizan respecto a los bounds interiores de la habitacion.
     */
    private static void collectFunctionalElements(
            InteriorVolumeDetector.DetectedVolume room,
            RoomBounds bounds,
            Map<BlockPos, BlockState> statesByPos,
            int floorY, int ceilingY,
            List<RawFunctionalElement> accumulator) {

        int interiorHeight = ceilingY - floorY - 1;

        for (BlockPos cell : room.cells()) {
            int cx = cell.getX();
            int cy = cell.getY();
            int cz = cell.getZ();

            // El bloque funcional no esta en la celda de aire, sino en la posicion
            // de bloque solido adyacente en el mismo XZ pero en el suelo de la
            // habitacion o en cualquier nivel interior.
            // Buscamos bloques no-aire en la columna encima de cada celda de suelo.
            // Solo procesamos celdas al nivel mas bajo del volumen (evitar duplicados).
            if (cy != bounds.minY) continue;

            for (int dy = 1; dy <= interiorHeight; dy++) {
                BlockPos blockPos = new BlockPos(cx, floorY + dy, cz);
                BlockState state = statesByPos.getOrDefault(blockPos, null);
                if (state == null || state.isAir()) continue;

                ArchitecturalFunction fn = MemoryFunctionClassifier.classify(state);
                if (!isFunctional(fn)) continue;

                // Posicion normalizada [0,1] dentro del interior de la habitacion.
                float normalizedX = bounds.spanX() <= 1 ? 0.5f
                        : (float) (cx - bounds.minX) / (bounds.spanX() - 1);
                float normalizedZ = bounds.spanZ() <= 1 ? 0.5f
                        : (float) (cz - bounds.minZ) / (bounds.spanZ() - 1);

                // Altura relativa al suelo: nivel 1 = primer bloque por encima del suelo.
                // Se clampea a [1, WallColumn.HEIGHT_LEVELS].
                int heightFromFloor = Math.max(1, Math.min(WallColumn.HEIGHT_LEVELS, dy));

                accumulator.add(new RawFunctionalElement(
                        normalizedX, normalizedZ, fn, state, heightFromFloor, null));
            }
        }
    }

    // ── Agrupacion por FunctionalRole ────────────────────────────────────────────

    /**
     * Agrupa los elementos recolectados en roles funcionales de habitacion.
     *
     * <p>La asignacion de {@link ArchitecturalFunction} a {@link FunctionalRole}
     * sigue la siguiente logica (Documento de Diseno, 6.9):
     * <ul>
     *   <li>BED → DORMITORIO</li>
     *   <li>STORAGE → ALMACEN</li>
     *   <li>LIGHT → (se distribuye al rol dominante de la habitacion donde aparece;
     *       aqui, como no tenemos habita ciones individuales en este punto, se
     *       asigna a UTILITARIO como rol neutro para iluminacion)</li>
     *   <li>FURNITURE, STAIRS → OFICINA (mobiliario de trabajo generico)</li>
     *   <li>DOOR, WINDOW → LOBBY (elementos de transicion / entrada)</li>
     * </ul>
     *
     * <p>Todos los roles que no tengan elementos propios no aparecen en el mapa
     * resultante; la fase de asignacion de grafos usara el fallback del
     * {@link com.glados.backrooms.analysis.StyleFingerprint} en ese caso.</p>
     */
    private static Map<FunctionalRole, List<PrototypeElement>> groupByFunctionalRole(
            List<RawFunctionalElement> elements) {

        Map<FunctionalRole, List<PrototypeElement>> result = new EnumMap<>(FunctionalRole.class);

        for (RawFunctionalElement raw : elements) {
            FunctionalRole role = mapFunctionToRole(raw.function);
            result.computeIfAbsent(role, k -> new ArrayList<>())
                    .add(new PrototypeElement(
                            raw.normalizedX,
                            raw.normalizedZ,
                            raw.function,
                            raw.blockState,
                            raw.heightFromFloor,
                            raw.blockEntityData));
        }

        // Hacer las listas inmutables.
        Map<FunctionalRole, List<PrototypeElement>> immutable = new EnumMap<>(FunctionalRole.class);
        for (Map.Entry<FunctionalRole, List<PrototypeElement>> entry : result.entrySet()) {
            immutable.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        return immutable;
    }

    /**
     * Asigna un {@link FunctionalRole} a cada {@link ArchitecturalFunction}
     * funcional segun la tabla de correspondencia del Documento de Diseno 6.9.
     */
    private static FunctionalRole mapFunctionToRole(ArchitecturalFunction fn) {
        return switch (fn) {
            case BED                     -> FunctionalRole.DORMITORIO;
            case STORAGE                 -> FunctionalRole.ALMACEN;
            case FURNITURE, STAIRS       -> FunctionalRole.OFICINA;
            case DOOR, WINDOW            -> FunctionalRole.LOBBY;
            case LIGHT                   -> FunctionalRole.UTILITARIO;
            // AIR, WALL, FLOOR, CEILING no llegaran aqui (filtrados en isFunctional).
            default                      -> FunctionalRole.UTILITARIO;
        };
    }

    // ── Utilidades ───────────────────────────────────────────────────────────────

    /**
     * Retorna {@code true} si la funcion representa un elemento funcional
     * (no estructural y no aire): los que deben aparecer en los RoomPrototypes.
     */
    private static boolean isFunctional(ArchitecturalFunction fn) {
        return switch (fn) {
            case AIR, WALL, FLOOR, CEILING -> false;
            default -> true;
        };
    }

    /**
     * Calcula los bounds XZ del volumen de la habitacion a partir de sus celdas.
     */
    private static RoomBounds computeRoomBounds(InteriorVolumeDetector.DetectedVolume room) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos cell : room.cells()) {
            minX = Math.min(minX, cell.getX()); maxX = Math.max(maxX, cell.getX());
            minY = Math.min(minY, cell.getY()); maxY = Math.max(maxY, cell.getY());
            minZ = Math.min(minZ, cell.getZ()); maxZ = Math.max(maxZ, cell.getZ());
        }
        return new RoomBounds(minX, maxX, minY, maxY, minZ, maxZ);
    }

    // ── Estructuras auxiliares internas ─────────────────────────────────────────

    /**
     * Elemento funcional recolectado antes de convertirlo a {@link PrototypeElement}.
     * Agrupa todos los campos necesarios en un objeto plano para facilitar el
     * procesamiento en {@link #groupByFunctionalRole}.
     */
    private record RawFunctionalElement(
            float normalizedX,
            float normalizedZ,
            ArchitecturalFunction function,
            BlockState blockState,
            int heightFromFloor,
            CompoundTag blockEntityData) {
    }

    /**
     * Bounds XZ e Y de un volumen de habitacion, precomputados para evitar
     * recalculos dentro de los bucles de recoleccion.
     */
    private record RoomBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        int spanX() { return maxX - minX + 1; }
        int spanZ() { return maxZ - minZ + 1; }
    }
}
