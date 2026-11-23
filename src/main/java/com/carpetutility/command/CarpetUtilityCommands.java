package com.carpetutility.command;

import com.carpetutility.CarpetUtilityConfig;
import com.carpetutility.bot.BotBrain;
import com.carpetutility.bot.BotWalkManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.command.permission.LeveledPermissionPredicate;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Registers and handles /carpetutility commands:
 *  - /carpetutility <botname> walkto <player>
 *  - /carpetutility <botname> walkto <x> <y> <z>
 *  - /carpetutility stop <botname>
 *  - /carpetutility status
 */
public class CarpetUtilityCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("carpetutility")
                .requires(source -> source.getPermissions().hasPermission(LeveledPermissionPredicate.fromLevel(PermissionLevel.fromLevel(CarpetUtilityConfig.permissionLevel))))
                // /carpetutility status
                .then(CommandManager.literal("status")
                    .executes(CarpetUtilityCommands::executeStatus)
                )
                // /carpetutility stop <botname>
                .then(CommandManager.literal("stop")
                    .then(CommandManager.argument("botname", StringArgumentType.word())
                        .suggests(CarpetUtilityCommands::suggestBots)
                        .executes(CarpetUtilityCommands::executeStop)
                    )
                )
                // /carpetutility <botname> walkto ...
                .then(CommandManager.argument("botname", StringArgumentType.word())
                    .suggests(CarpetUtilityCommands::suggestBots)
                    .then(CommandManager.literal("walkto")
                        // walkto <player>
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                            .executes(CarpetUtilityCommands::executeWalkToPlayer)
                        )
                        // walkto <x> <y> <z>
                        .then(CommandManager.argument("pos", Vec3ArgumentType.vec3())
                            .executes(CarpetUtilityCommands::executeWalkToPos)
                        )
                    )
                )
            );
        });
    }

    private static int executeWalkToPlayer(CommandContext<ServerCommandSource> ctx) {
        String botName = StringArgumentType.getString(ctx, "botname");
        ServerCommandSource source = ctx.getSource();
        MinecraftServer server = source.getServer();

        ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botName);
        if (bot == null) {
            source.sendFeedback(() -> Text.literal("Bot '" + botName + "' not found! (Spawn it first with /player " + botName + " spawn)")
                    .formatted(Formatting.RED), false);
            return 0;
        }

        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(ctx, "player");
            BotWalkManager.getInstance().startWalkToPlayer(botName, bot, targetPlayer, source);
            source.sendFeedback(() -> Text.literal("Bot '" + botName + "' is now walking to " + targetPlayer.getName().getString())
                    .formatted(Formatting.GREEN), false);
            return 1;
        } catch (CommandSyntaxException e) {
            source.sendFeedback(() -> Text.literal("Target player not found!").formatted(Formatting.RED), false);
            return 0;
        }
    }

    private static int executeWalkToPos(CommandContext<ServerCommandSource> ctx) {
        String botName = StringArgumentType.getString(ctx, "botname");
        ServerCommandSource source = ctx.getSource();
        MinecraftServer server = source.getServer();

        ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botName);
        if (bot == null) {
            source.sendFeedback(() -> Text.literal("Bot '" + botName + "' not found! (Spawn it first with /player " + botName + " spawn)")
                    .formatted(Formatting.RED), false);
            return 0;
        }

        Vec3d targetPos = Vec3ArgumentType.getVec3(ctx, "pos");
        BotWalkManager.getInstance().startWalkToPos(botName, bot, targetPos, source);
        source.sendFeedback(() -> Text.literal(String.format("Bot '%s' is now walking to (%.1f, %.1f, %.1f)",
                botName, targetPos.x, targetPos.y, targetPos.z)).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeStop(CommandContext<ServerCommandSource> ctx) {
        String botName = StringArgumentType.getString(ctx, "botname");
        ServerCommandSource source = ctx.getSource();

        boolean stopped = BotWalkManager.getInstance().stopBot(botName);
        if (stopped) {
            source.sendFeedback(() -> Text.literal("Stopped bot '" + botName + "'").formatted(Formatting.YELLOW), false);
            return 1;
        } else {
            source.sendFeedback(() -> Text.literal("Bot '" + botName + "' is not currently walking").formatted(Formatting.RED), false);
            return 0;
        }
    }

    private static int executeStatus(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        Collection<BotBrain> active = BotWalkManager.getInstance().getActiveWalkers();

        if (active.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No bots are currently walking").formatted(Formatting.GRAY), false);
            return 1;
        }

        source.sendFeedback(() -> Text.literal("=== CarpetUtility Active Walkers ===").formatted(Formatting.GOLD), false);
        for (BotBrain brain : active) {
            String targetDesc = brain.getMode() == BotBrain.WalkMode.PLAYER
                    ? "player " + brain.getTargetPlayerName()
                    : String.format("coords (%.1f, %.1f, %.1f)", brain.getTargetPos().x, brain.getTargetPos().y, brain.getTargetPos().z);

            String status = String.format("- %s -> walking to %s (distance: %.1fm, bridging: %s)",
                    brain.getBotName(), targetDesc, brain.getCurrentDistance(), brain.isBridging() ? "yes" : "no");

            source.sendFeedback(() -> Text.literal(status).formatted(Formatting.AQUA), false);
        }
        return 1;
    }

    public static CompletableFuture<Suggestions> suggestBots(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        MinecraftServer server = context.getSource().getServer();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            builder.suggest(player.getName().getString());
        }
        return builder.buildFuture();
    }
}
