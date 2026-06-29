package com.glados.backrooms.registry;

import com.glados.backrooms.util.ModConstants;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Registro de particulas del mod.
 * <p>
 * Sin entradas todavia. Se anadiran cuando los modulos de corrupcion o
 * portales requieran efectos visuales propios.
 */
public final class ModParticles {

    private ModParticles() {
    }

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ModConstants.MOD_ID);
}
