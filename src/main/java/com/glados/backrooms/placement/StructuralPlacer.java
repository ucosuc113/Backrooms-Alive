package com.glados.backrooms.placement;

import com.glados.backrooms.context.ChunkGenerationContext;

/** Fachada publica de la Capa 4: orquesta los sub-placers en orden. */
public final class StructuralPlacer {

    public void place(ChunkGenerationContext ctx) {
        new RoomWallPlacer().place(ctx);
        new CorridorWallPlacer().place(ctx);
        new CorridorInteriorCleaner().place(ctx);
        new FunctionalElementPlacer().place(ctx);
        new LightingPlacer().place(ctx);
    }
}
