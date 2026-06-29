package com.glados.backrooms.registry;

import com.glados.backrooms.util.ModConstants;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Registro de menus (GUI con contenedor) del mod.
 * <p>
 * Sin entradas todavia. Solo se anadiran si algun modulo futuro (por
 * ejemplo, el Almacen de Mentes) necesita una interfaz propia.
 */
public final class ModMenus {

    private ModMenus() {
    }

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ModConstants.MOD_ID);
}
