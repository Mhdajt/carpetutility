package com.carpetutility.scoreboard;

import net.minecraft.block.Blocks;
import net.minecraft.scoreboard.ScoreAccess;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/**
 * Manages the 'on_cobweb' scoreboard objective.
 * Continuously detects when any player (including Carpet bots) is standing on cobwebs,
 * updating their score to 1, or 0 when not on cobwebs, displayed on the sidebar.
 */
public class CobwebScoreboardTracker {
    public static final String OBJECTIVE_NAME = "on_cobweb";

    private static final CobwebScoreboardTracker INSTANCE = new CobwebScoreboardTracker();

    public static CobwebScoreboardTracker getInstance() {
        return INSTANCE;
    }

    private CobwebScoreboardTracker() {}

    /**
     * Checks and creates the scoreboard objective on the sidebar if not already initialized.
     */
    private ScoreboardObjective ensureObjective(Scoreboard scoreboard) {
        ScoreboardObjective objective = scoreboard.getNullableObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = scoreboard.addObjective(
                    OBJECTIVE_NAME,
                    ScoreboardCriterion.DUMMY,
                    Text.literal("On Cobweb").formatted(Formatting.GOLD, Formatting.BOLD),
                    ScoreboardCriterion.RenderType.INTEGER,
                    true,
                    null
            );
            scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective);
        }
        return objective;
    }

    /**
     * Evaluates all online players and bots every tick and updates their score.
     */
    public void tick(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective objective = ensureObjective(scoreboard);

        for (ServerPlayerEntity player : server.getPlayerList().getPlayers()) {
            boolean standingOnCobweb = isPlayerOnCobweb(player);

            ScoreAccess scoreAccess = scoreboard.getOrCreateScore(player, objective);
            int newScore = standingOnCobweb ? 1 : 0;
            if (scoreAccess.get() != newScore) {
                scoreAccess.set(newScore);
            }
        }
    }

    /**
     * Determines whether the player or fake bot is inside or standing on a cobweb block.
     */
    private boolean isPlayerOnCobweb(ServerPlayerEntity player) {
        BlockPos feetPos = player.getBlockPos();
        BlockPos underPos = feetPos.down();

        // Check block at feet
        if (player.getServerWorld().getBlockState(feetPos).isOf(Blocks.COBWEB)) {
            return true;
        }

        // Check block directly under feet
        if (player.getServerWorld().getBlockState(underPos).isOf(Blocks.COBWEB)) {
            return true;
        }

        // Check actual stepping block state (e.g. edge collisions)
        return player.getSteppingBlockState().isOf(Blocks.COBWEB);
    }
}
