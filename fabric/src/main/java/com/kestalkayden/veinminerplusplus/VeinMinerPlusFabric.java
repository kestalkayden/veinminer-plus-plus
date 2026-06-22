package com.kestalkayden.veinminerplusplus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kestalkayden.veinminerplusplus.config.VeinMinerPlusConfig;
import com.kestalkayden.veinminerplusplus.core.MineShape;
import com.kestalkayden.veinminerplusplus.core.ShapeState;
import com.kestalkayden.veinminerplusplus.core.VeinMiner;
import com.kestalkayden.veinminerplusplus.network.ShapeSelectPayload;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class VeinMinerPlusFabric implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(VeinMinerPlus.MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Veinminer++ (Fabric)");

        // Inject the Fabric config directory and load the Gson-backed config. Must happen before
        // any event listeners that read VeinMinerConfig, so loaded values are in place from the
        // first server tick.
        VeinMinerPlusConfig.setConfigDir(FabricLoader.getInstance().getConfigDir());
        VeinMinerPlusConfig.load();

        // After a player breaks a block on the server, hand it to the shared vein-mine core.
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerLevel level)) return;
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            VeinMiner.onBlockBroken(serverPlayer, level, pos, state);
        });

        // Drain queued veins a few blocks per tick.
        ServerTickEvents.END_SERVER_TICK.register(VeinMiner::tick);

        // Register the C2S shape-select payload and its server receiver HERE (main init), not in
        // the client entrypoint -- so a dedicated server registers them too. The client only sends.
        PayloadTypeRegistry.playC2S().register(
                ShapeSelectPayload.TYPE, ShapeSelectPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(
                ShapeSelectPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    MineShape[] shapes = MineShape.values();
                    int ordinal = Math.max(0, Math.min(payload.shapeOrdinal(), shapes.length - 1));
                    ShapeState.set(player.getUUID(), shapes[ordinal]);
                });
    }
}
