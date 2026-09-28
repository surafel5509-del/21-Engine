"""Host-side archive validation tests (no JDK or Android SDK needed)."""

import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from tools import build_android_game as builder


def project():
    return {
        "formatVersion": 1, "id": "project-1", "name": "My Game",
        "activeSceneId": "scene-1", "assets": [], "scripts": [],
        "scenes": [{"id": "scene-1", "name": "World", "gameWidth": 480, "gameHeight": 320,
                    "entities": [{"id": "sprite-1", "name": "Sprite", "visual": {"type": "BOX"}}]}],
    }


class PackageTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.stage_patch = patch.object(builder, "STAGE", self.root / "player" / "build" / "stagedGame")
        self.icons_patch = patch.object(builder, "ICONS", self.root / "player" / "build" / "generatedIcons")
        self.stage_patch.start()
        self.icons_patch.start()
        self.addCleanup(self.stage_patch.stop)
        self.addCleanup(self.icons_patch.stop)

    def archive(self, payload, extra=None):
        destination = self.root / "sample.sengine"
        with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as package:
            package.writestr("project.json", json.dumps(payload))
            for name, data in (extra or {}).items():
                package.writestr(name, data)
        return destination

    def test_valid_package_stages_same_scene_and_image_data(self):
        payload = project()
        payload["assets"] = [{"id": "image-1", "name": "Pixel", "folder": "Textures/UI"}]
        payload["scenes"][0]["entities"][0]["visual"] = {"type": "IMAGE", "assetId": "image-1"}
        archive = self.archive(payload, {"assets/image-1.img": b"image bytes"})
        actual = builder.stage_project(archive, None)
        self.assertEqual(actual["scenes"], payload["scenes"])
        self.assertEqual((builder.STAGE / "game/project.json").read_text(), json.dumps(payload))
        self.assertEqual((builder.STAGE / "game/assets/image-1.img").read_bytes(), b"image bytes")

    def test_cannot_package_unlisted_image_or_missing_reference(self):
        for name, data in (("assets/extra.img", b"x"), ("elsewhere.txt", b"x")):
            with self.subTest(name=name):
                with self.assertRaises(ValueError):
                    builder.stage_project(self.archive(project(), {name: data}), None)
        bad = project()
        bad["scenes"][0]["entities"][0]["visual"] = {"type": "IMAGE", "assetId": "missing"}
        with self.assertRaises(ValueError):
            builder.stage_project(self.archive(bad), None)

    def test_traversal_entries_are_rejected_even_without_extraction(self):
        for name in ("../outside", "/tmp/outside", "assets/../other.img", "assets\\backslash.img"):
            with self.subTest(name=name):
                with self.assertRaises(ValueError):
                    builder.stage_project(self.archive(project(), {name: b"attack"}), None)

    def test_previous_stage_is_removed_after_invalid_input(self):
        builder.stage_project(self.archive(project()), None)
        self.assertTrue(builder.STAGE.exists())
        bad = project()
        bad["formatVersion"] = 999
        with self.assertRaises(ValueError):
            builder.stage_project(self.archive(bad), None)
        self.assertFalse(builder.STAGE.exists())

    def test_wrong_types_or_duplicate_entities_are_rejected(self):
        for change in (lambda p: p["scenes"][0].update(gameWidth="wide"),
                       lambda p: p["scenes"][0].update(entities=[p["scenes"][0]["entities"][0]] * 2),
                       lambda p: p.update(scripts=[{"id": "../script", "name": "Bad", "source": "on start"}]),
                       lambda p: p.update(name="")):
            payload = copy.deepcopy(project())
            change(payload)
            with self.assertRaises(ValueError):
                builder.stage_project(self.archive(payload), None)

    def test_declared_decompressed_size_is_bounded(self):
        payload = project()
        payload["assets"] = [{"id": "big", "name": "Big"}]
        with self.assertRaises(ValueError):
            builder.stage_project(self.archive(payload, {"assets/big.img": b"A" * (builder.MAX_IMAGE + 1)}), None)

    def test_duplicate_archive_entries_are_rejected(self):
        destination = self.root / "duplicate.sengine"
        with zipfile.ZipFile(destination, "w") as package:
            package.writestr("project.json", json.dumps(project()))
            package.writestr("assets/extra.img", b"first")
            with self.assertWarns(UserWarning):
                package.writestr("assets/extra.img", b"second")
        with self.assertRaises(ValueError):
            builder.stage_project(destination, None)

    def test_invalid_icon_cannot_leave_staged_game(self):
        builder.stage_project(self.archive(project()), None)
        icon = self.root / "wrong.png"
        icon.write_bytes(b"NOT A PNG")
        with self.assertRaises(ValueError):
            builder.stage_project(self.root / "sample.sengine", icon)
        self.assertFalse(builder.STAGE.exists())


if __name__ == "__main__":
    unittest.main()
