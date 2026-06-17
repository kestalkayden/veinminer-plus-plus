package com.kestalkayden.veinminerplusplus.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;

/**
 * Optional Iris integration — Veinminer++ neither depends on nor bundles Iris.
 *
 * <h3>Why this exists</h3>
 * <p>With a shaderpack active, Iris renders only geometry tied to a shader program it recognises.
 * A mod's custom {@link RenderPipeline} has none, so the see-through shape guide is silently
 * dropped under shaders (it renders fine otherwise, and under Sodium once the pipeline is
 * registered). Iris exposes {@code IrisApi.assignPipeline(RenderPipeline, IrisProgram)} — the same
 * hook Iris itself uses for NeoForge's entity pipelines — to map a custom pipeline onto one of its
 * programs. Pointing ours at {@code IrisProgram.LINES} makes Iris draw it with the pack's line
 * program, preserving the pipeline's own (depth-disabled) state.
 *
 * <h3>Why reflection</h3>
 * <p>Calling the API reflectively keeps Iris an entirely optional, build-free integration: there is
 * no compile- or run-time dependency and nothing to bundle. The presence check is loader-agnostic,
 * so Fabric and NeoForge share this one code path with no per-loader detection. When Iris is absent
 * (or its API differs), every call here is a silent no-op.
 */
public final class IrisCompat {

    private IrisCompat() {}

    /** True once the assignment has been attempted. Iris's {@code assignPipeline} throws if called
     *  twice for the same pipeline, and there is nothing to retry, so we only ever try once. */
    private static boolean attempted;

    /**
     * If Iris is installed, map {@code pipeline} onto Iris's {@code LINES} program so it renders
     * under an active shaderpack. No-op when Iris is absent or exposes a different API. Idempotent.
     *
     * @param pipeline the custom render pipeline to route through Iris's line program
     */
    public static void assignToLinesProgram(RenderPipeline pipeline) {
        if (attempted) {
            return;
        }
        attempted = true;
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Class<?> programClass = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object linesProgram = programClass.getField("LINES").get(null);
            apiClass.getMethod("assignPipeline", RenderPipeline.class, programClass)
                    .invoke(api, pipeline, linesProgram);
        } catch (ClassNotFoundException e) {
            // Iris is not installed — the guide renders without shader support, which is fine.
        } catch (ReflectiveOperationException e) {
            // Iris is present but exposes a different API shape (older/newer build) — fail soft.
        }
    }
}
