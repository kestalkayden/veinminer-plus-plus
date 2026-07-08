package com.kestalkayden.veinminerplusplus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import com.kestalkayden.veinminerplusplus.VeinMinerPlus;

/**
 * C2S packet: the client tells the server whether this player's vein-mining is currently enabled
 * (the on/off toggle keybind) — pre-1.20.5 channel form.
 *
 * <p>The typed {@code CustomPacketPayload} + {@code StreamCodec} system is 1.20.5+; on 1.20.1
 * custom packets are a {@link ResourceLocation} channel carrying a raw {@link FriendlyByteBuf}
 * (see {@link ShapeSelectPayload} for the general note). The server stores the value in {@link
 * com.kestalkayden.veinminerplusplus.core.PlayerState} and skips vein-mining entirely while off.
 * The client also re-sends its current value whenever it connects, so a long-running dedicated
 * server can never keep a stale value from a previous session. Registration is loader-specific
 * (see the Fabric and NeoForge entrypoints); this class only holds the channel id + buffer codec
 * helpers so it compiles in common against vanilla classes.
 */
public final class ToggleEnabledPayload {

    /** Channel / wire id. */
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(VeinMinerPlus.MOD_ID, "toggle_enabled");

    private ToggleEnabledPayload() {}

    /** Write the enabled flag into a packet buffer. */
    public static void write(FriendlyByteBuf buf, boolean enabled) {
        buf.writeBoolean(enabled);
    }

    /** Read the enabled flag from a packet buffer. */
    public static boolean read(FriendlyByteBuf buf) {
        return buf.readBoolean();
    }
}
