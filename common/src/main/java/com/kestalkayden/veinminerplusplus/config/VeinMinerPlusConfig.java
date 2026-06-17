package com.kestalkayden.veinminerplusplus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kestalkayden.veinminerplusplus.core.VeinMinerConfig;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Gson-backed config POJO for Veinminer++.
 *
 * <p>This class is loader-agnostic: it uses only vanilla Minecraft classes and the Gson library
 * (which ships with every MC installation). No Fabric or NeoForge imports are allowed here.
 *
 * <h3>Seam</h3>
 * <p>Each loader entrypoint calls {@link #setConfigDir(Path)} once — before any call to
 * {@link #load()} — passing its loader-specific config directory:
 * <ul>
 *   <li>Fabric: {@code FabricLoader.getInstance().getConfigDir()}
 *   <li>NeoForge: {@code FMLPaths.CONFIGDIR.get()}
 * </ul>
 * The config file is always named {@code veinminerplusplus.json} inside that directory, keeping
 * the same path that Cloth AutoConfig used so existing user configs migrate unchanged.
 *
 * <h3>JSON shape</h3>
 * <p>Field names match the original Cloth AutoConfig POJO, plus {@code enableExtraShapes} (added
 * post-cloth; absent in old configs, so Gson leaves it at its {@code false} default):
 * {@code veinMax}, {@code spreadMax}, {@code treeMax}, {@code smartTrees}, {@code clearLeaves},
 * {@code blocksPerTick}, {@code requireTool}, {@code enableSpread}, {@code enableExtraShapes},
 * {@code durabilityPercent}, {@code alwaysShowGuide}.
 */
public class VeinMinerPlusConfig {

    // -------------------------------------------------------------------------
    // Field definitions + defaults (same values as the original cloth POJO)
    // -------------------------------------------------------------------------

    /** Block cap for the everyday Vein mode. Range: 1-128. */
    public int veinMax = 32;

    /** Block cap for the larger, opt-in Spread mode. Range: 1-1024. */
    public int spreadMax = 256;

    /** Block cap when felling a tree -- large enough for tall 2x2 giant trees. Range: 1-512. */
    public int treeMax = 256;

    /** When on, logs only fell as a tree if leaves are attached -- protects log/wood houses. */
    public boolean smartTrees = true;

    /** When on, felling an isolated tree also clears its leaves with decay-equivalent drops. */
    public boolean clearLeaves = true;

    /** Blocks broken per server tick when draining the queue (the lenient stagger). Range: 1-64. */
    public int blocksPerTick = 16;

    /** When on, soft blocks (grass/dirt/sand) still need a tool in hand; when off, they can be
     *  vein-mined by hand or any item. Hard blocks always need the correct tool to drop. */
    public boolean requireTool = false;

    /** When on, the larger Spread mode is offered in the [ / ] cycle. */
    public boolean enableSpread = false;

    /** When on, the extra box shapes (5x5x5, 9x9x3) are offered in the [ / ] cycle. */
    public boolean enableExtraShapes = false;

    /** Durability cost as a percentage of vanilla per block: 100 = vanilla, 0 = free, 150 = 1.5x.
     *  Range: 0-150. */
    public int durabilityPercent = 80;

    /** When on, the 3x3x3 edge guide is shown continuously while sneaking, not just after cycling. */
    public boolean alwaysShowGuide = false;

    // -------------------------------------------------------------------------
    // Static infrastructure
    // -------------------------------------------------------------------------

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "veinminerplusplus.json";

    /** Set by each loader entrypoint before the first {@link #load()} call. */
    private static Path configDir;

    /** The live singleton instance, populated by {@link #load()}. */
    private static VeinMinerPlusConfig instance = new VeinMinerPlusConfig();

    // -------------------------------------------------------------------------
    // Seam -- called from loader entrypoints
    // -------------------------------------------------------------------------

    /**
     * Injects the loader-specific config directory.
     * Must be called before {@link #load()}.
     *
     * @param dir the directory in which {@code veinminerplusplus.json} resides
     *            (e.g. {@code .minecraft/config/})
     */
    public static void setConfigDir(Path dir) {
        configDir = dir;
    }

    // -------------------------------------------------------------------------
    // Load / save
    // -------------------------------------------------------------------------

    /**
     * Load (or create) {@code veinminerplusplus.json} from the injected config dir, then
     * {@link #apply()} the values into the shared static holder {@link VeinMinerConfig}.
     *
     * <p>If the file does not exist, the defaults are written so the file is self-documenting on
     * first run. If JSON parsing fails for any field the default is silently preserved by Gson.
     */
    public static void load() {
        Path file = resolveFile();
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                VeinMinerPlusConfig loaded = GSON.fromJson(r, VeinMinerPlusConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (IOException e) {
                // Keep defaults; broken file will be overwritten on next save.
            }
        }
        clamp(instance);
        instance.apply();
        // Write the file back to ensure it exists and reflects any new fields.
        writeFile(instance);
    }

    /**
     * Clamp all fields to their documented bounds, copy into {@link VeinMinerConfig}, and persist
     * to disk. Call this after mutating {@link #get()} from the config screen.
     */
    public static void save() {
        clamp(instance);
        instance.apply();
        writeFile(instance);
    }

    /** The live config instance. Mutate fields directly, then call {@link #save()}. */
    public static VeinMinerPlusConfig get() {
        return instance;
    }

    // -------------------------------------------------------------------------
    // Apply into shared static holder
    // -------------------------------------------------------------------------

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
        VeinMinerConfig.enableExtraShapes    = enableExtraShapes;
        VeinMinerConfig.durabilityMultiplier = durabilityPercent / 100.0;
        VeinMinerConfig.alwaysShowGuide      = alwaysShowGuide;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Path resolveFile() {
        if (configDir == null) {
            throw new IllegalStateException(
                    "VeinMinerPlusConfig.setConfigDir() was not called before load()");
        }
        return configDir.resolve(FILE_NAME);
    }

    /** Clamp all bounded int fields to their documented min/max. */
    private static void clamp(VeinMinerPlusConfig c) {
        c.veinMax           = clampInt(c.veinMax,           1,   128);
        c.spreadMax         = clampInt(c.spreadMax,         1,  1024);
        c.treeMax           = clampInt(c.treeMax,           1,   512);
        c.blocksPerTick     = clampInt(c.blocksPerTick,     1,    64);
        c.durabilityPercent = clampInt(c.durabilityPercent, 0,   150);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void writeFile(VeinMinerPlusConfig cfg) {
        Path file = resolveFile();
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file)) {
                GSON.toJson(cfg, w);
            }
        } catch (IOException e) {
            // Non-fatal: the in-memory state is already correct.
        }
    }
}
