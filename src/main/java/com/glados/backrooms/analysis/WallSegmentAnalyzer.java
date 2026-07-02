package com.glados.backrooms.analysis;

import com.glados.backrooms.classification.MemoryFunctionClassifier;
import com.glados.backrooms.classification.ArchitecturalFunction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Implementa la seccion 4.3 del Documento de Diseno: para cada plano
 * vertical de bloques solidos contiguo detectado en una memoria, extrae:
 * <ul>
 *   <li>El patron de columna (WallPrototype), con sus rangos tileables y sus
 *       extremos independientes.</li>
 *   <li>Las aberturas (posicion, anchura, tipo puerta vs. ventana) presentes
 *       en el segmento.</li>
 *   <li>El CornerPrototype si existen al menos dos segmentos perpendiculares
 *       que se tocan en la misma esquina.</li>
 * </ul>
 *
 * Esta clase no tiene estado entre invocaciones: todos sus metodos son
 * estaticos. El punto de entrada principal es {@link #analyze}.
 *
 * <p><b>Coordenadas</b>: toda la logica opera en el sistema de coordenadas
 * relativo de la memoria (el mismo que {@code MemoryBlockSnapshot.relativePos()}),
 * es decir, origen en (0,0,0), todos los valores positivos dentro de los
 * bounds de la memoria.</p>
 */
public final class WallSegmentAnalyzer {

    /** Numero de columnas de extremo que nunca se repiten (Documento de Diseno 4.3 y WallPrototype.END_SIZE). */
    private static final int END_SIZE = 2;

    /**
     * Una abertura detectada dentro de un segmento de muro. Se distingue
     * puerta (abarca toda la altura interior) de ventana (abarca solo parte).
     */
    public record DetectedOpening(int positionAlongWall, int widthInBlocks, boolean isDoor) {
    }

    /**
     * Un segmento de muro detectado y completamente procesado.
     *
     * @param prototype    prototipo de columnas listo para uso en {@link WallPrototype}.
     * @param wallRole     rol inferido segun seccion 4.3 (EXTERIOR, INTERIOR o PASILLO).
     * @param openings     aberturas encontradas a lo largo del segmento.
     * @param axis         eje dominante del segmento: 'X' o 'Z'.
     * @param fixedCoord   valor de la coordenada perpendicular al eje (Z si axis=X, X si axis=Z).
     * @param startCoord   inicio del segmento en la direccion del eje.
     * @param endCoord     fin del segmento en la direccion del eje (inclusive).
     */
    public record AnalyzedSegment(WallPrototype prototype, WallRole wallRole,
                                   List<DetectedOpening> openings, char axis,
                                   int fixedCoord, int startCoord, int endCoord) {
    }

    /**
     * Resultado completo del analisis de segmentos de muro de una memoria.
     *
     * @param segments       todos los segmentos detectados.
     * @param wallPrototypes mapa WallRole → WallPrototype (el primer prototipo de cada rol
     *                       encontrado, o el mas largo si hay varios del mismo rol).
     * @param cornerPrototype prototipo de esquina derivado de los segmentos perpendiculares,
     *                        o {@code null} si no se pudo detectar ninguna esquina clara.
     * @param openingPrototype prototipo de abertura tipica de esta memoria, o {@code null}
     *                         si no se encontro ninguna abertura.
     */
    public record AnalysisResult(List<AnalyzedSegment> segments,
                                  Map<WallRole, WallPrototype> wallPrototypes,
                                  CornerPrototype cornerPrototype,
                                  OpeningPrototype openingPrototype) {
    }

    private WallSegmentAnalyzer() {
    }

    // ── Punto de entrada ─────────────────────────────────────────────────────────

    /**
     * Analiza todos los segmentos de muro presentes en una memoria.
     *
     * @param statesByPos  mapa posicion-relativa → BlockState de la memoria completa.
     * @param floorY       nivel Y del suelo interior de la memoria (limite inferior de
     *                     la region de pared; los bloques por debajo de este nivel se
     *                     ignoran como parte de la base estructural, no de los muros).
     * @param ceilingY     nivel Y del techo interior (limite superior exclusivo: los
     *                     bloques en ceilingY tambien son estructura, no muro).
     * @param interiorHeight altura util en niveles de bloque entre suelo y techo
     *                     (debe ser igual a ceilingY - floorY - 1; se recibe para
     *                     no recalcularlo en cada invocacion).
     * @param volumes      volumenes de aire detectados por {@link InteriorVolumeDetector},
     *                     usados para inferir el rol de cada segmento.
     */
    public static AnalysisResult analyze(Map<BlockPos, BlockState> statesByPos,
            int floorY, int ceilingY, int interiorHeight,
            List<InteriorVolumeDetector.DetectedVolume> volumes) {

        // Paso 1: extraer todos los bloques solidos del rango de pared (entre suelo y techo).
        Set<BlockPos> wallBlocks = extractWallBlocks(statesByPos, floorY, ceilingY);

        // Paso 2: detectar segmentos planares axis-aligned.
        List<RawSegment> rawSegments = detectPlanarSegments(wallBlocks, floorY, interiorHeight);

        // Paso 3: para cada segmento raw, construir el AnalyzedSegment.
        List<AnalyzedSegment> analyzedSegments = new ArrayList<>();
        for (RawSegment raw : rawSegments) {
            AnalyzedSegment analyzed = buildAnalyzedSegment(raw, statesByPos, floorY, interiorHeight, volumes);
            if (analyzed != null) {
                analyzedSegments.add(analyzed);
            }
        }

        // Paso 4: construir el mapa WallRole → WallPrototype eligiendo el prototipo
        // mas largo (mayor referenceLength) de cada rol.
        Map<WallRole, WallPrototype> wallPrototypes = buildWallPrototypeMap(analyzedSegments);

        // Paso 5: CornerPrototype a partir de segmentos perpendiculares que comparten esquina.
        CornerPrototype cornerPrototype = buildCornerPrototype(analyzedSegments, statesByPos, floorY, interiorHeight);

        // Paso 6: OpeningPrototype a partir de la primera abertura encontrada que tenga
        // algun contexto de marco detectable.
        OpeningPrototype openingPrototype = buildOpeningPrototype(analyzedSegments, statesByPos);

        return new AnalysisResult(
                Collections.unmodifiableList(analyzedSegments),
                wallPrototypes,
                cornerPrototype,
                openingPrototype);
    }

    // ── Paso 1: extraer bloques de pared ────────────────────────────────────────

    /**
     * Selecciona los bloques solidos que se encuentran estrictamente entre
     * {@code floorY} (exclusive) y {@code ceilingY} (exclusive), ignorando el
     * suelo y el techo que son superficie estructural, no muro.
     */
    private static Set<BlockPos> extractWallBlocks(Map<BlockPos, BlockState> statesByPos,
            int floorY, int ceilingY) {
        Set<BlockPos> result = new HashSet<>();
        for (Map.Entry<BlockPos, BlockState> entry : statesByPos.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();
            int y = pos.getY();
            if (y > floorY && y < ceilingY && !state.isAir()) {
                ArchitecturalFunction fn = MemoryFunctionClassifier.classify(state);
                // Solo nos interesan bloques estructurales (WALL) o de borde estructural
                // (CEILING/FLOOR no pueden aparecer aqui segun el filtro de Y, pero se
                // excluyen funciones claramente no-estructurales como STORAGE, BED, etc.).
                if (fn == ArchitecturalFunction.WALL) {
                    result.add(pos.immutable());
                }
            }
        }
        return result;
    }

    // ── Paso 2: detectar segmentos planares ─────────────────────────────────────

    /**
     * Agrupa los bloques de pared en segmentos planares. Un segmento planar
     * es un conjunto de bloques que comparten la misma coordenada en un eje
     * (X o Z) y forman un plano vertical contiguo en ese eje a lo largo del
     * otro eje y del eje Y.
     *
     * <p>El algoritmo: para cada Y del rango interior, se buscan corridas
     * horizontales de bloques alineados en X (fila X: mismo Z, varios X) y
     * en Z (fila Z: mismo X, varios Z). Las corridas de la misma coordenada
     * fija se fusionan verticalmente cuando tienen el mismo rango horizontal.</p>
     */
    private static List<RawSegment> detectPlanarSegments(Set<BlockPos> wallBlocks, int floorY, int interiorHeight) {
        // Agrupa por (axis, fixedCoord) → lista de posiciones del plano.
        Map<Long, List<BlockPos>> planesByKey = new HashMap<>();

        for (BlockPos pos : wallBlocks) {
            // Clave para plano en X (mismo Z): eje=X, fijo=Z
            long keyX = planeKey('X', pos.getZ());
            planesByKey.computeIfAbsent(keyX, k -> new ArrayList<>()).add(pos);
            // Clave para plano en Z (mismo X): eje=Z, fijo=X
            long keyZ = planeKey('Z', pos.getX());
            planesByKey.computeIfAbsent(keyZ, k -> new ArrayList<>()).add(pos);
        }

        List<RawSegment> segments = new ArrayList<>();
        for (Map.Entry<Long, List<BlockPos>> entry : planesByKey.entrySet()) {
            long key = entry.getKey();
            char axis = (char) ((key >> 32) & 0xFFFFFFFFL);
            int fixedCoord = (int) (key & 0xFFFFFFFFL);
            List<BlockPos> planeCells = entry.getValue();

            // Descomponer el plano en corridas horizontales contiguas a cada Y.
            List<RawSegment> fromPlane = extractSegmentsFromPlane(axis, fixedCoord, planeCells, floorY, interiorHeight);
            segments.addAll(fromPlane);
        }

        // Deduplicar: un bloque puede estar en un plano X y en un plano Z a la vez
        // (esquina). Quitamos segmentos cuya longitud sea < 2.
        segments.removeIf(seg -> seg.length() < 2);
        return segments;
    }

    /**
     * Dado un plano (axis, fixedCoord, listaDeCeldas), identifica los
     * sub-segmentos horizontalmente contiguos que tengan al menos
     * {@link #END_SIZE}*2 bloques de longitud y al menos 1 nivel de altura.
     * Retorna uno por cada corrida contigua distinta.
     */
    private static List<RawSegment> extractSegmentsFromPlane(char axis, int fixedCoord,
            List<BlockPos> planeCells, int floorY, int interiorHeight) {

        // Agrupar por nivel Y → lista de coordenadas a lo largo del eje movil.
        Map<Integer, List<Integer>> coordsByY = new HashMap<>();
        for (BlockPos pos : planeCells) {
            int movingCoord = axis == 'X' ? pos.getX() : pos.getZ();
            int y = pos.getY();
            coordsByY.computeIfAbsent(y, k -> new ArrayList<>()).add(movingCoord);
        }

        // Para cada Y, detectar corridas contiguas.
        // Una corrida es una secuencia de enteros consecutivos.
        Map<String, RawSegmentAccumulator> accumulators = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> yEntry : coordsByY.entrySet()) {
            int y = yEntry.getKey();
            List<Integer> coords = new ArrayList<>(yEntry.getValue());
            Collections.sort(coords);

            List<int[]> runs = extractRuns(coords);
            for (int[] run : runs) {
                int start = run[0];
                int end = run[1];
                String runKey = start + ":" + end;
                accumulators.computeIfAbsent(runKey,
                        k -> new RawSegmentAccumulator(axis, fixedCoord, start, end))
                        .addY(y);
            }
        }

        List<RawSegment> result = new ArrayList<>();
        for (RawSegmentAccumulator acc : accumulators.values()) {
            result.add(acc.build(floorY, interiorHeight));
        }
        return result;
    }

    /**
     * Extrae corridas de enteros consecutivos de una lista ya ordenada.
     * Retorna una lista de arrays [start, end] (ambos inclusive).
     */
    private static List<int[]> extractRuns(List<Integer> sortedCoords) {
        List<int[]> runs = new ArrayList<>();
        if (sortedCoords.isEmpty()) return runs;
        int start = sortedCoords.get(0);
        int prev = start;
        for (int i = 1; i < sortedCoords.size(); i++) {
            int cur = sortedCoords.get(i);
            if (cur > prev + 1) {
                runs.add(new int[]{start, prev});
                start = cur;
            }
            prev = cur;
        }
        runs.add(new int[]{start, prev});
        return runs;
    }

    /** Codifica (axis, fixedCoord) en un unico long para usarlo como clave de mapa. */
    private static long planeKey(char axis, int fixedCoord) {
        return ((long) axis << 32) | (fixedCoord & 0xFFFFFFFFL);
    }

    // ── Paso 3: construir AnalyzedSegment ────────────────────────────────────────

    /**
     * Transforma un {@link RawSegment} en un {@link AnalyzedSegment} completo:
     * resamplea las columnas al numero de niveles interiores del sistema target
     * (WallColumn.HEIGHT_LEVELS = 4), detecta aberturas, e infiere el WallRole.
     */
    private static AnalyzedSegment buildAnalyzedSegment(RawSegment raw, Map<BlockPos, BlockState> statesByPos,
            int floorY, int interiorHeight,
            List<InteriorVolumeDetector.DetectedVolume> volumes) {

        int segmentLength = raw.length();
        if (segmentLength < 1) return null;

        // Construir una columna por cada posicion a lo largo del segmento.
        List<WallColumn> columns = new ArrayList<>(segmentLength);
        List<DetectedOpening> openings = new ArrayList<>();

        int openingStart = -1;

        for (int i = 0; i < segmentLength; i++) {
            int movingCoord = raw.startCoord + i;
            WallColumn column = buildColumn(raw.axis, raw.fixedCoord, movingCoord,
                    statesByPos, floorY, interiorHeight, raw);
            columns.add(column);

            // Detectar aberturas: columna de opening = columna donde todos los
            // bloques interiores son aire (o funciones de puerta/ventana).
            boolean isOpening = column.isOpeningColumn();
            if (isOpening && openingStart < 0) {
                openingStart = i;
            } else if (!isOpening && openingStart >= 0) {
                int openingWidth = i - openingStart;
                boolean isDoor = isFullHeightOpening(raw.axis, raw.fixedCoord,
                        raw.startCoord + openingStart, openingWidth,
                        statesByPos, floorY, interiorHeight);
                openings.add(new DetectedOpening(openingStart, openingWidth, isDoor));
                openingStart = -1;
            }
        }
        // Cierre de abertura al final del segmento.
        if (openingStart >= 0) {
            int openingWidth = segmentLength - openingStart;
            boolean isDoor = isFullHeightOpening(raw.axis, raw.fixedCoord,
                    raw.startCoord + openingStart, openingWidth,
                    statesByPos, floorY, interiorHeight);
            openings.add(new DetectedOpening(openingStart, openingWidth, isDoor));
        }

        // Calcular rangos tileables: los extremos END_SIZE columnas de cada lado
        // no se repiten; el centro si. Si el segmento es demasiado corto para
        // tener un rango tileable distinto de los extremos, el rango tileable
        // comprende todas las columnas (se repite todo).
        int tileableStart = Math.min(END_SIZE, columns.size() / 2);
        int tileableEnd = Math.max(tileableStart, columns.size() - END_SIZE);

        WallRole role = inferWallRole(raw, volumes);

        WallPrototype prototype = new WallPrototype(role, columns, tileableStart, tileableEnd);
        return new AnalyzedSegment(prototype, role, Collections.unmodifiableList(openings),
                raw.axis, raw.fixedCoord, raw.startCoord, raw.endCoord);
    }

    /**
     * Construye una WallColumn para la posicion (movingCoord) a lo largo del
     * segmento, resampleando la altura real de la memoria al numero de niveles
     * target ({@link WallColumn#HEIGHT_LEVELS}).
     */
    private static WallColumn buildColumn(char axis, int fixedCoord, int movingCoord,
            Map<BlockPos, BlockState> statesByPos, int floorY, int interiorHeight,
            RawSegment raw) {

        BlockState[] blocks = new BlockState[WallColumn.HEIGHT_LEVELS];
        boolean isOpening = true; // se asume abertura hasta probar lo contrario

        for (int heightIndex = 0; heightIndex < WallColumn.HEIGHT_LEVELS; heightIndex++) {
            // Resampleo lineal: mapea [0, HEIGHT_LEVELS-1] → [0, interiorHeight-1].
            int sourceHeightOffset = interiorHeight <= 1 ? 0
                    : heightIndex * (interiorHeight - 1) / (WallColumn.HEIGHT_LEVELS - 1);
            int worldY = floorY + 1 + sourceHeightOffset;

            BlockPos pos = axis == 'X'
                    ? new BlockPos(movingCoord, worldY, fixedCoord)
                    : new BlockPos(fixedCoord, worldY, movingCoord);

            BlockState state = statesByPos.getOrDefault(pos, Blocks.AIR.defaultBlockState());
            blocks[heightIndex] = state;

            if (!state.isAir()) {
                isOpening = false;
            }
        }

        return new WallColumn(blocks, isOpening);
    }

    /**
     * Determina si una abertura abarca toda la altura interior (puerta) o solo
     * parte (ventana). Una abertura es de tipo puerta si al menos el 75% de los
     * niveles de altura del primer bloque de la abertura son aire.
     */
    private static boolean isFullHeightOpening(char axis, int fixedCoord,
            int openingStartCoord, int openingWidth,
            Map<BlockPos, BlockState> statesByPos, int floorY, int interiorHeight) {
        if (interiorHeight <= 0) return false;
        int midCoord = openingStartCoord + openingWidth / 2;
        int airCount = 0;
        for (int dy = 1; dy <= interiorHeight; dy++) {
            BlockPos pos = axis == 'X'
                    ? new BlockPos(midCoord, floorY + dy, fixedCoord)
                    : new BlockPos(fixedCoord, floorY + dy, midCoord);
            BlockState state = statesByPos.getOrDefault(pos, Blocks.AIR.defaultBlockState());
            if (state.isAir()) airCount++;
        }
        return airCount >= (interiorHeight * 3 / 4);
    }

    // ── Inferencia de WallRole ───────────────────────────────────────────────────

    /**
     * Infiere el rol del segmento comparando su posicion con los volumenes de
     * aire detectados:
     * <ul>
     *   <li>Si el segmento separa dos volumenes de tipo ROOM: INTERIOR.</li>
     *   <li>Si el segmento separa un ROOM de un CORRIDOR (o bordea solo CORRIDOR): PASILLO.</li>
     *   <li>Si el segmento bordea un unico volumen o ningun volumen: EXTERIOR.</li>
     * </ul>
     */
    private static WallRole inferWallRole(RawSegment raw,
            List<InteriorVolumeDetector.DetectedVolume> volumes) {

        // Buscamos volumenes a ambos lados del segmento (distancia 1 en la direccion perpendicular).
        Set<InteriorVolumeDetector.VolumeShape> sideA = volumesAdjacentToSide(raw, +1, volumes);
        Set<InteriorVolumeDetector.VolumeShape> sideB = volumesAdjacentToSide(raw, -1, volumes);

        boolean hasRoomA = sideA.contains(InteriorVolumeDetector.VolumeShape.ROOM);
        boolean hasRoomB = sideB.contains(InteriorVolumeDetector.VolumeShape.ROOM);
        boolean hasCorridorA = sideA.contains(InteriorVolumeDetector.VolumeShape.CORRIDOR);
        boolean hasCorridorB = sideB.contains(InteriorVolumeDetector.VolumeShape.CORRIDOR);

        if (hasRoomA && hasRoomB) {
            return WallRole.INTERIOR;
        }
        if ((hasRoomA || hasRoomB) && (hasCorridorA || hasCorridorB)) {
            return WallRole.PASILLO;
        }
        if (hasCorridorA || hasCorridorB) {
            return WallRole.PASILLO;
        }
        return WallRole.EXTERIOR;
    }

    /**
     * Recoge los {@link InteriorVolumeDetector.VolumeShape} de los volumenes cuyas celdas se
     * encuentran a {@code side} bloques de distancia en el eje perpendicular del segmento.
     */
    private static Set<InteriorVolumeDetector.VolumeShape> volumesAdjacentToSide(
            RawSegment raw, int side,
            List<InteriorVolumeDetector.DetectedVolume> volumes) {

        Set<InteriorVolumeDetector.VolumeShape> shapes = new HashSet<>();
        int checkCoord = raw.fixedCoord + side;

        for (InteriorVolumeDetector.DetectedVolume vol : volumes) {
            for (BlockPos cell : vol.cells()) {
                int cellFixed = raw.axis == 'X' ? cell.getZ() : cell.getX();
                int cellMoving = raw.axis == 'X' ? cell.getX() : cell.getZ();
                if (cellFixed == checkCoord
                        && cellMoving >= raw.startCoord
                        && cellMoving <= raw.endCoord) {
                    shapes.add(vol.shape());
                    break;
                }
            }
        }
        return shapes;
    }

    // ── Paso 4: mapa WallRole → WallPrototype ───────────────────────────────────

    /**
     * Para cada rol, elige el WallPrototype con mayor {@code referenceLength}.
     * Si no hay ningun segmento de un rol, ese rol no aparece en el mapa.
     */
    private static Map<WallRole, WallPrototype> buildWallPrototypeMap(List<AnalyzedSegment> segments) {
        Map<WallRole, WallPrototype> result = new HashMap<>();
        for (AnalyzedSegment seg : segments) {
            WallRole role = seg.wallRole();
            WallPrototype existing = result.get(role);
            if (existing == null || seg.prototype().referenceLength() > existing.referenceLength()) {
                result.put(role, seg.prototype());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    // ── Paso 5: CornerPrototype ─────────────────────────────────────────────────

    /**
     * Intenta construir un CornerPrototype buscando una esquina donde se toquen
     * dos segmentos perpendiculares (uno en eje X y otro en eje Z) cuyos
     * extremos comparten una posicion comun.
     *
     * Se toma la primera esquina valida encontrada. Si no existe ninguna, retorna null.
     */
    private static CornerPrototype buildCornerPrototype(List<AnalyzedSegment> segments,
            Map<BlockPos, BlockState> statesByPos, int floorY, int interiorHeight) {

        // Separar segmentos por eje.
        List<AnalyzedSegment> axisX = new ArrayList<>();
        List<AnalyzedSegment> axisZ = new ArrayList<>();
        for (AnalyzedSegment seg : segments) {
            if (seg.axis() == 'X') axisX.add(seg);
            else axisZ.add(seg);
        }

        for (AnalyzedSegment segX : axisX) {
            for (AnalyzedSegment segZ : axisZ) {
                // El segmento X tiene fixedCoord = Z; el segmento Z tiene fixedCoord = X.
                // Se tocan si el fixedCoord de X esta en el rango [startCoord, endCoord] de Z
                // y el fixedCoord de Z esta en el rango [startCoord, endCoord] de X.
                int cornerX = segZ.fixedCoord();
                int cornerZ = segX.fixedCoord();

                boolean xTouchesZ = cornerX >= segX.startCoord() && cornerX <= segX.endCoord();
                boolean zTouchesX = cornerZ >= segZ.startCoord() && cornerZ <= segZ.endCoord();
                if (!xTouchesZ || !zTouchesX) continue;

                // Tenemos una esquina en (cornerX, *, cornerZ). Extraer la rejilla 2x2.
                WallColumn[][] grid = new WallColumn[2][2];
                // (0,0) = el bloque de la esquina exacta.
                // (1,0) = vecino a lo largo del eje X (cornerX+1, cornerZ).
                // (0,1) = vecino a lo largo del eje Z (cornerX, cornerZ+1).
                // (1,1) = diagonal interior (cornerX+1, cornerZ+1).
                int[][] offsets = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
                boolean valid = true;
                for (int[] off : offsets) {
                    int ox = off[0];
                    int oz = off[1];
                    WallColumn col = readCornerColumn(cornerX + ox, cornerZ + oz,
                            statesByPos, floorY, interiorHeight);
                    if (col == null) {
                        valid = false;
                        break;
                    }
                    grid[ox][oz] = col;
                }
                if (!valid) continue;

                try {
                    return new CornerPrototype(grid);
                } catch (IllegalArgumentException ignored) {
                    // Rejilla invalida, intentar siguiente combinacion.
                }
            }
        }
        return null;
    }

    /**
     * Lee los bloques de la columna vertical en (worldX, worldZ) dentro del
     * rango interior y los resamplea a {@link WallColumn#HEIGHT_LEVELS}.
     * Retorna null si no hay ningun bloque solido en esa posicion (columna de
     * aire puro: no es una columna de esquina valida).
     */
    private static WallColumn readCornerColumn(int worldX, int worldZ,
            Map<BlockPos, BlockState> statesByPos, int floorY, int interiorHeight) {
        BlockState[] blocks = new BlockState[WallColumn.HEIGHT_LEVELS];
        boolean hasAnyBlock = false;
        for (int heightIndex = 0; heightIndex < WallColumn.HEIGHT_LEVELS; heightIndex++) {
            int sourceOffset = interiorHeight <= 1 ? 0
                    : heightIndex * (interiorHeight - 1) / (WallColumn.HEIGHT_LEVELS - 1);
            int worldY = floorY + 1 + sourceOffset;
            BlockPos pos = new BlockPos(worldX, worldY, worldZ);
            BlockState state = statesByPos.getOrDefault(pos, Blocks.AIR.defaultBlockState());
            blocks[heightIndex] = state;
            if (!state.isAir()) hasAnyBlock = true;
        }
        return hasAnyBlock ? new WallColumn(blocks, false) : null;
    }

    // ── Paso 6: OpeningPrototype ─────────────────────────────────────────────────

    /**
     * Construye el OpeningPrototype buscando la primera abertura que tenga
     * bloques contiguos no-aire en las posiciones de marco lateral o dintel.
     *
     * Si ninguna abertura tiene marco detectado, retorna {@code OpeningPrototype.bare()}
     * (aberturas simplemente como aire desnudo).
     */
    private static OpeningPrototype buildOpeningPrototype(List<AnalyzedSegment> analyzedSegments,
            Map<BlockPos, BlockState> statesByPos) {

        for (AnalyzedSegment seg : analyzedSegments) {
            for (DetectedOpening opening : seg.openings()) {
                // Posicion en el segmento donde empieza la abertura.
                int openingCoord = seg.startCoord() + opening.positionAlongWall();

                // Comprobar si hay un bloque de marco en la columna inmediatamente
                // anterior a la abertura (el "jamba" izquierda).
                if (opening.positionAlongWall() > 0) {
                    int frameCoord = openingCoord - 1;
                    BlockPos framePos = seg.axis() == 'X'
                            ? new BlockPos(frameCoord, seg.startCoord(), seg.fixedCoord())
                            : new BlockPos(seg.fixedCoord(), seg.startCoord(), frameCoord);
                    // Buscamos cualquier bloque solido no-WALL (por ejemplo un marco de madera
                    // o un bloque diferente al cuerpo principal del muro).
                    BlockState frameState = statesByPos.getOrDefault(framePos, Blocks.AIR.defaultBlockState());
                    if (!frameState.isAir() && MemoryFunctionClassifier.classify(frameState) == ArchitecturalFunction.WALL) {
                        // Hay algun material de marco: investigar si es distinto del muro principal
                        // buscando el material en el dintel (Y mas alto de la abertura).
                        boolean hasLintel = checkLintel(seg, opening, statesByPos, seg.startCoord());
                        return new OpeningPrototype(true, frameState, hasLintel);
                    }
                }
            }
        }

        // Ninguna abertura con marco detectado: aberturas son simplemente aire.
        return OpeningPrototype.bare();
    }

    /**
     * Comprueba si existe un bloque de dintel (bloque solido en el nivel Y mas
     * alto de la abertura, por encima del vano).
     */
    private static boolean checkLintel(AnalyzedSegment seg, DetectedOpening opening,
            Map<BlockPos, BlockState> statesByPos, int floorY) {
        // El dintel es el bloque en la misma coordenada horizontal que el centro de
        // la abertura, un nivel por encima del vano interior.
        int centerCoord = seg.startCoord() + opening.positionAlongWall() + opening.widthInBlocks() / 2;
        // Usamos HEIGHT_LEVELS como referencia para el nivel superior del vano.
        int lintelY = floorY + WallColumn.HEIGHT_LEVELS; // justo por encima del ultimo nivel interior
        BlockPos lintelPos = seg.axis() == 'X'
                ? new BlockPos(centerCoord, lintelY, seg.fixedCoord())
                : new BlockPos(seg.fixedCoord(), lintelY, centerCoord);
        BlockState lintelState = statesByPos.getOrDefault(lintelPos, Blocks.AIR.defaultBlockState());
        return !lintelState.isAir();
    }

    // ── Estructuras auxiliares internas ─────────────────────────────────────────

    /**
     * Segmento planar sin procesar: eje, coordenada fija, rango de coordenada
     * movil y niveles Y que lo componen.
     */
    private static final class RawSegment {
        final char axis;
        final int fixedCoord;
        final int startCoord;
        final int endCoord;
        final int minY;
        final int maxY;

        RawSegment(char axis, int fixedCoord, int startCoord, int endCoord, int minY, int maxY) {
            this.axis = axis;
            this.fixedCoord = fixedCoord;
            this.startCoord = startCoord;
            this.endCoord = endCoord;
            this.minY = minY;
            this.maxY = maxY;
        }

        int length() {
            return endCoord - startCoord + 1;
        }
    }

    /** Acumula los niveles Y de un segmento mientras se detectan las corridas. */
    private static final class RawSegmentAccumulator {
        final char axis;
        final int fixedCoord;
        final int startCoord;
        final int endCoord;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        RawSegmentAccumulator(char axis, int fixedCoord, int startCoord, int endCoord) {
            this.axis = axis;
            this.fixedCoord = fixedCoord;
            this.startCoord = startCoord;
            this.endCoord = endCoord;
        }

        void addY(int y) {
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        RawSegment build(int floorY, int interiorHeight) {
            int safeMinY = minY == Integer.MAX_VALUE ? floorY + 1 : minY;
            int safeMaxY = maxY == Integer.MIN_VALUE ? floorY + interiorHeight : maxY;
            return new RawSegment(axis, fixedCoord, startCoord, endCoord, safeMinY, safeMaxY);
        }
    }
}
