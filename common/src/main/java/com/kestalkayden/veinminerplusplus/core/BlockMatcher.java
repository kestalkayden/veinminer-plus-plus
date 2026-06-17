package com.kestalkayden.veinminerplusplus.core;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Decides whether a candidate block belongs to the same vein as the origin block.
 *
 *  <p>A candidate matches if it is the exact same block as the origin, OR if the origin and
 *  candidate share one of the equivalence tags below. The ore-family tags group the stone and
 *  deepslate variants of an ore; the per-species log/stem tags group a tree's wood — so an axe on
 *  a log fells the whole tree, and a pickaxe on a deepslate ore also takes the touching stone
 *  variant. Using per-species log tags keeps an oak tree from chaining into a neighbouring birch.
 *
 *  <p>The origin's tag memberships are resolved once via {@link #forOrigin} and reused per
 *  candidate, keeping the test to a couple of set lookups. */
public final class BlockMatcher {

    /** Ore families + per-species tree (log/stem) families whose members are treated as one vein. */
    private static final List<TagKey<Block>> EQUIVALENCE_TAGS = List.of(
        BlockTags.COAL_ORES, BlockTags.IRON_ORES, BlockTags.COPPER_ORES, BlockTags.GOLD_ORES,
        BlockTags.REDSTONE_ORES, BlockTags.EMERALD_ORES, BlockTags.LAPIS_ORES, BlockTags.DIAMOND_ORES,
        BlockTags.OAK_LOGS, BlockTags.BIRCH_LOGS, BlockTags.SPRUCE_LOGS, BlockTags.JUNGLE_LOGS,
        BlockTags.ACACIA_LOGS, BlockTags.DARK_OAK_LOGS, BlockTags.MANGROVE_LOGS, BlockTags.CHERRY_LOGS,
        BlockTags.PALE_OAK_LOGS, BlockTags.CRIMSON_STEMS, BlockTags.WARPED_STEMS);

    private final Block originBlock;
    private final List<TagKey<Block>> originGroups;

    private BlockMatcher(Block originBlock, List<TagKey<Block>> originGroups) {
        this.originBlock = originBlock;
        this.originGroups = originGroups;
    }

    /** Build a matcher seeded from the block the player actually broke. */
    public static BlockMatcher forOrigin(BlockState originState) {
        List<TagKey<Block>> groups = new ArrayList<>();
        for (TagKey<Block> tag : EQUIVALENCE_TAGS) {
            if (originState.is(tag)) {
                groups.add(tag);
            }
        }
        return new BlockMatcher(originState.getBlock(), groups);
    }

    /** True if {@code state} is in the same vein as the origin. */
    public boolean matches(BlockState state) {
        if (state.is(originBlock)) {
            return true;
        }
        for (TagKey<Block> tag : originGroups) {
            if (state.is(tag)) {
                return true;
            }
        }
        return false;
    }
}
