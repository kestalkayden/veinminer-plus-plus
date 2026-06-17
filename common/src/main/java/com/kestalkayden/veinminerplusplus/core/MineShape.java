package com.kestalkayden.veinminerplusplus.core;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** The selectable mining modes, cycled with the {@code [} / {@code ]} keys.
 *
 *  <ul>
 *    <li>{@link #VEIN} (default) and {@link #SPREAD} are connectivity floods — they follow
 *        connected matching blocks (same block, ore family, or tree). They differ only in their
 *        block cap (Vein is small/everyday, Spread is large) and Spread is gated behind config.
 *    <li>{@link #CUBE_3} is an oriented 3x3x3 box: the block the player broke sits on the near
 *        face and the box extends into the surface along the look direction. No vein extension.
 *    <li>{@link #CUBE_5} (5x5x5) and {@link #HALL} (9 wide x 3 tall x 9 deep) are larger oriented
 *        boxes, gated behind the "enable extra shapes" config toggle. Like {@link #CUBE_3} they
 *        break their full volume (no cap — the shape is its own bound), staggered by blocksPerTick.
 *  </ul> */
public enum MineShape {

    VEIN("Vein", false, Gate.NONE, 0, 0, 0),
    CUBE_3("3x3x3", true, Gate.NONE, 3, 3, 3),
    CUBE_5("5x5x5", true, Gate.EXTRA, 5, 5, 5),
    HALL("9x9x3", true, Gate.EXTRA, 9, 3, 9),
    SPREAD("Spread", false, Gate.SPREAD, 0, 0, 0);

    /** Which config toggle, if any, gates a shape's appearance in the [ / ] cycle. */
    public enum Gate { NONE, SPREAD, EXTRA }

    public final String label;
    /** True for the oriented cuboid; false for connectivity floods. */
    public final boolean box;
    /** Which config toggle gates this shape in the cycle ({@link Gate#NONE} = always shown). */
    public final Gate gate;
    public final int width;
    public final int height;
    public final int depth;

    MineShape(String label, boolean box, Gate gate, int width, int height, int depth) {
        this.label = label;
        this.box = box;
        this.gate = gate;
        this.width = width;
        this.height = height;
        this.depth = depth;
    }

    public boolean isBox() {
        return box;
    }

    /** The ordered cycle list. Spread appears only when {@code includeSpread}; the extra box
     *  shapes (5x5x5, 9x9x3) appear only when {@code includeExtra}. */
    public static List<MineShape> cycle(boolean includeSpread, boolean includeExtra) {
        List<MineShape> list = new ArrayList<>();
        for (MineShape shape : values()) {
            boolean show = switch (shape.gate) {
                case NONE -> true;
                case SPREAD -> includeSpread;
                case EXTRA -> includeExtra;
            };
            if (show) list.add(shape);
        }
        return list;
    }

    /** Positions of the oriented cuboid: the broken block ({@code origin}) is on the near face,
     *  and the box runs {@code depth} blocks along {@code depthDir} (the player's look direction),
     *  centered in width/height around the origin. Empty for non-box modes. The origin is included;
     *  the caller drops it (vanilla already broke it). */
    public List<BlockPos> positions(BlockPos origin, Direction depthDir) {
        if (!box) {
            return List.of();
        }
        Direction widthDir;
        Direction heightDir;
        switch (depthDir.getAxis()) {
            case Y -> { widthDir = Direction.EAST; heightDir = Direction.SOUTH; }  // looking up/down
            case X -> { widthDir = Direction.SOUTH; heightDir = Direction.UP; }     // looking east/west
            default -> { widthDir = Direction.EAST; heightDir = Direction.UP; }     // looking north/south
        }
        int wHalf = (width - 1) / 2;
        int hHalf = (height - 1) / 2;
        List<BlockPos> out = new ArrayList<>(width * height * depth);
        for (int d = 0; d < depth; d++) {
            BlockPos slice = origin.relative(depthDir, d);
            for (int w = -wHalf; w <= wHalf; w++) {
                for (int h = -hHalf; h <= hHalf; h++) {
                    out.add(slice.relative(widthDir, w).relative(heightDir, h));
                }
            }
        }
        return out;
    }

    /** AABB tightly enclosing the cuboid (world coords), for the edge guide. A single-block box at
     *  {@code origin} for non-box modes (the caller guards on {@link #isBox()}). */
    public AABB bounds(BlockPos origin, Direction depthDir) {
        if (!box) {
            return new AABB(origin);
        }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (BlockPos p : positions(origin, depthDir)) {
            if (p.getX() < minX) minX = p.getX();
            if (p.getY() < minY) minY = p.getY();
            if (p.getZ() < minZ) minZ = p.getZ();
            if (p.getX() + 1 > maxX) maxX = p.getX() + 1;
            if (p.getY() + 1 > maxY) maxY = p.getY() + 1;
            if (p.getZ() + 1 > maxZ) maxZ = p.getZ() + 1;
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
