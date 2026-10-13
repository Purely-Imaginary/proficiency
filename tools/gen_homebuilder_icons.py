"""The ability wheel's two Homebuilder slots, 16x16, in the skill-icon style (tools/gen_skill_icons.py).

    python3 tools/gen_homebuilder_icons.py     # writes textures/gui/homebuilder/<magnet|homeward>.png

Same rules and palette as the skill icons: 16 rows of 16 characters, light from the top left, the script
adds the 1 px rim; 'A' 'a' 'b' 'c' are Construction's colour, so the slots read as Homebuilder's.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_skill_icons as S  # noqa: E402

OUT = os.path.join(S.ROOT, "common/src/main/resources/assets/proficiency/textures/gui/homebuilder")

MAPS = {
# Tidy Home's magnet: a horseshoe magnet with iron tips, a gold nugget flying in between the arms.
"magnet": """
................
....RRRRRRRR....
...RRRRRRRRRr...
..RRRrrrrrrRRr..
..RRr......RRr..
..RRr......RRr..
..RRr......RRr..
..RRr..Gg..RRr..
..RRr..gh..RRr..
..RRr......RRr..
..qqi......qqi..
..qij......qij..
..iij......iij..
................
................
................""",
# Homeward: a gold arrow turning home to a house with a lit window.
"homeward": """
G...............
.G......bb......
..G.G..bAAb.....
...GG.bAAAAb....
..GGgbAAAAAAb...
....bAAAAAAAAb..
...bbbbbbbbbbbb.
....uuuuuuuuuu..
....uwwffwwwwu..
....uwwFfwwwwu..
....uwwwwwwwwu..
....uwwwwxxwwu..
....uwwwwx.xwu..
...sstsstx.xsst.
................
................""",
}

NO_OUTLINE = {"homeward": "Gg"}


def main():
    _, _, colours = S.read_categories()
    os.makedirs(OUT, exist_ok=True)
    for name, text in MAPS.items():
        img = S.render(name, text, colours["CONSTRUCTION"], bare=NO_OUTLINE.get(name, ""))
        img.save(os.path.join(OUT, name + ".png"))
    print(f"wrote {len(MAPS)} icons to {OUT}")


if __name__ == "__main__":
    main()
