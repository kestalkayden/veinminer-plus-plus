package com.kestalkayden.veinminerplusplus.client;

import com.kestalkayden.veinminerplusplus.config.VeinMinerPlusConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import me.shedaniel.autoconfig.AutoConfigClient;

/**
 * ModMenu integration — supplies the "Config" button on the Fabric mod list screen.
 *
 * <p>Registered under the {@code "modmenu"} entrypoint in {@code fabric.mod.json}.
 * This class is client-only: it imports {@link AutoConfigClient} (which references
 * {@code Screen}), so it must never load on a dedicated server. Fabric's client entrypoint
 * loading guarantees that; the {@code "modmenu"} entrypoint is also client-only.
 */
public class VeinMinerPlusModMenu implements ModMenuApi {

    /**
     * Returns a factory that opens the Cloth AutoConfig screen for {@link VeinMinerPlusConfig}.
     *
     * <p>{@code AutoConfigClient.getConfigScreen(cls, parent)} returns a
     * {@code Supplier<Screen>} — we call {@code .get()} to unwrap it immediately.
     */
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> AutoConfigClient.getConfigScreen(VeinMinerPlusConfig.class, parent).get();
    }
}
