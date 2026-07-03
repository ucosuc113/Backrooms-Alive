package com.glados.backrooms.common;

import com.glados.backrooms.analysis.MemoryAnalysisRepository;
import com.glados.backrooms.memory.MemoryLibrary;
import com.glados.backrooms.memory.MemoryRegion;
import com.glados.backrooms.util.ModConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;

/**
 * Punto de arranque del servidor: carga la libreria de memorias y construye
 * el repositorio de `MemoryAnalysis` antes de que se genere cualquier chunk.
 */
@Mod.EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class ServerLifecycleHooks {

    private ServerLifecycleHooks() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        var library = MemoryLibrary.get(server);
        var regions = new ArrayList<MemoryRegion>(library.all());
        var blockLookup = server.registryAccess().lookup(Registries.BLOCK).orElseThrow();
        MemoryAnalysisRepository.build(regions, blockLookup);
    }
}
