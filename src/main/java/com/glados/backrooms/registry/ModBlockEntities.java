package com.glados.backrooms.registry;

import com.glados.backrooms.util.ModConstants;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Registro de Block Entities del mod.
 * <p>
 * Sin entradas todavia. Se anadiran cuando un modulo futuro requiera que
 * algun bloque (por ejemplo, el Almacen de Mentes) guarde estado propio.
 */
public final class ModBlockEntities {

    private ModBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ModConstants.MOD_ID);
}
