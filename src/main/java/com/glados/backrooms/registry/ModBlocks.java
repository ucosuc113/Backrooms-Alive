package com.glados.backrooms.registry;

import com.glados.backrooms.util.ModConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registro de bloques del mod.
 * <p>
 * En este modulo solo se registra el bloque "Almacen de Mentes" como objeto
 * base, sin ningun comportamiento especial todavia. La logica de memoria
 * y corrupcion se anadira en sus modulos correspondientes.
 */
public final class ModBlocks {

    private ModBlocks() {
    }

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, ModConstants.MOD_ID);

    public static final RegistryObject<Block> MIND_STORAGE = BLOCKS.register(
            "mind_storage",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops())
    );
}
