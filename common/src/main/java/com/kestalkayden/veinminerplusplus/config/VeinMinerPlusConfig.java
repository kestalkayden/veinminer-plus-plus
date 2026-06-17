package com.kestalkayden.veinminerplusplus.config;

import com.kestalkayden.veinminerplusplus.core.VeinMinerConfig;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.minecraft.world.InteractionResult;

/**
 * Cloth AutoConfig POJO that backs the in-game config screen.
 *
 * <p>The shared core reads from the plain static holder {@link VeinMinerConfig}; after every load
 * or save, {@link #apply()} copies these fields into that holder, keeping the core free of any
 * Cloth dependency. Both loaders call {@link #register()} from their entrypoints.
 */
@Config(name = "veinminerplusplus")
public class VeinMinerPlusConfig implements ConfigData {

    /** Block cap for the everyday Vein mode. */
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 128)
    public int veinMax = 32;

    /** Block cap for the larger, opt-in Spread mode. */
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 1024)
    public int spreadMax = 256;

    /** Block cap when felling a tree — large enough for tall 2x2 giant trees. */
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 512)
    public int treeMax = 256;

    /** When on, logs only fell as a tree if leaves are attached — protects log/wood houses. */
    @ConfigEntry.Gui.Tooltip
    public boolean smartTrees = true;

    /** When on, felling an isolated tree also clears its leaves with decay-equivalent drops. */
    @ConfigEntry.Gui.Tooltip
    public boolean clearLeaves = true;

    /** Blocks broken per server tick when draining the queue (the lenient stagger). */
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 64)
    public int blocksPerTick = 16;

    /** When on, soft blocks (grass/dirt/sand) still need a tool in hand; when off, they can be
     *  vein-mined by hand or any item. Hard blocks always need the correct tool to drop. */
    @ConfigEntry.Gui.Tooltip
    public boolean requireTool = false;

    /** When on, the larger Spread mode is offered in the [ / ] cycle. */
    @ConfigEntry.Gui.Tooltip
    public boolean enableSpread = false;

    /** Durability cost as a percentage of vanilla per block: 100 = vanilla, 0 = free, 150 = 1.5x. */
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 0, max = 150)
    public int durabilityPercent = 80;

    /** When on, the 3x3x3 edge guide is shown continuously while sneaking, not just after cycling. */
    @ConfigEntry.Gui.Tooltip
    public boolean alwaysShowGuide = false;

    /** Copy every field into the shared static holder the core reads. */
    public void apply() {
        VeinMinerConfig.veinMax              = veinMax;
        VeinMinerConfig.spreadMax            = spreadMax;
        VeinMinerConfig.treeMax              = treeMax;
        VeinMinerConfig.smartTrees           = smartTrees;
        VeinMinerConfig.clearLeaves          = clearLeaves;
        VeinMinerConfig.blocksPerTick        = blocksPerTick;
        VeinMinerConfig.requireTool          = requireTool;
        VeinMinerConfig.enableSpread         = enableSpread;
        VeinMinerConfig.durabilityMultiplier = durabilityPercent / 100.0;
        VeinMinerConfig.alwaysShowGuide      = alwaysShowGuide;
    }

    /** Register with Cloth, apply the loaded values, and re-apply after every save. */
    public static void register() {
        AutoConfig.register(VeinMinerPlusConfig.class, GsonConfigSerializer::new);
        AutoConfig.getConfigHolder(VeinMinerPlusConfig.class)
                .registerSaveListener((holder, cfg) -> {
                    cfg.apply();
                    return InteractionResult.SUCCESS;
                });
        AutoConfig.getConfigHolder(VeinMinerPlusConfig.class).getConfig().apply();
    }

    /** The live config instance from the Cloth holder. */
    public static VeinMinerPlusConfig get() {
        return AutoConfig.getConfigHolder(VeinMinerPlusConfig.class).getConfig();
    }
}
