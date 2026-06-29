package com.glados.backrooms.memory;

import com.glados.backrooms.BackroomsMod;
import com.glados.backrooms.util.ModConstants;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Biblioteca central de recuerdos. El generador futuro debe leer esta clase
 * en lugar de volver a analizar el Overworld.
 */
public final class MemoryLibrary {

    private static final Map<MinecraftServer, MemoryLibrary> LIBRARIES = new HashMap<>();

    private final Path directory;
    private final Map<UUID, MemoryRegion> memories = new HashMap<>();
    private boolean loaded;

    private MemoryLibrary(Path directory) {
        this.directory = directory;
    }

    public static synchronized MemoryLibrary get(MinecraftServer server) {
        return LIBRARIES.computeIfAbsent(server, currentServer -> {
            Path root = currentServer.getWorldPath(LevelResource.ROOT);
            return new MemoryLibrary(root.resolve(ModConstants.MOD_ID).resolve("memories"));
        });
    }

    public synchronized Collection<MemoryRegion> all() {
        ensureLoaded();
        return memories.values().stream()
                .sorted(Comparator.comparing(MemoryRegion::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public synchronized Optional<MemoryRegion> find(UUID uuid) {
        ensureLoaded();
        return Optional.ofNullable(memories.get(uuid));
    }

    public synchronized Optional<MemoryRegion> findByName(String name) {
        ensureLoaded();
        return memories.values().stream()
                .filter(memory -> memory.name().equalsIgnoreCase(name))
                .findFirst();
    }

    public synchronized MemoryRegion create(ServerLevel level, String name, BoundingBox bounds) {
        ensureLoaded();
        validateOverworld(level);
        validateBounds(bounds);

        UUID uuid = UUID.randomUUID();
        Instant now = Instant.now();
        MemoryRegion memory = capture(level, uuid, name, bounds, now, now);
        memories.put(uuid, memory);
        write(memory);
        return memory;
    }

    public synchronized MemoryRegion update(ServerLevel level, UUID uuid, BoundingBox bounds) {
        ensureLoaded();
        validateOverworld(level);
        validateBounds(bounds);

        MemoryRegion existing = memories.get(uuid);
        if (existing == null) {
            throw new IllegalArgumentException("No memory exists with UUID " + uuid);
        }

        MemoryRegion updated = capture(level, existing.uuid(), existing.name(), bounds, existing.createdAt(), Instant.now());
        memories.put(updated.uuid(), updated);
        write(updated);
        return updated;
    }

    private MemoryRegion capture(ServerLevel level, UUID uuid, String name, BoundingBox bounds, Instant createdAt, Instant updatedAt) {
        BlockPos min = new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());
        List<MemoryBlockSnapshot> blocks = new ArrayList<>();

        for (BlockPos absolutePos : BlockPos.betweenClosed(
                bounds.minX(),
                bounds.minY(),
                bounds.minZ(),
                bounds.maxX(),
                bounds.maxY(),
                bounds.maxZ()
        )) {
            BlockPos immutablePos = absolutePos.immutable();
            BlockPos relativePos = immutablePos.subtract(min);
            CompoundTag stateTag = NbtUtils.writeBlockState(level.getBlockState(immutablePos));
            BlockEntity blockEntity = level.getBlockEntity(immutablePos);
            CompoundTag blockEntityTag = null;

            if (blockEntity != null) {
                blockEntityTag = blockEntity.saveWithFullMetadata();
                blockEntityTag.remove("x");
                blockEntityTag.remove("y");
                blockEntityTag.remove("z");
            }

            blocks.add(new MemoryBlockSnapshot(relativePos, stateTag, blockEntityTag));
        }

        return new MemoryRegion(
                uuid,
                name,
                level.dimension().location().toString(),
                bounds,
                createdAt,
                updatedAt,
                blocks
        );
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }

        try {
            Files.createDirectories(directory);
            try (var paths = Files.list(directory)) {
                paths.filter(path -> path.getFileName().toString().endsWith(".nbt"))
                        .forEach(this::readMemory);
            }
            loaded = true;
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not load Backrooms memory library.", exception);
        }
    }

    private void readMemory(Path path) {
        try {
            CompoundTag tag = NbtIo.readCompressed(path.toFile());
            MemoryRegion memory = MemoryRegion.load(tag);
            memories.put(memory.uuid(), memory);
        } catch (IOException | RuntimeException exception) {
            BackroomsMod.LOGGER.warn("Skipping invalid memory file: {}", path, exception);
        }
    }

    private void write(MemoryRegion memory) {
        try {
            Files.createDirectories(directory);
            NbtIo.writeCompressed(memory.save(), fileFor(memory).toFile());
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not save Backrooms memory " + memory.uuid(), exception);
        }
    }

    private Path fileFor(MemoryRegion memory) {
        return directory.resolve(memory.uuid() + "-" + sanitize(memory.name()) + ".nbt");
    }

    private static String sanitize(String name) {
        String sanitized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
        return sanitized.isBlank() ? "memory" : sanitized;
    }

    private static void validateBounds(BoundingBox bounds) {
        if (!MemorySelection.isWithinLimit(bounds)) {
            throw new IllegalArgumentException("Memory regions cannot exceed 64 x 64 x 64 blocks.");
        }
    }

    private static void validateOverworld(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            throw new IllegalArgumentException("Memories must be captured from the Overworld.");
        }
    }
}
