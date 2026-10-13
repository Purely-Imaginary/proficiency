#!/usr/bin/env python3
"""python3 tools/tests/test_hearth_art.py   (stdlib unittest; needs Pillow)

The self-check of tools/gen_hearth_art.py has to catch what it claims to catch: a rotated element that
z-fights, an element that leaves the block, a HUD glyph with the wrong advance. And the shipped assets
have to pass it."""
import contextlib
import copy
import io
import json
import os
import shutil
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
import gen_hearth_art as G  # noqa: E402
import gen_skill_icons as S  # noqa: E402
from PIL import Image  # noqa: E402


def shipped(name):
    with open(os.path.join(G.MODELS, "block", name + ".json"), encoding="utf-8") as fh:
        return json.load(fh)


class Geometry(unittest.TestCase):
    def test_shipped_assets_pass_the_self_check(self):
        self.assertEqual(G.check(), [])

    def test_every_hearth_stage_stays_in_the_plus_shaped_zone(self):
        for stage in G.STAGES:
            for suffix in ("", "_smouldering"):
                els = shipped(f"hearth_stage_{stage}{suffix}")["elements"]
                self.assertEqual(G.leaving_the_plus(els), [], (stage, suffix))
                self.assertEqual(G.zfighting_faces(els), [], (stage, suffix))

    def test_the_stages_grow_taller(self):
        tops = [max(e["to"][1] for e in shipped(f"hearth_stage_{s}")["elements"]) for s in G.STAGES]
        self.assertEqual(tops, sorted(tops))
        self.assertLessEqual(tops[-1], 32)
        self.assertGreater(tops[-1], tops[0] + 10)

    def test_fire_frames_are_16_wide_and_16_or_32_high(self):
        for stage in G.STAGES:
            p = os.path.join(G.BLOCK_TEX, f"hearth_stage_{stage}_fire.png")
            with open(p + ".mcmeta", encoding="utf-8") as fh:
                anim = json.load(fh)["animation"]
            self.assertEqual(anim["width"], 16)
            self.assertIn(anim["height"], (16, 32))
            self.assertEqual(Image.open(p).size, (16, anim["height"] * 4))

    def test_a_box_in_a_diagonal_cell_is_caught(self):
        els = copy.deepcopy(shipped("hearth_stage_6")["elements"])
        els.append({"from": [-3, 0, -3], "to": [0, 2, 0], "faces": {"up": {"texture": "#collar"}}})
        self.assertEqual(G.leaving_the_plus(els), [len(els) - 1])

    def test_an_arm_longer_than_6_px_or_wider_than_the_block_is_caught(self):
        far = {"from": [-7, 0, 4], "to": [0, 2, 12], "faces": {}}
        wide = {"from": [-3, 0, -1], "to": [0, 2, 12], "faces": {}}
        ok = {"from": [-6, 0, 0], "to": [0, 2, 16], "faces": {}}
        self.assertEqual(G.leaving_the_plus([far, wide, ok]), [0, 1])

    def test_a_flame_wider_than_the_centre_cell_is_caught(self):
        els = copy.deepcopy(shipped("hearth_stage_6")["elements"])
        flame = [e for e in els if e.get("rotation")][0]
        flame["from"][0], flame["to"][0] = -1, 17          # turned 45 degrees it reaches into the diagonal cells
        self.assertTrue(G.leaving_the_plus(els))

    def test_a_tower_above_32_is_caught(self):
        self.assertTrue(G.leaving_the_plus([{"from": [0, 0, 0], "to": [4, 33, 4], "faces": {}}]))

    def test_siege_trophy_models_still_stay_inside_the_block(self):
        els = [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {}}]
        self.assertEqual(G.leaving_the_block(els), [])
        els[0]["to"][1] = 20
        self.assertTrue(G.leaving_the_block(els))

    def test_the_two_sides_of_a_flame_plane_do_not_count_as_z_fighting(self):
        flame = [e for e in shipped("hearth_stage_6")["elements"] if e.get("rotation")]
        self.assertEqual(len(flame), 2)
        self.assertEqual(G.zfighting_faces(flame), [])

    def test_a_second_rotated_plane_on_the_same_plane_is_caught(self):
        flame = [e for e in shipped("hearth_stage_6")["elements"] if e.get("rotation")]
        twin = copy.deepcopy(flame[0])
        twin["to"][1] = 10                          # shorter, but the same plane and the same side
        self.assertTrue(G.zfighting_faces([flame[0], twin]))

    def test_a_plane_turned_the_other_way_is_not(self):
        flame = [e for e in shipped("hearth_stage_6")["elements"] if e.get("rotation")]
        self.assertEqual(G.zfighting_faces([flame[0], flame[1]]), [])

    def test_two_unrotated_boxes_sharing_a_face_plane_are_caught(self):
        a = {"from": [0, 0, 0], "to": [4, 4, 4], "faces": {"up": {"texture": "#t"}}}
        b = {"from": [2, 0, 2], "to": [6, 4, 6], "faces": {"up": {"texture": "#t"}}}
        c = {"from": [2, 0, 2], "to": [6, 5, 6], "faces": {"up": {"texture": "#t"}}}
        self.assertTrue(G.zfighting_faces([a, b]))
        self.assertEqual(G.zfighting_faces([a, c]), [])     # c's top is one unit higher: no shared plane


