package com.glados.backrooms.memory;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.glados.backrooms.util.ModConstants;

@Mod.EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class MemoryCommands {

    private MemoryCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("backrooms")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("memory")
                        .then(Commands.literal("save")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> save(
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "name")
                                        ))))
                        .then(Commands.literal("update")
                                .then(Commands.argument("uuid_or_name", StringArgumentType.greedyString())
                                        .executes(context -> update(
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "uuid_or_name")
                                        ))))
                        .then(Commands.literal("list")
                                .executes(context -> list(context.getSource().getPlayerOrException())))
                        .then(Commands.literal("clear-selection")
                                .executes(context -> clearSelection(context.getSource().getPlayerOrException())))));
    }

    private static int save(ServerPlayer player, String name) {
        if (name.isBlank()) {
            player.sendSystemMessage(Component.translatable("message.backrooms.memory.name_required").withStyle(ChatFormatting.RED));
            return 0;
        }

        return withSelection(player, bounds -> {
            MemoryRegion memory = MemoryLibrary.get(player.getServer()).create((ServerLevel) player.level(), name.trim(), bounds);
            player.sendSystemMessage(Component.translatable(
                    "message.backrooms.memory.saved",
                    memory.name(),
                    memory.uuid().toString(),
                    memory.blocks().size()
            ).withStyle(ChatFormatting.GREEN));
            return 1;
        });
    }

    private static int update(ServerPlayer player, String uuidOrName) {
        return withSelection(player, bounds -> {
            MemoryLibrary library = MemoryLibrary.get(player.getServer());
            UUID uuid = parseUuid(uuidOrName);
            MemoryRegion existing = uuid != null
                    ? library.find(uuid).orElse(null)
                    : library.findByName(uuidOrName.trim()).orElse(null);

            if (existing == null) {
                player.sendSystemMessage(Component.translatable("message.backrooms.memory.not_found", uuidOrName).withStyle(ChatFormatting.RED));
                return 0;
            }

            MemoryRegion updated = library.update((ServerLevel) player.level(), existing.uuid(), bounds);
            player.sendSystemMessage(Component.translatable(
                    "message.backrooms.memory.updated",
                    updated.name(),
                    updated.uuid().toString(),
                    updated.blocks().size()
            ).withStyle(ChatFormatting.GREEN));
            return 1;
        });
    }

    private static int list(ServerPlayer player) {
        var memories = MemoryLibrary.get(player.getServer()).all();
        if (memories.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.backrooms.memory.empty").withStyle(ChatFormatting.GRAY));
            return 1;
        }

        player.sendSystemMessage(Component.translatable("message.backrooms.memory.list_header", memories.size()).withStyle(ChatFormatting.YELLOW));
        for (MemoryRegion memory : memories) {
            player.sendSystemMessage(Component.literal("- " + memory.name() + " [" + memory.uuid() + "] "
                    + memory.width() + "x" + memory.height() + "x" + memory.depth()
                    + " blocks=" + memory.blocks().size()));
        }
        return memories.size();
    }

    private static int clearSelection(ServerPlayer player) {
        MemorySelections.clear(player);
        player.sendSystemMessage(Component.translatable("message.backrooms.memory.selection_cleared").withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private static int withSelection(ServerPlayer player, SelectionAction action) {
        return MemorySelections.get(player).map(selection -> {
            BoundingBox bounds = selection.bounds();
            if (!MemorySelection.isWithinLimit(bounds)) {
                player.sendSystemMessage(Component.translatable(
                        "message.backrooms.memory.too_large",
                        MemorySelection.sizeX(bounds),
                        MemorySelection.sizeY(bounds),
                        MemorySelection.sizeZ(bounds)
                ).withStyle(ChatFormatting.RED));
                return 0;
            }

            try {
                return action.run(bounds);
            } catch (IllegalArgumentException exception) {
                player.sendSystemMessage(Component.literal(exception.getMessage()).withStyle(ChatFormatting.RED));
                return 0;
            }
        }).orElseGet(() -> {
            player.sendSystemMessage(Component.translatable("message.backrooms.memory.selection_required").withStyle(ChatFormatting.RED));
            return 0;
        });
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    @FunctionalInterface
    private interface SelectionAction {
        int run(BoundingBox bounds);
    }
}
