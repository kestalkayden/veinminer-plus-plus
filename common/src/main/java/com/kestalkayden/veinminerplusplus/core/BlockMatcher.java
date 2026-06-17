package com.kestalkayden.veinminerplusplus.core;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
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
 *  candidate, keeping the test to a couple of set lookups.
 *
 *  <h3>26.2 migration note</h3>
 *  <p>Several per-species log/stem tags and per-type ore tags lost their Java constants in 26.2
 *  ({@code BlockTags.OAK_LOGS}, {@code COAL_ORES}, {@code CRIMSON_STEMS}, etc. were removed).
 *  The underlying data-pack tag JSON files still exist in the game jar, so we reconstruct the
 *  {@link TagKey} instances directly via {@link TagKey#create}.  Tags that retained their Java
 *  constants ({@code JUNGLE_LOGS}, {@code PALE_OAK_LOGS}, {@code IRON_ORES}, {@code COPPER_ORES},
 *  {@code GOLD_ORES}) continue to use {@code BlockTags.*} for clarity. */
public final class BlockMatcher {

    // -------------------------------------------------------------------------
    // Equivalence-tag helpers
    // -------------------------------------------------------------------------

    /** Short-hand to create a {@link TagKey} for blocks using a vanilla {@code minecraft:} path. */
    private static TagKey<Block> vanillaBlockTag(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace(path));
    }

    // -------------------------------------------------------------------------
    // Equivalence-tag list
    // -------------------------------------------------------------------------

    /** Ore families + per-species tree (log/stem) families whose members are treated as one vein.
     *
     *  <p>Java constants that still exist in 26.2 use {@code BlockTags.*}; constants removed in
     *  26.2 are reconstructed via {@link #vanillaBlockTag(String)} using the unchanged data-pack
     *  tag path.  Both forms resolve to the same in-game tag at runtime. */
    private static final List<TagKey<Block>> EQUIVALENCE_TAGS = List.of(
        // --- Ore families ---
        // coal_ores, redstone_ores, emerald_ores, lapis_ores, diamond_ores lost their Java
        // constants in 26.2 but the tag JSON files remain in the game data.
        vanillaBlockTag("coal_ores"),
        BlockTags.IRON_ORES,
        BlockTags.COPPER_ORES,
        BlockTags.GOLD_ORES,
        vanillaBlockTag("redstone_ores"),
        vanillaBlockTag("emerald_ores"),
        vanillaBlockTag("lapis_ores"),
        vanillaBlockTag("diamond_ores"),
        // --- Per-species log/stem families ---
        // oak, birch, spruce, acacia, dark_oak, mangrove, cherry logs and crimson/warped stems
        // lost their Java constants in 26.2.  jungle_logs and pale_oak_logs still have constants.
        vanillaBlockTag("oak_logs"),
        vanillaBlockTag("birch_logs"),
        vanillaBlockTag("spruce_logs"),
        BlockTags.JUNGLE_LOGS,
        vanillaBlockTag("acacia_logs"),
        vanillaBlockTag("dark_oak_logs"),
        vanillaBlockTag("mangrove_logs"),
        vanillaBlockTag("cherry_logs"),
        BlockTags.PALE_OAK_LOGS,
        vanillaBlockTag("crimson_stems"),
        vanillaBlockTag("warped_stems"));

    // -------------------------------------------------------------------------
    // Instance state
    // -------------------------------------------------------------------------

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
