package com.glados.backrooms.registry;

import com.glados.backrooms.util.ModConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registro de bloques del mod.
 * <p>
 * En este modulo solo se registra el bloque "Almacen de Mentes" como objeto
 * base, sin ningun comportamiento especial todavia. La logica de memoria
 * y corrupcion se anadira en sus modulos correspondientes.
 */
public final class ModBlocks {

    private ModBlocks() {
    }

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, ModConstants.MOD_ID);

    public static final RegistryObject<Block> MIND_STORAGE = BLOCKS.register(
            "mind_storage",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops())
    );

    public static final RegistryObject<Block> BACK_WALL = BLOCKS.register(
            "back_wall",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_YELLOW)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.WOOL))
    );

    public static final RegistryObject<Block> GHOST_OAK_PLANKS = BLOCKS.register(
            "ghost_oak_planks",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)
                    .noOcclusion()) {
                @Override
                public VoxelShape getCollisionShape(BlockState state, BlockGetter level, net.minecraft.core.BlockPos pos, CollisionContext context) {
                    return Shapes.empty();
                }
            }
    );

    public static final RegistryObject<Block> BACK_WALL_GHOST = BLOCKS.register(
            "back_wall_ghost",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_YELLOW)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.WOOL)
                    .noOcclusion()) {
                @Override
                public VoxelShape getCollisionShape(BlockState state, BlockGetter level, net.minecraft.core.BlockPos pos, CollisionContext context) {
                    return Shapes.empty();
                }
            }
    );

    public static final RegistryObject<Block> BACK_FLOOR = BLOCKS.register(
            "back_floor",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.STONE))
    );

    public static final RegistryObject<Block> BACK_CEILING = BLOCKS.register(
            "back_ceiling",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_YELLOW)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.WOOL))
    );

    public static final RegistryObject<Block> BACK_LIGHT = BLOCKS.register(
            "back_light",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(0.3F)
                    .lightLevel(state -> 15)
                    .sound(SoundType.GLASS)
                    .noOcclusion())
    );
}
