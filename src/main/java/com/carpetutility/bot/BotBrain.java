package com.carpetutility.bot;

import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import com.carpetutility.CarpetUtilityConfig;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Bot pathfinding brain.
 * - Walks toward a player or fixed position
 * - Bridges over holes
 * - Mines through solid walls when it cannot go around
 * - Pillars up when the target is above / in the air
 */
public class BotBrain {
    private final String botName;
    private final WalkMode mode;
    private UUID targetPlayerUuid;
    private String targetPlayerName;
    private Vec3d fixedTargetPos;
    private final ServerCommandSource source;

    private boolean arrived = false;
    private boolean isBridging = false;
    private boolean isMining = false;
    private boolean isPillaring = false;
    private double currentDistance = 0.0;
    private int ticksSinceLastTurn = 0;
    private float currentSteeringOffset = 0.0f;
    private int consecutiveSteers = 0;
    private int digTicks = 0;
    private int pillarTicks = 0;

    public BotBrain(String botName, ServerPlayerEntity bot, ServerPlayerEntity targetPlayer, ServerCommandSource source) {
        this.botName = botName;
        this.mode = WalkMode.PLAYER;
        this.targetPlayerUuid = targetPlayer.getUuid();
        this.targetPlayerName = targetPlayer.getName().getString();
        this.source = source;
    }

    public BotBrain(String botName, ServerPlayerEntity bot, Vec3d targetPos, ServerCommandSource source) {
        this.botName = botName;
        this.mode = WalkMode.POS;
        this.fixedTargetPos = targetPos;
        this.source = source;
    }

    public String getBotName() { return botName; }
    public WalkMode getMode() { return mode; }
    public String getTargetPlayerName() { return targetPlayerName; }
    public Vec3d getTargetPos() { return fixedTargetPos; }
    public boolean isArrived() { return arrived; }
    public boolean isBridging() { return isBridging; }
    public boolean isMining() { return isMining; }
    public boolean isPillaring() { return isPillaring; }
    public double getCurrentDistance() { return currentDistance; }
    public ServerCommandSource getSource() { return source; }

    private Vec3d resolveTargetPos(MinecraftServer server) {
        if (mode == WalkMode.PLAYER) {
            ServerPlayerEntity targetPlayer = server.getPlayerManager().getPlayer(targetPlayerUuid);
            if (targetPlayer != null) {
                return new Vec3d(targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ());
            }
            return null;
        }
        return fixedTargetPos;
    }

    private EntityPlayerActionPack getActionPack(ServerPlayerEntity bot) {
        if (bot instanceof ServerPlayerInterface fake) {
            return fake.getActionPack();
        }
        return null;
    }

    public void tick(ServerPlayerEntity bot, MinecraftServer server) {
        Vec3d targetPos = resolveTargetPos(server);
        if (targetPos == null) {
            stopMovement(bot, server);
            arrived = true;
            return;
        }

        ServerWorld world = bot.getEntityWorld();
        Vec3d botPos = new Vec3d(bot.getX(), bot.getY(), bot.getZ());
        double dx = targetPos.x - botPos.x;
        double dz = targetPos.z - botPos.z;
        double dy = targetPos.y - botPos.y;
        currentDistance = Math.sqrt(dx * dx + dz * dz);

        // Arrived (close horizontally and not too far vertically)
        if (currentDistance < CarpetUtilityConfig.arrivalDistance && Math.abs(dy) < 2.5) {
            arrived = true;
            stopMovement(bot, server);
            return;
        }

        EntityPlayerActionPack actionPack = getActionPack(bot);

        // === PILLARING ===
        // Target is significantly above us and we are close horizontally
        if (dy > 1.2 && currentDistance < 2.5) {
            if (tryPillar(bot, world, actionPack, server, dy)) {
                isPillaring = true;
                isMining = false;
                isBridging = false;
                return;
            }
        } else {
            isPillaring = false;
            pillarTicks = 0;
        }

        // Compute look direction toward target
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        ++ticksSinceLastTurn;
        if (ticksSinceLastTurn > 20) {
            currentSteeringOffset *= 0.8f;
            if (Math.abs(currentSteeringOffset) < 1.0f) {
                currentSteeringOffset = 0.0f;
            }
        }
        float effectiveYaw = targetYaw + currentSteeringOffset;
        double rad = Math.toRadians(effectiveYaw);

        int checkX = MathHelper.floor(botPos.x - Math.sin(rad) * 1.0);
        int checkZ = MathHelper.floor(botPos.z + Math.cos(rad) * 1.0);
        int botY = MathHelper.floor(botPos.y);

        // Hole detection for bridging
        BlockPos underStep = new BlockPos(checkX, botY - 1, checkZ);
        BlockState underState = world.getBlockState(underStep);
        boolean isHole = underState.isAir() || underState.isLiquid() || !underState.isSolid();

        if (isHole) {
            boolean bridged = tryBridge(bot, world, underStep, effectiveYaw, actionPack, server);
            if (!bridged) {
                steerAroundObstacle(effectiveYaw);
                consecutiveSteers++;
                if (consecutiveSteers > 8) {
                    tryMineFront(bot, world, checkX, botY, checkZ, effectiveYaw, actionPack, server);
                }
                return;
            }
            consecutiveSteers = 0;
        } else if (isBridging) {
            isBridging = false;
            if (actionPack != null) {
                actionPack.setSneaking(false);
            }
        }

        // Wall / obstacle in front
        BlockPos frontFoot = new BlockPos(checkX, botY, checkZ);
        BlockPos frontHead = new BlockPos(checkX, botY + 1, checkZ);
        BlockState footState = world.getBlockState(frontFoot);
        BlockState headState = world.getBlockState(frontHead);

        if (footState.isSolid()) {
            BlockPos aboveHead = bot.getBlockPos().up(2);
            if (!headState.isSolid() && !world.getBlockState(aboveHead).isSolid()) {
                if (actionPack != null) {
                    actionPack.start(EntityPlayerActionPack.ActionType.JUMP, EntityPlayerActionPack.Action.once());
                } else {
                    executeCarpetCommand(server, String.format("player %s jump once", botName));
                }
                consecutiveSteers = 0;
            } else {
                consecutiveSteers++;
                if (consecutiveSteers > 3 || isMining) {
                    tryMineFront(bot, world, checkX, botY, checkZ, effectiveYaw, actionPack, server);
                    return;
                } else {
                    steerAroundObstacle(effectiveYaw);
                    return;
                }
            }
        } else {
            consecutiveSteers = 0;
            isMining = false;
            digTicks = 0;
        }

        // Normal movement toward target
        if (actionPack != null) {
            actionPack.look(effectiveYaw, 0.0f);
            actionPack.setForward(1.0f);
            actionPack.setSprinting(true);
        } else {
            executeCarpetCommand(server, String.format("player %s look %f 0", botName, effectiveYaw));
            executeCarpetCommand(server, String.format("player %s move forward", botName));
            executeCarpetCommand(server, String.format("player %s sprint true", botName));
        }
    }

