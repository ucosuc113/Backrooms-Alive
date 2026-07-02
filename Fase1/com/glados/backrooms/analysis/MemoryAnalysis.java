package com.glados.backrooms.analysis;

import com.glados.backrooms.classification.FunctionalRole;
import java.util.Map;

/**
 * Conocimiento de estilo extraido de una memoria NBT (Documento de Diseno,
 * Capa 0, seccion 10.1). Producido una unica vez por {@link MemoryAnalyzer}
 * al cargar el servidor; inmutable desde ese instante. Ninguna capa
 * superior (district, graph, context, placement, degradation) debe
 * modificarlo jamas — esto sostiene el Invariante 6 del Documento de
 * Diseno.
 */
public record MemoryAnalysis(String id, Map<WallRole, WallPrototype> wallPrototypes,
                              CornerPrototype cornerPrototype, OpeningPrototype openingPrototype,
                              Map<FunctionalRole, RoomPrototype> roomPrototypes,
                              StyleFingerprint styleFingerprint, boolean isUsableAsPrimary) {

    public MemoryAnalysis {
        wallPrototypes = Map.copyOf(wallPrototypes);
        roomPrototypes = Map.copyOf(roomPrototypes);
    }

    public WallPrototype wallPrototypeFor(WallRole role) {
        return wallPrototypes.get(role);
    }

    public RoomPrototype roomPrototypeFor(FunctionalRole role) {
        return roomPrototypes.get(role);
    }
}
