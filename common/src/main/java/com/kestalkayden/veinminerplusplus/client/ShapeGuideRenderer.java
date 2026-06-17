package com.kestalkayden.veinminerplusplus.client;

import com.kestalkayden.veinminerplusplus.core.ClientShapeState;
import com.kestalkayden.veinminerplusplus.core.MineShape;
import com.kestalkayden.veinminerplusplus.core.VeinMinerConfig;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Renders an xray-esque (depth-test-disabled) outline of the currently selected cuboid shape
 * centred on the block the player is looking at.  Call {@link #render} from both loaders'
 * AFTER_TRANSLUCENT_FEATURES stage.
 *
 * <h3>Custom RenderType rationale</h3>
 *
 * <p>The vanilla {@code RenderTypes.LINES} constant uses {@link RenderPipelines#LINES}, which
 * applies a standard {@code LESS_THAN_OR_EQUAL} depth test — lines are hidden behind opaque
 * geometry.  To achieve the xray-through-walls effect we need a pipeline whose depth-stencil state
 * uses {@link CompareOp#ALWAYS_PASS} with {@code writeDepth = false}.
 *
 * <p>We build this once ({@link #LINES_NO_DEPTH}) by:
 * <ol>
 *   <li>Calling {@link RenderPipeline#builder(RenderPipeline.Snippet...)} with
 *       {@link RenderPipelines#LINES_SNIPPET} so all shaders, vertex format, blending, and other
 *       state are inherited from the vanilla lines pipeline.</li>
 *   <li>Overriding the depth-stencil state via
 *       {@link RenderPipeline.Builder#withDepthStencilState(DepthStencilState)} to use
 *       {@code ALWAYS_PASS, writeDepth=false} — fragments win regardless of depth (xray).</li>
 *   <li>Assigning a unique location so the pipeline can be looked up in debug tools.</li>
 *   <li>Registering with {@code RenderPipelines.register()} as vanilla does for its own pipelines
 *       (registration makes the pipeline available to Blaze3D's compiled pipeline cache).</li>
 *   <li>Wrapping in a {@link RenderType} via {@link RenderType#create} so the
 *       {@link MultiBufferSource} can batch vertices for it.</li>
 * </ol>
 *
 * <p>The resulting {@link RenderType} accepts the same vertex format as the vanilla lines type
 * ({@code POSITION_COLOR_NORMAL_LINE_WIDTH}) — exactly what {@link ShapeRenderer#renderShape}
 * emits.
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
     * Encoded as a packed {@code 0xAARRGGBB} int as accepted by {@link ShapeRenderer#renderShape}.
     */
    private static final int GUIDE_COLOR_ARGB = 0x8030E0FF;  // α=0x80, r=0x30, g=0xE0, b=0xFF

    /** Line width passed to {@link ShapeRenderer#renderShape}. */
    private static final float LINE_WIDTH = 2.0f;

    // -------------------------------------------------------------------------
    // Custom no-depth render pipeline and RenderType
    // -------------------------------------------------------------------------

    /**
     * A {@link RenderPipeline} identical to the vanilla lines pipeline but with the depth-stencil
     * state overridden to {@link CompareOp#ALWAYS_PASS} + {@code writeDepth=false}.
     *
     * <p>Construction:
     * <ul>
     *   <li>{@link RenderPipeline#builder(RenderPipeline.Snippet...)} with
     *       {@link RenderPipelines#LINES_SNIPPET} seeds the builder with the lines shaders, vertex
     *       format ({@code POSITION_COLOR_NORMAL_LINE_WIDTH}), mode ({@code LINES}), and blend
     *       state.</li>
     *   <li>{@link RenderPipeline.Builder#withDepthStencilState(DepthStencilState)} overrides the
     *       depth rule to ALWAYS_PASS so fragments render regardless of what is in the depth
     *       buffer, and disables depth writes so we don't corrupt subsequent depth-tested draws.</li>
     *   <li>{@link RenderPipeline.Builder#withLocation(Identifier)} gives the pipeline a unique id
     *       for Blaze3D's GPU pipeline cache.</li>
     * </ul>
     */
    private static final RenderPipeline PIPELINE_LINES_NO_DEPTH = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    // Override depth state only — ALWAYS_PASS means the depth test is disabled
                    // (lines always pass, even when behind solid geometry = xray effect).
                    // writeDepth=false avoids corrupting the depth buffer with overlay lines.
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    // Unique Identifier for Blaze3D's GPU pipeline cache.
                    .withLocation(Identifier.fromNamespaceAndPath("veinminerplusplus", "lines_no_depth"))
                    .build());
    // We register() the pipeline rather than leaving it loose. An UNregistered custom pipeline
    // draws fine in vanilla but Sodium/Iris silently drop it (it never enters their pipeline set) —
    // that's why the guide vanished in-pack even with shaders off. register() adds it to the shared
    // list they scan, so the pipeline becomes visible to them. No access widener needed — register()
    // is reachable on 26.1.2.

    /**
     * The {@link RenderType} that batches vertices for {@link #PIPELINE_LINES_NO_DEPTH}.
     *
     * <p>{@link RenderSetup#builder(RenderPipeline)} creates a setup from a pipeline and is the
     * correct way to tie a pipeline to a {@link RenderType} in 26.1.  No additional texture or
     * lightmap flags are needed because the lines shader is position+color only.
     */
    private static final RenderType LINES_NO_DEPTH = RenderType.create(
            "veinminerplusplus:lines_no_depth",
            RenderSetup.builder(PIPELINE_LINES_NO_DEPTH)
                    .createRenderSetup());

    static {
        // Optional Iris support: when Iris is installed, route our depth-disabled lines pipeline
        // through Iris's LINES program so the see-through guide survives an active shaderpack
        // (Iris draws only geometry tied to a program it knows). No-op without Iris — see IrisCompat.
        IrisCompat.assignToLinesProgram(PIPELINE_LINES_NO_DEPTH);
    }

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
     * <p>The caller supplies the {@link PoseStack} already in its render-frame state (identity at
     * the camera origin) and a {@link MultiBufferSource} to batch the lines into.  After drawing
     * the caller is responsible for flushing the batch (both loaders call
     * {@code ((MultiBufferSource.BufferSource) bufferSource).endBatch()} after this method).
     *
     * @param poseStack    the frame PoseStack (camera-relative, identity at call time)
     * @param bufferSource the batch to write vertices into
     */
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource) {
        Minecraft mc = Minecraft.getInstance();

        // ---- 1. Require a live in-world client player -----------------------------------
        if (mc.player == null || mc.level == null) return;

        // ---- 2. Activation key: Sneak must be held ------------------------------------
        // keyShift is the 26.1 name for the sneak key (migration doc Part 3).
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

        // ---- Camera-relative translation ----------------------------------------------
        // The PoseStack received from the event is at the camera origin (0,0,0 in eye space).
        // ShapeRenderer expects the VoxelShape in world coords and subtracts dx/dy/dz from each
        // vertex, so we pass the negative camera position to shift from world → camera space.
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos   = camera.position();

        // ---- Draw the outline with ShapeRenderer -------------------------------------
        // Signature: renderShape(PoseStack, VertexConsumer, VoxelShape, dx, dy, dz, argb, lineWidth)
        VoxelShape voxelShape = Shapes.create(bounds);
        VertexConsumer vc = bufferSource.getBuffer(LINES_NO_DEPTH);
        ShapeRenderer.renderShape(
                poseStack,
                vc,
                voxelShape,
                -camPos.x, -camPos.y, -camPos.z,
                GUIDE_COLOR_ARGB,
                LINE_WIDTH);
    }
}
