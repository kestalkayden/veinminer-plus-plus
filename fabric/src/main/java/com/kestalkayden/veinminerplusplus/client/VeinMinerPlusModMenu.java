package com.kestalkayden.veinminerplusplus.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenu integration -- supplies the "Config" button on the Fabric mod list screen.
 *
 * <p>Registered under the {@code "modmenu"} entrypoint in {@code fabric.mod.json}.
 * This class is client-only: it references {@link VeinMinerPlusConfigScreen} (which imports
 * {@code Screen}), so it must never load on a dedicated server. Fabric's modmenu entrypoint
 * loading guarantees that.
 */
public class VeinMinerPlusModMenu implements ModMenuApi {

    /**
     * Returns a factory that opens the hand-built vanilla config screen.
     */
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new VeinMinerPlusConfigScreen(parent);
    }
}
