package com.glados.backrooms.degradation;

import com.glados.backrooms.context.ChunkGenerationContext;

/** Fachada publica de la Capa 5: ejecuta los degradadores en orden. */
public final class DegradationPipeline {

    private final long worldSeed;

    public DegradationPipeline(long worldSeed) {
        this.worldSeed = worldSeed;
    }

    public void apply(ChunkGenerationContext ctx) {
        // Structural and material degraders were removed for safety: walls must stay solid.
        new FunctionalDegrader(worldSeed).apply(ctx);
        new ConnectivitySafeguard().apply(ctx);
    }
}
