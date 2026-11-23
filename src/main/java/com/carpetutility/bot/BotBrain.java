package com.carpetutility.bot;

import carpet.helpers.EntityPlayerActionPack;
import carpet.helpers.EntityPlayerActionPack.Action;
import carpet.helpers.EntityPlayerActionPack.ActionType;
import carpet.fakes.ServerPlayerInterface;
import com.carpetutility.CarpetUtilityConfig;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Controls the navigation, obstacle avoidance, hole bridging, and movement of a single bot.
 */
public class BotBrain {
    public enum WalkMode {
        PLAYER,
        POS
    }

    private final String botName;
    private final WalkMode mode;
    private UUID targetPlayerUuid;
    private String targetPlayerName;
    private Vec3d fixedTargetPos;
    private final ServerCommandSource source;

    private boolean arrived = false;
    private boolean isBridging = false;
    private double currentDistance = 0.0;
    private int ticksSinceLastTurn = 0;
    private float currentSteeringOffset = 0.0f;

    public BotBrain(String botName, ServerPlayerEntity bot, ServerPlayerEntity targetPlayer, ServerCommandSource source) {
        this.botName = botName;
        this.mode = WalkMode.PLAYER;
        this.targetPlayerUuid = targetPlayer.getUuid();
        this.targetPlayerName = targetPlayer.getName().getString();
        this.fixedTargetPos = null;
        this.source = source;
    }

    public BotBrain(String botName, ServerPlayerEntity bot, Vec3d targetPos, ServerCommandSource source) {
        this.botName = botName;
        this.mode = WalkMode.POS;
        this.targetPlayerUuid = null;
        this.targetPlayerName = null;
        this.fixedTargetPos = targetPos;
        this.source = source;
    }

    public String getBotName() {
        return botName;
    }

    public WalkMode getMode() {
        return mode;
    }

    public String getTargetPlayerName() {
        return targetPlayerName;
    }

    public Vec3d getTargetPos() {
        return fixedTargetPos;
    }

    public boolean isArrived() {
        return arrived;
    }

    public boolean isBridging() {
        return isBridging;
    }

    public double getCurrentDistance() {
        return currentDistance;
    }

    public ServerCommandSource getSource() {
        return source;
    }

    /**
     * Resolves the target position for this tick.
     */
    private Vec3d resolveTargetPos(MinecraftServer server) {
        if (mode == WalkMode.PLAYER) {
            ServerPlayerEntity targetPlayer = server.getPlayerManager().getPlayer(targetPlayerUuid);
            if (targetPlayer != null && targetPlayer.isAlive()) {
                return new Vec3d(targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ());
            }
            return null;
        }
        return fixedTargetPos;
    }

    /**
     * Obtains the Carpet ActionPack for the bot.
     */
    private EntityPlayerActionPack getActionPack(ServerPlayerEntity bot) {
        if (bot instanceof ServerPlayerInterface fake) {
            return fake.getActionPack();
        }
        return null;
    }

    /**
     * Main per-tick navigation loop.
     */
    public void tick(ServerPlayerEntity bot, MinecraftServer server) {
        Vec3d targetPos = resolveTargetPos(server);
        if (targetPos == null) {
            stopMovement(bot, server);
            this.arrived = true;
            return;
        }

        ServerWorld world = bot.getEntityWorld();
        Vec3d botPos = new Vec3d(bot.getX(), bot.getY(), bot.getZ());

        double dx = targetPos.x - botPos.x;
        double dz = targetPos.z - botPos.z;
        double dy = targetPos.y - botPos.y;
        this.currentDistance = Math.sqrt(dx * dx + dz * dz);

        // Check arrival criteria
        if (currentDistance < CarpetUtilityConfig.arrivalDistance && Math.abs(dy) < 2.0) {
            this.arrived = true;
            stopMovement(bot, server);
            return;
        }

        // Base yaw angle toward target
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        // Gradually decay obstacle steering offset if path has been clear
        ticksSinceLastTurn++;
        if (ticksSinceLastTurn > 20) {
            currentSteeringOffset *= 0.8f;
            if (Math.abs(currentSteeringOffset) < 1.0f) {
                currentSteeringOffset = 0.0f;
            }
        }

        float effectiveYaw = targetYaw + currentSteeringOffset;

        // Check for hole ahead in movement direction
        double rad = Math.toRadians(effectiveYaw);
        int checkX = MathHelper.floor(botPos.x - Math.sin(rad) * 1.0);
        int checkZ = MathHelper.floor(botPos.z + Math.cos(rad) * 1.0);
        int botY = MathHelper.floor(botPos.y);

        BlockPos underStep = new BlockPos(checkX, botY - 1, checkZ);
        BlockState underState = world.getBlockState(underStep);
        boolean isHole = underState.isAir() || underState.isLiquid() || !underState.isSolid();

        EntityPlayerActionPack actionPack = getActionPack(bot);

        if (isHole) {
            // Attempt to bridge over hole
            boolean bridged = tryBridge(bot, world, underStep, effectiveYaw, actionPack, server);
            if (!bridged) {
                // If bot cannot bridge (no blocks), steer around the hole!
                steerAroundObstacle(effectiveYaw);
                return;
            }
        } else {
            if (isBridging) {
                isBridging = false;
                if (actionPack != null) {
                    actionPack.setSneaking(false);
                }
            }
        }

        // Check for solid wall in front of bot
        BlockPos frontFoot = new BlockPos(checkX, botY, checkZ);
        BlockPos frontHead = new BlockPos(checkX, botY + 1, checkZ);
        BlockState footState = world.getBlockState(frontFoot);
        BlockState headState = world.getBlockState(frontHead);

        if (footState.isSolid()) {
            // Check if it is a 1-block step that can be jumped over
            BlockPos aboveHead = bot.getBlockPos().up(2);
            if (!headState.isSolid() && !world.getBlockState(aboveHead).isSolid()) {
                // Jump over 1-block obstacle
                if (actionPack != null) {
                    actionPack.start(ActionType.JUMP, Action.once());
                } else {
                    executeCarpetCommand(server, String.format("player %s jump once", botName));
                }
            } else {
                // Wall is 2+ blocks tall: steer around it!
                steerAroundObstacle(effectiveYaw);
                return;
            }
        }

        // Apply movement
        if (actionPack != null) {
            actionPack.look(effectiveYaw, 0.0f);
            actionPack.setForward(1.0f);
            actionPack.setSprinting(true);
        } else {
            executeCarpetCommand(server, String.format("player %s look %f 0", botName, effectiveYaw));
            executeCarpetCommand(server, String.format("player %s sprint true", botName));
            executeCarpetCommand(server, String.format("player %s move forward", botName));
        }
    }

