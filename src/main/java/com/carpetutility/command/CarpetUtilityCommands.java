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
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.Collection;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class CarpetUtilityCommands {

    private static final String[] NAME_PREFIXES = {
        "Shadow", "Dark", "Swift", "Iron", "Stone", "Void", "Frost", "Blaze",
        "Echo", "Silent", "Rapid", "Steel", "Ash", "Crimson", "Azure", "Nova",
        "Pixel", "Byte", "Glitch", "Neo", "Cyber", "Quantum", "Alpha", "Omega"
    };

    private static final String[] NAME_SUFFIXES = {
        "Walker", "Miner", "Bot", "Runner", "Crawler", "Digger", "Scout", "Guard",
        "Knight", "Phantom", "Wraith", "Golem", "Spark", "Core", "Node", "Unit",
        "X", "Prime", "Zero", "One", "Forge", "Blade", "Strike", "Pulse"
    };

    private static final Random RANDOM = new Random();

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // Main /carpetutility command
            dispatcher.register(CommandManager.literal("carpetutility")
                .requires(source -> {
                    if (CarpetUtilityConfig.permissionLevel <= 0) return true;
                    return source.getPermissions().hasPermission(
                        CarpetUtilityConfig.permissionLevel >= 4 ? net.minecraft.command.DefaultPermissions.OWNERS :
                        CarpetUtilityConfig.permissionLevel >= 3 ? net.minecraft.command.DefaultPermissions.ADMINS :
                        CarpetUtilityConfig.permissionLevel >= 2 ? net.minecraft.command.DefaultPermissions.GAMEMASTERS :
                        net.minecraft.command.DefaultPermissions.MODERATORS
                    );
                })
                .then(CommandManager.literal("status")
                    .executes(CarpetUtilityCommands::executeStatus))
                .then(CommandManager.literal("stop")
                    .then(CommandManager.argument("botname", StringArgumentType.word())
                        .suggests(CarpetUtilityCommands::suggestBots)
                        .executes(CarpetUtilityCommands::executeStop)))
                .then(CommandManager.argument("botname", StringArgumentType.word())
                    .suggests(CarpetUtilityCommands::suggestBots)
                    .then(CommandManager.literal("walkto")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                            .executes(CarpetUtilityCommands::executeWalkToPlayer))
                        .then(CommandManager.argument("pos", Vec3ArgumentType.vec3())
                            .executes(CarpetUtilityCommands::executeWalkToPos))))
            );

            // /namegen - restricted to specific usernames only
            dispatcher.register(CommandManager.literal("namegen")
                .requires(source -> {
                    if (!source.isExecutedByPlayer()) return false;
                    ServerPlayerEntity player = source.getPlayer();
                    if (player == null) return false;
                    String name = player.getName().getString();
                    return CarpetUtilityConfig.isAllowedNamegenUser(name);
                })
                .executes(CarpetUtilityCommands::executeNamegen)
                .then(CommandManager.literal("spawn")
                    .executes(CarpetUtilityCommands::executeNamegenAndSpawn))
            );
        });
    }

    private static int executeNamegen(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        String name = generateBotName();
        source.sendFeedback(() -> Text.literal("Generated bot name: ")
            .append(Text.literal(name).formatted(Formatting.AQUA, Formatting.BOLD))
            .append(Text.literal("  |  Use: /player " + name + " spawn").formatted(Formatting.GRAY)), false);
        return 1;
    }

    private static int executeNamegenAndSpawn(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        MinecraftServer server = source.getServer();
        String name = generateBotName();
        // Run the carpet player spawn command
        server.getCommandManager().parseAndExecute(server.getCommandSource(), "player " + name + " spawn");
        source.sendFeedback(() -> Text.literal("Spawned bot with name: ")
            .append(Text.literal(name).formatted(Formatting.AQUA, Formatting.BOLD)), false);
        return 1;
    }

    private static String generateBotName() {
        String prefix = NAME_PREFIXES[RANDOM.nextInt(NAME_PREFIXES.length)];
        String suffix = NAME_SUFFIXES[RANDOM.nextInt(NAME_SUFFIXES.length)];
        if (RANDOM.nextBoolean()) {
            return prefix + suffix + (10 + RANDOM.nextInt(90));
        }
        return prefix + suffix;
    }

    private static int executeWalkToPlayer(CommandContext<ServerCommandSource> ctx) {
        String botName = StringArgumentType.getString(ctx, "botname");
        ServerCommandSource source = ctx.getSource();
        MinecraftServer server = source.getServer();
        ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botName);
        if (bot == null) {
            source.sendError(Text.literal("Bot '" + botName + "' not found! (Spawn it first with /player " + botName + " spawn)"));
            return 0;
        }
        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(ctx, "player");
            BotWalkManager.getInstance().startWalkToPlayer(botName, bot, targetPlayer, source);
            source.sendFeedback(() -> Text.literal("Bot '" + botName + "' is now walking to " + targetPlayer.getName().getString())
                .formatted(Formatting.GREEN), false);
            return 1;
        } catch (CommandSyntaxException e) {
            source.sendError(Text.literal("Target player not found!"));
            return 0;
        }
    }

    private static int executeWalkToPos(CommandContext<ServerCommandSource> ctx) {
        String botName = StringArgumentType.getString(ctx, "botname");
        ServerCommandSource source = ctx.getSource();
        MinecraftServer server = source.getServer();
        ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botName);
        if (bot == null) {
            source.sendError(Text.literal("Bot '" + botName + "' not found! (Spawn it first with /player " + botName + " spawn)"));
            return 0;
        }
        Vec3d targetPos = Vec3ArgumentType.getVec3(ctx, "pos");
        BotWalkManager.getInstance().startWalkToPos(botName, bot, targetPos, source);
        source.sendFeedback(() -> Text.literal(String.format("Bot '%s' is now walking to (%.1f, %.1f, %.1f)", botName, targetPos.x, targetPos.y, targetPos.z))
            .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeStop(CommandContext<ServerCommandSource> ctx) {
        String botName = StringArgumentType.getString(ctx, "botname");
        ServerCommandSource source = ctx.getSource();
        boolean stopped = BotWalkManager.getInstance().stopBot(botName);
        if (stopped) {
            source.sendFeedback(() -> Text.literal("Stopped bot '" + botName + "'").formatted(Formatting.YELLOW), false);
            return 1;
        }
        source.sendError(Text.literal("Bot '" + botName + "' is not currently walking"));
        return 0;
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
            String status = String.format("- %s -> walking to %s (distance: %.1fm, bridging: %s, mining: %s, pillaring: %s)",
                brain.getBotName(), targetDesc, brain.getCurrentDistance(),
                brain.isBridging() ? "yes" : "no",
                brain.isMining() ? "yes" : "no",
                brain.isPillaring() ? "yes" : "no");
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
