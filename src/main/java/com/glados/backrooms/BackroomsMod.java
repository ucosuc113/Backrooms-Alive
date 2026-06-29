package com.glados.backrooms;

import com.glados.backrooms.config.BackroomsConfig;
import com.glados.backrooms.registry.ModBlockEntities;
import com.glados.backrooms.registry.ModBlocks;
import com.glados.backrooms.registry.ModChunkGenerators;
import com.glados.backrooms.registry.ModCreativeTabs;
import com.glados.backrooms.registry.ModItems;
import com.glados.backrooms.registry.ModMenus;
import com.glados.backrooms.registry.ModParticles;
import com.glados.backrooms.registry.ModSounds;
import com.glados.backrooms.util.ModConstants;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Punto de entrada del mod.
 * <p>
 * MODULO ACTUAL: solo infraestructura. Este constructor unicamente conecta
 * los DeferredRegister existentes al bus de eventos y registra la
 * configuracion (todavia vacia). No hay mecanicas, generacion, portales ni
 * logica de dimension implementadas aqui.
 */
@Mod(ModConstants.MOD_ID)
public class BackroomsMod {

    public static final Logger LOGGER = LogManager.getLogger(ModConstants.MOD_ID);

    public BackroomsMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // --- Registros ---
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
        ModParticles.PARTICLE_TYPES.register(modEventBus);
        ModMenus.MENU_TYPES.register(modEventBus);
        ModChunkGenerators.CHUNK_GENERATORS.register(modEventBus);

        // --- Configuracion ---
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BackroomsConfig.COMMON_SPEC);

        LOGGER.info("[{}] Infraestructura base inicializada (modulo: project-setup).", ModConstants.MOD_ID);
    }
}
