package com.kestalkayden.veinminerplusplus.core;

/** Mutable config holder with sane defaults, read by the shared core.
 *
 *  <p>Kept as plain static fields so the core reads it with no loader or library dependency. The
 *  in-game config GUI writes these on load and on change; until then the defaults apply. */
public final class VeinMinerConfig {

    /** Block cap for the everyday Vein mode (kept small so casual vein-mining stays bounded). */
    public static int veinMax = 32;

    /** Block cap for the larger, opt-in Spread mode. */
    public static int spreadMax = 256;

    /** Block cap used when felling a tree (logs), big enough for tall 2x2 giant trees. */
    public static int treeMax = 256;

    /** When true, a log is only tree-felled if leaves are attached to the connected logs —
     *  so log/wood houses (no leaves) are left intact. When false, any connected logs fell. */
    public static boolean smartTrees = true;

    /** When true, felling an isolated tree also clears its leaf canopy, dropping the same loot
     *  leaves give when they decay (saplings/sticks/apples). Shared (non-isolated) canopies are
     *  left to decay normally. */
    public static boolean clearLeaves = true;

    /** Blocks broken per player per server tick — the lenient auto-stagger. */
    public static int blocksPerTick = 16;

    /** Tool policy. The floor is always "never break a block that wouldn't drop for you".
     *  When {@code true} (default), soft blocks (grass/dirt/sand) additionally require holding a
     *  tool; when {@code false}, those collectible blocks can be vein-mined by hand or any item
     *  (early-game / durability-friendly). Hard blocks always need the correct tool to drop. */
    public static boolean requireTool = false;

    /** Whether the Spread mode is offered in the [ / ] cycle. */
    public static boolean enableSpread = false;

    /** Whether the extra box shapes (5x5x5, 9x9x3) are offered in the [ / ] cycle. */
    public static boolean enableExtraShapes = false;

    /** Per-block durability cost multiplier: 0.0 (free) .. 1.5 (50% extra); 1.0 = vanilla. */
    public static double durabilityMultiplier = 0.65;

    /** Always render the shape edge guide while the activation key is held (box modes only). */
    public static boolean alwaysShowGuide = false;

    private VeinMinerConfig() {}
}
