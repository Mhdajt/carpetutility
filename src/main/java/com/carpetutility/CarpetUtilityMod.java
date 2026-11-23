package com.carpetutility;

import com.carpetutility.bot.BotWalkManager;
import com.carpetutility.command.CarpetUtilityCommands;
import com.carpetutility.scoreboard.CobwebScoreboardTracker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CarpetUtility Fabric mod for Minecraft 1.21.11.
 * Provides fake player pathfinding, bridging, wall avoidance, and cobweb scoreboard tracking.
 */
public class CarpetUtilityMod implements ModInitializer {
    public static final String MOD_ID = "carpetutility";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[CarpetUtility] Initializing CarpetUtility for Minecraft 1.21.11");

        // Verify Carpet presence
        if (FabricLoader.getInstance().isModLoaded("carpet")) {
            LOGGER.info("[CarpetUtility] Fabric Carpet detected! Bot controls enabled.");
        } else {
            LOGGER.warn("[CarpetUtility] Fabric Carpet is not loaded! Some bot actions may be limited.");
        }

        // Optional Carpet+ detection
        if (FabricLoader.getInstance().isModLoaded("carpet-plus") || FabricLoader.getInstance().isModLoaded("carpetplus")) {
            LOGGER.info("[CarpetUtility] Carpet+ detected! Enhanced integration active.");
        }

        // Register commands
        CarpetUtilityCommands.register();

        // Register tick handlers for bot navigation and scoreboard tracking
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            BotWalkManager.getInstance().tick(server);
            CobwebScoreboardTracker.getInstance().tick(server);
        });

        // Clean up bots when server stops
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            BotWalkManager.getInstance().onServerStop();
            LOGGER.info("[CarpetUtility] Server stopping - stopped all active walkers.");
        });

        LOGGER.info("[CarpetUtility] Initialization complete!");
    }
}
