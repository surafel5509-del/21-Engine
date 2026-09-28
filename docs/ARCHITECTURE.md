# S Engine architecture

## Modules and flow

`engine` has no Android or UI dependencies. `GameProject → GameScene → Entity` is a serializable, immutable scene graph with a flat draw order (last = front). An entity carries a `Transform`, `Visual`, optional `PhysicsBody`, and a configurable `Motion`. `ProjectCodec` owns the version-1 JSON format and validates IDs, finite numbers, sizes, active scene references, and image references. `SceneOps` implements hit testing in inverse-rotated local coordinates and immutable edits.

`WorldRunner` copies an edit scene before play. It advances at 1/60 second per step, clamps long frames, applies spin/float/patrol relative to starting transforms, integrates dynamic bodies, and resolves axis-aligned collisions **against static bodies**. Dynamic-vs-dynamic and rotated physics colliders are deliberately not implemented. Touching a dynamic object assigns an upward velocity. Stop drops the runtime copy; edits remain untouched.

The Android app has one `StudioViewModel`, a `ProjectStore`, Compose workspace panels, and a native `View`-based Canvas. The View handles direct-manipulation gestures; the ViewModel owns selections, bounded undo/redo stacks, play/stop, and debounced atomic saves. The dashboard lists valid project folders. A `.sengine` file is a ZIP with `project.json` and optional `assets/<id>.img` entries. A web export embeds project JSON into an HTML document and includes `runtime.js`, `styles.css`, and images.

## Coordinates & rendering

The origin is the center of the viewport. X increases to the right; Y increases downward. Transforms store **center position**, width, height, and clockwise degrees. One world unit maps to one Android dp at zoom 1, or one CSS pixel in the web player. Camera X/Y denote the world point at screen center; zoom is 0.2–4. The grid spacing is 40 world units. Objects render in list order. Hit testing traverses that list in reverse and respects visibility/lock state.

The Android viewport downsamples image decoding to at most approximately 1024 × 1024 and uses a 16 MiB bitmap cache. Sprites render inside their transform rectangle. The web player uses the same color, transform, motion, and physics conventions; its small JavaScript simulation is regression-tested separately. The viewport can be replaced with an OpenGL renderer later without changing the project format.

## Storage and trust boundaries

- Internal layout: `filesDir/projects/<project UUID>/project.json` and `assets/<asset UUID>.img`. Writes use Android `AtomicFile`; user-selected files use the Storage Access Framework, not broad storage permission.
- Project JSON is capped at 2 MB. Each imported image is capped at 10 MB and 4096 × 4096 pixels. A project may have up to 32 scenes, 2,000 objects per scene, and 256 assets.
- ZIP import allowlists only `project.json` and `assets/<safe ID>.img`, bounds the number of entries and decompressed bytes (64 MB total), checks declared image IDs against extracted files, validates image dimensions, and assigns a **new project ID**. Extraction never concatenates arbitrary archive paths.
- Web export replaces `<` in embedded JSON with the JSON `\u003c` escape so user text cannot close its non-executable data script tag. UI uses `textContent` and Canvas drawing, never `innerHTML` or `eval`.
- Exported games run offline. The HTML file can also be hosted on any static hosting service. Do not import untrusted projects outside these built-in limits.

## Next milestones (not implemented)

1. Shared rendering backend and richer physics: collision layers, triggers, joints, rotated colliders, tilemaps.
2. Safe visual scripting / event graph, audio mixer, input mapping, animation timeline, prefabs.
3. Android **player** target and a hosted build service for separately signed game APKs (on-device building is not included).
4. Optional OpenGL ES/Vulkan 3D renderer, materials, meshes, lights, and an expanded project format.

A Unity-scale production engine is a much larger multi-year undertaking; these are extension points rather than claims that those systems already ship.
