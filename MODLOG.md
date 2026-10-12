# Depressed Ambient — mod journal

## 2026-10-11 — intake and route

- Target: Minecraft Java Edition 1.21.11, Fabric, Java 21.
- Route: a client-only Fabric API overlay. It observes only camera-ray hits and line-of-sight players; it sends no packets and changes no world state.
- Safety boundary: intended for single-player or servers the user operates. No anti-cheat bypass, wallhack, entity-through-wall rendering, save edits, loader installation, or game launch was performed.
- Recon: the local knowledge index contains no Fabric 1.21.11 overlay field note. Fabric's official 1.21.11 release guidance specifies the remapping Loom plugin; the project uses `net.fabricmc.fabric-loom-remap`, Mojang mappings, Loader 0.19.3, and Fabric API 0.141.6+1.21.11.
- Vertical slice: throttled camera-ray candidate scan -> LOS/FOV filter -> red depth-tested outline/text/arrow -> JSON configuration.
- Verification status: `git diff --check` passes. JDK 21.0.11 is installed. The Gradle wrapper could not download Gradle 9.7.1 because this sandbox blocks sockets (`Permission denied: getsockopt`), so a full compile and real-game verification remain pending. No Minecraft profile was touched.

## 2026-10-11 — first external build result

- User ran `gradlew.bat build`: Gradle 9.7.1 and Loom 1.14.10 resolved successfully.
- Compilation exposed a configuration mistake: remapped 1.21.11 projects must put Fabric Loader/API on `modImplementation`, not plain `implementation`, otherwise Fabric API's intermediary types cannot be mapped to the official Mojang names.
- Corrected the dependency configuration. Fabric's 1.21.11 template still resolves the `1.14-SNAPSHOT` plugin marker to Loom 1.14.10, so the project retains that marker; pinning `1.14.10` directly fails plugin-marker resolution. The next online build will distinguish remaining genuine 1.21.11 API name changes from mapping errors.

## 2026-10-11 — first real-game render check

- The mod loaded on Fabric Loader 0.19.5 with Fabric API 0.141.6+1.21.11 and entered an integrated world.
- Crash cause: the custom arrow used the `LINES` render type but omitted its required `LineWidth` vertex attribute. The stack trace pinpointed `WorldOverlayRenderer.line`.
- Fix: both arrow vertices now set line width to 1.5 pixels. Box rendering was already valid and remains depth-tested.

## 2026-10-11 — second real-game render check

- The line-width correction passed its original failure point, but `latest.log` then showed `IllegalStateException: Not building!` at the box writer (`ShapeRenderer.renderShape`).
- Cause: in Fabric API 0.141.6 for Minecraft 1.21.11, `AFTER_ENTITIES` is after the entity pass has drawn/closed its shared consumer in this renderer path.
- Fix: register the overlay at `BEFORE_DEBUG_RENDER`, the event Fabric documents for depth-tested debug-style lines and overlays.

## 2026-10-11 — third real-game render check

- `BEFORE_DEBUG_RENDER` was invoked, but Fabric's supplied world consumer was still closed (`Not building!`) when the outline attempted to append geometry.
- Fix: the overlay now owns a `Tesselator` line buffer and a dedicated immediate text buffer. Lines are built, then submitted by `RenderTypes.lines()`, and text is flushed by its own `BufferSource`. No closed vanilla consumer is reused.

## 2026-10-11 — fourth build check

- The previous three builds completed successfully. The immediate-buffer revision introduced two compile-time references to the old variable name `lines` after it had been renamed `lineBuilder`.
- Fix: both outline and arrow calls now receive `lineBuilder`.

## 2026-10-11 — visibility tuning

- The game runs without renderer errors, but the screenshot showed no marker on plainly visible village buildings.
- Cause: visible block candidates were raycast twice. The first ray hit the visible face; the second ray targeted the block centre and frequently collided with a neighbouring roof/structure block, discarding valid candidates.
- Fix: the original hit ray is now the block LOS proof, its exact hit location anchors the marker, and the bounded FOV sampler is increased from 96 to 192 rays every 15 ticks. This preserves no-through-wall behaviour while making visible roofs, doors, windows, and high structures reliably detectable.

## 2026-10-11 — deterministic visual test

- The user still observed no markers despite a clean client log. A sampling failure and a renderer failure must be distinguished before making further detection changes.
- Fix: while `enableDebugCrosshairMarker` is true (the default), the mod draws one fixed `I see this` marker on the exact vanilla crosshair block hit. That block is necessarily already visible to the player, so this diagnostic cannot reveal hidden geometry. It also writes the active marker count into the action bar every five seconds.
- Verification pending: rebuild and aim directly at a nearby door, wall, or roof block. If this marker is absent, the remaining issue is in the renderer; if it is present, camera sampling needs further tuning.

## 2026-10-11 — Fabric 1.21.11 render lifecycle correction

