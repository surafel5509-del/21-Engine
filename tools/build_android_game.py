#!/usr/bin/env python3
"""Build a separate Android APK from a portable .sengine project (JDK 17 + SDK 35 required).

This deliberately runs on a developer computer or trusted CI runner, not inside the editor
APK: distributing the Android SDK and signing keys inside a game editor is unsafe and huge.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import uuid
import zipfile

ROOT = Path(__file__).resolve().parent.parent
STAGE = ROOT / "player" / "build" / "stagedGame"
ICONS = ROOT / "player" / "build" / "generatedIcons"
ID = re.compile(r"[A-Za-z0-9_-]{1,80}\Z")
IMAGE_ENTRY = re.compile(r"assets/([A-Za-z0-9_-]{1,80})\.img\Z")
PACKAGE = re.compile(r"[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*){2,}\Z")
MAX_JSON = 2_000_000
MAX_IMAGE = 10_000_000
MAX_ARCHIVE = 64_000_000


def fail(message: str) -> None:
    raise ValueError(message)


def valid_id(value: object) -> bool:
    return isinstance(value, str) and ID.fullmatch(value) is not None


def number(value: object, low: float, high: float) -> bool:
    return type(value) in (int, float) and math.isfinite(value) and low <= value <= high


def folder(value: object) -> bool:
    return (isinstance(value, str) and 1 <= len(value) <= 100 and
            all(re.fullmatch(r"[A-Za-z0-9 _-]{1,32}", part) and part.strip() for part in value.split("/")))


def vector(value: object, x: float, y: float, low: float, high: float) -> bool:
    return (isinstance(value, dict) and number(value.get("x", x), low, high) and
            number(value.get("y", y), low, high))


def color(value: object) -> bool:
    return type(value) is int and -(2 ** 31) <= value < 2 ** 31


def validate_entity(entity: object, ids: list[str], script_ids: list[str], prefab_ids: list[str]) -> None:
    if not isinstance(entity, dict) or not valid_id(entity.get("id")) or not isinstance(entity.get("name"), str) or len(entity["name"]) > 100:
        fail("Invalid entity")
    if entity.get("scriptId") is not None and entity["scriptId"] not in script_ids:
        fail("Object references a missing script")
    if entity.get("prefabId") is not None and entity["prefabId"] not in prefab_ids:
        fail("Object references a missing prefab")
    if type(entity.get("visible", True)) is not bool or type(entity.get("locked", False)) is not bool:
        fail("Visibility and lock state must be booleans")
    transform = entity.get("transform", {})
    if (not isinstance(transform, dict) or not vector(transform, 0, 0, -1_000_000, 1_000_000) or
            not number(transform.get("width", 80), 1, 10_000) or
            not number(transform.get("height", 80), 1, 10_000) or
            not number(transform.get("rotation", 0), -1e100, 1e100)):
        fail("Invalid object transform")
    visual = entity.get("visual", {})
    if (not isinstance(visual, dict) or visual.get("type", "BOX") not in ("BOX", "CIRCLE", "TEXT", "IMAGE") or
            not color(visual.get("color", -6447873)) or
            not isinstance(visual.get("text", "Hello, world!"), str) or len(visual.get("text", "")) > 500):
        fail("Invalid object visual")
    if visual.get("type") == "IMAGE" and visual.get("assetId") not in ids:
        fail("Object references a missing image")
    body = entity.get("physics")
    if body is not None:
        if (not isinstance(body, dict) or body.get("type", "STATIC") not in ("STATIC", "DYNAMIC", "KINEMATIC") or
                body.get("collider", "AUTO") not in ("AUTO", "BOX", "CIRCLE") or
                type(body.get("sensor", False)) is not bool or type(body.get("fixedRotation", False)) is not bool or
                not vector(body.get("velocity", {}), 0, 0, -5000, 5000) or
                not number(body.get("gravityScale", 1), 0, 5) or
                not number(body.get("bounce", 0), 0, 1) or
                not number(body.get("friction", .35), 0, 1) or
                not number(body.get("density", 1), .01, 100) or
                not number(body.get("linearDamping", 0), 0, 20)):
            fail("Invalid object physics")
    motion = entity.get("motion", {})
    if (not isinstance(motion, dict) or motion.get("type", "NONE") not in ("NONE", "SPIN", "FLOAT", "PATROL") or
            not number(motion.get("speed", .6), 0, 6) or not number(motion.get("amplitude", 56), 0, 2000)):
        fail("Invalid object motion")


def validate_project(project: object, images: set[str]) -> dict:
    """Mirror the core format limits before invoking the Android toolchain."""
    if not isinstance(project, dict) or type(project.get("formatVersion")) is not int or project["formatVersion"] != 1:
        fail("Expected an S Engine version-1 project")
    if not valid_id(project.get("id")) or not isinstance(project.get("name"), str) or not 1 <= len(project["name"]) <= 80:
        fail("Invalid project ID or name")
    scenes, assets, scripts = (project.get(key, default) for key, default in
                              (("scenes", None), ("assets", []), ("scripts", [])))
    if not isinstance(scenes, list) or not 1 <= len(scenes) <= 32:
        fail("Project must contain 1–32 scenes")
    if not isinstance(assets, list) or len(assets) > 256 or not isinstance(scripts, list) or len(scripts) > 64:
        fail("Invalid asset/script list")
    if any(not isinstance(item, dict) or not valid_id(item.get("id")) or
           not isinstance(item.get("name"), str) or len(item["name"]) > 160 or
           not folder(item.get("folder", "Textures")) for item in assets):
        fail("Invalid image assets")
    ids = [item["id"] for item in assets]
    if len(set(ids)) != len(ids) or set(ids) != images:
        fail("Image references do not match ZIP entries")
    for script in scripts:
        if not isinstance(script, dict) or not valid_id(script.get("id")) or not isinstance(script.get("name"), str) or not 1 <= len(script["name"]) <= 100 or not folder(script.get("folder", "Scripts")) or not isinstance(script.get("source"), str) or len(script["source"]) > 16_000:
            fail("Invalid script asset")
    script_ids = [item["id"] for item in scripts]
    if len(set(script_ids)) != len(script_ids):
        fail("Duplicate script IDs")
    if any(not isinstance(scene, dict) or not valid_id(scene.get("id")) or
           not isinstance(scene.get("name"), str) or len(scene["name"]) > 100 for scene in scenes):
        fail("Invalid scene")
    scene_ids = [scene["id"] for scene in scenes]
    if project.get("activeSceneId") not in scene_ids or len(set(scene_ids)) != len(scene_ids):
        fail("Active scene is missing or scene IDs are duplicated")
    for key in ("createdAt", "updatedAt"):
        value = project.get(key, 0)
        if type(value) is not int or not -(2 ** 63) <= value < 2 ** 63:
            fail("Invalid project timestamp")
    prefabs = project.get("prefabs", [])
    if not isinstance(prefabs, list) or len(prefabs) > 128:
        fail("Invalid prefab list")
    if any(not isinstance(prefab, dict) or not valid_id(prefab.get("id")) or
           not isinstance(prefab.get("name"), str) or not 1 <= len(prefab["name"]) <= 100 or
           not folder(prefab.get("folder", "Prefabs")) for prefab in prefabs):
        fail("Invalid prefab asset")
    prefab_ids = [prefab["id"] for prefab in prefabs]
    if len(set(prefab_ids)) != len(prefab_ids):
        fail("Duplicate prefab IDs")
    for prefab in prefabs:
        template = prefab.get("template")
        if not isinstance(template, dict) or template.get("prefabId") is not None:
            fail("Prefab template is invalid")
        validate_entity(template, ids, script_ids, prefab_ids)
    for scene in scenes:
        camera = scene.get("camera", {})
        gravity = scene.get("gravity", {})
        if (not isinstance(camera, dict) or not vector(camera, 0, 0, -1_000_000, 1_000_000) or
                not number(camera.get("zoom", 1), 0.2, 4) or
                not vector(gravity, 0, 720, -2000, 2000) or
                not color(scene.get("background", -15393232))):
            fail("Invalid camera, gravity or background color")
        objects = scene.get("entities", [])
        if not isinstance(objects, list) or len(objects) > 2000:
            fail("Scene has too many objects")
        if not number(scene.get("gameWidth", 360), 100, 4000) or not number(scene.get("gameHeight", 300), 100, 4000):
            fail("Game view size is invalid")
        entity_ids = set()
        for entity in objects:
            validate_entity(entity, ids, script_ids, prefab_ids)
            if entity["id"] in entity_ids:
                fail("Duplicate entity ID")
            entity_ids.add(entity["id"])
    return project


def stage_project(archive: Path, icon: Path | None) -> dict:
    if not archive.is_file():
        fail(f"Project archive not found: {archive}")
    if archive.stat().st_size > MAX_ARCHIVE:
        fail("Compressed archive exceeds 64 MB")
    # Never retain an older game's staging after validation fails.
    STAGE.parent.mkdir(parents=True, exist_ok=True)
    if STAGE.exists():
        shutil.rmtree(STAGE)
    if ICONS.exists():
        shutil.rmtree(ICONS)
    icon_data = None
    if icon:
        if icon.stat().st_size > 2_000_000:
            fail("Icon exceeds 2 MB")
        icon_data = icon.read_bytes()
        if not icon_data.startswith(b"\x89PNG\r\n\x1a\n") or len(icon_data) < 24:
            fail("Icon must be a PNG under 2 MB")
        width, height = struct.unpack(">II", icon_data[16:24])
        if width != height or not 48 <= width <= 1024:
            fail("Icon must be square and between 48 and 1024 pixels")
    temporary = STAGE.parent / ("_incoming-" + uuid.uuid4().hex)
    temporary.mkdir()
    try:
        with zipfile.ZipFile(archive) as package:
            entries = package.infolist()
            files = [entry for entry in entries if not entry.is_dir()]
            names = [entry.filename for entry in files]
            if len(entries) > 260 or any(entry.is_dir() and entry.filename != "assets/" for entry in entries):
                fail("Unexpected archive directory or too many entries")
            if not names or len(set(names)) != len(names) or "project.json" not in names:
                fail("Invalid or duplicate archive entries")
            if sum(entry.file_size for entry in files) > MAX_ARCHIVE:
                fail("Archive expands beyond 64 MB")
            for entry in entries:
                if ((entry.external_attr >> 16) & 0o170000) == 0o120000:
                    fail("Symlink entries are not allowed")
            for entry in files:
                match = IMAGE_ENTRY.fullmatch(entry.filename)
                if entry.filename != "project.json" and not match:
                    fail(f"Unexpected archive entry: {entry.filename}")
                if entry.file_size > (MAX_JSON if entry.filename == "project.json" else MAX_IMAGE):
                    fail("Archive entry exceeds its size limit")
            source = package.read("project.json")
            if len(source) > MAX_JSON:
                fail("Project JSON exceeds 2 MB")
            project = validate_project(json.loads(source.decode("utf-8"), parse_constant=lambda value: fail(
                f"Non-finite JSON value: {value}")), {
                IMAGE_ENTRY.fullmatch(name).group(1) for name in names if IMAGE_ENTRY.fullmatch(name)
            })
            game = temporary / "game"
            game.mkdir()
            (game / "project.json").write_bytes(source)
            (game / "assets").mkdir()
            for image in project["assets"]:
                asset_id = image["id"]
                data = package.read(f"assets/{asset_id}.img")
                if len(data) > MAX_IMAGE:
                    fail("Image exceeds 10 MB")
                (game / "assets" / f"{asset_id}.img").write_bytes(data)
        temporary.rename(STAGE)
    finally:
        if temporary.exists():
            shutil.rmtree(temporary)

    if icon_data is not None:
        destination = ICONS / "drawable-nodpi"
        destination.mkdir(parents=True)
        (destination / "game_icon.png").write_bytes(icon_data)
    return project


def main() -> int:
    parser = argparse.ArgumentParser(description="Build an Android game APK from an S Engine project")
    parser.add_argument("archive", type=Path, help=".sengine file exported by the mobile editor")
    parser.add_argument("--application-id", help="Unique Android package ID, e.g. com.example.mygame")
    parser.add_argument("--name", help="Launcher label (defaults to project name)")
    parser.add_argument("--icon", type=Path, help="Optional square PNG app icon")
    parser.add_argument("--version-code", type=int, default=1)
    parser.add_argument("--version-name", default="1.0")
    parser.add_argument("--release", action="store_true", help="Build a signed release APK using SENGINE_KEY* env vars")
    parser.add_argument("--stage-only", action="store_true", help="Validate and stage without invoking Gradle")
    parser.add_argument("--output", type=Path, help="APK destination (defaults under build/game-apks/)")
    args = parser.parse_args()
    try:
        project = stage_project(args.archive.resolve(), args.icon.resolve() if args.icon else None)
        app_id = args.application_id or ("com.sengine.game.g" + project["id"].replace("-", "")[:12].lower())
        if not PACKAGE.fullmatch(app_id) or len(app_id) > 150:
            fail("Use an application ID with at least three lowercase dot-separated segments")
        name = args.name or str(project.get("name", "S Engine Game"))
        if (not name or len(name) > 48 or not name.isprintable() or name[0] in '@?' or
                any(character in name for character in '\"\'\\<>&')):
            fail("Game name must be 1–48 printable characters without quotes or XML punctuation")
        if args.version_code <= 0 or args.version_code > 2_100_000_000:
            fail("Version code must be positive")
        if not re.fullmatch(r"[A-Za-z0-9._-]{1,32}", args.version_name):
            fail("Invalid version name")
        if args.release:
            if not all(os.environ.get(key) for key in (
                "SENGINE_KEYSTORE_PATH", "SENGINE_KEYSTORE_PASSWORD", "SENGINE_KEY_ALIAS", "SENGINE_KEY_PASSWORD"
            )):
                fail("Release signing requires SENGINE_KEYSTORE_PATH, SENGINE_KEYSTORE_PASSWORD, SENGINE_KEY_ALIAS and SENGINE_KEY_PASSWORD")
            keystore = Path(os.environ["SENGINE_KEYSTORE_PATH"])
            if not keystore.is_absolute() or not keystore.is_file():
                fail("SENGINE_KEYSTORE_PATH must be an existing absolute path")
        if args.stage_only:
            print(f"Validated {project['name']} and staged assets at {STAGE}")
            return 0
        variant = "Release" if args.release else "Debug"
        command = [str(ROOT / "gradlew"), f":player:assemble{variant}",
                   f"-PgameApplicationId={app_id}", f"-PgameName={name}",
                   f"-PgameVersionCode={args.version_code}", f"-PgameVersionName={args.version_name}",
                   f"-PgameHasIcon={'true' if args.icon else 'false'}"]
        print(f"Building {name} ({app_id})…", flush=True)
        subprocess.run(command, cwd=ROOT, check=True)
        built = ROOT / "player" / "build" / "outputs" / "apk" / variant.lower() / f"player-{variant.lower()}.apk"
        if not built.is_file():
            fail("Gradle completed but no player APK was produced")
        target = args.output or (ROOT / "build" / "game-apks" / f"{app_id}-{variant.lower()}.apk")
        target = target.resolve()
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(built, target)
        print(f"APK: {target}\nSHA-256: {hashlib.sha256(target.read_bytes()).hexdigest()}")
        return 0
    except (ValueError, OSError, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        print(f"Build failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
