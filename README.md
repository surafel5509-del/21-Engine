# S Engine

S Engine is a **native Android 2D game studio** with a landscape editing workspace, a JBox2D play-mode runtime, offline project packages, and a separate Android game player. It replaces the former repository contents with a Kotlin/Compose editor and reusable game-engine modules.

## Editor and runtime

- **Unity-like landscape studio:** functional File/Edit/GameObject/Window menus; searchable hierarchy on the left; switchable Scene and Game views in the center; component inspector on the right; resizable side panes and a collapsible/resizable Project, Console, Assets, Scripts and Build dock. Move, rotate, resize, pan, zoom, snap to configurable world-unit grids, frame selection, layer/reorder, duplicate, lock/hide and undo/redo. Edit scenes independently from the play-mode copy.
- **Projects and assets:** multiple scenes with independent camera, gravity, background and output dimensions. Import PNG/JPEG/WebP through Android's file picker, reuse sprites across scenes, organize assets in named folders and browse scenes/textures/scripts/prefabs in a Project window. Save atomically in private app storage. Portable `.sengine` exports contain scenes, scripts, prefab definitions and referenced images.
- **Reusable prefabs:** create a single-object prefab from a selected object, place linked instances, apply component edits across instances, revert one instance, or unpack/delete the source without destroying placed objects. Instances keep independent names, locks and world positions. Prefabs are not nested or multi-object.
- **Rendering:** a shared Android Canvas renderer for the editor and standalone player, with boxes, circles, text, sprites, selection handles, optional grid/collider overlays, and a letterboxed game frame in play mode. The player and editor load the same project data and use the same renderer and physics runtime.
- **Physics 2D:** JBox2D fixed-step 60 Hz simulation; static, dynamic and kinematic bodies, rotated box/circle colliders, friction, restitution, density, gravity scale, damping, sensors, contacts and collision-event logs. Pause/step and collider outlines aid debugging.
- **S Script:** editable *sandboxed event language* attached to objects (`on start`, `on update`, `on tap`, `on collision`). Variables, arithmetic, conditions, movement, impulses, rotation, scene changes by name/ID and logging are supported, with bounded execution and parse/runtime diagnostics in the console. This is **not** arbitrary Kotlin, C# or Java execution.
- **Build/export:** export a `.sengine` project package, an offline playable HTML5 Canvas game ZIP, or build a **separately installable Android game APK** from the package using the included Gradle-backed builder on a development computer/CI runner. APKs contain the player's game data, not the editor.

## Build the studio

Requires **JDK 17**, **Android SDK platform 35**, Android build tools, and Python 3 for the game builder. Android Studio can install the SDK and open this repository directly. The Gradle wrapper uses Gradle 8.9; Android Gradle Plugin 8.7.3 and Kotlin 2.0.21 are configured. The minimum device version is Android 8.0 (API 26).

```bash
./gradlew :engine:test :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions runs engine tests, Python builder/archive tests, web-player tests, and builds the editor APK and a **smoke-test player APK**. Download them from a successful **Android CI** workflow run under *Artifacts*; the smoke-test APK contains only a CI fixture, **not your project**.

## Build your own Android game APK

1. In the editor, open the **Build** dock and export **Project package (.sengine)** to a file; copy that file to your development computer.
2. From this repository's root, with JDK 17 and Android SDK 35 configured, run:

   ```bash
   python3 tools/build_android_game.py /path/to/MyGame.sengine \
     --application-id com.example.mygame --name 'My Game'
   ```

3. Install the generated debug APK from `build/game-apks/com.example.mygame-debug.apk` with `adb install -r`. Debug builds use Android's development signing key; do **not** distribute them as release builds.

The builder checks the ZIP structure and project references, stages the game into ignored `player/build/` directories, invokes `:player:assembleDebug`, and copies the resulting installable APK. You may also provide `--icon /path/to/square.png` (48–1024 pixels), `--version-code`, `--version-name`, and `--output`. The default application ID is derived from the project ID; choose your own stable ID before publishing so future updates use the same package name. Run `python3 tools/build_android_game.py --help` for all options.

For a **signed release APK**, supply your own absolute keystore path and passwords as environment variables (never commit them), then use `--release`:

```bash
export SENGINE_KEYSTORE_PATH=/secure/path/my-game.jks
export SENGINE_KEYSTORE_PASSWORD='…'
export SENGINE_KEY_ALIAS='my-game'
export SENGINE_KEY_PASSWORD='…'
python3 tools/build_android_game.py /path/to/MyGame.sengine \
  --application-id com.example.mygame --release
```

A build computer or CI runner with the Android toolchain is necessary; **the editor app does not compile APKs on the device or upload projects to a hosted build service**. Keep signing credentials off the phone and out of the repository. An AAB / Play Store publishing pipeline is not provided.

## Everyday workflow

1. Create/open a project from the dashboard. Use the **Scene** tab to edit or **Game** to preview the output frame. Drag the vertical dividers or dock handle to resize windows; open **Project** to browse project files.
2. Select an object in the searchable hierarchy or scene; use the inspector to configure transforms, appearance, body/collider properties and motion. Enable grid snapping in the toolbar and set a step in the Scene toolbar. Import textures in **Assets**, create a script in **Scripts**, or create a reusable prefab from the selected object in the inspector or GameObject menu.
3. Press **Play** to simulate in isolation. Check the Console dock for collisions, scripts and errors; pause/step or stop to return to the unchanged edit scene. To connect scenes in a game, attach an S Script to a button/door object with `on tap`, then `scene Name of Next Scene` and `end`.
4. Export `.sengine` backups regularly. For a browser-only game, export the web ZIP and open `index.html` after unzipping. For Android, use the builder above.

Projects live in the app's private files directory and **Android backup is disabled**. Export before uninstalling/clearing app data. Importing a `.sengine` archive creates a separate local copy. The HTML5 runtime is a distinct, simpler JavaScript implementation and does **not** yet have complete physics parity with the Android JBox2D player.

## Repository layout

```text
engine/                        Pure Kotlin model, JBox2D runtime, S Script interpreter, JVM tests
renderer/                      Shared Android Canvas scene painter (editor + player)
app/                           Landscape Android editor, SAF storage and HTML5 export
player/                        Standalone Android game application target
tools/build_android_game.py    Project-package validation, staging, APK build/signing invocation
tools/tests/                   Python archive-builder tests
web/tests/                     Node tests for the separate offline web player
.github/workflows/android.yml  Editor/game build, tests and APK artifacts
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for project format, runtime semantics and security boundaries. S Engine currently targets **2D**. The workspace follows Unity's familiar layout but does not claim Unity feature or file-format parity: it has no 3D, audio pipeline, tilemaps, nested prefabs, animation timeline, networking, joints, arbitrary-language plugins or device-side APK compilation.