    /**
     * Try to dig the blocks that are blocking the path (foot + head level).
     */
    private void tryMineFront(ServerPlayerEntity bot, ServerWorld world, int x, int y, int z,
                              float yaw, EntityPlayerActionPack actionPack, MinecraftServer server) {
        isMining = true;
        digTicks++;

        // Prefer a pickaxe / tool if available
        int toolSlot = findToolSlot(bot);
        if (toolSlot != -1 && toolSlot < 9) {
            bot.getInventory().setSelectedSlot(toolSlot);
            if (actionPack != null) {
                actionPack.setSlot(toolSlot + 1);
            } else {
                executeCarpetCommand(server, String.format("player %s hotbar %d", botName, toolSlot + 1));
            }
        }

        BlockPos digPos = new BlockPos(x, y, z); // foot level first
        BlockState state = world.getBlockState(digPos);
        if (!state.isSolid()) {
            digPos = new BlockPos(x, y + 1, z); // then head
            state = world.getBlockState(digPos);
        }

        if (!state.isSolid()) {
            isMining = false;
            digTicks = 0;
            return;
        }

        // Look at the block and attack
        if (actionPack != null) {
            actionPack.look(yaw, digPos.getY() > bot.getY() ? -20.0f : 20.0f);
            actionPack.start(EntityPlayerActionPack.ActionType.ATTACK, EntityPlayerActionPack.Action.continuous());
        } else {
            executeCarpetCommand(server, String.format("player %s look %f %f", botName, yaw, digPos.getY() > bot.getY() ? -20f : 20f));
            executeCarpetCommand(server, String.format("player %s attack continuous", botName));
        }

        // Safety: stop continuous attack after a while if block still there
        if (digTicks > 80) {
            isMining = false;
            digTicks = 0;
            if (actionPack != null) {
                actionPack.stopAll();
            }
            steerAroundObstacle(yaw);
        }
    }

