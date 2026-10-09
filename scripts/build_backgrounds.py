#!/usr/bin/env python3
"""List the quiz background images so the static site can pick one at random.

Usage: python3 -I scripts/build_backgrounds.py <static-dir>

Every image in <static-dir>/images except the home-page one is a quiz background.
Writes <static-dir>/data/backgrounds.json. Re-run after adding or removing images.
"""
import json
import sys
from pathlib import Path

HOME_IMAGE = "feels like we just met yesterday.jpeg"
EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}


def main(static_dir):
    root = Path(static_dir)
    names = sorted(p.name for p in (root / "images").iterdir()
                   if p.suffix.lower() in EXTENSIONS and p.name != HOME_IMAGE)
    (root / "data" / "backgrounds.json").write_text(json.dumps(names, indent=2, ensure_ascii=False) + "\n")
    print(f"{len(names)} quiz backgrounds")


if __name__ == "__main__":
    main(sys.argv[1])