    /**
     * Steers the bot around an impassable obstacle or unbridgeable gap.
     */
    private void steerAroundObstacle(float baseYaw) {
        ticksSinceLastTurn = 0;
        // Alternate steering direction to avoid getting caught in corners
        if (Math.abs(currentSteeringOffset) < 10.0f) {
            currentSteeringOffset = 65.0f;
        } else if (currentSteeringOffset > 0) {
            currentSteeringOffset = Math.min(currentSteeringOffset + 25.0f, 120.0f);
        } else {
            currentSteeringOffset = Math.max(currentSteeringOffset - 25.0f, -120.0f);
        }
    }

    /**
     * Attempts to place a block into the gap ahead using the bot's inventory.
     */
    private boolean tryBridge(ServerPlayerEntity bot, ServerWorld world, BlockPos targetPos, float yaw,
                              EntityPlayerActionPack actionPack, MinecraftServer server) {
        // Find suitable bridging block in bot's inventory
        int bridgeSlot = findBridgingSlot(bot);
        if (bridgeSlot == -1) {
            return false;
        }

        isBridging = true;

        // Switch to slot if in hotbar
        if (bridgeSlot < 9) {
            bot.getInventory().setSelectedSlot(bridgeSlot);
            if (actionPack != null) {
                actionPack.setSlot(bridgeSlot + 1);
            } else {
                executeCarpetCommand(server, String.format("player %s hotbar %d", botName, bridgeSlot + 1));
            }
        }

        // Crouch to prevent falling over the edge
        if (actionPack != null) {
            actionPack.setSneaking(true);
            actionPack.look(yaw, 70.0f);
            actionPack.start(ActionType.USE, Action.once());
        } else {
            executeCarpetCommand(server, String.format("player %s sneak true", botName));
            executeCarpetCommand(server, String.format("player %s look %f 70", botName, yaw));
            executeCarpetCommand(server, String.format("player %s use once", botName));
        }

        // Directly place block if air remains, ensuring reliable server-side bridge
        if (world.getBlockState(targetPos).isAir()) {
            ItemStack stack = bot.getInventory().getStack(bridgeSlot);
            if (CarpetUtilityConfig.isBridgeable(stack)) {
                if (stack.getItem() instanceof net.minecraft.item.BlockItem blockItem) {
                    world.setBlockState(targetPos, blockItem.getBlock().getDefaultState());
                    if (!bot.isCreative()) {
                        stack.decrement(1);
                    }
                }
            }
        }

        return true;
    }

    /**
     * Finds the first inventory slot containing a valid bridging block.
     */
    private int findBridgingSlot(ServerPlayerEntity bot) {
        // Check hotbar first (0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (CarpetUtilityConfig.isBridgeable(stack)) {
                return i;
            }
        }
        // Check rest of inventory (9-35)
        for (int i = 9; i < bot.getInventory().size(); i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (CarpetUtilityConfig.isBridgeable(stack)) {
                return i;
            }
        }
        return -1;
    }

    public void stop() {
        this.arrived = true;
    }

    private void stopMovement(ServerPlayerEntity bot, MinecraftServer server) {
        EntityPlayerActionPack actionPack = getActionPack(bot);
        if (actionPack != null) {
            actionPack.stopMovement();
            actionPack.setSneaking(false);
            actionPack.setSprinting(false);
        } else {
            executeCarpetCommand(server, String.format("player %s stop", botName));
        }
    }

    private void executeCarpetCommand(MinecraftServer server, String command) {
        if (server != null) {
            server.getCommandManager().parseAndExecute(server.getCommandSource(), command);
        }
    }
}
