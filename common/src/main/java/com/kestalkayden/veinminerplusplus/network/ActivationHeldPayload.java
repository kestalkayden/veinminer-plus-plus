package com.kestalkayden.veinminerplusplus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import com.kestalkayden.veinminerplusplus.VeinMinerPlus;

/**
 * C2S packet: the client reports whether the rebindable "Vein-mine (hold)" activation key is
 * currently held — pre-1.20.5 channel form.
 *
 * <p>The typed {@code CustomPacketPayload} + {@code StreamCodec} system is 1.20.5+; on 1.20.1
 * custom packets are a {@link ResourceLocation} channel carrying a raw {@link FriendlyByteBuf}
 * (see {@link ShapeSelectPayload} for the general note). Sent edge-triggered — only when the held
 * state flips — so it costs at most a couple of bytes per press/release. The server stores it in
 * {@link com.kestalkayden.veinminerplusplus.core.PlayerState}; {@link
 * com.kestalkayden.veinminerplusplus.core.VeinMiner} treats it as an activation alongside Sneak.
 * Registration is loader-specific (see the Fabric and NeoForge entrypoints); this class only holds
 * the channel id + buffer codec helpers so it compiles in common against vanilla classes.
 */
public final class ActivationHeldPayload {

    /** Channel / wire id. */
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(VeinMinerPlus.MOD_ID, "activation_held");

    private ActivationHeldPayload() {}

    /** Write the held flag into a packet buffer. */
    public static void write(FriendlyByteBuf buf, boolean held) {
        buf.writeBoolean(held);
    }

    /** Read the held flag from a packet buffer. */
    public static boolean read(FriendlyByteBuf buf) {
        return buf.readBoolean();
    }
}
