#!/usr/bin/env python3
"""Generate Arboretum's tree structure templates.

Arboretum reads Minecraft structure-template NBT, which is what a structure block saves. That is
the format contributors author in: build a tree in-game, save the structure block, drop the .nbt in
next to the species file. This script is not the intended authoring path -- it exists so the four
shipped species have correct starting geometry without hand-building each one, and so the format is
covered by a second independent implementation.

Marker convention, matching what a contributor would produce by saving a real tree:
  <log block>      the trunk and branches
  <leaf block>     the foliage
  the species' root block, when it has one

Everything else in a saved structure (air, wool helpers) is ignored by the placement planner.

Usage:
    tools/generate_templates.py [--out DIR] [--species baobab jacaranda bald_cypress italian_cypress]

Output is written uncompressed so templates diff readably in git; the loader accepts either form.
"""

import argparse
import gzip
import math
import os
import struct
import sys

TAG_END = 0
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_STRING = 8
TAG_LIST = 9
TAG_INT = 3


class IntArray(list):
    """Marks a list as an NBT int array rather than a list of ints.

    Structure templates use both: 'size' and 'pos' are int arrays, 'blocks' and 'palette' are
    lists of compounds. Getting this wrong produces a file Minecraft silently ignores.
    """


# --- NBT writing --------------------------------------------------------------------------------
def _string(text):
    data = text.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def _payload(tag):
    if isinstance(tag, str):
        return _string(tag)
    if isinstance(tag, IntArray):
        return struct.pack(">i", len(tag)) + b"".join(struct.pack(">i", v) for v in tag)
    if isinstance(tag, int):
        return struct.pack(">i", tag)
    if isinstance(tag, list):
        return bytes([_tag_id(tag[0]) if tag else TAG_END]) + struct.pack(">i", len(tag)) \
            + b"".join(_payload(element) for element in tag)
    if isinstance(tag, dict):
        out = b""
        for key, value in tag.items():
            out += bytes([_tag_id(value)]) + _string(key) + _payload(value)
        return out + bytes([TAG_END])
    raise TypeError("unsupported NBT payload: %r" % (tag,))


def _tag_id(value):
    if isinstance(value, str):
        return TAG_STRING
    if isinstance(value, IntArray):
        return TAG_INT_ARRAY
    if isinstance(value, int):
        return TAG_INT
    if isinstance(value, list):
        return TAG_LIST
    if isinstance(value, dict):
        return TAG_COMPOUND
    raise TypeError("unsupported NBT value: %r" % (value,))


# The log, leaf and root block each species uses. These must match the species JSON files;
# tools/check_species.py cross-checks the two so a mismatch is caught in CI rather than in game.
SPECIES_BLOCKS = {
    "baobab": ("minecraft:dark_oak_log", "minecraft:dark_oak_leaves", None),
    "jacaranda": ("minecraft:acacia_log", "minecraft:acacia_leaves", None),
    "bald_cypress": ("minecraft:mangrove_log", "minecraft:mangrove_leaves", "minecraft:mangrove_roots"),
    "italian_cypress": ("minecraft:spruce_log", "minecraft:spruce_leaves", None),
}


def write_nbt(root, compress):
    body = bytes([TAG_COMPOUND]) + _string("") + _payload(root)
    return gzip.compress(body) if compress else body


