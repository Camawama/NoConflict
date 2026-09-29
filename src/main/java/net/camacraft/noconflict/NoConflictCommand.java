package net.camacraft.noconflict;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.camacraft.noconflict.client.ConflictManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Client-side informational/maintenance commands. Registered via RegisterClientCommandsEvent
 * (see KeyHandler) — this must never be registered on the server dispatcher, since every
 * executor touches client-only classes.
 */
public class NoConflictCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("noconflict")
                .then(Commands.literal("list")
                        .then(Commands.literal("keys")
                                .executes(context -> listKeys(context.getSource()))
                        )
                        .then(Commands.literal("categories")
                                .executes(context -> listCategories(context.getSource()))
                        )
                )
                .then(Commands.literal("find")
                        .then(Commands.argument("category", StringArgumentType.string())
                                .suggests(NoConflictCommand::suggestCategories)
                                .executes(context -> findKeysInCategory(context.getSource(), StringArgumentType.getString(context, "category")))
                        )
                )
                .then(Commands.literal("clear")
                        .executes(context -> clearAllSelections(context.getSource()))
                        .then(Commands.argument("key", StringArgumentType.string())
                                .suggests(NoConflictCommand::suggestSelectedKeys)
                                .executes(context -> clearSelection(context.getSource(), StringArgumentType.getString(context, "key")))
                        )
                );
        dispatcher.register(command);
    }

    private static int listKeys(CommandSourceStack source) {
        for (KeyMapping keyMapping : Minecraft.getInstance().options.keyMappings) {
            source.sendSuccess(() -> Component.literal(keyMapping.getName()), false);
        }
        return 1;
    }

    private static int listCategories(CommandSourceStack source) {
        getCategorySet().forEach(category -> source.sendSuccess(() -> Component.literal(category), false));
        return 1;
    }

    private static int findKeysInCategory(CommandSourceStack source, String category) {
        long count = 0;
        for (KeyMapping keyMapping : Minecraft.getInstance().options.keyMappings) {
            if (keyMapping.getCategory().equalsIgnoreCase(category)) {
                String id = keyMapping.getName();
                String key = keyMapping.getTranslatedKeyMessage().getString();
                String message = String.format("ID: %s, Key: %s", id, key);
                source.sendSuccess(() -> Component.literal(message), false);
                count++;
            }
        }
        if (count == 0) {
            source.sendFailure(Component.translatable("noconflict.command.no_keybinds", category));
        }
        return (int) count;
    }

    private static int clearAllSelections(CommandSourceStack source) {
        ConflictManager.clearAllSelections();
        source.sendSuccess(() -> Component.translatable("noconflict.command.cleared_all"), false);
        return 1;
    }

    private static int clearSelection(CommandSourceStack source, String keyName) {
        InputConstants.Key key;
        try {
            key = InputConstants.getKey(keyName);
        } catch (Exception e) {
            source.sendFailure(Component.translatable("noconflict.command.invalid_key", keyName));
            return 0;
        }

        if (ConflictManager.clearSelectionsFor(key)) {
            source.sendSuccess(() -> Component.translatable("noconflict.command.cleared_key", keyName), false);
            return 1;
        }
        source.sendFailure(Component.translatable("noconflict.command.nothing_to_clear", keyName));
        return 0;
    }

    private static CompletableFuture<Suggestions> suggestCategories(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(getCategorySet(), builder);
    }

    private static CompletableFuture<Suggestions> suggestSelectedKeys(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(ConflictManager.getSelectedKeyNames(), builder);
    }

    private static Set<String> getCategorySet() {
        return Arrays.stream(Minecraft.getInstance().options.keyMappings)
                .map(KeyMapping::getCategory)
                .collect(Collectors.toSet());
    }
}
