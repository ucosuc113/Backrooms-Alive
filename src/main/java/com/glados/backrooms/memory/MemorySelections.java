package com.glados.backrooms.memory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Selecciones temporales por jugador. Los recuerdos persistentes viven en
 * {@link MemoryLibrary}; esta clase solo guarda las dos esquinas activas.
 */
public final class MemorySelections {

    private static final Map<UUID, MemorySelection> SELECTIONS = new ConcurrentHashMap<>();

    private MemorySelections() {
    }

    public static MemorySelection setFirst(ServerPlayer player, BlockPos pos) {
        return SELECTIONS.compute(player.getUUID(), (uuid, current) -> {
            MemorySelection selection = current == null ? new MemorySelection(null, null) : current;
            return selection.withFirst(pos.immutable());
        });
    }

    public static MemorySelection setSecond(ServerPlayer player, BlockPos pos) {
        return SELECTIONS.compute(player.getUUID(), (uuid, current) -> {
            MemorySelection selection = current == null ? new MemorySelection(null, null) : current;
            return selection.withSecond(pos.immutable());
        });
    }

    public static Optional<MemorySelection> get(ServerPlayer player) {
        MemorySelection selection = SELECTIONS.get(player.getUUID());
        return selection != null && selection.isComplete() ? Optional.of(selection) : Optional.empty();
    }

    public static void clear(ServerPlayer player) {
        SELECTIONS.remove(player.getUUID());
    }
}
