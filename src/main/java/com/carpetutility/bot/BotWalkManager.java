package com.carpetutility.bot;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all active Carpet bot navigation tasks and coordinates tick updates.
 */
public class BotWalkManager {
    private static final BotWalkManager INSTANCE = new BotWalkManager();

    public static BotWalkManager getInstance() {
        return INSTANCE;
    }

    private final Map<String, BotBrain> activeWalkers = new ConcurrentHashMap<>();

    private BotWalkManager() {}

    public void startWalkToPlayer(String botName, ServerPlayerEntity bot, ServerPlayerEntity targetPlayer, ServerCommandSource source) {
        BotBrain brain = new BotBrain(botName, bot, targetPlayer, source);
        activeWalkers.put(botName.toLowerCase(), brain);
    }

    public void startWalkToPos(String botName, ServerPlayerEntity bot, Vec3d targetPos, ServerCommandSource source) {
        BotBrain brain = new BotBrain(botName, bot, targetPos, source);
        activeWalkers.put(botName.toLowerCase(), brain);
    }

    public boolean stopBot(String botName) {
        BotBrain brain = activeWalkers.remove(botName.toLowerCase());
        if (brain != null) {
            brain.stop();
            return true;
        }
        return false;
    }

    public Collection<BotBrain> getActiveWalkers() {
        return activeWalkers.values();
    }

    public void tick(MinecraftServer server) {
        if (activeWalkers.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<String, BotBrain>> iterator = activeWalkers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, BotBrain> entry = iterator.next();
            BotBrain brain = entry.getValue();

            ServerPlayerEntity bot = server.getPlayerList().getPlayer(brain.getBotName());
            if (bot == null || !bot.isAlive()) {
                brain.stop();
                iterator.remove();
                continue;
            }

            brain.tick(bot, server);

            if (brain.isArrived()) {
                brain.stop();
                if (brain.getSource() != null) {
                    brain.getSource().sendFeedback(() -> Text.literal("Bot '" + brain.getBotName() + "' arrived at destination!")
                            .formatted(Formatting.GREEN), false);
                }
                iterator.remove();
            }
        }
    }

    public void onServerStop() {
        for (BotBrain brain : activeWalkers.values()) {
            brain.stop();
        }
        activeWalkers.clear();
    }
}
