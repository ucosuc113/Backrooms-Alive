package com.glados.backrooms.portal;

import com.glados.backrooms.dimension.ModDimensions;
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

        if (spawnPortal(serverPlayer, (ServerLevel) level, frame)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            serverPlayer.displayClientMessage(Component.translatable("message.backrooms.portal.created").withStyle(ChatFormatting.YELLOW), true);
        }
    }

    private static boolean spawnPortal(ServerPlayer player, ServerLevel originLevel, PortalFrame frame) {
        MinecraftServer server = originLevel.getServer();
        ServerLevel destinationLevel = server.getLevel(ModDimensions.BACKROOMS_LEVEL_KEY);
        if (destinationLevel == null) {
            player.sendSystemMessage(Component.translatable("message.backrooms.portal.dimension_missing").withStyle(ChatFormatting.RED));
            return false;
        }

        Vec3 origin = frame.originCenter();
        Vec3 destination = new Vec3(0.5D, 50.5D, 0.5D);
        Portal portal = new Portal(IPRegistry.PORTAL.get(), originLevel);
        portal.setOriginPos(origin);
        portal.setDestinationDimension(ModDimensions.BACKROOMS_LEVEL_KEY);
        portal.setDestination(destination);
        portal.setOrientationAndSize(frame.axisW(), new Vec3(0.0D, 1.0D, 0.0D), frame.innerWidth(), frame.innerHeight());
        portal.setRotationTransformation(frame.rotation());
        portal.setTeleportable(true);
        portal.portalTag = "backrooms_entry";
        portal.setIsVisible(true);
        PortalAPI.spawnServerEntity(portal);

        Portal reverse = PortalAPI.createReversePortal(portal);
        reverse.portalTag = "backrooms_exit";
        PortalAPI.spawnServerEntity(reverse);
        return true;
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

        Vec3 originCenter() {
            Vec3 bottomCenter = Vec3.atCenterOf(bottomLeft.relative(widthDirection, 1).above(1));
            double widthOffset = (innerWidth() - 1.0D) / 2.0D;
            double heightOffset = (innerHeight() - 1.0D) / 2.0D;
            return bottomCenter.add(Vec3.atLowerCornerOf(widthDirection.getNormal()).scale(widthOffset)).add(0.0D, heightOffset, 0.0D);
        }

        Vec3 axisW() {
            return Vec3.atLowerCornerOf(widthDirection.getNormal());
        }

        int innerWidth() {
            return outerWidth - 2;
        }

        int innerHeight() {
            return outerHeight - 2;
        }

        DQuaternion rotation() {
            return widthDirection == Direction.EAST
                    ? DQuaternion.identity
                    : DQuaternion.rotationByDegrees(new Vec3(0.0D, 1.0D, 0.0D), 90.0D);
        }
    }
}
