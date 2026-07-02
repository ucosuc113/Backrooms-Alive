package com.glados.backrooms.graph;

import com.glados.backrooms.classification.ArchitecturalFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Un elemento funcional con su posicion absoluta calculada en coordenadas
 * del mundo (Documento de Diseno, seccion 10.9). Producido por
 * {@link FunctionalRoleAssigner} en el paso 8 del generador; la posicion
 * nunca cambia despues (Invariante 1).
 *
 * @param worldX          coordenada X exacta en coords mundo.
 * @param worldZ          coordenada Z exacta en coords mundo.
 * @param heightFromFloor nivel Y relativo al suelo del interior [1, 4].
 * @param function        tipo funcional del elemento.
 * @param blockState      bloque concreto a colocar.
 * @param blockEntityData datos de block entity, o null si no aplica.
 */
public record PlannedElement(
        int worldX,
        int worldZ,
        int heightFromFloor,
        ArchitecturalFunction function,
        BlockState blockState,
        CompoundTag blockEntityData
) {

    public PlannedElement {
        blockEntityData = blockEntityData == null ? null : blockEntityData.copy();
    }
}
