package com.kestalkayden.veinminerplusplus.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-player selected {@link MineShape}, held server-side.
 *
 *  <p>The client cycles with {@code [} / {@code ]} and sends the chosen shape to the server (see
 *  each loader's networking); the server stores it here, and {@link VeinMiner} reads it when the
 *  player breaks a block. Defaults to {@link MineShape#VEIN}. */
public final class ShapeState {

    private static final Map<UUID, MineShape> SELECTED = new HashMap<>();

    private ShapeState() {}

    public static MineShape get(UUID player) {
        return SELECTED.getOrDefault(player, MineShape.VEIN);
    }

    public static void set(UUID player, MineShape shape) {
        SELECTED.put(player, shape);
    }

    public static void clear(UUID player) {
        SELECTED.remove(player);
    }

    /** Cycle from {@code current} by {@code direction} (+1 next, -1 previous) within the
     *  config-allowed list. Used client-side to pick the shape that is then sent to the server. */
    public static MineShape cycle(MineShape current, int direction, boolean includeSpread) {
        List<MineShape> list = MineShape.cycle(includeSpread);
        int index = list.indexOf(current);
        if (index < 0) {
            index = 0;
        }
        return list.get(Math.floorMod(index + direction, list.size()));
    }
}
