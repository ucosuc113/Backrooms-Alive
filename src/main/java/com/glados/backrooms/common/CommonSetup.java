package com.glados.backrooms.common;

import com.glados.backrooms.util.ModConstants;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Inicializacion comun (cliente + servidor) del mod.
 * <p>
 * Sin logica todavia. Reservado para cuando los modulos futuros necesiten
 * registrar canales de red, capacidades u otra configuracion compartida.
 */
@Mod.EventBusSubscriber(modid = ModConstants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CommonSetup {

    private CommonSetup() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        // Sin logica todavia.
    }
}
