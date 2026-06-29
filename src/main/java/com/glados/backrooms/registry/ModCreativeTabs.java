package com.glados.backrooms.registry;

import com.glados.backrooms.util.ModConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registro de pestanas (Creative Mode Tabs) del mod.
 * <p>
 * Solo se crea una pestana base que contiene los objetos ya registrados
 * en {@link ModBlocks} y {@link ModItems}. No hay logica adicional.
 */
public final class ModCreativeTabs {

    private ModCreativeTabs() {
    }

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ModConstants.MOD_ID);

    public static final RegistryObject<CreativeModeTab> BACKROOMS_TAB = CREATIVE_MODE_TABS.register(
            "backrooms_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("creativetab." + ModConstants.MOD_ID + ".backrooms_tab"))
                    .icon(() -> new ItemStack(ModItems.EPISODIC_COMPARATOR.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.MIND_STORAGE_ITEM.get());
                        output.accept(ModItems.EPISODIC_COMPARATOR.get());
                        output.accept(ModItems.MEMORY_SELECTOR.get());
                    })
                    .build()
    );
}