class Glyphs(unittest.TestCase):
    def test_a_glyph_one_pixel_too_wide_is_caught(self):
        with tempfile.TemporaryDirectory() as tmp:
            for fn in ("hearth_warmth.png", "hearth_hud.png"):
                shutil.copy(os.path.join(G.FONT_TEX, fn), tmp)
            path = os.path.join(tmp, "hearth_hud.png")
            im = Image.open(path).convert("RGBA")
            im.putpixel((2 * G.CELL + G.ICON_W, 4), (255, 255, 255, 255))      # cell 2, column 7
            im.save(path)
            saved, G.FONT_TEX = G.FONT_TEX, tmp
            try:
                bad = []
                G.check_glyphs(bad.append)
            finally:
                G.FONT_TEX = saved
        self.assertEqual(len(bad), 1, bad)
        self.assertIn("cell 2", bad[0])


class Icons(unittest.TestCase):
    """The GUI icons under textures/gui/hearth/ are a contract the client lane draws from: every path has
    to exist, in the skill icons' style, and the check has to say so when one does not."""

    def run_check(self, mutate):
        """Copy the shipped icons, let `mutate(dir)` break one thing, run check_icons on the copy."""
        with tempfile.TemporaryDirectory() as tmp:
            root = os.path.join(tmp, "hearth")
            shutil.copytree(G.GUI_TEX, root)
            mutate(root)
            bad = []
            G.check_icons(bad.append, root)
        return bad

    def test_the_contract_lists_every_path_of_the_spec(self):
        names = G.icon_names()
        self.assertEqual(len(names), 5 + 12 + 6 + 10)
        for want in ("pool/hearty", "pool/homecoming", "comfort/bed", "comfort/trophy", "stage/1", "stage/6",
                     "warmth", "siege", "tales", "provisioned", "character", "comfort", "ledger", "bell", "horn", "trophy"):
            self.assertIn(want, names)

    def test_shipped_icons_are_16_px_and_pass(self):
        self.assertEqual(self.run_check(lambda root: None), [])
        for name in G.icon_names():
            with Image.open(os.path.join(G.GUI_TEX, name + ".png")) as im:
                self.assertEqual(im.size, (16, 16), name)

    def test_every_map_is_rendered_from_the_generator(self):
        for name in G.icon_names():
            fresh = G.icon_image(name).tobytes()
            with Image.open(os.path.join(G.GUI_TEX, name + ".png")) as im:
                shipped_img = im.convert("RGBA").tobytes()
            self.assertEqual(fresh, shipped_img, f"{name}: the PNG is not what the generator draws, rerun tools/gen_hearth_art.py")

    def test_a_missing_icon_is_caught(self):
        bad = self.run_check(lambda root: os.remove(os.path.join(root, "pool", "regen.png")))
        self.assertEqual(len(bad), 1, bad)
        self.assertIn("pool/regen", bad[0])

    def test_a_translucent_pixel_is_caught(self):
        def mutate(root):
            path = os.path.join(root, "comfort", "bed.png")
            im = Image.open(path).convert("RGBA")
            im.putpixel((8, 8), (200, 40, 40, 128))
            im.save(path)
        bad = self.run_check(mutate)
        self.assertTrue(any("translucent" in b and "comfort/bed" in b for b in bad), bad)

    def test_a_wrong_size_is_caught(self):
        def mutate(root):
            Image.new("RGBA", (32, 32), (255, 0, 0, 255)).save(os.path.join(root, "stage", "3.png"))
        bad = self.run_check(mutate)
        self.assertEqual(len(bad), 1, bad)
        self.assertIn("32", bad[0])

    def test_a_stale_file_is_caught(self):
        def mutate(root):
            shutil.copy(os.path.join(root, "bell.png"), os.path.join(root, "gong.png"))
        bad = self.run_check(mutate)
        self.assertTrue(any("stale" in b and "gong" in b for b in bad), bad)

    def test_two_identical_icons_are_caught(self):
        def mutate(root):
            shutil.copy(os.path.join(root, "bell.png"), os.path.join(root, "ledger.png"))
        bad = self.run_check(mutate)
        self.assertTrue(any("identical" in b for b in bad), bad)

    def test_an_icon_without_the_dark_rim_is_caught(self):
        def mutate(root):
            path = os.path.join(root, "warmth.png")
            im = Image.open(path).convert("RGBA")
            for y in range(16):
                for x in range(16):
                    r, g, b, a = im.getpixel((x, y))
                    if a and max(r, g, b) <= 100:
                        im.putpixel((x, y), (230, 200, 120, 255))      # paint the rim light
            im.save(path)
        bad = self.run_check(mutate)
        self.assertTrue(any("rim" in b and "warmth" in b for b in bad), bad)

    def test_icons_sit_in_the_skill_icons_range(self):
        """The limits in the check are the range the 35 skill icons span, so a new icon that passes is no
        busier, no emptier and no less outlined than they are."""
        skills = []
        for fn in sorted(os.listdir(G.SKILL_TEX)):
            if fn.endswith(".png"):
                with Image.open(os.path.join(G.SKILL_TEX, fn)) as im:
                    skills.append(G.icon_stats(im))
        lo, hi = G.ICON_LIMITS["colours"]
        self.assertGreaterEqual(min(s[0] for s in skills), lo)
        self.assertLessEqual(max(s[0] for s in skills), hi)
        lo, hi = G.ICON_LIMITS["fill"]
        self.assertGreaterEqual(min(s[1] for s in skills), lo)
        self.assertLessEqual(max(s[1] for s in skills), hi)
        self.assertGreaterEqual(min(s[2] for s in skills), G.ICON_LIMITS["reach"])


