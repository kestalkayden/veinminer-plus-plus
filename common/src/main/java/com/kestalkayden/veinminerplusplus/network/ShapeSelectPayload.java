package com.kestalkayden.veinminerplusplus.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.kestalkayden.veinminerplusplus.VeinMinerPlus;
import com.kestalkayden.veinminerplusplus.core.MineShape;

/**
 * C2S packet: the client tells the server which {@link MineShape} the player has selected.
 *
 * <p>The payload carries the {@link MineShape#ordinal()} as a single byte. The server clamps
 * the received value to the valid ordinal range before calling {@link
 * com.kestalkayden.veinminerplusplus.core.ShapeState#set}. Defined in common so it compiles
 * against both loaders' classpaths; registered differently per loader (see the Fabric and
 * NeoForge client entrypoints). */
public record ShapeSelectPayload(int shapeOrdinal) implements CustomPacketPayload {

    /** Wire ID. */
    public static final CustomPacketPayload.Type<ShapeSelectPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(VeinMinerPlus.MOD_ID, "shape_select"));

    /**
     * StreamCodec over {@link RegistryFriendlyByteBuf}, as required by Fabric's
     * {@code serverboundPlay()} registry and NeoForge's {@code playToServer()} registrar.
     * Encodes/decodes a single {@code VarInt} holding the ordinal.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, ShapeSelectPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeVarInt(payload.shapeOrdinal()),
                    buf -> new ShapeSelectPayload(buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<ShapeSelectPayload> type() {
        return TYPE;
    }
}
