package com.kestalkayden.veinminerplusplus.client;

import com.kestalkayden.veinminerplusplus.config.VeinMinerPlusConfig;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/**
 * Hand-built vanilla config screen for Veinminer++.
 *
 * <p>Extends {@link OptionsSubScreen} — the standard Minecraft base for settings pages — which
 * handles the title widget, the scrolling {@link net.minecraft.client.gui.components.OptionsList},
 * and the Done/Cancel footer automatically. We only implement {@link #addOptions()} to register
 * the 10 mod options, and override {@link #removed()} to persist when the screen is closed.
 *
 * <h3>Loader safety</h3>
 * <p>This class lives in {@code common/src/…/client/}, mirrors the placement of
 * {@link ShapeGuideRenderer}, and imports only vanilla classes. It is never referenced from
 * any server-side code path — only from {@code VeinMinerPlusModMenu} (Fabric) and
 * {@code VeinMinerPlusNeoForgeClient} (NeoForge), both of which are already client-only.
 *
 * <h3>26.2 API used</h3>
 * <ul>
 *   <li>{@link OptionsSubScreen} — abstract base screen with layout, OptionsList, and Done footer.
 *   <li>{@link OptionInstance#createBoolean} — for the five boolean toggles.
 *   <li>{@code new OptionInstance<>(caption, tooltip, toString, IntRange, initialValue, listener)}
 *       with {@link OptionInstance.IntRange} — for the five bounded integer sliders.
 *   <li>{@link OptionInstance#cachedConstantTooltip} — attaches the tooltip text.
 * </ul>
 */
public class VeinMinerPlusConfigScreen extends OptionsSubScreen {

    /** Screen title shown in the header. */
    private static final Component TITLE =
            Component.translatable("veinminerplusplus.config.title");

    /**
     * Create a new config screen.
     *
     * @param parent  the screen to return to when Done is pressed
     * @param options the vanilla {@code Options} object — required by {@link OptionsSubScreen}
     *                but only used for layout; our options are stored in {@link VeinMinerPlusConfig}
     */
    public VeinMinerPlusConfigScreen(Screen parent, Options options) {
        super(parent, options, TITLE);
    }

    // -------------------------------------------------------------------------
    // OptionsSubScreen implementation
    // -------------------------------------------------------------------------

