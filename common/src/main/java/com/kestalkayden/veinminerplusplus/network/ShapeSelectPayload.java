package com.kestalkayden.veinminerplusplus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import com.kestalkayden.veinminerplusplus.VeinMinerPlus;
import com.kestalkayden.veinminerplusplus.core.MineShape;

/**
 * C2S shape-select message — pre-1.20.5 channel form.
 *
 * <p>The typed {@code CustomPacketPayload} + {@code StreamCodec} system is 1.20.5+; on 1.20.1
 * custom packets are a {@link ResourceLocation} channel carrying a raw {@link FriendlyByteBuf}.
 * The client writes the selected {@link MineShape#ordinal()} as a {@code VarInt} to {@link
 * #CHANNEL}; the server reads it and clamps to the valid range. Registration is loader-specific
 * (see the Fabric entrypoints); this class only holds the channel id + buffer codec helpers so it
 * compiles in common against vanilla classes. */
public final class ShapeSelectPayload {

    /** Channel / wire id. */
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(VeinMinerPlus.MOD_ID, "shape_select");

    private ShapeSelectPayload() {}

    /** Write the shape ordinal into a packet buffer. */
    public static void write(FriendlyByteBuf buf, int shapeOrdinal) {
        buf.writeVarInt(shapeOrdinal);
    }

    /** Read the shape ordinal from a packet buffer. */
    public static int read(FriendlyByteBuf buf) {
        return buf.readVarInt();
    }
}
