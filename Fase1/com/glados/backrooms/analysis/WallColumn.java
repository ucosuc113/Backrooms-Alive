package com.glados.backrooms.analysis;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Una columna vertical dentro de un {@link WallPrototype}: el bloque que
 * aparecia en cada uno de los 4 niveles interiores de la memoria original
 * (Documento de Diseno, seccion 10.3). El sistema actual fija la altura
 * interior util en 4 bloques (entre {@code FLOOR_Y} y {@code CEILING_Y}),
 * por lo que toda columna de pared se resamplea a exactamente 4 entradas
 * sin importar la altura interior real de la habitacion de origen en la
 * memoria; ese resampleo es responsabilidad de {@link WallSegmentAnalyzer}.
 *
 * Nota tecnica: al ser un record con un componente de tipo array, el
 * {@code equals}/{@code hashCode} generados automaticamente comparan el
 * array por identidad de referencia, no por contenido. Esto es aceptable
 * aqui porque ningun consumidor de {@code WallColumn} necesita comparar dos
 * instancias por igualdad estructural; solo se leen sus bloques por indice.
 */
public record WallColumn(BlockState[] blocksByHeight, boolean isOpeningColumn) {

    public static final int HEIGHT_LEVELS = 4;

    public WallColumn {
        if (blocksByHeight.length != HEIGHT_LEVELS) {
            throw new IllegalArgumentException(
                    "WallColumn requiere exactamente " + HEIGHT_LEVELS + " niveles de altura.");
        }
        blocksByHeight = blocksByHeight.clone();
    }

    public BlockState blockAt(int heightIndex) {
        return blocksByHeight[heightIndex];
    }
}
