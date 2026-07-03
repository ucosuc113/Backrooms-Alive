package com.glados.backrooms.generation;

import com.glados.backrooms.analysis.MemoryAnalysisRepository;
import com.glados.backrooms.memory.MemoryBlockSnapshot;
import com.glados.backrooms.memory.MemoryLibrary;
import com.glados.backrooms.memory.MemoryRegion;
import com.glados.backrooms.context.ChunkContextBuilder;
import com.glados.backrooms.degradation.DegradationPipeline;
import com.glados.backrooms.district.DistrictLookup;
import com.glados.backrooms.graph.RoomGraph;
import com.glados.backrooms.graph.RoomGraphCache;
import com.glados.backrooms.graph.RoomNode;
import com.glados.backrooms.util.VoronoiLookup;
import com.glados.backrooms.placement.StructuralPlacer;
import com.mojang.serialization.Codec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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
import com.glados.backrooms.registry.ModBlocks;
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

    public static final int FLOOR_Y = 47;
    public static final int CEILING_Y = 52;
    private static final int ROOM_MIN = 2;
    private static final int ROOM_MAX = 13;
    private static final int DOOR_MIN = 6;
    private static final int DOOR_MAX = 9;

    private final Holder<Biome> biome;
    private volatile boolean pipelineInitialized = false;
    private DistrictLookup districtLookup;
    private RoomGraphCache graphCache;
    private ChunkContextBuilder contextBuilder;
    private DegradationPipeline degradationPipeline;
    private final StructuralPlacer placer = new StructuralPlacer();

    public BackroomsChunkGenerator(Holder<Biome> biome) {
        super(new FixedBiomeSource(biome));
        this.biome = biome;
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    // FIX 5: carveImpossibleHouse movido aquí, ANTES de buildSurface.
    // Antes estaba en buildSurface, lo que significaba que cuando chunk A escribía
    // en el chunk B vecino, luego B llamaba carveImpossibleHouse y borraba todo.
    // fillFromNoise opera sólo sobre el chunk propio y no tiene acceso a vecinos.
    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender,
            RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        carveImpossibleHouse(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager,
            RandomState randomState, ChunkAccess chunk) {
        initPipeline(region.getSeed());
        ChunkPos chunkPos = chunk.getPos();
        RandomSource random = RandomSource.create(seedFor(region.getSeed(), chunkPos.x, chunkPos.z));
        var ctx = contextBuilder.build(chunk, region, random);
        placer.place(ctx);
        degradationPipeline.apply(ctx);

        Heightmap.primeHeightmaps(chunk, java.util.EnumSet.of(
                Heightmap.Types.WORLD_SURFACE_WG,
                Heightmap.Types.OCEAN_FLOOR_WG,
                Heightmap.Types.MOTION_BLOCKING,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES
        ));
    }

    void initPipeline(long worldSeed) {
        if (pipelineInitialized) return;
        synchronized (this) {
            if (pipelineInitialized) return;
            var repo = MemoryAnalysisRepository.getInstance();
            this.districtLookup = new DistrictLookup(worldSeed);
            this.graphCache = new RoomGraphCache(districtLookup, repo);
            this.contextBuilder = new ChunkContextBuilder(worldSeed, districtLookup, graphCache, repo);
            this.degradationPipeline = new DegradationPipeline(worldSeed);
            this.pipelineInitialized = true;
        }
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState,
            BiomeManager biomeManager, StructureManager structureManager,
            ChunkAccess chunk, GenerationStep.Carving carving) {
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
    public int getBaseHeight(int x, int z, Heightmap.Types type,
            LevelHeightAccessor heightAccessor, RandomState randomState) {
        return CEILING_Y + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor,
            RandomState randomState) {
        BlockState[] states = new BlockState[heightAccessor.getHeight()];
        for (int i = 0; i < states.length; i++) {
            int y = heightAccessor.getMinBuildHeight() + i;
            states[i] = y == FLOOR_Y
                ? ModBlocks.BACK_FLOOR.get().defaultBlockState()
                : (y == CEILING_Y ? ModBlocks.BACK_CEILING.get().defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        return new NoiseColumn(heightAccessor.getMinBuildHeight(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        info.add("Backrooms: function-memory generator");
    }

    // ── Construcción base ───────────────────────────────────────────────────────

    private void carveImpossibleHouse(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        BlockState floor = ModBlocks.BACK_FLOOR.get().defaultBlockState();
        BlockState ceiling = ModBlocks.BACK_CEILING.get().defaultBlockState();

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

    // ── Detalles de sala (antes métodos muertos) ────────────────────────────────

    private void addInteriorLogic(ChunkAccess chunk, RandomSource random,
            int minX, int minZ, BlockState wall) {
        if (random.nextBoolean()) {
            int dividerX = minX + 7 + random.nextInt(3);
            for (int z = minZ + ROOM_MIN; z <= minZ + ROOM_MAX; z++) {
                if (z - minZ >= DOOR_MIN && z - minZ <= DOOR_MAX) continue;
                for (int y = FLOOR_Y + 1; y < CEILING_Y; y++) {
                    set(chunk, dividerX, y, z, wall);
                }
            }
        } else {
            int dividerZ = minZ + 7 + random.nextInt(3);
            for (int x = minX + ROOM_MIN; x <= minX + ROOM_MAX; x++) {
                if (x - minX >= DOOR_MIN && x - minX <= DOOR_MAX) continue;
                for (int y = FLOOR_Y + 1; y < CEILING_Y; y++) {
                    set(chunk, x, y, dividerZ, wall);
                }
            }
        }
    }

    private void addLowCeilingLights(ChunkAccess chunk, RandomSource random,
            int minX, int minZ) {
        if (random.nextInt(3) == 0) {
            set(chunk,
                minX + 7 + random.nextInt(2),
                CEILING_Y - 1,
                minZ + 7 + random.nextInt(2),
                ModBlocks.BACK_LIGHT.get().defaultBlockState());
        }
    }

    private void addOccasionalStairs(ChunkAccess chunk, RandomSource random,
            int minX, int minZ) {
        if (random.nextInt(8) != 0) return;
        int baseX = minX + 5;
        int baseZ = minZ + 5;
        for (int i = 0; i < 5; i++) {
            set(chunk, baseX + i, FLOOR_Y + 1 + Math.min(i, 2), baseZ,
                    Blocks.STONE_BRICKS.defaultBlockState());
            set(chunk, baseX + i, FLOOR_Y + 1 + Math.min(i, 2), baseZ + 1,
                    Blocks.STONE_BRICKS.defaultBlockState());
        }
    }

    public record PortalWallResult(BlockPos base, net.minecraft.core.Direction widthDirection,
                                   net.minecraft.core.Direction normalDirection) {}

    public static PortalWallResult findPortalWallBase(net.minecraft.server.level.ServerLevel backroomsLevel,
                                                     int worldX, int worldZ,
                                                     int frameWidth, int frameHeight) {
        net.minecraft.world.level.chunk.ChunkGenerator generator = backroomsLevel.getChunkSource().getGenerator();
        if (!(generator instanceof BackroomsChunkGenerator bcg)) {
            return new PortalWallResult(new BlockPos(worldX, FLOOR_Y + 1, worldZ),
                    net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.SOUTH);
        }
        bcg.initPipeline(backroomsLevel.getSeed());

        VoronoiLookup.CellDistance cell = bcg.districtLookup.nearestDistrict(worldX, worldZ);
        RoomGraph graph = bcg.graphCache.getOrGenerate(cell.cellX(), cell.cellZ());

        RoomNode nearest = null;
        double bestDist = Double.MAX_VALUE;
        for (RoomNode room : graph.rooms()) {
            double d = Math.hypot(room.centerX() - worldX, room.centerZ() - worldZ);
            if (d < bestDist) {
                bestDist = d;
                nearest = room;
            }
        }
        if (nearest == null) {
            // expand search to neighboring district cells
            outer: for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    RoomGraph g = bcg.graphCache.getOrGenerate(cell.cellX() + dx, cell.cellZ() + dz);
                    for (RoomNode room : g.rooms()) {
                        double d = Math.hypot(room.centerX() - worldX, room.centerZ() - worldZ);
                        if (d < bestDist) {
                            bestDist = d;
                            nearest = room;
                        }
                    }
                    if (nearest != null) break outer;
                }
            }
            if (nearest == null) {
                throw new IllegalStateException("No room found near portal coordinates: " + worldX + "," + worldZ);
            }
        }

        int cx = (int) nearest.centerX();
        int cz = (int) nearest.centerZ();
        int dN = cz - nearest.minZ();
        int dS = nearest.maxZ() - cz;
        int dO = cx - nearest.minX();
        int dE = nearest.maxX() - cx;
        int minDist = Math.min(Math.min(dN, dS), Math.min(dO, dE));

        BlockPos base;
        net.minecraft.core.Direction widthDir;
        net.minecraft.core.Direction normalDir;

        if (minDist == dN) {
            int midX = Math.max(nearest.minX() + 2,
                    Math.min(nearest.maxX() - frameWidth - 1, cx - frameWidth / 2));
            base = new BlockPos(midX, FLOOR_Y + 1, nearest.minZ());
            widthDir = net.minecraft.core.Direction.EAST;
            normalDir = net.minecraft.core.Direction.SOUTH;
        } else if (minDist == dS) {
            int midX = Math.max(nearest.minX() + 2,
                    Math.min(nearest.maxX() - frameWidth - 1, cx - frameWidth / 2));
            base = new BlockPos(midX, FLOOR_Y + 1, nearest.maxZ());
            widthDir = net.minecraft.core.Direction.EAST;
            normalDir = net.minecraft.core.Direction.NORTH;
        } else if (minDist == dO) {
            int midZ = Math.max(nearest.minZ() + 2,
                    Math.min(nearest.maxZ() - frameWidth - 1, cz - frameWidth / 2));
            base = new BlockPos(nearest.minX(), FLOOR_Y + 1, midZ);
            widthDir = net.minecraft.core.Direction.SOUTH;
            normalDir = net.minecraft.core.Direction.EAST;
        } else {
            int midZ = Math.max(nearest.minZ() + 2,
                    Math.min(nearest.maxZ() - frameWidth - 1, cz - frameWidth / 2));
            base = new BlockPos(nearest.maxX(), FLOOR_Y + 1, midZ);
            widthDir = net.minecraft.core.Direction.SOUTH;
            normalDir = net.minecraft.core.Direction.WEST;
        }
        return new PortalWallResult(base, widthDir, normalDir);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private int mapCoordinate(int value, int minValue, int maxValue,
            int targetMin, int targetMax) {
        int span = maxValue - minValue;
        if (span <= 1) return targetMin;
        return targetMin + (value - minValue) * (targetMax - targetMin) / span;
    }

    private BlockState pickStructuralState(SurfaceCandidate surface,
            Map<BlockPos, BlockState> rememberedStatesByPos) {
        for (BlockPos pos : surface.cells()) {
            BlockState state = rememberedStatesByPos.get(pos);
            if (state != null) return state;
        }
        return ModBlocks.BACK_WALL.get().defaultBlockState();
    }

    private boolean isStructuralMaterial(BlockState state) {
        if (state.isAir()) return false;
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path = key == null ? "" : key.getPath().toLowerCase();
        return path.contains("plank") || path.contains("wood") || path.contains("log")
                || path.contains("stone") || path.contains("sandstone") || path.contains("cobble")
                || path.contains("brick") || path.contains("concrete") || path.contains("terracotta")
                || path.contains("deepslate") || path.contains("granite") || path.contains("diorite")
                || path.contains("andesite") || path.contains("wool");
    }

    private int clamp(int minimum, int lower, int upper, int value) {
        return Math.max(lower, Math.min(upper, Math.max(minimum, value)));
    }

    private boolean isInCurrentChunk(ChunkAccess chunk, int x, int z) {
        return isInCurrentChunkStatic(chunk, x, z);
    }

    private static boolean isInCurrentChunkStatic(ChunkAccess chunk, int x, int z) {
        ChunkPos chunkPos = chunk.getPos();
        return x >= chunkPos.getMinBlockX() && x <= chunkPos.getMaxBlockX()
                && z >= chunkPos.getMinBlockZ() && z <= chunkPos.getMaxBlockZ();
    }

    // FIX 1 (parte 2): setInWorld ya NO escribe en chunks vecinos.
    // Antes: region.getChunk(x >> 4, z >> 4) escribía en el vecino, que luego
    // al generarse llamaba carveImpossibleHouse() y borraba todo lo escrito.
    // Ahora: writes fuera del chunk actual se ignoran silenciosamente.
    // Los vecinos generan su propio contenido de forma independiente.
    private static ChunkAccess setInWorld(WorldGenRegion region, ChunkAccess chunk,
            int x, int y, int z, BlockState state) {
        if (!isInCurrentChunkStatic(chunk, x, z)) {
            return null; // fuera del chunk actual → ignorar
        }
        set(chunk, x, y, z, state);
        return chunk;
    }

    private void setRememberedBlock(WorldGenRegion region, ChunkAccess chunk,
            BlockState rememberedState, int x, int y, int z, CompoundTag blockEntity) {
        ChunkAccess targetChunk = setInWorld(region, chunk, x, y, z, rememberedState);
        if (targetChunk == null || blockEntity == null) return;
        if (MemoryFunctionClassifier.classify(rememberedState) == ArchitecturalFunction.STORAGE) {
            CompoundTag tag = blockEntity.copy();
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("z", z);
            targetChunk.setBlockEntityNbt(tag);
        }
    }

    // ── Corrupción / misremembering ─────────────────────────────────────────────

    private void corruptFunction(ChunkAccess chunk, RandomSource random,
            ArchitecturalFunction function, int x, int y, int z) {
        int intensity = 1 + random.nextInt(3);
        switch (function) {
            case STORAGE: {
                for (int i = 0; i < intensity * 2; i++) {
                    int tx = x + random.nextInt(3) - 1;
                    int ty = y + random.nextInt(2);
                    int tz = z + random.nextInt(3) - 1;
                    if (isWallColumn(chunk, tx, tz)) continue;
                    set(chunk, tx, ty, tz, FunctionalMaterialTable.stateFor(ArchitecturalFunction.STORAGE, random));
                }
                break;
            }
            case BED: {
                for (int i = 0; i < intensity; i++) {
                    int tx = x + i;
                    int yy = random.nextInt(4) == 0 ? CEILING_Y - 1 : y;
                    int tz = z + random.nextInt(2);
                    if (isWallColumn(chunk, tx, tz)) continue;
                    set(chunk, tx, yy, tz, FunctionalMaterialTable.stateFor(ArchitecturalFunction.BED, random));
                }
                break;
            }
            case DOOR: {
                for (int i = 0; i < intensity * 2; i++) {
                    int dx = x + i - intensity;
                    if (isWallColumn(chunk, dx, z)) continue;
                    set(chunk, dx, y,     z, FunctionalMaterialTable.stateFor(ArchitecturalFunction.DOOR, random));
                    set(chunk, dx, y + 1, z, Blocks.BIRCH_DOOR.defaultBlockState());
                }
                break;
            }
            case WINDOW: {
                if (random.nextBoolean()) {
                    if (!isWallColumn(chunk, x, z)) set(chunk, x,     y + 1, z, FunctionalMaterialTable.stateFor(ArchitecturalFunction.WINDOW, random));
                    if (!isWallColumn(chunk, x + 1, z)) set(chunk, x + 1, y + 1, z, FunctionalMaterialTable.stateFor(ArchitecturalFunction.WINDOW, random));
                }
                break;
            }
            case LIGHT:
            case FURNITURE:
            case STAIRS: {
                if (!isWallColumn(chunk, x, z)) set(chunk, x, y, z, FunctionalMaterialTable.stateFor(function, random));
                break;
            }
            default: {
                break;
            }
        }
    }

    private void addGenericMisrememberedObjects(ChunkAccess chunk, RandomSource random,
            int minX, int minZ) {
        ArchitecturalFunction function = switch (random.nextInt(4)) {
            case 0 -> ArchitecturalFunction.STORAGE;
            case 1 -> ArchitecturalFunction.BED;
            case 2 -> ArchitecturalFunction.DOOR;
            default -> ArchitecturalFunction.FURNITURE;
        };
        corruptFunction(chunk, random, function,
                minX + 4 + random.nextInt(8), FLOOR_Y + 1, minZ + 4 + random.nextInt(8));
    }

    // Reservados para futura conectividad entre chunks (puertas en bordes)
    private static boolean isPerimeter(int localX, int localZ) {
        return localX == 0 || localX == 15 || localZ == 0 || localZ == 15;
    }

    private static boolean isSharedDoorOpening(int localX, int localZ) {
        return (localX == 0 || localX == 15) && localZ >= DOOR_MIN && localZ <= DOOR_MAX
                || (localZ == 0 || localZ == 15) && localX >= DOOR_MIN && localX <= DOOR_MAX;
    }

    // FIX global: set() ahora valida bounds antes de escribir.
    // corruptFunction() puede desplazar coords hasta ±3 bloques, lo que sin
    // esta guardia causaría un IllegalArgumentException en chunk.setBlockState().
    private static void set(ChunkAccess chunk, int x, int y, int z, BlockState state) {
        if (!isInCurrentChunkStatic(chunk, x, z)) return;
        if (y < chunk.getMinBuildHeight() || y >= chunk.getMaxBuildHeight()) return;
        chunk.setBlockState(new BlockPos(x, y, z), state, false);
    }

    public static boolean isWallColumn(ChunkAccess chunk, int x, int z) {
        for (int y = FLOOR_Y + 1; y < CEILING_Y; y++) {
            BlockState s = chunk.getBlockState(new BlockPos(x, y, z));
            if (s.is(ModBlocks.BACK_WALL.get())) return true;
        }
        return false;
    }

    private static long seedFor(long worldSeed, int chunkX, int chunkZ) {
        long seed = worldSeed;
        seed ^= chunkX * 341873128712L;
        seed ^= chunkZ * 132897987541L;
        seed ^= 0x5DEECE66DL;
        return seed;
    }

    // ── Records internos ────────────────────────────────────────────────────────

    private record SurfaceCandidate(List<BlockPos> cells,
            int minX, int maxX, int minZ, int maxZ, int level) {
        int spanX() { return Math.max(1, maxX - minX + 1); }
        int spanZ() { return Math.max(1, maxZ - minZ + 1); }
    }

    private record DetectedWall(List<BlockPos> cells,
            int minX, int maxX, int minY, int maxY, int anchor, boolean xAligned) {
        int minZ()     { return anchor; }
        int minXValue(){ return minX;   }
    }
}