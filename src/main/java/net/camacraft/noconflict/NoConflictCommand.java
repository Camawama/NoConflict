package net.camacraft.noconflict;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
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
            source.sendFailure(Component.literal("No keybindings found in category: " + category));
        }
        return (int) count;
    }

    private static CompletableFuture<Suggestions> suggestCategories(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(getCategorySet(), builder);
    }

    private static Set<String> getCategorySet() {
        return Arrays.stream(Minecraft.getInstance().options.keyMappings)
                .map(KeyMapping::getCategory)
                .collect(Collectors.toSet());
    }
}
