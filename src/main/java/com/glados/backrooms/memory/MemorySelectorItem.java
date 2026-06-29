package com.glados.backrooms.memory;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class MemorySelectorItem extends Item {

    public MemorySelectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }

        MemorySelection selection;
        if (player.isShiftKeyDown()) {
            selection = MemorySelections.setSecond(player, context.getClickedPos());
            player.displayClientMessage(Component.translatable("message.backrooms.memory_selector.second", formatPos(context.getClickedPos())).withStyle(ChatFormatting.YELLOW), true);
        } else {
            selection = MemorySelections.setFirst(player, context.getClickedPos());
            player.displayClientMessage(Component.translatable("message.backrooms.memory_selector.first", formatPos(context.getClickedPos())).withStyle(ChatFormatting.YELLOW), true);
        }

        if (selection.isComplete()) {
            BoundingBox bounds = selection.bounds();
            ChatFormatting color = MemorySelection.isWithinLimit(bounds) ? ChatFormatting.GREEN : ChatFormatting.RED;
            player.sendSystemMessage(Component.translatable(
                    "message.backrooms.memory_selector.bounds",
                    MemorySelection.sizeX(bounds),
                    MemorySelection.sizeY(bounds),
                    MemorySelection.sizeZ(bounds)
            ).withStyle(color));
        }

        return InteractionResult.CONSUME;
    }

    private static String formatPos(net.minecraft.core.BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
