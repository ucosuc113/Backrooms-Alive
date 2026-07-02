package com.glados.backrooms.analysis;

import com.glados.backrooms.util.GeometryUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Implementa la seccion 4.2 del Documento de Diseno: deteccion de volumenes
 * de aire acotados dentro de una memoria, y su clasificacion en habitacion
 * o pasillo (o su descarte si no cumplen los requisitos minimos).
 *
 * Esta clase no tiene estado entre invocaciones: cada llamada a
 * {@link #detectVolumes} produce una lista de volumenes a partir de los
 * datos que se le pasan, sin leer ni escribir ningun campo estatico.
 */
public final class InteriorVolumeDetector {

    /** Radio maximo de busqueda en bloques, en todas direcciones (Documento de Diseno, 4.2). */
    public static final int MAX_SEARCH_RADIUS = 8;

    /** Si la relacion entre las dos dimensiones horizontales supera esto, el volumen es un pasillo. */
    private static final double CORRIDOR_ASPECT_THRESHOLD = 2.5;

    /** Si la dimension minima horizontal es menor a esto, el volumen se descarta (demasiado estrecho). */
    private static final int MINIMUM_HORIZONTAL_DIMENSION = 2;

    private InteriorVolumeDetector() {
    }

    public enum VolumeShape {
        ROOM,
        CORRIDOR
    }

    /**
     * Volumen de aire detectado y clasificado. {@code cells} conserva todas
     * las posiciones relativas (respecto al origen de la memoria, es decir,
     * el mismo sistema de coordenadas que {@code MemoryBlockSnapshot.relativePos()})
     * que forman el volumen. {@code interiorHeight} es la distancia vertical
     * entre el primer bloque solido superior y el primer bloque solido
     * inferior detectados al delimitar el volumen (Documento de Diseno,
     * 4.2), aproximada aqui por la extension vertical del propio volumen de
     * aire, ya que la inundacion se detiene exactamente en esos limites
     * solidos.
     */
    public record DetectedVolume(List<BlockPos> cells, VolumeShape shape,
                                  int horizontalSpanX, int horizontalSpanZ, int interiorHeight) {
    }

    /**
     * Recorre todas las posiciones de aire de la memoria (segun
     * {@code statesByPos}) y agrupa las que pertenecen al mismo volumen
     * acotado mediante {@link GeometryUtil#boundedFloodFill3D}, descartando
     * los volumenes que exceden {@link #MAX_SEARCH_RADIUS} sin encontrar un
     * limite solido en alguna direccion, o cuya dimension horizontal minima
     * es menor a {@link #MINIMUM_HORIZONTAL_DIMENSION}.
     *
     * @param statesByPos    estado de cada posicion relativa dentro de la memoria (incluye aire y solido).
     * @param relativeBounds bounds de la memoria en su propio sistema de coordenadas relativo (origen en 0,0,0).
     */
    public static List<DetectedVolume> detectVolumes(Map<BlockPos, BlockState> statesByPos, BoundingBox relativeBounds) {
        List<DetectedVolume> volumes = new ArrayList<>();
        Set<BlockPos> globallyVisited = new HashSet<>();

        for (Map.Entry<BlockPos, BlockState> entry : statesByPos.entrySet()) {
            BlockPos seed = entry.getKey();
            if (globallyVisited.contains(seed) || !entry.getValue().isAir()) {
                continue;
            }

            GeometryUtil.NeighborPredicate3D predicate =
                    (x, y, z) -> isTraversableAir(statesByPos, relativeBounds, x, y, z);

            GeometryUtil.FloodFillResult result =
                    GeometryUtil.boundedFloodFill3D(seed, MAX_SEARCH_RADIUS, predicate);

            globallyVisited.addAll(result.visited());

            if (result.exceededRadius()) {
                continue; // sin limite solido util dentro del radio de busqueda (4.2)
            }

            DetectedVolume volume = classify(result.visited());
            if (volume != null) {
                volumes.add(volume);
            }
        }
        return volumes;
    }

    private static boolean isTraversableAir(Map<BlockPos, BlockState> statesByPos,
            BoundingBox relativeBounds, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!relativeBounds.isInside(pos)) {
            return false; // fuera de la memoria: se trata como limite solido
        }
        BlockState state = statesByPos.get(pos);
        return state != null && state.isAir();
    }

    private static DetectedVolume classify(List<BlockPos> cells) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BlockPos cell : cells) {
            minX = Math.min(minX, cell.getX());
            maxX = Math.max(maxX, cell.getX());
            minY = Math.min(minY, cell.getY());
            maxY = Math.max(maxY, cell.getY());
            minZ = Math.min(minZ, cell.getZ());
            maxZ = Math.max(maxZ, cell.getZ());
        }

        int spanX = maxX - minX + 1;
        int spanZ = maxZ - minZ + 1;
        int interiorHeight = maxY - minY + 1;

        int minHorizontal = Math.min(spanX, spanZ);
        int maxHorizontal = Math.max(spanX, spanZ);
        if (minHorizontal < MINIMUM_HORIZONTAL_DIMENSION) {
            return null; // demasiado estrecho (4.2)
        }

        VolumeShape shape = (maxHorizontal / (double) minHorizontal) > CORRIDOR_ASPECT_THRESHOLD
                ? VolumeShape.CORRIDOR
                : VolumeShape.ROOM;

        return new DetectedVolume(List.copyOf(cells), shape, spanX, spanZ, interiorHeight);
    }
}
