package com.glados.backrooms.generation;

import com.glados.backrooms.memory.MemoryBlockSnapshot;
import com.glados.backrooms.memory.MemoryLibrary;
import com.glados.backrooms.memory.MemoryRegion;
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
        ChunkPos chunkPos = chunk.getPos();
        RandomSource random = RandomSource.create(seedFor(region.getSeed(), chunkPos.x, chunkPos.z));
        List<MemoryRegion> memories = new ArrayList<>(MemoryLibrary.get(region.getServer()).all());
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();

        // carveImpossibleHouse ya no va aquí (movido a fillFromNoise)
        applyRememberedFunctions(region, chunk, random, memories);

        // FIX 6: Métodos muertos ahora conectados al pipeline de generación.
        // Antes definidos pero nunca llamados — toda la lógica de corrupción/
        // misremembering nunca se ejecutaba.
        if (memories.isEmpty()) {
            // Sin memorias: fallback con sala genérica + objetos misremembered
            BlockState wallState = FunctionalMaterialTable.stateFor(ArchitecturalFunction.WALL, random);
            addInteriorLogic(chunk, random, minX, minZ, wallState);
            addGenericMisrememberedObjects(chunk, random, minX, minZ);
        }
        // Luces y escaleras aplican siempre (afectan volumen interior, nunca salen del chunk)
        addLowCeilingLights(chunk, random, minX, minZ);
        addOccasionalStairs(chunk, random, minX, minZ);

        Heightmap.primeHeightmaps(chunk, java.util.EnumSet.of(
                Heightmap.Types.WORLD_SURFACE_WG,
                Heightmap.Types.OCEAN_FLOOR_WG,
                Heightmap.Types.MOTION_BLOCKING,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES
        ));
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
            states[i] = y == FLOOR_Y || y == CEILING_Y
                    ? Blocks.SMOOTH_SANDSTONE.defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
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
                    FunctionalMaterialTable.stateFor(ArchitecturalFunction.LIGHT, random));
        }
    }

    private void addOccasionalStairs(ChunkAccess chunk, RandomSource random,
            int minX, int minZ) {
        if (random.nextInt(8) != 0) return;
        int baseX = minX + 5;
        int baseZ = minZ + 5;
        for (int i = 0; i < 5; i++) {
            set(chunk, baseX + i, FLOOR_Y + 1 + Math.min(i, 2), baseZ,
                    FunctionalMaterialTable.stateFor(ArchitecturalFunction.STAIRS, random));
            set(chunk, baseX + i, FLOOR_Y + 1 + Math.min(i, 2), baseZ + 1,
                    FunctionalMaterialTable.stateFor(ArchitecturalFunction.STAIRS, random));
        }
    }

    // ── Pipeline de memoria ─────────────────────────────────────────────────────

    private void applyRememberedFunctions(WorldGenRegion region, ChunkAccess chunk,
            RandomSource random, List<MemoryRegion> memories) {
        if (memories.isEmpty()) return;

        MemoryRegion memory = memories.get(Math.floorMod(
                (int) (random.nextLong() ^ chunk.getPos().x * 31L ^ chunk.getPos().z),
                memories.size()));
        var blockLookup = region.registryAccess().lookup(Registries.BLOCK).orElseThrow();
        adaptRememberedStructure(region, chunk, random, memory, blockLookup);
    }

    private void adaptRememberedStructure(WorldGenRegion region, ChunkAccess chunk,
            RandomSource random, MemoryRegion memory,
            net.minecraft.core.HolderGetter<net.minecraft.world.level.block.Block> blockLookup) {

        List<BlockPos> structuralSeeds = new ArrayList<>();
        List<MemoryBlockSnapshot> snapshots = memory.blocks();
        Map<BlockPos, BlockState> rememberedStatesByPos = new HashMap<>();
        List<BlockPos> functionalSeeds = new ArrayList<>();
        int minMemoryX = Integer.MAX_VALUE, maxMemoryX = Integer.MIN_VALUE;
        int minMemoryY = Integer.MAX_VALUE, maxMemoryY = Integer.MIN_VALUE;
        int minMemoryZ = Integer.MAX_VALUE, maxMemoryZ = Integer.MIN_VALUE;

        for (MemoryBlockSnapshot snapshot : snapshots) {
            BlockPos pos = snapshot.relativePos();
            minMemoryX = Math.min(minMemoryX, pos.getX());
            maxMemoryX = Math.max(maxMemoryX, pos.getX());
            minMemoryY = Math.min(minMemoryY, pos.getY());
            maxMemoryY = Math.max(maxMemoryY, pos.getY());
            minMemoryZ = Math.min(minMemoryZ, pos.getZ());
            maxMemoryZ = Math.max(maxMemoryZ, pos.getZ());

            BlockState rememberedState = NbtUtils.readBlockState(blockLookup, snapshot.blockState());
            rememberedStatesByPos.put(snapshot.relativePos(), rememberedState);
            if (rememberedState.isAir()) continue;
            if (isStructuralMaterial(rememberedState)) structuralSeeds.add(pos);
            ArchitecturalFunction function = MemoryFunctionClassifier.classify(rememberedState);
            if (function != ArchitecturalFunction.AIR
                    && function != ArchitecturalFunction.WALL
                    && function != ArchitecturalFunction.FLOOR
                    && function != ArchitecturalFunction.CEILING) {
                functionalSeeds.add(pos);
            }
        }

        if (structuralSeeds.isEmpty()) return;

        Set<BlockPos> structuralSet = new HashSet<>(structuralSeeds);
        List<SurfaceCandidate> surfaces = findHorizontalSurfaces(structuralSet, random);
        SurfaceCandidate floorSurface = surfaces.stream()
                .min(Comparator.comparingInt(SurfaceCandidate::level)).orElse(null);
        SurfaceCandidate ceilingSurface = surfaces.stream()
                .max(Comparator.comparingInt(SurfaceCandidate::level)).orElse(null);
        if (floorSurface == null || ceilingSurface == null) return;

        // FIX 2: Estructura acotada a un chunk (máx 14) con origen anclado al chunk.
        // Antes: originX = chunkMinX + random.nextInt(96 - w) - 48
        // → podía caer 48 bloques FUERA del chunk, dejándolo vacío.
        int targetWidth = clamp(4, 4, 14, floorSurface.spanX() + random.nextInt(5) - 2);
        int targetDepth = clamp(4, 4, 14, floorSurface.spanZ() + random.nextInt(5) - 2);
        int chunkMinX = chunk.getPos().getMinBlockX();
        int chunkMinZ = chunk.getPos().getMinBlockZ();
        int maxOffsetX = Math.max(0, 16 - targetWidth);
        int maxOffsetZ = Math.max(0, 16 - targetDepth);
        int originX = chunkMinX + (maxOffsetX > 0 ? random.nextInt(maxOffsetX) : 0);
        int originZ = chunkMinZ + (maxOffsetZ > 0 ? random.nextInt(maxOffsetZ) : 0);

        BlockState floorState = pickStructuralState(floorSurface, rememberedStatesByPos);
        BlockState ceilingState = pickStructuralState(ceilingSurface, rememberedStatesByPos);
        placeSurfaceRegion(region, chunk, floorSurface, originX, originZ,
                targetWidth, targetDepth, FLOOR_Y, floorState);
        placeSurfaceRegion(region, chunk, ceilingSurface, originX, originZ,
                targetWidth, targetDepth, CEILING_Y, ceilingState);

        List<DetectedWall> walls = detectWalls(structuralSet, floorSurface, ceilingSurface, random);
        for (DetectedWall wall : walls) {
            // FIX 4: Pasamos los bounds de memoria para que placeWall proyecte
            // el anchor sobre el eje correcto
            placeWall(region, chunk, wall, originX, originZ, targetWidth, targetDepth,
                    FLOOR_Y, CEILING_Y, random, rememberedStatesByPos,
                    minMemoryX, maxMemoryX, minMemoryZ, maxMemoryZ);
        }
        connectCorners(region, chunk, originX, originZ, targetWidth, targetDepth,
                FLOOR_Y, CEILING_Y, random);

        placeFunctionalBlocks(region, chunk, random, snapshots, blockLookup,
                originX, originZ, targetWidth, targetDepth, FLOOR_Y, CEILING_Y,
                minMemoryX, maxMemoryX, minMemoryY, maxMemoryY, minMemoryZ, maxMemoryZ);
    }

    // ── Detección de superficies horizontales ───────────────────────────────────

    private List<SurfaceCandidate> findHorizontalSurfaces(Set<BlockPos> structuralPositions,
            RandomSource random) {
        List<SurfaceCandidate> surfaces = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> seeds = new ArrayList<>(structuralPositions);
        Collections.shuffle(seeds, new java.util.Random(random.nextLong()));

        for (BlockPos seed : seeds) {
            if (visited.contains(seed)) continue;
            Deque<BlockPos> queue = new ArrayDeque<>();
            List<BlockPos> region = new ArrayList<>();
            visited.add(seed);
            queue.add(seed);
            while (!queue.isEmpty()) {
                BlockPos current = queue.removeFirst();
                region.add(current);
                for (BlockPos neighbor : List.of(
                        current.east(), current.west(), current.north(), current.south())) {
                    // FIX 3a: Solo traversal HORIZONTAL (mismo Y).
                    // Antes: sin restricción de Y → el BFS conectaba suelo con techo
                    // a través de paredes, dando un único componente gigante.
                    if (structuralPositions.contains(neighbor)
                            && !visited.contains(neighbor)
                            && neighbor.getY() == current.getY()) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }

            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            // FIX 3b: Con la restricción same-Y, todos los bloques de la región
            // tienen el mismo Y que seed → level = seed.getY() es siempre correcto.
            // Antes: `level = pos.getY()` se sobreescribía en cada iteración y
            // quedaba con el Y del último bloque procesado (orden BFS indeterminado).
            int level = seed.getY();
            for (BlockPos pos : region) {
                minX = Math.min(minX, pos.getX());
                maxX = Math.max(maxX, pos.getX());
                minZ = Math.min(minZ, pos.getZ());
                maxZ = Math.max(maxZ, pos.getZ());
                // level ya no se sobreescribe aquí
            }
            if (maxX - minX + 1 > 2 && maxZ - minZ + 1 > 2) {
                surfaces.add(new SurfaceCandidate(region, minX, maxX, minZ, maxZ, level));
            }
        }
        return surfaces;
    }

    // ── Detección de paredes ────────────────────────────────────────────────────

    private List<DetectedWall> detectWalls(Set<BlockPos> structuralPositions,
            SurfaceCandidate floorSurface, SurfaceCandidate ceilingSurface,
            RandomSource random) {
        Set<BlockPos> wallCandidates = new HashSet<>(structuralPositions);
        wallCandidates.removeAll(floorSurface.cells());
        wallCandidates.removeAll(ceilingSurface.cells());

        List<DetectedWall> walls = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos seed : new ArrayList<>(wallCandidates)) {
            if (visited.contains(seed)) continue;
            Deque<BlockPos> queue = new ArrayDeque<>();
            List<BlockPos> region = new ArrayList<>();
            visited.add(seed);
            queue.add(seed);
            while (!queue.isEmpty()) {
                BlockPos current = queue.removeFirst();
                region.add(current);
                for (BlockPos neighbor : List.of(
                        current.east(), current.west(), current.north(), current.south(),
                        current.above(), current.below())) {
                    if (wallCandidates.contains(neighbor) && !visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }

            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            int anchorX = seed.getX(), anchorZ = seed.getZ();
            boolean xAligned = false;
            for (BlockPos pos : region) {
                minX = Math.min(minX, pos.getX()); maxX = Math.max(maxX, pos.getX());
                minY = Math.min(minY, pos.getY()); maxY = Math.max(maxY, pos.getY());
                minZ = Math.min(minZ, pos.getZ()); maxZ = Math.max(maxZ, pos.getZ());
                anchorX = pos.getX();
                anchorZ = pos.getZ();
            }

            boolean horizontalPlane = maxX - minX + 1 > 2 && maxY - minY + 1 > 2;
            boolean verticalPlane   = maxZ - minZ + 1 > 2 && maxY - minY + 1 > 2;
            if (horizontalPlane) {
                walls.add(new DetectedWall(region, minX, maxX, minY, maxY, anchorZ, true));
                xAligned = true;
            }
            if (verticalPlane) {
                walls.add(new DetectedWall(region, minX, maxX, minY, maxY, anchorX, false));
            }
            if (!xAligned && !verticalPlane) {
                walls.add(new DetectedWall(region, minX, maxX, minY, maxY, anchorZ, true));
            }
        }
        return walls;
    }

    // ── Colocación de geometría ─────────────────────────────────────────────────

    private void placeSurfaceRegion(WorldGenRegion region, ChunkAccess chunk,
            SurfaceCandidate surface, int originX, int originZ,
            int targetWidth, int targetDepth, int targetY, BlockState state) {
        int originalWidth = Math.max(1, surface.maxX - surface.minX + 1);
        int originalDepth = Math.max(1, surface.maxZ - surface.minZ + 1);
        for (BlockPos pos : surface.cells()) {
            int relativeX = pos.getX() - surface.minX;
            int relativeZ = pos.getZ() - surface.minZ;
            int targetX = originX + (originalWidth <= 1 ? 0 :
                    relativeX * (targetWidth - 1) / (originalWidth - 1));
            int targetZ = originZ + (originalDepth <= 1 ? 0 :
                    relativeZ * (targetDepth - 1) / (originalDepth - 1));
            setInWorld(region, chunk, targetX, targetY, targetZ, state);
        }
    }

    private void placeWall(WorldGenRegion region, ChunkAccess chunk, DetectedWall wall,
            int originX, int originZ, int targetWidth, int targetDepth,
            int floorY, int ceilingY, RandomSource random,
            Map<BlockPos, BlockState> rememberedStatesByPos,
            int minMemoryX, int maxMemoryX, int minMemoryZ, int maxMemoryZ) {

        int targetHeight = Math.max(2, ceilingY - floorY - 1);
        int originalHeight = Math.max(1, wall.maxY - wall.minY + 1);
        int minAxis = Integer.MAX_VALUE, maxAxis = Integer.MIN_VALUE;
        for (BlockPos pos : wall.cells()) {
            int axis = wall.xAligned() ? pos.getX() : pos.getZ();
            minAxis = Math.min(minAxis, axis);
            maxAxis = Math.max(maxAxis, axis);
        }
        int originalSpan = Math.max(1, maxAxis - minAxis + 1);

        Map<Integer, Map<Integer, BlockState>> statesByRelativeAxis = new HashMap<>();
        for (BlockPos pos : wall.cells()) {
            int relativeAxis = wall.xAligned() ? pos.getX() - minAxis : pos.getZ() - minAxis;
            int relativeY    = pos.getY() - wall.minY;
            BlockState state = rememberedStatesByPos.getOrDefault(
                    pos, Blocks.SMOOTH_SANDSTONE.defaultBlockState());
            statesByRelativeAxis.computeIfAbsent(relativeAxis, k -> new HashMap<>())
                    .put(relativeY, state);
        }

        if (wall.xAligned()) {
            // FIX 4 (pared X-alineada): proyectar anchor sobre eje Z de la memoria.
            // Antes: int originalDepth = Math.max(1, targetDepth)  ← usaba targetDepth,
            // que es el tamaño TARGET, no el tamaño SOURCE de la memoria → posición incorrecta.
            int memoryDepthZ = Math.max(1, maxMemoryZ - minMemoryZ);
            int relativeDepth = Math.max(0, wall.anchor - minMemoryZ);
            int targetZ = originZ + relativeDepth * (targetDepth - 1) / memoryDepthZ;
            int targetSpanX = clamp(3, 3, Math.max(3, targetWidth - 1),
                    Math.max(originalSpan, originalSpan + random.nextInt(5) - 2));
            int localOriginX = originX + random.nextInt(Math.max(1, targetWidth - targetSpanX));
            for (int relativeX = 0; relativeX < targetSpanX; relativeX++) {
                int sourceX = Math.min(relativeX, originalSpan - 1);
                for (int relativeY = 0; relativeY < targetHeight; relativeY++) {
                    int sourceY = Math.min(relativeY, originalHeight - 1);
                    BlockState state = statesByRelativeAxis
                            .getOrDefault(sourceX, Map.of())
                            .getOrDefault(sourceY, Blocks.SMOOTH_SANDSTONE.defaultBlockState());
                    setInWorld(region, chunk, localOriginX + relativeX, floorY + 1 + relativeY, targetZ, state);
                }
            }
            return;
        }

        // FIX 4 (pared Z-alineada): proyectar anchor sobre eje X de la memoria.
        // Antes: usaba el mismo originalDepth = targetDepth para ambos casos.
        int memoryDepthX = Math.max(1, maxMemoryX - minMemoryX);
        int relativeDepth = Math.max(0, wall.anchor - minMemoryX);
        int targetX = originX + relativeDepth * (targetWidth - 1) / memoryDepthX;
        int targetSpanZ = clamp(3, 3, Math.max(3, targetDepth - 1),
                Math.max(originalSpan, originalSpan + random.nextInt(5) - 2));
        int localOriginZ = originZ + random.nextInt(Math.max(1, targetDepth - targetSpanZ));
        for (int relativeZ = 0; relativeZ < targetSpanZ; relativeZ++) {
            int sourceZ = Math.min(relativeZ, originalSpan - 1);
            for (int relativeY = 0; relativeY < targetHeight; relativeY++) {
                int sourceY = Math.min(relativeY, originalHeight - 1);
                BlockState state = statesByRelativeAxis
                        .getOrDefault(sourceZ, Map.of())
                        .getOrDefault(sourceY, Blocks.SMOOTH_SANDSTONE.defaultBlockState());
                setInWorld(region, chunk, targetX, floorY + 1 + relativeY, localOriginZ + relativeZ, state);
            }
        }
    }

    private void connectCorners(WorldGenRegion region, ChunkAccess chunk,
            int originX, int originZ, int targetWidth, int targetDepth,
            int floorY, int ceilingY, RandomSource random) {
        BlockState wallState = FunctionalMaterialTable.stateFor(ArchitecturalFunction.WALL, random);
        int maxX = originX + targetWidth - 1;
        int maxZ = originZ + targetDepth - 1;
        for (int y = floorY + 1; y < ceilingY; y++) {
            setInWorld(region, chunk, originX, y, originZ,  wallState);
            setInWorld(region, chunk, originX, y, maxZ,     wallState);
            setInWorld(region, chunk, maxX,    y, originZ,  wallState);
            setInWorld(region, chunk, maxX,    y, maxZ,     wallState);
        }
    }

    private void placeFunctionalBlocks(WorldGenRegion region, ChunkAccess chunk,
            RandomSource random, List<MemoryBlockSnapshot> snapshots,
            net.minecraft.core.HolderGetter<net.minecraft.world.level.block.Block> blockLookup,
            int originX, int originZ, int targetWidth, int targetDepth,
            int floorY, int ceilingY,
            int minMemoryX, int maxMemoryX, int minMemoryY, int maxMemoryY,
            int minMemoryZ, int maxMemoryZ) {

        int targetHeight = Math.max(2, ceilingY - floorY - 1);
        for (MemoryBlockSnapshot snapshot : snapshots) {
            BlockState rememberedState = NbtUtils.readBlockState(blockLookup, snapshot.blockState());
            ArchitecturalFunction function = MemoryFunctionClassifier.classify(rememberedState);
            if (function == ArchitecturalFunction.AIR
                    || function == ArchitecturalFunction.WALL
                    || function == ArchitecturalFunction.FLOOR
                    || function == ArchitecturalFunction.CEILING) continue;
            if (!random.nextBoolean()) continue;

            BlockPos pos = snapshot.relativePos();
            int localX = mapCoordinate(pos.getX(), minMemoryX, maxMemoryX, 0, targetWidth - 1);
            int localZ = mapCoordinate(pos.getZ(), minMemoryZ, maxMemoryZ, 0, targetDepth - 1);
            int localY = mapCoordinate(pos.getY(), minMemoryY, maxMemoryY, 1, targetHeight);
            int worldX = originX + localX;
            int worldZ = originZ + localZ;
            int worldY = floorY + localY;

            // FIX 6: El "misremembering" — un tercio de los bloques funcionales
            // se corrompen en lugar de colocarse literalmente.
            // corruptFunction sólo opera sobre el chunk propio (usa set() directo).
            if (random.nextInt(3) == 0 && isInCurrentChunk(chunk, worldX, worldZ)) {
                corruptFunction(chunk, random, function, worldX, worldY, worldZ);
            } else {
                setRememberedBlock(region, chunk, rememberedState,
                        worldX, worldY, worldZ, snapshot.blockEntity());
            }
        }
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
        return Blocks.SMOOTH_SANDSTONE.defaultBlockState();
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
            case STORAGE -> {
                for (int i = 0; i < intensity * 2; i++) {
                    set(chunk, x + random.nextInt(3) - 1, y + random.nextInt(2),
                            z + random.nextInt(3) - 1,
                            FunctionalMaterialTable.stateFor(ArchitecturalFunction.STORAGE, random));
                }
            }
            case BED -> {
                for (int i = 0; i < intensity; i++) {
                    int yy = random.nextInt(4) == 0 ? CEILING_Y - 1 : y;
                    set(chunk, x + i, yy, z + random.nextInt(2),
                            FunctionalMaterialTable.stateFor(ArchitecturalFunction.BED, random));
                }
            }
            case DOOR -> {
                for (int i = 0; i < intensity * 2; i++) {
                    int dx = x + i - intensity;
                    set(chunk, dx, y,     z, FunctionalMaterialTable.stateFor(ArchitecturalFunction.DOOR, random));
                    set(chunk, dx, y + 1, z, Blocks.BIRCH_DOOR.defaultBlockState());
                }
            }
            case WINDOW -> {
                if (random.nextBoolean()) {
                    set(chunk, x,     y + 1, z, FunctionalMaterialTable.stateFor(ArchitecturalFunction.WINDOW, random));
                    set(chunk, x + 1, y + 1, z, FunctionalMaterialTable.stateFor(ArchitecturalFunction.WINDOW, random));
                }
            }
            case LIGHT, FURNITURE, STAIRS ->
                set(chunk, x, y, z, FunctionalMaterialTable.stateFor(function, random));
            default -> {}
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