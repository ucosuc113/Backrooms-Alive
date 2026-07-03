package com.glados.backrooms.registry;

import com.glados.backrooms.memory.MemorySelectorItem;
import com.glados.backrooms.util.ModConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registro de items del mod.
 * <p>
 * En este modulo solo se registran los objetos pedidos, sin comportamiento:
 * - "Comparador Episodico" como item independiente.
 * - El BlockItem del bloque "Almacen de Mentes" (para que el bloque pueda
 *   tenerse en el inventario y aparecer en la pestana creativa).
 */
public final class ModItems {

    private ModItems() {
    }

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, ModConstants.MOD_ID);

    public static final RegistryObject<Item> EPISODIC_COMPARATOR = ITEMS.register(
            "episodic_comparator",
            () -> new Item(new Item.Properties())
    );

    public static final RegistryObject<Item> MEMORY_SELECTOR = ITEMS.register(
            "memory_selector",
            () -> new MemorySelectorItem(new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> MIND_STORAGE_ITEM = ITEMS.register(
            "mind_storage",
            () -> new BlockItem(ModBlocks.MIND_STORAGE.get(), new Item.Properties())
    );

    public static final RegistryObject<Item> BACK_WALL_ITEM = ITEMS.register(
            "back_wall",
            () -> new BlockItem(ModBlocks.BACK_WALL.get(), new Item.Properties())
    );

    public static final RegistryObject<Item> BACK_FLOOR_ITEM = ITEMS.register(
            "back_floor",
            () -> new BlockItem(ModBlocks.BACK_FLOOR.get(), new Item.Properties())
    );

    public static final RegistryObject<Item> BACK_CEILING_ITEM = ITEMS.register(
            "back_ceiling",
            () -> new BlockItem(ModBlocks.BACK_CEILING.get(), new Item.Properties())
    );

    public static final RegistryObject<Item> BACK_LIGHT_ITEM = ITEMS.register(
            "back_light",
            () -> new BlockItem(ModBlocks.BACK_LIGHT.get(), new Item.Properties())
    );
}
