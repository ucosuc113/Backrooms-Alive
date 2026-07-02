package com.glados.backrooms.analysis;

import com.glados.backrooms.classification.ArchitecturalFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Un elemento funcional dentro de un {@link RoomPrototype}, en posicion
 * relativa normalizada respecto al interior de la habitacion de origen en
 * la memoria (Documento de Diseno, seccion 10.6). {@code graph.FunctionalRoleAssigner}
 * mapea estas posiciones normalizadas al espacio interior real de cada
 * {@code RoomNode} concreto del grafo, produciendo coordenadas absolutas de
 * mundo (ese mapeo ocurre una sola vez, ver Invariante 1 del Documento de
 * Diseno: la posicion absoluta resultante nunca vuelve a cambiar).
 */
public record PrototypeElement(float normalizedX, float normalizedZ, ArchitecturalFunction function,
                                BlockState blockState, int heightFromFloor, CompoundTag blockEntityData) {

    public PrototypeElement {
        if (normalizedX < 0f || normalizedX > 1f || normalizedZ < 0f || normalizedZ > 1f) {
            throw new IllegalArgumentException("Las posiciones normalizadas deben estar en [0,1].");
        }
        if (heightFromFloor < 1 || heightFromFloor > 4) {
            throw new IllegalArgumentException("heightFromFloor debe estar en [1,4] (Documento de Diseno, 10.6).");
        }
        blockEntityData = blockEntityData == null ? null : blockEntityData.copy();
    }
}
