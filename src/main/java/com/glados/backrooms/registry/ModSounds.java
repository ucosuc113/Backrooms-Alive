package com.glados.backrooms.registry;

import com.glados.backrooms.util.ModConstants;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Registro de sonidos del mod.
 * <p>
 * Sin entradas todavia. Se anadiran en los modulos que requieran sonidos
 * propios (ambiente de Backrooms, corrupcion, portales, etc.).
 */
public final class ModSounds {

    private ModSounds() {
    }

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ModConstants.MOD_ID);
}