# --- template assembly --------------------------------------------------------------------------
class Template:
    """Accumulates blocks and emits a structure template with a deduplicated palette."""

    def __init__(self):
        self.blocks = []
        self.palette = []

    def add(self, x, y, z, block, properties=None):
        entry = {"Name": block}
        if properties:
            entry["Properties"] = {k: str(v) for k, v in properties.items()}
        if entry not in self.palette:
            self.palette.append(entry)
        self.blocks.append({"pos": [x, y, z], "state": self.palette.index(entry)})

    def build(self, data_version=4325):
        if not self.blocks:
            raise ValueError("template has no blocks")
        xs = [b["pos"][0] for b in self.blocks]
        ys = [b["pos"][1] for b in self.blocks]
        zs = [b["pos"][2] for b in self.blocks]
        # The declared size must contain every block. Generous by one so a rotated template's
        # bounding box never pokes outside the declared box.
        size = [max(xs) - min(xs) + 3, max(ys) - min(ys) + 1, max(zs) - min(zs) + 3]
        return {
            "size": IntArray(size),
            "entities": [],
            "blocks": self.blocks,
            "palette": self.palette,
            "DataVersion": data_version,
        }


def _blob(radius, y_offset, jitter, rng, taper=1.0):
    """A roughly ellipsoidal set of (x, y, z) offsets, for canopies."""
    points = []
    # One-block steps: a two-block lattice left every leaf unconnected, so canopies decayed.
    steps = 1.0
    x = -radius
    while x <= radius:
        y = -radius
        while y <= radius:
            z = -radius
            while z <= radius:
                distance = math.sqrt(x * x + y * y + (z * z) / (taper * taper))
                if distance <= radius + jitter * rng.uniform(-0.35, 0.35):
                    points.append((int(round(x)), int(round(y_offset + y)), int(round(z))))
                z += steps
            y += steps
        x += steps
    return points


# --- species geometry ---------------------------------------------------------------------------
def build_baobab(rng):
    """A baobab: an enormous tapering trunk that forks high, with a broad flat crown.

    Modelled on Adansonia digitata, whose trunk is disproportionately wide for its height and whose
    branches are stubby and upswept rather than long and pendulous.
    """
    log, leaf, _ = SPECIES_BLOCKS["baobab"]
    trunk = Template()
    canopy = Template()

    height = 16
    # Baobab trunks are wider than they are tall in proportion. A 5-wide column over 16 blocks is
    # roughly right for a mature specimen.
    base_radius = 2.6
    top_radius = 1.6

    for y in range(height):
        t = y / (height - 1)
        radius = base_radius + (top_radius - base_radius) * t
        r = int(math.floor(radius))
        for x in range(-r, r + 1):
            for z in range(-r, r + 1):
                if x * x + z * z <= radius * radius:
                    trunk.add(x, y, z, log, {"axis": "y"})

    # Fork into a handful of stubby upswept limbs. Each limb climbs and drifts outward, so the
    # crown sits wide rather than tall.
    limbs = 6
    for limb in range(limbs):
        angle = 2.0 * math.pi * limb / limbs + rng.uniform(-0.2, 0.2)
        dx, dz = math.cos(angle), math.sin(angle)
        x, z = round(dx * top_radius), round(dz * top_radius)
        y = height - 2
        length = rng.randint(5, 8)
        for step in range(length):
            # Limbs rise for the first two thirds and then flatten out, which is the baobab read.
            rise = 1 if step < length * 0.6 else 0
            x, z = x + round(dx), z + round(dz)
            y += rise
            canopy.add(x, y, z, log, {"axis": "y"})
            tip = (x, y, z)
            # Foliage rides on the outer half of each limb, sparse as the resource pack intends.
            for offset in _blob(1.4, 0, 0.5, rng, taper=1.4):
                fx, fy, fz = tip[0] + offset[0], tip[1] + offset[1], tip[2] + offset[2]
                if offset[0] ** 2 + offset[2] ** 2 >= 1.0:
                    canopy.add(fx, fy, fz, leaf)

    # A shallow crown across the top of the trunk, so the fork does not read as six bare sticks.
    for offset in _blob(4.2, 0, 0.6, rng, taper=0.55):
        canopy.add(offset[0], height + offset[1], offset[2], leaf)

    return trunk.build(), canopy.build()