    /**
     * Populate the {@link net.minecraft.client.gui.components.OptionsList} with all 10 options.
     *
     * <p>Called by {@code OptionsSubScreen.addContents()} during screen init, after {@code this.list}
     * has been constructed. Each {@link OptionInstance} is created with the current value from
     * {@link VeinMinerPlusConfig#get()} and a {@link OptionInstance.ValueUpdateListener} that
     * writes back into the config POJO immediately on every slider or toggle change.
     */
    @Override
    protected void addOptions() {
        VeinMinerPlusConfig cfg = VeinMinerPlusConfig.get();

        // ---- Integer sliders (pair them so they sit side-by-side in the two-column layout) ----

        this.list.addSmall(
                // veinMax: 1–128
                new OptionInstance<>(
                        "veinminerplusplus.config.veinMax",
                        OptionInstance.cachedConstantTooltip(
                                Component.translatable("veinminerplusplus.config.veinMax.tooltip")),
                        (caption, val) -> Component.literal(String.valueOf(val)),
                        new OptionInstance.IntRange(1, 128),
                        cfg.veinMax,
                        val -> VeinMinerPlusConfig.get().veinMax = val),

                // spreadMax: 1–1024
                new OptionInstance<>(
                        "veinminerplusplus.config.spreadMax",
                        OptionInstance.cachedConstantTooltip(
                                Component.translatable("veinminerplusplus.config.spreadMax.tooltip")),
                        (caption, val) -> Component.literal(String.valueOf(val)),
                        new OptionInstance.IntRange(1, 1024),
                        cfg.spreadMax,
                        val -> VeinMinerPlusConfig.get().spreadMax = val));

        this.list.addSmall(
                // treeMax: 1–512
                new OptionInstance<>(
                        "veinminerplusplus.config.treeMax",
                        OptionInstance.cachedConstantTooltip(
                                Component.translatable("veinminerplusplus.config.treeMax.tooltip")),
                        (caption, val) -> Component.literal(String.valueOf(val)),
                        new OptionInstance.IntRange(1, 512),
                        cfg.treeMax,
                        val -> VeinMinerPlusConfig.get().treeMax = val),

                // blocksPerTick: 1–64
                new OptionInstance<>(
                        "veinminerplusplus.config.blocksPerTick",
                        OptionInstance.cachedConstantTooltip(
                                Component.translatable("veinminerplusplus.config.blocksPerTick.tooltip")),
                        (caption, val) -> Component.literal(String.valueOf(val)),
                        new OptionInstance.IntRange(1, 64),
                        cfg.blocksPerTick,
                        val -> VeinMinerPlusConfig.get().blocksPerTick = val));

        this.list.addSmall(
                // durabilityPercent: 0–150 (full width to give room for the wide range)
                new OptionInstance<>(
                        "veinminerplusplus.config.durabilityPercent",
                        OptionInstance.cachedConstantTooltip(
                                Component.translatable("veinminerplusplus.config.durabilityPercent.tooltip")),
                        (caption, val) -> Component.translatable("options.percent_value", caption, val),
                        new OptionInstance.IntRange(0, 150),
                        cfg.durabilityPercent,
                        val -> VeinMinerPlusConfig.get().durabilityPercent = val),

                // smartTrees boolean
                OptionInstance.createBoolean(
                        "veinminerplusplus.config.smartTrees",
                        OptionInstance.cachedConstantTooltip(
                                Component.translatable("veinminerplusplus.config.smartTrees.tooltip")),
                        cfg.smartTrees,
                        val -> VeinMinerPlusConfig.get().smartTrees = val));

        this.list.addSmall(
                // clearLeaves boolean
                OptionInstance.createBoolean(
                        "veinminerplusplus.config.clearLeaves",
                        OptionInstance.cachedConstantTooltip(
                                Component.translatable("veinminerplusplus.config.clearLeaves.tooltip")),
                        cfg.clearLeaves,
                        val -> VeinMinerPlusConfig.get().clearLeaves = val),

                // requireTool boolean
                OptionInstance.createBoolean(
                        "veinminerplusplus.config.requireTool",
                        OptionInstance.cachedConstantTooltip(
                                Component.translatable("veinminerplusplus.config.requireTool.tooltip")),
                        cfg.requireTool,
                        val -> VeinMinerPlusConfig.get().requireTool = val));

        this.list.addSmall(
                // enableSpread boolean
                OptionInstance.createBoolean(
                        "veinminerplusplus.config.enableSpread",
                        OptionInstance.cachedConstantTooltip(
                                Component.translatable("veinminerplusplus.config.enableSpread.tooltip")),
                        cfg.enableSpread,
                        val -> VeinMinerPlusConfig.get().enableSpread = val),

                // alwaysShowGuide boolean
                OptionInstance.createBoolean(
                        "veinminerplusplus.config.alwaysShowGuide",
                        OptionInstance.cachedConstantTooltip(
                                Component.translatable("veinminerplusplus.config.alwaysShowGuide.tooltip")),
                        cfg.alwaysShowGuide,
                        val -> VeinMinerPlusConfig.get().alwaysShowGuide = val));
    }

    // -------------------------------------------------------------------------
    // Persistence hook
    // -------------------------------------------------------------------------

    /**
     * Persist the config when the screen is closed (by Done, Escape, or any other navigation).
     *
     * <p>Each {@link OptionInstance.ValueUpdateListener} above has already written its value into
     * the {@link VeinMinerPlusConfig} POJO; {@link VeinMinerPlusConfig#save()} clamps, applies
     * into {@link com.kestalkayden.veinminerplusplus.core.VeinMinerConfig}, and flushes to disk.
     */
    @Override
    public void removed() {
        VeinMinerPlusConfig.save();
        super.removed();
    }
}
