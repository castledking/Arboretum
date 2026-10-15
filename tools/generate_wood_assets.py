#!/usr/bin/env python3
"""Writes the Fabric mod's assets and data for each Arboretum wood type.

For every wood in WOODS: a log, a wood (bark on all sides) and leaves, with the blockstates,
block and item models, loot tables, tags and English names vanilla gives its own woods - in
vanilla 26.3's formats, copied from its acacia files. The textures come from the artist's folder.

    python3 tools/generate_wood_assets.py [--textures ~/Documents/aboretum]

Adding a species' wood: add it to WOODS here and to ArboretumBlocks.WOODS in the mod, put its
log, log top and leaves textures in the texture folder, and run this. The block registration
in Java and these files must name the same blocks; the mod's boot test checks they all load.
"""
import argparse
import json
import os
import shutil
import sys

# name -> (display name, texture folder, texture file prefix in that folder)
WOODS = {
    "baobab": ("Baobab", "baobab", "dark_oak"),
    "jacaranda": ("Jacaranda", "jacaranda", "acacia"),
}

ROOT = os.path.join(os.path.dirname(__file__), "..", "arboretum-fabric", "src", "main", "resources")
NS = "arboretum"


def write(path, data):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w") as handle:
        json.dump(data, handle, indent=2)
        handle.write("\n")


def pillar_blockstate(model, horizontal):
    return {"variants": {
        "axis=x": {"model": horizontal, "x": 90, "y": 90},
        "axis=y": {"model": model},
        "axis=z": {"model": horizontal, "x": 90},
    }}


def self_drop(block):
    return {"type": "minecraft:block", "random_sequence": f"{NS}:blocks/{block}", "pools": [{
        "rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
        "entries": [{"type": "minecraft:item", "name": f"{NS}:{block}"}]}]}


def leaves_drop(block):
    """Vanilla's leaves table without the sapling: itself to shears or silk touch, else sometimes sticks."""
    tool = {"type": "minecraft:any_of", "terms": ["minecraft:tool/can_shear", "minecraft:tool/can_silk_touch"]}
    return {"type": "minecraft:block", "random_sequence": f"{NS}:blocks/{block}", "pools": [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "condition": tool, "name": f"{NS}:{block}"}]},
        {"rolls": 1, "condition": {"type": "minecraft:inverted", "term": tool},
         "entries": [{"type": "minecraft:item", "name": "minecraft:stick",
                      "condition": {"type": "minecraft:table_bonus", "enchantment": "minecraft:fortune",
                                    "chances": [0.02, 0.022222223, 0.025, 0.033333335, 0.1]},
                      "modifier": [{"type": "minecraft:set_count",
                                    "count": {"type": "minecraft:uniform", "min": 1, "max": 2}},
                                   {"type": "minecraft:explosion_decay"}]}]}]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--textures", default=os.path.expanduser("~/Documents/aboretum"))
    args = parser.parse_args()

    lang = {}
    logs, leaves = [], []
    for name, (display, folder, prefix) in WOODS.items():
        log, wood, leaf = f"{name}_log", f"{name}_wood", f"{name}_leaves"
        for source, target in ((f"{prefix}_log.png", f"{name}_log.png"),
                               (f"{prefix}_log_top.png", f"{name}_log_top.png"),
                               (f"{prefix}_leaves.png", f"{name}_leaves.png")):
            src = os.path.join(args.textures, folder, source)
            if not os.path.exists(src):
                print(f"missing texture {src}", file=sys.stderr)
                return 1
            dst = os.path.join(ROOT, "assets", NS, "textures", "block", target)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(src, dst)

        write(f"assets/{NS}/models/block/{log}.json", {"parent": "minecraft:block/cube_column", "textures": {
            "end": f"{NS}:block/{log}_top", "side": f"{NS}:block/{log}"}})
        write(f"assets/{NS}/models/block/{log}_horizontal.json", {"parent": "minecraft:block/cube_column_horizontal",
            "textures": {"end": f"{NS}:block/{log}_top", "side": f"{NS}:block/{log}"}})
        write(f"assets/{NS}/models/block/{wood}.json", {"parent": "minecraft:block/cube_column", "textures": {
            "end": f"{NS}:block/{log}", "side": f"{NS}:block/{log}"}})
        # The tinted leaves parent, as vanilla's cherry: with no colour provider registered the
        # tint is white, so the texture shows its own painted colour.
        write(f"assets/{NS}/models/block/{leaf}.json", {"parent": "minecraft:block/leaves", "textures": {
            "all": f"{NS}:block/{leaf}"}})

        write(f"assets/{NS}/blockstates/{log}.json", pillar_blockstate(f"{NS}:block/{log}", f"{NS}:block/{log}_horizontal"))
        write(f"assets/{NS}/blockstates/{wood}.json", pillar_blockstate(f"{NS}:block/{wood}", f"{NS}:block/{wood}"))
        write(f"assets/{NS}/blockstates/{leaf}.json", {"variants": {"": {"model": f"{NS}:block/{leaf}"}}})

        for block in (log, wood, leaf):
            write(f"assets/{NS}/items/{block}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{block}"}})
        write(f"data/{NS}/loot_table/blocks/{log}.json", self_drop(log))
        write(f"data/{NS}/loot_table/blocks/{wood}.json", self_drop(wood))
        write(f"data/{NS}/loot_table/blocks/{leaf}.json", leaves_drop(leaf))

        lang[f"block.{NS}.{log}"] = f"{display} Log"
        lang[f"block.{NS}.{wood}"] = f"{display} Wood"
        lang[f"block.{NS}.{leaf}"] = f"{display} Leaves"
        logs += [f"{NS}:{log}", f"{NS}:{wood}"]
        leaves.append(f"{NS}:{leaf}")

    # Logs must be in #minecraft:logs: vanilla leaves count their distance to it, so leaves
    # beside a log outside the tag would decay. logs_that_burn is inside logs, and also makes
    # them fuel and charcoal.
    for kind in ("block", "item"):
        write(f"data/minecraft/tags/{kind}/logs_that_burn.json", {"replace": False, "values": logs})
        write(f"data/minecraft/tags/{kind}/leaves.json", {"replace": False, "values": leaves})
    write("data/minecraft/tags/block/mineable/axe.json", {"replace": False, "values": logs})
    write("data/minecraft/tags/block/mineable/hoe.json", {"replace": False, "values": leaves})
    write(f"assets/{NS}/lang/en_us.json", lang)
    print(f"wrote assets and data for {', '.join(WOODS)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