- The crosshair diagnostic was absent while the config confirmed it was enabled and `latest.log` had no mod exception. This proves the scanner is no longer the first failure.
- Cause: Fabric API 0.141.6 for 1.21.11 changed world rendering into distinct extraction/drawing stages. The old renderer was issuing its own buffers during `BEFORE_DEBUG_RENDER`; that stage is intended for direct framebuffer work and the approach did not produce geometry.
- Fix: render the depth-tested lines and text through the shared, still-open `WorldRenderContext.consumers()` at `BEFORE_ENTITIES`, the event Fabric documents for non-translucent world geometry. Added a temporary red HUD health check (`DEPRESSED AMBIENT | markers: N`) which must appear in a loaded world even if 3D geometry fails.
- Local compile limitation: Gradle 9.7.1 is not present in the sandbox wrapper cache; its network download is blocked. A local Gradle 9.3 offline attempt cannot resolve the Fabric Loom snapshot marker. User's earlier normal build resolved it successfully, so this revision requires one user-side build check.

## 2026-10-11 — buffer lifecycle failure confirmed in game

- New `latest.log` confirms the latest JAR was installed and loaded. The integrated server shuts down because the client throws `IllegalStateException: Not building!` at `WorldOverlayRenderer.java:54` while `ShapeRenderer` writes the box.
- The `WorldRenderContext` shared consumer is not usable for the `LINES` layer at that stage, despite the event's general consumer documentation.
- Fix: return to `BEFORE_DEBUG_RENDER` (a drawing event) and use a dedicated `ByteBufferBuilder`/`BufferBuilder` for the line mesh. The completed mesh is submitted directly to `RenderTypes.lines()`. This removes all writes to Minecraft's closed shared buffer. World text is temporarily held back until the line vertical slice is verified; the HUD diagnostic retains a visible text health check.

## 2026-10-11 — scan/renderer split verified

- Screenshot confirmed the HUD health check: `DEPRESSED AMBIENT | markers: 50`. The client initializer, scanner, config, and marker registry are functioning; a marker-search change is not needed.
- The direct `RenderTypes.lines().draw(mesh)` convenience path still produced no visible geometry. Replaced it with the explicit Minecraft 1.21.11 GPU submission sequence from Fabric's official custom-render-pipeline reference: upload dedicated mesh data to a `MappableRingBuffer`, bind `RenderPipelines.LINES`, bind dynamic/default uniforms and depth texture, and issue indexed draw.
- The pipeline is the vanilla depth-tested `LINES` pipeline; it cannot see through walls. Pending in-game verification after the next build.

## 2026-10-11 — direct-event matrix fix

- The next game log reached the direct rendering event but threw a `NullPointerException` at `pose.pushPose`: `WorldRenderContext.matrices()` is null during Fabric 1.21.11's `BEFORE_DEBUG_RENDER` direct drawing event.
- Fix: create an identity `PoseStack` for the dedicated mesh, then apply the camera translation explicitly. The global render model-view state remains supplied to the GPU pipeline by Minecraft.

## 2026-10-11 — visible-pass tuning

- The matrix fix stopped the crash, and the HUD reported 51 active markers, but no geometry was visible and no GPU error appeared in `latest.log`.
- Adjustment: submit the direct depth-tested pass from `END_MAIN`, after terrain/entities/translucent terrain are complete; slightly inflate marker boxes to avoid depth-plane fighting with their block faces; widen diagnostic lines from 1.5 to 3 pixels.

## 2026-10-11 — in-game line verification

- Screenshot confirms red arrows are now rendered in the world and depth-tested against the visible village.
- Visual correction: arrow wings were aligned to global axes, which made them appear flat when the camera rotated. Their side vector now uses the camera-to-target plane, while the shaft retains its target direction. A parallel-vector fallback preserves valid geometry when the player looks almost exactly along an arrow.

## 2026-10-11 — feature completion pass

- Reintroduced 3D, camera-facing phrase labels using a dedicated immediate font buffer with `Font.DisplayMode.NORMAL`; it retains depth testing and avoids the closed world buffer.
- Increased outline separation to prevent z-fighting with target blocks. Kept category phrase selection, phrase rotation, jitter, distance fade, player targeting, and all required JSON settings.
- Removed temporary HUD text and the crosshair-only diagnostic marker from the shipped feature, returning the configuration surface to the requested settings.

## 2026-10-11 — labels, clustering, sky, mobs

- Screenshot of the village house showed roof arrows with no readable phrases, a nest of extra pointers, empty sky, and no mob overlays.
- Cause: 7-segment labels sat on top of the roof so the depth test hid them from ground view; every roof plank became its own marker; closest-block quota starved entities; empty sky rays were ignored.
- Fix: cluster nearby blocks (one roof/window/structure frame), draw a single camera-facing square plus one arrow, pull distance-scaled 5x7 stroke text toward the camera, reserve snapshot slots by category, treat living entities with AABB-aware LOS, and place a SKYLINE annotation on visible empty sky when looking up.
- New config fields: `enableMobTargeting`, `enableSkyMarkers`. Default `maxActiveMarkers` is 6.
