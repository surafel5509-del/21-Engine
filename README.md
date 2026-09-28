# S Engine

**A mobile-first 2D game studio for Android.** Create and edit scenes on your phone, preview them with a fixed-step runtime, and export either a portable project package or a playable offline web game.

> **Scope, honestly:** this is a functional **v0.1 2D engine/editor**, not a Unity replacement. It does **not** yet include 3D, arbitrary user scripting, audio, skeletal animation, prefab systems, multiplayer, dynamic-vs-dynamic collision, or on-device APK compilation. The web export is a playable game; exporting a *separate Android game APK* would require an Android build toolchain and a game-player target.

## What works

- Native Android Studio app (Kotlin, Jetpack Compose) with a responsive portrait/landscape workspace and custom touch-driven Canvas viewport.
- Create a blank project, a gravity-based platformer starter, or an animated playground. Keep multiple scenes per project.
- Add rectangles, circles, text, and imported PNG/JPEG/WebP sprites. Select, drag, pan, pinch-zoom, hide, lock, reorder, duplicate, and delete objects.
- Inspector for position, size, rotation, color palette/hex, text, static/dynamic physics, initial velocity, gravity, restitution, and spin/float/patrol behaviors.
- Play/stop preview without changing the edit scene. Tap dynamic objects to jump. Fixed 60 Hz simulation with gravity and axis-aligned collisions against static bodies.
- Local project persistence with atomic JSON writes, undo/redo (40 steps), and explicit save. Images are copied into private project storage.
- Export/import `.sengine` ZIP projects through Android's file picker. Import validates IDs, entries, file sizes, image dimensions, and project schema before installation.
- Export a **playable web game ZIP** containing `index.html`, a self-contained Canvas runtime, your scenes, and images. Unzip and open `index.html` in a modern browser; no server is needed.
- JVM engine tests, Node web-runtime tests, and CI configured to build and upload a debug APK.

## Build & install

Requirements: **JDK 17**, **Android SDK platform 35** and build tools, Android Studio with Gradle support. The wrapper downloads **Gradle 8.9**; the project uses Android Gradle Plugin 8.7.3 and Kotlin 2.0.21. Android 8.0 / API 26 or later is supported.

```bash
./gradlew :engine:test :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Alternatively open the repository root in Android Studio, let Gradle sync, and run the `app` configuration. On GitHub Actions the **Android CI** workflow uploads `app-debug.apk` as an artifact. This repository does **not** contain a signed release APK or signing keys.

The web runtime can be tested independently with Node 20+:

```bash
node --test web/tests/*.test.js
```

## Five-minute tour

1. Tap **New project** or select **Platformer**. Tap an object in the canvas or **Hierarchy** to inspect it.
2. Drag an object to move it; drag empty canvas space to pan, pinch to zoom. Use the `+` toolbar to add a shape or text.
3. In **Inspector**, edit transforms, visuals, physics, and motion. In **Assets**, import an image using Android's system document picker.
4. Press **Play**. The starter orb falls onto the floor; tap it to jump. Press **Stop** to discard play-mode changes.
5. Tap **Export** in the editor header. Choose a `.sengine` project backup or a **Playable web game** ZIP. Import backups from the dashboard.

Projects live in the app's private files directory. **Android backup is disabled**, so export a `.sengine` package before uninstalling the app or clearing app data. Importing a package creates a new project rather than overwriting an existing one. A web-game ZIP is not a project package; use `.sengine` for re-importing into the editor.

## Project layout

```text
engine/                        Pure Kotlin scene model, validation, hit testing, simulation, JVM tests
app/                           Android editor, viewport, private storage, import/export, web assets
app/src/main/assets/web/       Offline HTML5 Canvas game player bundled into web exports
web/tests/                     Node tests for the exported player
.github/workflows/android.yml  Engine tests, web tests, APK build + debug artifact
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for coordinate conventions, persistence, player parity, security limits, and a development roadmap.
