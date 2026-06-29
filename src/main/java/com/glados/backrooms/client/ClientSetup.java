package com.glados.backrooms.client;

import com.glados.backrooms.util.ModConstants;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Inicializacion exclusiva de cliente.
 * <p>
 * Sin logica todavia. Reservado para registrar renderers de bloque/entidad,
 * pantallas de menu o renderizado de portal en modulos futuros.
 */
@Mod.EventBusSubscriber(modid = ModConstants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Sin logica todavia.
    }
}
