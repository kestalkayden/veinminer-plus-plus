package com.kestalkayden.veinminerplusplus.client;

import org.lwjgl.glfw.GLFW;

import com.kestalkayden.veinminerplusplus.core.ClientShapeState;
import com.kestalkayden.veinminerplusplus.core.MineShape;
import com.kestalkayden.veinminerplusplus.core.ShapeState;
import com.kestalkayden.veinminerplusplus.core.VeinMinerConfig;
import com.kestalkayden.veinminerplusplus.network.ShapeSelectPayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge client-side logic -- registered only when the dist is CLIENT.
 *
 * <p>Instantiated by {@link com.kestalkayden.veinminerplusplus.VeinMinerPlusNeoForge} after a
 * {@link net.neoforged.fml.loading.FMLEnvironment#dist} check so that this class (and its
 * client-only imports) is never loaded on a dedicated server.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Register the two key mappings (under a string category) via {@link RegisterKeyMappingsEvent}.
 *   <li>Poll the keys each {@link ClientTickEvent.Post}, cycle the local shape, and write to
 *       {@link ClientShapeState}.
 *   <li>Show an action-bar overlay message and send {@link ShapeSelectPayload} to the server via
 *       {@code Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(...))}.
 *   <li>Subscribe to {@link RenderLevelStageEvent} and, on the
 *       {@link RenderLevelStageEvent.Stage#AFTER_TRANSLUCENT_BLOCKS} stage, delegate to
 *       {@link ShapeGuideRenderer}.
 *   <li>Register the mod config screen via {@link IConfigScreenFactory} so the mod-list
 *       "Config" button opens the hand-built {@link VeinMinerPlusConfigScreen}.
 * </ol>
 */
public final class VeinMinerPlusNeoForgeClient {

    // -------------------------------------------------------------------------
    // Keybind category
    // -------------------------------------------------------------------------

    /** Category translation key shown as "Veinminer++" in the Controls screen (1.21.1 string
     *  category; the typed {@code KeyMapping.Category} arrived in 26.x). */
    private static final String CATEGORY = "key.category.veinminerplusplus.main";

    // -------------------------------------------------------------------------
    // Key mappings
    // -------------------------------------------------------------------------

    /** Cycle to the previous shape. Default: [ */
    public static final KeyMapping KEY_PREV_SHAPE = new KeyMapping(
            "key.veinminerplusplus.shape_prev",
            GLFW.GLFW_KEY_LEFT_BRACKET,
            CATEGORY);

    /** Cycle to the next shape. Default: ] */
    public static final KeyMapping KEY_NEXT_SHAPE = new KeyMapping(
            "key.veinminerplusplus.shape_next",
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            CATEGORY);

    // -------------------------------------------------------------------------
    // Constructor (called once from the mod entrypoint on the CLIENT dist only)
    // -------------------------------------------------------------------------

    public VeinMinerPlusNeoForgeClient(IEventBus modBus, ModContainer modContainer) {
        // Key mapping registration fires on the mod event bus.
        modBus.addListener(VeinMinerPlusNeoForgeClient::onRegisterKeyMappings);

        // Client tick and level render fire on the game/forge event bus.
        NeoForge.EVENT_BUS.addListener(VeinMinerPlusNeoForgeClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(VeinMinerPlusNeoForgeClient::onRenderLevel);

        // Register the config screen factory so the mod-list "Config" button opens our
        // hand-built vanilla screen. IConfigScreenFactory is a client-only NeoForge extension
        // point -- safe here because this constructor only runs when dist == CLIENT.
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, parent) -> new VeinMinerPlusConfigScreen(parent));
    }

    // -------------------------------------------------------------------------
    // Mod-bus listener
    // -------------------------------------------------------------------------

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        // 1.21.1 has no category registration — the string category groups the mappings in Controls.
        event.register(KEY_PREV_SHAPE);
        event.register(KEY_NEXT_SHAPE);
    }

    // -------------------------------------------------------------------------
    // Game-bus listeners
    // -------------------------------------------------------------------------

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();

        boolean changed = false;
        while (KEY_PREV_SHAPE.consumeClick()) {
            ClientShapeState.current = ShapeState.cycle(
                    ClientShapeState.current, -1, VeinMinerConfig.enableSpread, VeinMinerConfig.enableExtraShapes);
            changed = true;
        }
        while (KEY_NEXT_SHAPE.consumeClick()) {
            ClientShapeState.current = ShapeState.cycle(
                    ClientShapeState.current, +1, VeinMinerConfig.enableSpread, VeinMinerConfig.enableExtraShapes);
            changed = true;
        }

        if (changed && client.player != null) {
            // Stamp the cycle time so the guide knows to show briefly.
            if (client.level != null) {
                ClientShapeState.lastCycleGameTime = client.level.getGameTime();
            }

            // Show the chosen shape on the action bar overlay.
            client.player.displayClientMessage(
                    Component.translatable("veinminerplusplus.shape", ClientShapeState.current.label), true);

            // Send C2S packet: wrap the payload in ServerboundCustomPayloadPacket and send it
            // directly via the active connection.
            if (client.getConnection() != null) {
                client.getConnection().send(
                        new ServerboundCustomPayloadPacket(
                                new ShapeSelectPayload(ClientShapeState.current.ordinal())));
            }
        }
    }

    /**
     * On {@link RenderLevelStageEvent}, draw the guide during
     * {@link RenderLevelStageEvent.Stage#AFTER_TRANSLUCENT_BLOCKS} — the correct stage for a
     * translucent overlay that must appear in front of the world. The renderer draws in immediate
     * mode (RenderSystem + Tesselator), so it only needs the frame PoseStack.
     */
    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        ShapeGuideRenderer.render(event.getPoseStack());
    }
}
