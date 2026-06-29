package com.glados.backrooms.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuracion del mod (categoria COMMON).
 * <p>
 * Sin entradas todavia. Los modulos futuros (generacion, corrupcion,
 * memoria, portales) anadiran aqui sus propias opciones configurables.
 */
public final class BackroomsConfig {

    private BackroomsConfig() {
    }

    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        // Sin opciones todavia.

        COMMON_SPEC = builder.build();
    }
}