    /**
     * Pillar up when the target is above the bot.
     */
    private boolean tryPillar(ServerPlayerEntity bot, ServerWorld world, EntityPlayerActionPack actionPack,
                              MinecraftServer server, double dy) {
        int bridgeSlot = findBridgingSlot(bot);
        if (bridgeSlot == -1) {
            return false;
        }

        isPillaring = true;
        pillarTicks++;

        if (bridgeSlot < 9) {
            bot.getInventory().setSelectedSlot(bridgeSlot);
            if (actionPack != null) {
                actionPack.setSlot(bridgeSlot + 1);
            } else {
                executeCarpetCommand(server, String.format("player %s hotbar %d", botName, bridgeSlot + 1));
            }
        }

        BlockPos feet = bot.getBlockPos();
        BlockPos below = feet.down();

        if (actionPack != null) {
            actionPack.look(bot.getYaw(), 70.0f);
            actionPack.setSneaking(true);
            actionPack.start(EntityPlayerActionPack.ActionType.USE, EntityPlayerActionPack.Action.once());
            if (pillarTicks % 8 == 0) {
                actionPack.start(EntityPlayerActionPack.ActionType.JUMP, EntityPlayerActionPack.Action.once());
            }
            actionPack.setForward(0.3f);
        } else {
            executeCarpetCommand(server, String.format("player %s sneak true", botName));
            executeCarpetCommand(server, String.format("player %s look %f 70", botName, bot.getYaw()));
            executeCarpetCommand(server, String.format("player %s use once", botName));
            if (pillarTicks % 8 == 0) {
                executeCarpetCommand(server, String.format("player %s jump once", botName));
            }
        }

        if (world.getBlockState(below).isAir() || !world.getBlockState(below).isSolid()) {
            ItemStack stack = bot.getInventory().getStack(bridgeSlot);
            if (CarpetUtilityConfig.isBridgeable(stack) && stack.getItem() instanceof BlockItem blockItem) {
                world.setBlockState(below, blockItem.getBlock().getDefaultState());
                if (!bot.isCreative()) {
                    stack.decrement(1);
                }
            }
        }

        if (pillarTicks > 120) {
            isPillaring = false;
            pillarTicks = 0;
            return false;
        }
        return true;
    }

    private void steerAroundObstacle(float baseYaw) {
        ticksSinceLastTurn = 0;
        if (Math.abs(currentSteeringOffset) < 10.0f) {
            currentSteeringOffset = 65.0f;
        } else if (currentSteeringOffset > 0.0f) {
            currentSteeringOffset = Math.min(currentSteeringOffset + 25.0f, 120.0f);
        } else {
            currentSteeringOffset = Math.max(currentSteeringOffset - 25.0f, -120.0f);
        }
    }

    private boolean tryBridge(ServerPlayerEntity bot, ServerWorld world, BlockPos targetPos, float yaw,
                              EntityPlayerActionPack actionPack, MinecraftServer server) {
        int bridgeSlot = findBridgingSlot(bot);
        if (bridgeSlot == -1) {
            return false;
        }
        isBridging = true;

        if (bridgeSlot < 9) {
            bot.getInventory().setSelectedSlot(bridgeSlot);
            if (actionPack != null) {
                actionPack.setSlot(bridgeSlot + 1);
            } else {
                executeCarpetCommand(server, String.format("player %s hotbar %d", botName, bridgeSlot + 1));
            }
        }
        if (actionPack != null) {
            actionPack.setSneaking(true);
            actionPack.look(yaw, 70.0f);
            actionPack.start(EntityPlayerActionPack.ActionType.USE, EntityPlayerActionPack.Action.once());
        } else {
            executeCarpetCommand(server, String.format("player %s sneak true", botName));
            executeCarpetCommand(server, String.format("player %s look %f 70", botName, yaw));
            executeCarpetCommand(server, String.format("player %s use once", botName));
        }

        ItemStack stack = bot.getInventory().getStack(bridgeSlot);
        if (world.getBlockState(targetPos).isAir() && CarpetUtilityConfig.isBridgeable(stack)
                && stack.getItem() instanceof BlockItem blockItem) {
            world.setBlockState(targetPos, blockItem.getBlock().getDefaultState());
            if (!bot.isCreative()) {
                stack.decrement(1);
            }
        }
        return true;
    }

    private int findBridgingSlot(ServerPlayerEntity bot) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (CarpetUtilityConfig.isBridgeable(stack)) return i;
        }
        for (int i = 9; i < bot.getInventory().size(); i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (CarpetUtilityConfig.isBridgeable(stack)) return i;
        }
        return -1;
    }

    private int findToolSlot(ServerPlayerEntity bot) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            Item item = stack.getItem();
            if (item == Items.DIAMOND_PICKAXE || item == Items.NETHERITE_PICKAXE
                    || item == Items.IRON_PICKAXE || item == Items.GOLDEN_PICKAXE
                    || item == Items.STONE_PICKAXE || item == Items.WOODEN_PICKAXE) {
                return i;
            }
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isDamageable()) return i;
        }
        return -1;
    }

    public void stop() {
        arrived = true;
    }

    private void stopMovement(ServerPlayerEntity bot, MinecraftServer server) {
        EntityPlayerActionPack actionPack = getActionPack(bot);
        if (actionPack != null) {
            actionPack.stopMovement();
            actionPack.setSneaking(false);
            actionPack.setSprinting(false);
            actionPack.stopAll();
        } else {
            executeCarpetCommand(server, String.format("player %s stop", botName));
        }
        isMining = false;
        isPillaring = false;
        isBridging = false;
    }

    private void executeCarpetCommand(MinecraftServer server, String command) {
        if (server != null) {
            server.getCommandManager().parseAndExecute(server.getCommandSource(), command);
        }
    }

    public enum WalkMode {
        PLAYER,
        POS
    }
}
