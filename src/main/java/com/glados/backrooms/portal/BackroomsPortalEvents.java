package com.glados.backrooms.portal;

import com.glados.backrooms.dimension.ModDimensions;
import com.glados.backrooms.generation.BackroomsChunkGenerator;
import com.glados.backrooms.registry.ModBlocks;
import com.glados.backrooms.registry.ModItems;
import com.glados.backrooms.util.ModConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.platform_specific.IPRegistry;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.my_util.DQuaternion;

@Mod.EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class BackroomsPortalEvents {

    private static final int MIN_OUTER_WIDTH = 3;
    private static final int MIN_OUTER_HEIGHT = 4;
    private static final int MAX_OUTER_SIZE = 18;

    private BackroomsPortalEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        ItemStack stack = event.getItemStack();

        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !stack.is(ModItems.EPISODIC_COMPARATOR.get())) {
            return;
        }

        PortalFrame frame = findFrame(level, event.getPos());
        if (frame == null) {
            if (level.getBlockState(event.getPos()).is(ModBlocks.MIND_STORAGE.get())) {
                serverPlayer.displayClientMessage(Component.translatable("message.backrooms.portal.invalid_frame").withStyle(ChatFormatting.RED), true);
            }
            return;
        }

        // decide destination: if origin is backrooms, go to overworld; otherwise go to backrooms
        if (spawnPortal(serverPlayer, (ServerLevel) level, frame)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            serverPlayer.displayClientMessage(Component.translatable("message.backrooms.portal.created").withStyle(ChatFormatting.YELLOW), true);
        }
    }

    private static boolean spawnPortal(ServerPlayer player, ServerLevel originLevel, PortalFrame frame) {
        MinecraftServer server = originLevel.getServer();

        var originKey = originLevel.dimension();
        var destKey = originKey == ModDimensions.BACKROOMS_LEVEL_KEY ? Level.OVERWORLD : ModDimensions.BACKROOMS_LEVEL_KEY;
        ServerLevel destinationLevel = server.getLevel(destKey);
        if (destinationLevel == null) {
            player.sendSystemMessage(Component.translatable("message.backrooms.portal.dimension_missing").withStyle(ChatFormatting.RED));
            return false;
        }

        PortalFrame originFrame = frame;
        PortalFrame destFrame;

        if (destKey == ModDimensions.BACKROOMS_LEVEL_KEY) {
                var result = BackroomsChunkGenerator.findPortalWallBase(destinationLevel,
                    originFrame.bottomLeft().getX(), originFrame.bottomLeft().getZ(), originFrame.outerWidth(), originFrame.outerHeight());
                destFrame = new PortalFrame(result.base(), result.widthDirection(), originFrame.outerWidth(), originFrame.outerHeight());
            buildPortalFrame(destinationLevel, destFrame);
        } else {
            // Search the destination world (Overworld) for an existing valid frame near the origin coordinates.
            int ox = originFrame.bottomLeft().getX();
            int oz = originFrame.bottomLeft().getZ();
            PortalFrame found = null;
            int searchRadius = 12;
            for (int r = 0; r <= searchRadius && found == null; r++) {
                for (int dx = -r; dx <= r && found == null; dx++) {
                    for (int dz = -r; dz <= r && found == null; dz++) {
                        int tx = ox + dx;
                        int tz = oz + dz;
                        PortalFrame candidate = findFrame(destinationLevel, new BlockPos(tx, BackroomsChunkGenerator.FLOOR_Y, tz));
                        if (candidate != null) {
                            found = candidate;
                        }
                    }
                }
            }
            if (found == null) {
                player.sendSystemMessage(Component.literal("No valid portal frame found in destination world, aborting").withStyle(ChatFormatting.RED));
                return false;
            }
            destFrame = found;
            buildPortalFrame(destinationLevel, destFrame);
        }

        if (originFrame.outerWidth() != destFrame.outerWidth() || originFrame.outerHeight() != destFrame.outerHeight()) {
            player.sendSystemMessage(Component.literal("Portal size mismatch between worlds, aborting").withStyle(ChatFormatting.RED));
            return false;
        }

        Vec3 originCenter = originCenter(originFrame, originFrame.bottomLeft().getY());
        Vec3 destCenter = originCenter(destFrame, BackroomsChunkGenerator.FLOOR_Y);

        Portal originPortal = new Portal(IPRegistry.PORTAL.get(), originLevel);
        PortalAPI.setPortalPositionOrientationAndSize(originPortal, originCenter, originFrame.rotation(), originFrame.innerWidth(), originFrame.innerHeight());
        originPortal.setDestinationDimension(destKey);
        originPortal.setDestination(destCenter);
        originPortal.setTeleportable(true);
        originPortal.portalTag = originKey == ModDimensions.BACKROOMS_LEVEL_KEY ? "backrooms_entry" : "overworld_entry";
        originPortal.setIsVisible(true);
        PortalAPI.spawnServerEntity(originPortal);

        Portal destPortal = PortalAPI.createReversePortal(originPortal);
        destPortal.portalTag = destKey == ModDimensions.BACKROOMS_LEVEL_KEY ? "backrooms_entry" : "overworld_entry";
        destPortal.setIsVisible(true);
        PortalAPI.spawnServerEntity(destPortal);
        return true;
        // return true indicates successful portal creation
    }

    private static void buildPortalFrame(ServerLevel level, PortalFrame frame) {
        BlockPos base = frame.bottomLeft();
        for (int x = 0; x < frame.outerWidth(); x++) {
            for (int y = 0; y < frame.outerHeight(); y++) {
                BlockPos pos = base.relative(frame.widthDirection(), x).above(y);
                boolean border = x == 0 || x == frame.outerWidth() - 1 || y == 0 || y == frame.outerHeight() - 1;
                level.setBlock(pos, border ? ModBlocks.MIND_STORAGE.get().defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static Vec3 originCenter(PortalFrame frame, int baseY) {
        BlockPos bottomLeft = new BlockPos(frame.bottomLeft().getX(), baseY, frame.bottomLeft().getZ());
        Vec3 bottomCenter = Vec3.atCenterOf(bottomLeft.relative(frame.widthDirection(), 1).above(1));
        double widthOffset = (frame.innerWidth() - 1.0D) / 2.0D;
        double heightOffset = (frame.innerHeight() - 1.0D) / 2.0D;
        return bottomCenter.add(Vec3.atLowerCornerOf(frame.widthDirection().getNormal()).scale(widthOffset)).add(0.0D, heightOffset, 0.0D);
    }

    private static PortalFrame findFrame(Level level, BlockPos clickedPos) {
        PortalFrame frame = findFrame(level, clickedPos, Direction.EAST);
        return frame != null ? frame : findFrame(level, clickedPos, Direction.SOUTH);
    }

    private static PortalFrame findFrame(Level level, BlockPos clickedPos, Direction widthDirection) {
        for (int width = MIN_OUTER_WIDTH; width <= MAX_OUTER_SIZE; width++) {
            for (int height = MIN_OUTER_HEIGHT; height <= MAX_OUTER_SIZE; height++) {
                PortalFrame frame = findFrame(level, clickedPos, widthDirection, width, height);
                if (frame != null) {
                    return frame;
                }
            }
        }
        return null;
    }

    private static PortalFrame findFrame(Level level, BlockPos clickedPos, Direction widthDirection, int width, int height) {
        for (int leftOffset = 0; leftOffset < width; leftOffset++) {
            for (int bottomOffset = 0; bottomOffset < height; bottomOffset++) {
                BlockPos origin = clickedPos.relative(widthDirection, -leftOffset).below(bottomOffset);
                if (matchesFrame(level, origin, widthDirection, width, height)) {
                    return new PortalFrame(origin, widthDirection, width, height);
                }
            }
        }
        return null;
    }

    private static boolean matchesFrame(Level level, BlockPos bottomLeft, Direction widthDirection, int width, int height) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                BlockPos pos = bottomLeft.relative(widthDirection, x).above(y);
                boolean border = x == 0 || x == width - 1 || y == 0 || y == height - 1;
                BlockState state = level.getBlockState(pos);
                if (border) {
                    if (!state.is(ModBlocks.MIND_STORAGE.get())) {
                        return false;
                    }
                } else if (!state.isAir()) {
                    return false;
                }
            }
        }
        return true;
    }

    private record PortalFrame(BlockPos bottomLeft, Direction widthDirection, int outerWidth, int outerHeight) {

        Vec3 axisW() {
            return Vec3.atLowerCornerOf(widthDirection.getNormal());
        }

        int innerWidth() {
            return Math.max(1, outerWidth - 2);
        }

        int innerHeight() {
            return Math.max(1, outerHeight - 2);
        }

        DQuaternion rotation() {
            return switch (widthDirection) {
                case EAST -> DQuaternion.identity;
                case SOUTH -> DQuaternion.rotationByDegrees(new Vec3(0.0D, 1.0D, 0.0D), 90.0D);
                case WEST -> DQuaternion.rotationByDegrees(new Vec3(0.0D, 1.0D, 0.0D), 180.0D);
                case NORTH -> DQuaternion.rotationByDegrees(new Vec3(0.0D, 1.0D, 0.0D), 270.0D);
                default -> DQuaternion.identity;
            };
        }
    }
}
