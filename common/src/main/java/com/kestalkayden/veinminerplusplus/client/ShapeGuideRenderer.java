package com.kestalkayden.veinminerplusplus.client;

import com.kestalkayden.veinminerplusplus.core.ClientShapeState;
import com.kestalkayden.veinminerplusplus.core.MineShape;
import com.kestalkayden.veinminerplusplus.core.VeinMinerConfig;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Renders an xray-esque (depth-test-disabled) outline of the currently selected cuboid shape
 * centred on the block the player is looking at.  Call {@link #render} from both loaders'
 * AFTER_TRANSLUCENT_FEATURES / SubmitCustomGeometryEvent hook.
 *
 * <h3>26.2 render pipeline</h3>
 *
 * <p>In 26.2, immediate-mode buffer sources ({@code MultiBufferSource}, {@code ShapeRenderer}) are
 * gone.  Geometry is submitted to the frame graph through a {@link SubmitNodeCollector} obtained
 * from the Fabric {@code LevelRenderContext} or the NeoForge {@code SubmitCustomGeometryEvent}.
 *
 * <p>We use {@link SubmitNodeCollector#submitShapeOutline} with {@code xray=true} (last {@code boolean}
 * parameter) which disables the depth test so the outline is visible through walls — the same
 * xray effect the 26.1 version achieved via a custom {@code RenderPipeline}.  No custom pipeline
 * registration is required.
 *
 * <h3>Visibility rules (checked every frame in {@link #render})</h3>
 * <ol>
 *   <li>The local player must be in-world and holding Sneak (the vein-miner activation key).
 *   <li>The selected shape must be a cuboid (not {@link MineShape#isVein()}).
 *   <li>The crosshair must be aimed at a block ({@code hitResult instanceof BlockHitResult} with
 *       type {@code BLOCK}).
 *   <li>If {@code VeinMinerConfig.alwaysShowGuide} is {@code false}, the guide is only shown
 *       within {@value #GUIDE_TICKS} ticks of the last cycle key press.
 * </ol>
 */
public final class ShapeGuideRenderer {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** Ticks after a cycle key press during which the guide remains visible when
     *  {@code VeinMinerConfig.alwaysShowGuide == false}. */
    private static final long GUIDE_TICKS = 40L;

    /**
     * Color of the guide outline — cyan with 50 % alpha.
     * Encoded as a packed {@code 0xAARRGGBB} int as accepted by
     * {@link SubmitNodeCollector#submitShapeOutline}.
     */
    private static final int GUIDE_COLOR_ARGB = 0x8030E0FF;  // α=0x80, r=0x30, g=0xE0, b=0xFF

    /** Line width passed to {@link SubmitNodeCollector#submitShapeOutline}. */
    private static final float LINE_WIDTH = 2.0f;

    // -------------------------------------------------------------------------
    // Private constructor — all methods are static
    // -------------------------------------------------------------------------

    private ShapeGuideRenderer() {}

    // -------------------------------------------------------------------------
    // Public entry point (called by both loaders' render hooks)
    // -------------------------------------------------------------------------

    /**
     * Evaluate visibility conditions and, if met, draw the cuboid outline.
     *
     * <p>The caller supplies the {@link SubmitNodeCollector} and the {@link PoseStack} obtained
     * from the render event.  The PoseStack is identity at the camera origin; the
     * {@link VoxelShape} is submitted in world coordinates.  {@code submitShapeOutline} with
     * {@code xray=true} handles camera-relative projection and depth-test disabling internally.
     *
     * @param collector  the node collector from the current frame's render event
     * @param poseStack  the frame PoseStack (camera-relative, identity at call time)
     */
    public static void render(SubmitNodeCollector collector, PoseStack poseStack) {
        Minecraft mc = Minecraft.getInstance();

        // ---- 1. Require a live in-world client player -----------------------------------
        if (mc.player == null || mc.level == null) return;

        // ---- 2. Activation key: Sneak must be held ------------------------------------
        if (!mc.options.keyShift.isDown()) return;

        // ---- 3. Selected shape must be a box (only the cuboid has an edge to preview) -
        MineShape shape = ClientShapeState.current;
        if (!shape.isBox()) return;

        // ---- 4. Crosshair must be aimed at a block ------------------------------------
        if (!(mc.hitResult instanceof BlockHitResult bhr)) return;
        if (bhr.getType() != HitResult.Type.BLOCK) return;

        // ---- 5. Time-based visibility when alwaysShowGuide is false -------------------
        if (!VeinMinerConfig.alwaysShowGuide) {
            long gameTime = mc.level.getGameTime();
            long elapsed  = gameTime - ClientShapeState.lastCycleGameTime;
            // lastCycleGameTime starts at -1, so elapsed is always huge until the first cycle.
            if (elapsed < 0 || elapsed > GUIDE_TICKS) return;
        }

        // ---- Compute the oriented bounding box ----------------------------------------
        BlockPos origin  = bhr.getBlockPos();
        // Direction.getApproximateNearest returns the cardinal direction closest to the
        // view vector — the same orientation VeinMiner uses to lay out the cuboid.
        Direction depthDir = Direction.getApproximateNearest(mc.player.getViewVector(1.0f));
        AABB bounds = shape.bounds(origin, depthDir);

        // ---- Submit the outline via the 26.2 node collector ---------------------------
        // submitShapeOutline takes a world-space VoxelShape.  The PoseStack is at camera
        // origin (identity).  xray=true disables the depth test so lines show through walls.
        // RenderTypes.lines() is the vanilla lines render type (unchanged from 26.1).
        VoxelShape voxelShape = Shapes.create(bounds);
        collector.submitShapeOutline(poseStack, voxelShape, RenderTypes.lines(), GUIDE_COLOR_ARGB, LINE_WIDTH, true);
    }
}
