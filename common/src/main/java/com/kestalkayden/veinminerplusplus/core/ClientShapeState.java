package com.kestalkayden.veinminerplusplus.core;

/**
 * Lightweight client-side holder for the currently selected {@link MineShape} and the game-time
 * at which the last cycle key was pressed.
 *
 * <p>This class lives in common so the per-loader renderer ({@code ShapeGuideRenderer}) can read
 * the state without importing a Fabric or NeoForge class.  Both loader client entrypoints write
 * here whenever the player cycles with {@code [} / {@code ]}.
 *
 * <p>All fields are plain {@code static} — there is exactly one client, so a singleton map is
 * unnecessary overhead.
 */
public final class ClientShapeState {

    /** Currently selected shape.  Starts at VEIN (the flood-fill mode). */
    public static MineShape current = MineShape.VEIN;

    /**
     * The {@code Minecraft.getInstance().level.getGameTime()} value recorded the last time the
     * player pressed a cycle key.  Used to drive the "show briefly after cycle" fade window when
     * {@code VeinMinerConfig.alwaysShowGuide} is {@code false}.
     *
     * <p>Initialised to {@code -1} so the guide is not shown on the very first frame.
     */
    public static long lastCycleGameTime = -1L;

    private ClientShapeState() {}

    /**
     * Record a shape cycle: update {@link #current} and stamp {@link #lastCycleGameTime}.
     *
     * @param shape        the newly selected shape
     * @param gameTime     {@code Minecraft.getInstance().level.getGameTime()} at the cycle tick
     */
    public static void onCycle(MineShape shape, long gameTime) {
        current = shape;
        lastCycleGameTime = gameTime;
    }
}
