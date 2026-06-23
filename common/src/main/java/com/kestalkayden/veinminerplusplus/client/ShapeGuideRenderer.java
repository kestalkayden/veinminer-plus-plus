package com.kestalkayden.veinminerplusplus.client;

import com.kestalkayden.veinminerplusplus.core.ClientShapeState;
import com.kestalkayden.veinminerplusplus.core.MineShape;
import com.kestalkayden.veinminerplusplus.core.VeinMinerConfig;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Renders an xray-esque (depth-test-disabled) outline of the currently selected cuboid shape
 * centred on the block the player is looking at.  Call {@link #render} from both loaders'
 * AFTER_TRANSLUCENT world-render hook.
 *
 * <h3>1.21.1 render path (pre-RenderPipeline)</h3>
 *
 * <p>1.21.1 predates the {@code RenderPipeline}/{@code RenderSetup} rework (1.21.6) and the
 * {@code SubmitNodeCollector} rework (26.2).  Rendering is classic immediate-mode: we set GL state
 * via {@link RenderSystem}, build the 12 box edges into a {@link BufferBuilder} from the shared
 * {@link Tesselator}, and draw them straight away with {@link BufferUploader#drawWithShader}.
 *
 * <p>The xray (see-through) effect comes from {@link RenderSystem#disableDepthTest()} around the
 * draw — no custom no-depth {@code RenderType} (and so no access widener) is needed.  The vertices
 * are baked camera-relative via the event {@link PoseStack} (translated by {@code -cameraPos}), so
 * {@code drawWithShader} transforms them with the live world model-view/projection exactly as the
 * vanilla line batch would.
 *
 * <p>Shader-pack note: under Iris/OptiFine shaders the manual draw is best-effort (Iris draws only
 * geometry tied to a program it knows); the guide renders in vanilla and under Sodium.  The
 * shader-program routing the 26.x build does via {@code IrisApi.assignPipeline} has no pre-pipeline
 * equivalent, so it is simply omitted here.
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

    /** Color of the guide outline — cyan with 50 % alpha (packed {@code 0xAARRGGBB}). */
    private static final int GUIDE_COLOR_ARGB = 0x8030E0FF;  // α=0x80, r=0x30, g=0xE0, b=0xFF

    /** Line width for {@link RenderSystem#lineWidth}. */
    private static final float LINE_WIDTH = 2.0f;

    private ShapeGuideRenderer() {}

    // -------------------------------------------------------------------------
    // Public entry point (called by both loaders' render hooks)
    // -------------------------------------------------------------------------

    /**
     * Evaluate visibility conditions and, if met, draw the cuboid outline.
     *
     * <p>The caller supplies the frame {@link PoseStack} from its render event (camera-relative,
     * identity at the camera origin).  This method translates it by {@code -cameraPos} so the
     * world-space box lands at the target block, then draws the edges in immediate mode.
     *
     * @param poseStack the frame PoseStack (camera-relative, identity at call time)
     */
    public static void render(PoseStack poseStack) {
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

        // ---- Draw the outline in immediate mode ---------------------------------------
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos   = camera.getPosition();

        float a = ((GUIDE_COLOR_ARGB >> 24) & 0xFF) / 255.0f;
        float r = ((GUIDE_COLOR_ARGB >> 16) & 0xFF) / 255.0f;
        float g = ((GUIDE_COLOR_ARGB >>  8) & 0xFF) / 255.0f;
        float b = ( GUIDE_COLOR_ARGB        & 0xFF) / 255.0f;

        // GL state for see-through translucent lines.
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.lineWidth(LINE_WIDTH);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();   // xray — the lines win regardless of terrain depth
        RenderSystem.depthMask(false);     // don't pollute the depth buffer with overlay lines
        RenderSystem.disableCull();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        // 1.20.1 immediate-mode buffer: getBuilder() + begin(), draw via end() (which returns a
        // RenderedBuffer; Tesselator.begin(...) returning a started BufferBuilder + MeshData are 1.21).
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        // renderLineBox emits the 12 edges (POSITION_COLOR_NORMAL) transformed by the pose.
        LevelRenderer.renderLineBox(poseStack, buffer, bounds, r, g, b, a);
        BufferUploader.drawWithShader(buffer.end());

        poseStack.popPose();

        // Restore default GL state.
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
