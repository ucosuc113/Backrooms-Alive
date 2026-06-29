package com.glados.backrooms.dimension;

import com.glados.backrooms.util.ModConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Claves de registro de la dimension del mod.
 * <p>
 * Este modulo solo deja preparada la infraestructura para referenciar la
 * dimension desde codigo (por ejemplo, en futuros modulos de portal o
 * teletransporte). La definicion real de la dimension (tipo + generador)
 * vive como datos en {@code data/backrooms/dimension_type} y
 * {@code data/backrooms/dimension}, con un generador provisional.
 * <p>
 * No se implementa generacion de mundo en este modulo.
 */
public final class ModDimensions {

    private ModDimensions() {
    }

    public static final ResourceKey<Level> BACKROOMS_LEVEL_KEY = ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation(ModConstants.MOD_ID, "backrooms")
    );

    public static final ResourceKey<DimensionType> BACKROOMS_TYPE_KEY = ResourceKey.create(
            Registries.DIMENSION_TYPE,
            new ResourceLocation(ModConstants.MOD_ID, "backrooms")
    );
}