def build_jacaranda(rng):
    """A jacaranda: a slimmer, branching tree with a wide airy canopy.

    Jacaranda mimosifolia forks low and spreads; the crown is broad and comparatively thin.
    """
    log, leaf, _ = SPECIES_BLOCKS["jacaranda"]
    trunk = Template()
    canopy = Template()

    height = 13
    for y in range(height):
        radius = 1.4 - 0.6 * (y / (height - 1))
        r = int(math.floor(radius))
        for x in range(-r, r + 1):
            for z in range(-r, r + 1):
                if x * x + z * z <= radius * radius:
                    trunk.add(x, y, z, log, {"axis": "y"})

    # Fork low, at about a third of the height, which is what separates a jacaranda's silhouette
    # from a generic broadleaf.
    forks = 4
    fork_y = 5
    for fork in range(forks):
        angle = 2.0 * math.pi * fork / forks + rng.uniform(-0.15, 0.15)
        dx, dz = math.cos(angle), math.sin(angle)
        x, z = round(dx), round(dz)
        y = fork_y
        while y < height + 3:
            x, z = x + round(dx), z + round(dz)
            y += 1
            canopy.add(x, y, z, log, {"axis": "y"})
            for offset in _blob(1.6, 0, 0.4, rng, taper=1.1):
                if offset[0] ** 2 + offset[2] ** 2 >= 1.2:
                    canopy.add(x + offset[0], y + offset[1], z + offset[2], leaf)

    for offset in _blob(5.0, 0, 0.8, rng, taper=0.5):
        canopy.add(offset[0], height + offset[1], offset[2], leaf)

    return trunk.build(), canopy.build()


def build_bald_cypress(rng):
    """A bald cypress: a straight trunk flaring into buttresses, with knees at the base.

    Taxodium distichum is famous for its buttressed base and the cypress knees that rise out of the
    water beside it, which is why it is the species that carries the wetland look.
    """
    log, leaf, root = SPECIES_BLOCKS["bald_cypress"]
    trunk = Template()
    canopy = Template()
    roots = Template()

    height = 20
    for y in range(height):
        radius = 1.1
        for x in range(-1, 2):
            for z in range(-1, 2):
                if x * x + z * z <= radius * radius:
                    trunk.add(x, y, z, log, {"axis": "y"})

    # Buttresses: wedges flaring out and down from the base.
    for direction in range(8):
        angle = 2.0 * math.pi * direction / 8
        dx, dz = math.cos(angle), math.sin(angle)
        for step in range(1, 4):
            x, z = round(dx * step), round(dz * step)
            trunk.add(x, 0, z, root, {"axis": _axis_of(dx, dz)})
            for rise in range(1, 3):
                # The buttress thins as it rises.
                if step > rise:
                    trunk.add(x, rise, z, root, {"axis": _axis_of(dx, dz)})

    # Cypress knees: the conical root projections that stand out of the water around an old tree.
    for _ in range(9):
        angle = rng.uniform(0, 2 * math.pi)
        distance = rng.uniform(3.0, 6.0)
        kx, kz = round(math.cos(angle) * distance), round(math.sin(angle) * distance)
        knee_height = rng.randint(1, 3)
        for y in range(knee_height):
            roots.add(kx, y, kz, root, {"axis": "y"})

    # A rounded but open crown. Bald cypress foliage is notably fine-textured, so the canopy is
    # punctured rather than solid.
    for offset in _blob(4.6, 0, 0.7, rng, taper=0.85):
        if rng.random() > 0.18:
            canopy.add(offset[0], height - 2 + offset[1], offset[2], leaf)
    return trunk.build(), canopy.build(), roots.build()


def _axis_of(dx, dz):
    if abs(dx) > abs(dz):
        return "x"
    return "z"


