package com.kestalkayden.veinminerplusplus;

import org.lwjgl.glfw.GLFW;

import com.kestalkayden.veinminerplusplus.client.ShapeGuideRenderer;
import com.kestalkayden.veinminerplusplus.core.ClientShapeState;
import com.kestalkayden.veinminerplusplus.core.ClientState;
import com.kestalkayden.veinminerplusplus.core.ShapeState;
import com.kestalkayden.veinminerplusplus.core.VeinMinerConfig;
import com.kestalkayden.veinminerplusplus.network.ActivationHeldPayload;
import com.kestalkayden.veinminerplusplus.network.ShapeSelectPayload;
import com.kestalkayden.veinminerplusplus.network.ToggleEnabledPayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/**
 * Fabric client entrypoint — runs only on the client dist.
 *
 * <p>Responsibilities (client-only; the serverbound payloads + receivers live in the main initializer):
 * <ol>
 *   <li>Register the four keybinds (under a string category) via {@link KeyBindingHelper}: the
 *       shape-cycle pair, the rebindable "Vein-mine (hold)" activation key, and the on/off toggle.
 *   <li>Poll the keys each client tick, cycle the local shape, write to {@link ClientShapeState},
 *       display an action-bar message, and send a {@link ShapeSelectPayload} to the server.
 *   <li>Flip {@link ClientState#enabled} on the toggle keybind, print a client-only chat line, and
 *       mirror the value to the server (also resynced on every fresh connection).
 *   <li>Report the activation keybind's held-state to the server, edge-triggered.
 *   <li>Register the {@link WorldRenderEvents#AFTER_TRANSLUCENT} callback that delegates to
 *       {@link ShapeGuideRenderer} for the xray-esque cuboid outline.
 * </ol>
 */
public class VeinMinerPlusFabricClient implements ClientModInitializer {

    // -------------------------------------------------------------------------
    // Keybind category
    // -------------------------------------------------------------------------

    /** Keybind category translation key — shown as "Veinminer++" in Controls (1.21.1 uses a
     *  string category; the typed {@code KeyMapping.Category} arrived in 26.x). */
    private static final String CATEGORY = "key.category.veinminerplusplus.main";

    // -------------------------------------------------------------------------
    // Key mappings
    // -------------------------------------------------------------------------

    /** Cycle to the previous shape. Default: [ */
    public static final KeyMapping KEY_PREV_SHAPE = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "key.veinminerplusplus.shape_prev",
                    GLFW.GLFW_KEY_LEFT_BRACKET,
                    CATEGORY));

    /** Cycle to the next shape. Default: ] */
    public static final KeyMapping KEY_NEXT_SHAPE = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "key.veinminerplusplus.shape_next",
                    GLFW.GLFW_KEY_RIGHT_BRACKET,
                    CATEGORY));

    /** Rebindable vein-mine activation (hold while breaking). Default: unbound. */
    public static final KeyMapping KEY_ACTIVATE = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "key.veinminerplusplus.activate",
                    GLFW.GLFW_KEY_UNKNOWN,
                    CATEGORY));

    /** Toggle vein-mining on/off for this client. Default: unbound. */
    public static final KeyMapping KEY_TOGGLE = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "key.veinminerplusplus.toggle",
                    GLFW.GLFW_KEY_UNKNOWN,
                    CATEGORY));

    // -------------------------------------------------------------------------
    // ClientModInitializer
    // -------------------------------------------------------------------------

    @Override
    public void onInitializeClient() {
        // The serverbound payload type + receiver are registered in the main initializer
        // (VeinMinerPlusFabric) so a dedicated server has them too; the client only sends.

        // Poll both cycle keys at the end of each client tick.
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        // Register the world-render callback for the shape guide outline.
        // AFTER_TRANSLUCENT fires after translucent geometry is drawn — the correct stage for a
        // translucent-blended overlay that must appear in front of the world. The renderer draws
        // in immediate mode (RenderSystem + Tesselator), so it only needs the frame PoseStack.
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> ShapeGuideRenderer.render(ctx.matrixStack()));
    }

    // -------------------------------------------------------------------------
    // Tick handler
    // -------------------------------------------------------------------------

    private void onClientTick(Minecraft client) {
        // (Re)sync per-connection client state the moment we connect, so a long-running dedicated
        // server never keeps a stale toggle from a previous session.
        boolean connected = client.player != null && client.getConnection() != null;
        if (connected && !ClientState.wasConnected) {
            sendToggle(ClientState.enabled);
            ClientState.activationHeldSent = false;   // force an activation re-sync below
        }
        ClientState.wasConnected = connected;

        // consumeClick() returns true once per queued press, so holding the key for
        // multiple ticks will cycle the shape only as many times as presses were queued.
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

        if (client.player == null) return;

        if (changed) {
            // Stamp the cycle time so the guide knows to show briefly.
            if (client.level != null) {
                ClientShapeState.lastCycleGameTime = client.level.getGameTime();
            }

            // Show the active shape name on the action bar (the hotbar-area overlay line).
            client.player.displayClientMessage(
                    Component.translatable("veinminerplusplus.shape", ClientShapeState.current.label), true);

            // Tell the server what we picked (1.20.1 channel send).
            FriendlyByteBuf buf = PacketByteBufs.create();
            ShapeSelectPayload.write(buf, ClientShapeState.current.ordinal());
            ClientPlayNetworking.send(ShapeSelectPayload.CHANNEL, buf);
        }

        // On/off toggle — one flip per press, with a client-only chat line.
        while (KEY_TOGGLE.consumeClick()) {
            ClientState.enabled = !ClientState.enabled;
            // displayClientMessage(..., false) puts this in the chat log (not the action bar) and
            // never touches the network, so only this player sees the on/off feedback.
            client.player.displayClientMessage(
                    Component.translatable(ClientState.enabled
                            ? "veinminerplusplus.toggle.enabled"
                            : "veinminerplusplus.toggle.disabled"), false);
            sendToggle(ClientState.enabled);
        }

        // Rebindable activation key — report held-state changes (edge-triggered).
        boolean held = KEY_ACTIVATE.isDown();
        if (held != ClientState.activationHeldSent) {
            ClientState.activationHeldSent = held;
            sendActivationHeld(held);
        }
    }

    /** Send the master on/off toggle (1.20.1 channel send — see {@link ShapeSelectPayload} for the
     *  general networking note). */
    private static void sendToggle(boolean enabled) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        ToggleEnabledPayload.write(buf, enabled);
        ClientPlayNetworking.send(ToggleEnabledPayload.CHANNEL, buf);
    }

    /** Send the rebindable activation key's held-state (1.20.1 channel send). */
    private static void sendActivationHeld(boolean held) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        ActivationHeldPayload.write(buf, held);
        ClientPlayNetworking.send(ActivationHeldPayload.CHANNEL, buf);
    }
}
