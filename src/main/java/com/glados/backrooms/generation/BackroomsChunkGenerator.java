package com.glados.backrooms.generation;

import com.glados.backrooms.memory.MemoryBlockSnapshot;
import com.glados.backrooms.memory.MemoryConnectorAnalyzer;
import com.glados.backrooms.memory.MemoryConnectorType;
import com.glados.backrooms.memory.MemoryLibrary;
import com.glados.backrooms.memory.MemoryRegion;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

public class BackroomsChunkGenerator extends ChunkGenerator {

    public static final Codec<BackroomsChunkGenerator> CODEC = Biome.CODEC
            .fieldOf("biome")
            .xmap(BackroomsChunkGenerator::new, generator -> generator.biome)
            .codec();

    private static final int FLOOR_Y = 47;
    private static final int CEILING_Y = 52;
    private static final int ROOM_MIN = 2;
    private static final int ROOM_MAX = 13;
    private static final int DOOR_MIN = 6;
    private static final int DOOR_MAX = 9;

    private final Holder<Biome> biome;

    public BackroomsChunkGenerator(Holder<Biome> biome) {
        super(new FixedBiomeSource(biome));
        this.biome = biome;
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        RandomSource random = RandomSource.create(seedFor(region.getSeed(), chunkPos.x, chunkPos.z));
        List<MemoryRegion> memories = new ArrayList<>(MemoryLibrary.get(region.getServer()).all());

        carveImpossibleHouse(chunk);
        applyRememberedFunctions(region, chunk, random, memories);
        Heightmap.primeHeightmaps(chunk, java.util.EnumSet.of(
                Heightmap.Types.WORLD_SURFACE_WG,
                Heightmap.Types.OCEAN_FLOOR_WG,
                Heightmap.Types.MOTION_BLOCKING,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES
        ));
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving carving) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return FLOOR_Y;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
        return CEILING_Y + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
        BlockState[] states = new BlockState[heightAccessor.getHeight()];
        for (int i = 0; i < states.length; i++) {
            int y = heightAccessor.getMinBuildHeight() + i;
            states[i] = y == FLOOR_Y || y == CEILING_Y ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }
        return new NoiseColumn(heightAccessor.getMinBuildHeight(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        info.add("Backrooms: function-memory generator");
    }

    private void carveImpossibleHouse(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        BlockState floor = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        BlockState ceiling = Blocks.SMOOTH_SANDSTONE.defaultBlockState();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = minX + localX;
                int z = minZ + localZ;
                set(chunk, x, FLOOR_Y, z, floor);
                set(chunk, x, CEILING_Y, z, ceiling);
                for (int y = FLOOR_Y + 1; y < CEILING_Y; y++) {
                    set(chunk, x, y, z, Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private void addInteriorLogic(ChunkAccess chunk, RandomSource random, int minX, int minZ, BlockState wall) {
        if (random.nextBoolean()) {
            int dividerX = minX + 7 + random.nextInt(3);
            for (int z = minZ + ROOM_MIN; z <= minZ + ROOM_MAX; z++) {
                if (z - minZ >= DOOR_MIN && z - minZ <= DOOR_MAX) {
                    continue;
                }
                for (int y = FLOOR_Y + 1; y < CEILING_Y; y++) {
                    set(chunk, dividerX, y, z, wall);
                }
            }
        } else {
            int dividerZ = minZ + 7 + random.nextInt(3);
            for (int x = minX + ROOM_MIN; x <= minX + ROOM_MAX; x++) {
                if (x - minX >= DOOR_MIN && x - minX <= DOOR_MAX) {
                    continue;
                }
                for (int y = FLOOR_Y + 1; y < CEILING_Y; y++) {
                    set(chunk, x, y, dividerZ, wall);
                }
            }
        }
    }

    private void addLowCeilingLights(ChunkAccess chunk, RandomSource random, int minX, int minZ) {
        if (random.nextInt(3) == 0) {
            set(chunk, minX + 7 + random.nextInt(2), CEILING_Y - 1, minZ + 7 + random.nextInt(2), FunctionalMaterialTable.stateFor(ArchitecturalFunction.LIGHT, random));
        }
    }

    private void addOccasionalStairs(ChunkAccess chunk, RandomSource random, int minX, int minZ) {
        if (random.nextInt(8) != 0) {
            return;
        }
        int baseX = minX + 5;
        int baseZ = minZ + 5;
        for (int i = 0; i < 5; i++) {
            set(chunk, baseX + i, FLOOR_Y + 1 + Math.min(i, 2), baseZ, FunctionalMaterialTable.stateFor(ArchitecturalFunction.STAIRS, random));
            set(chunk, baseX + i, FLOOR_Y + 1 + Math.min(i, 2), baseZ + 1, FunctionalMaterialTable.stateFor(ArchitecturalFunction.STAIRS, random));
        }
    }

    private void applyRememberedFunctions(WorldGenRegion region, ChunkAccess chunk, RandomSource random, List<MemoryRegion> memories) {
        if (memories.isEmpty()) {
            return;
        }

        MemoryRegion memory = memories.get(Math.floorMod((int) (random.nextLong() ^ chunk.getPos().x * 31L ^ chunk.getPos().z), memories.size()));
        var blockLookup = region.registryAccess().lookup(Registries.BLOCK).orElseThrow();
        applyRememberedLayout(chunk, memory, blockLookup);
    }

    private int applyRememberedLayout(ChunkAccess chunk, MemoryRegion memory, net.minecraft.core.HolderGetter<net.minecraft.world.level.block.Block> blockLookup) {
        int placed = 0;
        int minMemoryY = Integer.MAX_VALUE;
        int maxMemoryY = Integer.MIN_VALUE;

        for (MemoryBlockSnapshot snapshot : memory.blocks()) {
            int relativeY = snapshot.relativePos().getY();
            if (relativeY < minMemoryY) {
                minMemoryY = relativeY;
            }
            if (relativeY > maxMemoryY) {
                maxMemoryY = relativeY;
            }
        }

        int verticalSpan = Math.max(1, maxMemoryY - minMemoryY);
        int interiorHeight = Math.max(1, CEILING_Y - FLOOR_Y - 1);

        for (MemoryBlockSnapshot snapshot : memory.blocks()) {
            BlockState rememberedState = NbtUtils.readBlockState(blockLookup, snapshot.blockState());
            if (rememberedState.isAir()) {
                continue;
            }

            int localX = 1 + Math.floorMod(snapshot.relativePos().getX(), 14);
            int localZ = 1 + Math.floorMod(snapshot.relativePos().getZ(), 14);
            int worldX = chunk.getPos().getMinBlockX() + localX;
            int worldZ = chunk.getPos().getMinBlockZ() + localZ;

            int relativeY = snapshot.relativePos().getY();
            int worldY;
            if (relativeY == minMemoryY) {
                worldY = FLOOR_Y;
            } else if (relativeY == maxMemoryY) {
                worldY = CEILING_Y;
            } else if (verticalSpan <= 1) {
                worldY = FLOOR_Y + 1 + (interiorHeight / 2);
            } else {
                int normalized = (relativeY - minMemoryY - 1) * (interiorHeight - 1) / Math.max(1, verticalSpan - 1);
                worldY = FLOOR_Y + 1 + normalized;
            }

            setRememberedBlock(chunk, rememberedState, worldX, worldY, worldZ, snapshot.blockEntity());
            placed++;

            if (placed >= 180) {
                return placed;
            }
        }
        return placed;
    }

    private void setRememberedBlock(ChunkAccess chunk, BlockState rememberedState, int x, int y, int z, CompoundTag blockEntity) {
        set(chunk, x, y, z, rememberedState);
        if (blockEntity != null && MemoryFunctionClassifier.classify(rememberedState) == ArchitecturalFunction.STORAGE) {
            CompoundTag tag = blockEntity.copy();
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("z", z);
            chunk.setBlockEntityNbt(tag);
        }
    }

    private void corruptFunction(ChunkAccess chunk, RandomSource random, ArchitecturalFunction function, int x, int y, int z) {
        int intensity = 1 + random.nextInt(3);
        switch (function) {
            case STORAGE -> {
                for (int i = 0; i < intensity * 2; i++) {
                    set(chunk, x + random.nextInt(3) - 1, y + random.nextInt(2), z + random.nextInt(3) - 1, FunctionalMaterialTable.stateFor(ArchitecturalFunction.STORAGE, random));
                }
            }
            case BED -> {
                for (int i = 0; i < intensity; i++) {
                    int yy = random.nextInt(4) == 0 ? CEILING_Y - 1 : y;
                    set(chunk, x + i, yy, z + random.nextInt(2), FunctionalMaterialTable.stateFor(ArchitecturalFunction.BED, random));
                }
            }
            case DOOR -> {
                for (int i = 0; i < intensity * 2; i++) {
                    int dx = x + i - intensity;
                    set(chunk, dx, y, z, FunctionalMaterialTable.stateFor(ArchitecturalFunction.DOOR, random));
                    set(chunk, dx, y + 1, z, Blocks.BIRCH_DOOR.defaultBlockState());
                }
            }
            case WINDOW -> {
                if (random.nextBoolean()) {
                    set(chunk, x, y + 1, z, FunctionalMaterialTable.stateFor(ArchitecturalFunction.WINDOW, random));
                    set(chunk, x + 1, y + 1, z, FunctionalMaterialTable.stateFor(ArchitecturalFunction.WINDOW, random));
                }
            }
            case LIGHT, FURNITURE, STAIRS -> set(chunk, x, y, z, FunctionalMaterialTable.stateFor(function, random));
            default -> {
            }
        }
    }

    private void addGenericMisrememberedObjects(ChunkAccess chunk, RandomSource random, int minX, int minZ) {
        ArchitecturalFunction function = switch (random.nextInt(4)) {
            case 0 -> ArchitecturalFunction.STORAGE;
            case 1 -> ArchitecturalFunction.BED;
            case 2 -> ArchitecturalFunction.DOOR;
            default -> ArchitecturalFunction.FURNITURE;
        };
        corruptFunction(chunk, random, function, minX + 4 + random.nextInt(8), FLOOR_Y + 1, minZ + 4 + random.nextInt(8));
    }

    private static boolean isPerimeter(int localX, int localZ) {
        return localX == 0 || localX == 15 || localZ == 0 || localZ == 15;
    }

    private static boolean isSharedDoorOpening(int localX, int localZ) {
        return (localX == 0 || localX == 15) && localZ >= DOOR_MIN && localZ <= DOOR_MAX
                || (localZ == 0 || localZ == 15) && localX >= DOOR_MIN && localX <= DOOR_MAX;
    }

    private static void set(ChunkAccess chunk, int x, int y, int z, BlockState state) {
        chunk.setBlockState(new BlockPos(x, y, z), state, false);
    }

    private static long seedFor(long worldSeed, int chunkX, int chunkZ) {
        long seed = worldSeed;
        seed ^= chunkX * 341873128712L;
        seed ^= chunkZ * 132897987541L;
        seed ^= 0x5DEECE66DL;
        return seed;
    }
}
