package com.kestalkayden.veinminerplusplus.client;

import org.lwjgl.glfw.GLFW;

import com.kestalkayden.veinminerplusplus.VeinMinerPlus;
import com.kestalkayden.veinminerplusplus.core.ClientShapeState;
import com.kestalkayden.veinminerplusplus.core.MineShape;
import com.kestalkayden.veinminerplusplus.core.ShapeState;
import com.kestalkayden.veinminerplusplus.core.VeinMinerConfig;
import com.kestalkayden.veinminerplusplus.network.ShapeSelectPayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
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
 * {@link net.neoforged.fml.loading.FMLEnvironment#getDist()} check so that this class (and its
 * client-only imports) is never loaded on a dedicated server.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Register the keybind category and the two key mappings via {@link RegisterKeyMappingsEvent}.
 *   <li>Poll the keys each {@link ClientTickEvent.Post}, cycle the local shape, and write to
 *       {@link ClientShapeState}.
 *   <li>Show an action-bar overlay message and send {@link ShapeSelectPayload} to the server via
 *       {@code Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(...))}
 *       — {@code PacketDistributor.sendToServer} is removed in 26.1.
 *   <li>Subscribe to {@link RenderLevelStageEvent.AfterTranslucentFeatures} (the specific subclass,
 *       not the abstract base — {@code RenderLevelStageEvent} is abstract in 26.1 and has no
 *       {@code getStage()} check) and delegate to {@link ShapeGuideRenderer}.
 *   <li>Register the mod config screen via {@link IConfigScreenFactory} so the mod-list
 *       "Config" button opens the hand-built {@link VeinMinerPlusConfigScreen}.
 * </ol>
 */
public final class VeinMinerPlusNeoForgeClient {

    // -------------------------------------------------------------------------
    // Keybind category
    // -------------------------------------------------------------------------

    /** Category shown as "Veinminer++" in the Controls screen. */
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(VeinMinerPlus.MOD_ID, "main"));

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
        // Subscribe to the specific RenderLevelStageEvent subclass.
        // In NeoForge 26.1 the base class is abstract — subscribing to it would not fire.
        // AfterTranslucentFeatures is the correct stage for a translucent overlay.
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
        // Register the category first so the Controls screen can group the mappings under it.
        event.registerCategory(CATEGORY);
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
                    ClientShapeState.current, -1, VeinMinerConfig.enableSpread);
            changed = true;
        }
        while (KEY_NEXT_SHAPE.consumeClick()) {
            ClientShapeState.current = ShapeState.cycle(
                    ClientShapeState.current, +1, VeinMinerConfig.enableSpread);
            changed = true;
        }

        if (changed && client.player != null) {
            // Stamp the cycle time so the guide knows to show briefly.
            if (client.level != null) {
                ClientShapeState.lastCycleGameTime = client.level.getGameTime();
            }

            // Show the chosen shape on the action bar overlay.
            client.player.sendOverlayMessage(
                    Component.translatable("veinminerplusplus.shape", ClientShapeState.current.label));

            // Send C2S packet. PacketDistributor.sendToServer() is removed in NeoForge 26.1;
            // the correct approach is to wrap the payload in ServerboundCustomPayloadPacket and
            // send it directly via the active connection.
            if (client.getConnection() != null) {
                client.getConnection().send(
                        new ServerboundCustomPayloadPacket(
                                new ShapeSelectPayload(ClientShapeState.current.ordinal())));
            }
        }
    }

    /**
     * Subscribes to {@link RenderLevelStageEvent.AfterTranslucentFeatures} — the specific
     * subclass, not the abstract base.  No {@code getStage()} guard needed in 26.1.
     *
     * <p>We obtain the {@link MultiBufferSource} from {@code Minecraft.getInstance().renderBuffers()
     * .bufferSource()} because the NeoForge event does not carry one.  We must call
     * {@code endBatch()} on the render type we used so the GPU buffer is flushed before the frame
     * continues — otherwise the lines may not appear until the next frame (or later).
     */
    private static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentFeatures event) {
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        // Delegate to the shared renderer (common code, no loader imports).
        ShapeGuideRenderer.render(event.getPoseStack(), bufferSource);

        // Flush immediately so the lines are submitted before the next render pass.
        bufferSource.endBatch();
    }
}
