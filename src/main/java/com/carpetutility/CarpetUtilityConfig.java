package com.carpetutility;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * Configuration and helpers for CarpetUtility.
 */
public class CarpetUtilityConfig {
    /**
     * Permission level required to execute /carpetutility commands (default 2 for server ops).
     */
    public static int permissionLevel = 2;

    /**
     * Distance in blocks considered 'arrived' at the target.
     */
    public static double arrivalDistance = 1.8;

    /**
     * Default list of solid building blocks suitable for bridging.
     */
    private static final Set<Block> ALLOWED_BRIDGING_BLOCKS = new HashSet<>();

    static {
        // Natural & stone blocks
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.DIRT);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.COBBLESTONE);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.STONE);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.DEEPSLATE);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.COBBLED_DEEPSLATE);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.ANDESITE);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.DIORITE);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.GRANITE);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.TUFF);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.NETHERRACK);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.BLACKSTONE);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.BASALT);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.END_STONE);

        // Planks
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.OAK_PLANKS);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.SPRUCE_PLANKS);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.BIRCH_PLANKS);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.JUNGLE_PLANKS);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.ACACIA_PLANKS);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.DARK_OAK_PLANKS);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.MANGROVE_PLANKS);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.CHERRY_PLANKS);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.BAMBOO_PLANKS);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.CRIMSON_PLANKS);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.WARPED_PLANKS);

        // Bricks
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.BRICKS);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.STONE_BRICKS);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.DEEPSLATE_BRICKS);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.NETHER_BRICKS);
    }

    /**
     * Checks if the item in hand or inventory is suitable for bridging.
     */
    public static boolean isBridgeable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (ALLOWED_BRIDGING_BLOCKS.contains(block)) {
                return true;
            }
            // General fallback: full solid cube, not falling block (not sand/gravel), not air
            return block.getDefaultState().isSolid() && !block.getDefaultState().hasRandomTicks();
        }
        return false;
    }
}
