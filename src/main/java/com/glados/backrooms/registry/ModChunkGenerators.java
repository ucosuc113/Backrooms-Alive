package com.glados.backrooms.registry;

import com.glados.backrooms.generation.BackroomsChunkGenerator;
import com.glados.backrooms.util.ModConstants;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModChunkGenerators {

    private ModChunkGenerators() {
    }

    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, ModConstants.MOD_ID);

    public static final RegistryObject<Codec<? extends ChunkGenerator>> BACKROOMS =
            CHUNK_GENERATORS.register("backrooms", () -> BackroomsChunkGenerator.CODEC);
}
