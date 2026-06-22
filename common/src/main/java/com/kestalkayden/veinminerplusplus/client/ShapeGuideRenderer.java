package com.kestalkayden.veinminerplusplus.client;

import java.util.OptionalDouble;

import com.kestalkayden.veinminerplusplus.core.ClientShapeState;
import com.kestalkayden.veinminerplusplus.core.MineShape;
import com.kestalkayden.veinminerplusplus.core.VeinMinerConfig;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Renders an xray-esque (depth-test-disabled) outline of the currently selected cuboid shape
 * centred on the block the player is looking at.  Call {@link #render} from both loaders'
 * AFTER_TRANSLUCENT world-render hook.
 *
 * <h3>1.21.5 render path (early RenderPipeline)</h3>
 *
 * <p>The RenderPipeline rework landed in 1.21.5 (immediate-mode {@code RenderSystem.setShader} /
 * {@code BufferUploader} are gone). Geometry is batched through a {@link RenderType} backed by a
 * {@link RenderPipeline} and flushed by the caller's {@link MultiBufferSource.BufferSource}.
 *
 * <p>The xray (see-through) effect comes from a custom pipeline that mirrors vanilla's lines
 * pipeline but uses {@link DepthTestFunction#NO_DEPTH_TEST} (and no depth write). Vanilla's
 * {@code RenderPipelines.LINES_SNIPPET} is package-private, so the pipeline is rebuilt from scratch
 * with the same shaders/uniforms/format ({@code core/rendertype_lines}, {@code POSITION_COLOR_NORMAL}
 * lines, translucent blend, no cull). {@link RenderType#create} and the composite-state builder's
 * setters are reopened via the access widener / access transformer ({@code lines_no_depth}).
 *
 * <p>Shader-pack note: the custom pipeline is not registered with Sodium/Iris (1.21.5 has no public
 * {@code RenderPipelines.register}), so under shaders the guide is best-effort; it renders in vanilla.
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

    /** Color of the guide outline — cyan with 50 % alpha (packed {@code 0xAARRGGBB}), as accepted
     *  by {@link ShapeRenderer#renderShape}. */
    private static final int GUIDE_COLOR_ARGB = 0x8030E0FF;  // α=0x80, r=0x30, g=0xE0, b=0xFF

    /** Fixed line width baked into the RenderType's line state. */
    private static final double LINE_WIDTH = 2.0;

    // -------------------------------------------------------------------------
    // Custom no-depth lines pipeline + RenderType
    // -------------------------------------------------------------------------

    /**
     * A lines {@link RenderPipeline} identical to vanilla's (shaders {@code core/rendertype_lines},
     * {@code LineWidth}/{@code ScreenSize} uniforms, translucent blend, no cull,
     * {@code POSITION_COLOR_NORMAL} in {@code LINES} mode) but with the depth test disabled and no
     * depth write — fragments win regardless of terrain depth (the xray effect).
     */
    private static final RenderPipeline PIPELINE_LINES_NO_DEPTH = RenderPipeline.builder()
            .withLocation(ResourceLocation.fromNamespaceAndPath("veinminerplusplus", "pipeline/lines_no_depth"))
            .withVertexShader("core/rendertype_lines")
            .withFragmentShader("core/rendertype_lines")
            .withUniform("LineWidth", UniformType.FLOAT)
            .withUniform("ScreenSize", UniformType.VEC2)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build();

    /**
     * The {@link RenderType} that batches vertices for {@link #PIPELINE_LINES_NO_DEPTH}. Mirrors
     * vanilla {@code RenderType.lines()} (buffer size 1536, line state) minus the depth-dependent
     * layering/output shards (unnecessary with depth disabled). {@code RenderType.create} and the
     * builder setters are reopened by the access widener / access transformer.
     */
    private static final RenderType LINES_NO_DEPTH = RenderType.create(
            "veinminerplusplus:lines_no_depth",
            1536,
            PIPELINE_LINES_NO_DEPTH,
            RenderType.CompositeState.builder()
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(LINE_WIDTH)))
                    .createCompositeState(false));

    private ShapeGuideRenderer() {}

    // -------------------------------------------------------------------------
    // Public entry point (called by both loaders' render hooks)
    // -------------------------------------------------------------------------

    /**
     * Evaluate visibility conditions and, if met, draw the cuboid outline into {@code bufferSource}.
     *
     * <p>The caller supplies the frame {@link PoseStack} (camera-relative, identity at the camera
     * origin) and a {@link MultiBufferSource} to batch into; it flushes the batch after this returns.
     *
     * @param poseStack    the frame PoseStack (camera-relative, identity at call time)
     * @param bufferSource the batch to write the line vertices into
     */
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource) {
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
        // Orient by the FACE the look ray enters (MineShape.directionInto), not the look vector's
        // dominant axis — so the box always bores into the face you're aimed at, and the guide
        // matches VeinMiner (same helper) at any view angle.
        Direction depthDir = MineShape.directionInto(
                mc.player.getEyePosition(1.0f), mc.player.getViewVector(1.0f), origin);
        AABB bounds = shape.bounds(origin, depthDir);

        // ---- Submit the outline -------------------------------------------------------
        // The PoseStack is at the camera origin, so ShapeRenderer's dx/dy/dz shift the world-space
        // shape into camera space (negative camera position).
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos   = camera.getPosition();
        VoxelShape voxelShape = Shapes.create(bounds);
        VertexConsumer vc = bufferSource.getBuffer(LINES_NO_DEPTH);
        ShapeRenderer.renderShape(
                poseStack, vc, voxelShape, -camPos.x, -camPos.y, -camPos.z, GUIDE_COLOR_ARGB);
    }
}
