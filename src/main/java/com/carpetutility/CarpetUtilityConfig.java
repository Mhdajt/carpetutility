package com.carpetutility;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

public class CarpetUtilityConfig {
    public static int permissionLevel = 2;
    public static double arrivalDistance = 1.8;

    /** Allowed usernames for the restricted /namegen command */
    public static final Set<String> NAMEGEN_ALLOWED = Set.of("Fins", "SR888V");

    private static final Set<Block> ALLOWED_BRIDGING_BLOCKS = new HashSet<>();

    public static boolean isBridgeable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (ALLOWED_BRIDGING_BLOCKS.contains(block)) {
                return true;
            }
            // solid full cube and not fluid-like
            return block.getDefaultState().isSolid() && !block.getDefaultState().isLiquid();
        }
        return false;
    }

    public static boolean isAllowedNamegenUser(String username) {
        return NAMEGEN_ALLOWED.contains(username);
    }

    static {
        // Common safe bridging blocks
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.COBBLESTONE);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.STONE);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.DIRT);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.NETHERRACK);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.BLACKSTONE);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.DEEPSLATE);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.COBBLED_DEEPSLATE);
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
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.SANDSTONE);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.RED_SANDSTONE);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.END_STONE);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.OBSIDIAN);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.CRYING_OBSIDIAN);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.BASALT);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.SMOOTH_BASALT);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.TUFF);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.CALCITE);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.ANDESITE);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.DIORITE);
        ALLOWED_BRIDGING_BLOCKS.add(Blocks.GRANITE);
    }
}