class PendingSkill(unittest.TestCase):
    """gen_skill_icons.PENDING draws an icon before Skill.java lists the skill. It must not hide a stale
    entry or a wrong category."""

    def run_main(self, java_category):
        # Homebuilder is in Skill.java now and its PENDING entry is gone; the mechanism is still
        # tested with a stand-in entry for it.
        real_read, real_out, real_pending = S.read_categories, S.OUT, S.PENDING

        def read():
            order, skills, colours = real_read()
            order = [o for o in order if o != "homebuilder"]
            skills.pop("homebuilder", None)
            if java_category:
                skills["homebuilder"] = java_category
                order.append("homebuilder")
            return order, skills, colours

        err = io.StringIO()
        with tempfile.TemporaryDirectory() as tmp:
            S.read_categories, S.OUT, S.PENDING = read, tmp, {"homebuilder": "CONSTRUCTION"}
            try:
                with contextlib.redirect_stderr(err), contextlib.redirect_stdout(io.StringIO()):
                    S.main()
            finally:
                S.read_categories, S.OUT, S.PENDING = real_read, real_out, real_pending
        return err.getvalue()

    def test_a_skill_not_in_java_yet_is_drawn_and_announced(self):
        self.assertIn("not in Skill.java yet", self.run_main(None))

    def test_a_stale_entry_is_reported(self):
        self.assertIn("delete its PENDING entry", self.run_main("CONSTRUCTION"))

    def test_a_different_category_stops_the_run(self):
        with self.assertRaises(SystemExit) as cm:
            self.run_main("GATHERING")
        self.assertIn("GATHERING", str(cm.exception))


if __name__ == "__main__":
    unittest.main()
