package com.glados.backrooms.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;

/**
 * Primitivas geometricas y de busqueda sin ningun conocimiento de dominio
 * sobre Backrooms (Documento de Arquitectura, seccion 2.1). Es consumida
 * por {@code analysis} (deteccion de volumenes y muros dentro de una
 * memoria), por {@code graph} (interseccion de pasillos, triangulacion) y
 * por {@code context} (filtrado de elementos activos por chunk).
 *
 * Nada en esta clase conoce que existen habitaciones, distritos o chunks de
 * Backrooms: solo geometria y grafos genericos.
 */
public final class GeometryUtil {

    private GeometryUtil() {
    }

    /** Rectangulo axis-aligned en el plano XZ, con bounds inclusivos en ambos extremos. */
    public record IntRect(int minX, int minZ, int maxX, int maxZ) {

        public IntRect {
            if (minX > maxX || minZ > maxZ) {
                throw new IllegalArgumentException("IntRect invalido: min debe ser <= max en ambos ejes.");
            }
        }

        public int width() {
            return maxX - minX + 1;
        }

        public int depth() {
            return maxZ - minZ + 1;
        }

        public boolean intersects(IntRect other) {
            return minX <= other.maxX && maxX >= other.minX
                    && minZ <= other.maxZ && maxZ >= other.minZ;
        }

        public boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        public IntRect expanded(int margin) {
            return new IntRect(minX - margin, minZ - margin, maxX + margin, maxZ + margin);
        }

        /** Interseccion de dos rectangulos solapados, o {@code null} si no se solapan. */
        public IntRect intersection(IntRect other) {
            if (!intersects(other)) {
                return null;
            }
            return new IntRect(
                    Math.max(minX, other.minX), Math.max(minZ, other.minZ),
                    Math.min(maxX, other.maxX), Math.min(maxZ, other.maxZ));
        }

        /** Distancia minima (0 si hay solape) desde este rectangulo a un punto. */
        public double distanceToPoint(double x, double z) {
            double dx = Math.max(0, Math.max(minX - x, x - maxX));
            double dz = Math.max(0, Math.max(minZ - z, z - maxZ));
            return Math.hypot(dx, dz);
        }
    }

    /** Segmento 1D inclusivo, usado para detectar solapes de pasillos a lo largo de un solo eje. */
    public record IntSegment1D(int start, int end) {

        public IntSegment1D {
            if (start > end) {
                throw new IllegalArgumentException("IntSegment1D invalido: start debe ser <= end.");
            }
        }

        public boolean intersects(IntSegment1D other) {
            return start <= other.end && end >= other.start;
        }

        public IntSegment1D expanded(int margin) {
            return new IntSegment1D(start - margin, end + margin);
        }
    }

    /** Predicado de vecindad para la inundacion 3D: true si la posicion es transitable (aire). */
    @FunctionalInterface
    public interface NeighborPredicate3D {
        boolean isTraversable(int x, int y, int z);
    }

    /** Resultado de una inundacion acotada: las celdas visitadas y si se excedio el radio de busqueda. */
    public record FloodFillResult(List<BlockPos> visited, boolean exceededRadius) {
    }

    private static final int[][] DIRECTIONS_6 = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    /**
     * BFS de 6 vecinos (cardinales + arriba/abajo) que expande desde
     * {@code start} mientras {@code predicate} indique que la posicion es
     * transitable, limitado a un radio Chebyshev de {@code maxRadius}
     * bloques respecto a {@code start} (Documento de Diseno, seccion 4.2).
     *
     * Si la inundacion intenta avanzar mas alla de ese radio en cualquier
     * direccion, esa direccion deja de explorarse y el resultado se marca
     * con {@code exceededRadius = true}; el resto de la inundacion continua
     * con normalidad. Esto permite distinguir un volumen acotado (la
     * inundacion termino porque encontro bloques solidos en todas
     * direcciones) de uno sin limite util dentro del radio de busqueda.
     */
    public static FloodFillResult boundedFloodFill3D(BlockPos start, int maxRadius, NeighborPredicate3D predicate) {
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        boolean[] exceededRadius = {false};

        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (int[] direction : DIRECTIONS_6) {
                int nx = current.getX() + direction[0];
                int ny = current.getY() + direction[1];
                int nz = current.getZ() + direction[2];

                if (Math.abs(nx - start.getX()) > maxRadius
                        || Math.abs(ny - start.getY()) > maxRadius
                        || Math.abs(nz - start.getZ()) > maxRadius) {
                    exceededRadius[0] = true;
                    continue;
                }

                BlockPos neighbor = new BlockPos(nx, ny, nz);
                if (visited.contains(neighbor)) {
                    continue;
                }
                if (!predicate.isTraversable(nx, ny, nz)) {
                    continue; // bloque solido: limite del volumen, no se expande mas alla
                }
                visited.add(neighbor);
                queue.add(neighbor);
            }
        }

        return new FloodFillResult(new ArrayList<>(visited), exceededRadius[0]);
    }
}