def build_italian_cypress(rng):
    """An Italian cypress: tall, narrow and columnar, which is almost entirely silhouette.

    Cupressus sempervirens is the shape every Mediterranean landscape painting reaches for, so the
    narrowness matters more than any detail.
    """
    log, leaf, _ = SPECIES_BLOCKS["italian_cypress"]
    trunk = Template()
    canopy = Template()

    height = 22
    for y in range(height):
        trunk.add(0, y, 0, log, {"axis": "y"})

    # A tight column that tapers to a point. The radius shrinks with height and the top rounds off.
    for y in range(height - 4, height + 9):
        t = (y - (height - 4)) / 12.0
        radius = 2.2 * (1.0 - t ** 1.6) + 0.3
        r = int(math.floor(radius))
        for x in range(-r, r + 1):
            for z in range(-r, r + 1):
                if x * x + z * z <= radius * radius:
                    # Skip the trunk column itself, which the trunk template already filled.
                    if x == 0 and z == 0 and y < height:
                        continue
                    canopy.add(x, y, z, leaf)
    # Dense skirt down the lower trunk, where a real cypress carries foliage to the ground.
    for y in range(2, height - 2):
        for x in (-1, 0, 1):
            for z in (-1, 0, 1):
                if x == 0 and z == 0:
                    continue
                if rng.random() > 0.15:
                    canopy.add(x, y, z, leaf)
    return trunk.build(), canopy.build()


BUILDERS = {
    "baobab": build_baobab,
    "jacaranda": build_jacaranda,
    "bald_cypress": build_bald_cypress,
    "italian_cypress": build_italian_cypress,
}


DECAY_DISTANCE = 7


def prune_decaying(templates, log, leaf):
    """Drops every leaf vanilla would let decay: farther than 6 blocks from a log through leaves.

    Run over the species' templates together, as they are placed - where a canopy leaf and a
    trunk log share a position the log wins, as in the planner. Without this a generated canopy
    rots away within minutes of the tree growing.
    """
    def name(template, block):
        return template["palette"][block["state"]]["Name"]

    logs = {tuple(b["pos"]) for t in templates for b in t["blocks"] if name(t, b) == log}
    leaves = {tuple(b["pos"]) for t in templates for b in t["blocks"] if name(t, b) == leaf} - logs
    distance = {pos: 0 for pos in logs}
    frontier = list(logs)
    while frontier:
        nxt = []
        for (x, y, z) in frontier:
            d = distance[(x, y, z)] + 1
            if d >= DECAY_DISTANCE:
                continue
            for n in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
                if n in leaves and n not in distance:
                    distance[n] = d
                    nxt.append(n)
        frontier = nxt
    pruned = 0
    for t in templates:
        kept = [b for b in t["blocks"] if name(t, b) != leaf or tuple(b["pos"]) in distance]
        pruned += len(t["blocks"]) - len(kept)
        t["blocks"] = kept
    return pruned


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", default="arboretum-core/src/main/resources/data/arboretum/templates",
                        help="output directory for the .nbt templates")
    parser.add_argument("--species", nargs="+", default=sorted(BUILDERS),
                        help="which species to generate")
    parser.add_argument("--compress", action="store_true", help="gzip the output")
    parser.add_argument("--seed", type=int, default=20261013, help="RNG seed, for reproducible output")
    args = parser.parse_args()

    import random

    for name in args.species:
        rng = random.Random(args.seed + sum(ord(c) for c in name))
        built = BUILDERS[name](rng)
        log, leaf, _ = SPECIES_BLOCKS[name]
        pruned = prune_decaying(list(built), log, leaf)
        if pruned:
            print("%s: pruned %d leaves that would decay" % (name, pruned))
        directory = os.path.join(args.out, name)
        os.makedirs(directory, exist_ok=True)
        parts = list(zip(("trunk", "canopy", "roots"), built))
        for part, template in parts:
            path = os.path.join(directory, part + ".nbt")
            with open(path, "wb") as handle:
                handle.write(write_nbt(template, args.compress))
            print("wrote %s (%d blocks)" % (path, len(template["blocks"])))
    return 0


if __name__ == "__main__":
    sys.exit(main())