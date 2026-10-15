# Adding a tree

How a tree built in a world becomes an Arboretum species. The jacaranda's four trees were made
exactly this way.

## 1. Build it

Build the tree in a test world from any vanilla blocks; they're only markers. The jacaranda is
acacia wood and acacia leaves. Two rules the game enforces:

- **Every leaf within 6 blocks of a log**, counted through leaves. Vanilla decays leaves farther
  out, minutes after the tree grows. Run branches into each clump of foliage. Arboretum warns
  at startup about any template with leaves out of reach.
- **The lowest layer of logs is where the tree meets the ground.** Don't bury the trunk: logs in
  the dirt hit the terrain when the tree generates, and the tree is refused as obstructed.

## 2. Copy it

Copy the whole tree into one file per tree. Each file is a variant; make several.

- Box anything around it: air, grass, flowers and dirt in the copy are ignored. Only the blocks
  the species declares as markers are placed.
- The copy's corner doesn't matter. Arboretum finds the trunk's base - the centre of its lowest
  layer of logs - and grows the tree from there.
- FastAsyncWorldEdit plus a schematic-to-structure converter works (the converter's
  `id`/`properties` palette spelling is read). So does a vanilla structure block in Save mode,
  up to 48×48×48.

Put the files in `arboretum-core/src/main/resources/data/arboretum/templates/<species>/`.

## 3. Declare it

In `data/arboretum/species/<species>.json`, list the trees and map each building block to the
block it becomes:

```json
"structure": {
  "templates": [
    "data/arboretum/templates/jacaranda/jacaranda_01.nbt",
    { "path": "data/arboretum/templates/jacaranda/jacaranda_02.nbt", "weight": 2 }
  ],
  "log_markers": { "minecraft:acacia_wood": "arboretum:jacaranda_wood" },
  "leaf_markers": { "minecraft:acacia_leaves": "arboretum:jacaranda_leaves" },
  "growable_on": ["minecraft:dirt", "minecraft:grass_block", "minecraft:coarse_dirt"]
}
```

A tree's blocks keep the shape they were built with: a sideways branch stays sideways. Leaves
grow as natural leaves (they decay once the tree is cut), whatever they were when you built it.

## 4. Its blocks

A species with its own wood needs textures (`<prefix>_log.png`, `<prefix>_log_top.png` and
`<prefix>_leaves.png` in the texture folder), an entry in `tools/generate_wood_assets.py`, and
one in `ArboretumBlocks.WOODS` in the mod (strength and sounds copied from a vanilla wood, plus
the falling-leaf colour). Then:

```sh
python3 tools/generate_wood_assets.py
```

## 5. Check it

```sh
./gradlew :arboretum-core:test        # every tree, every rotation, every refusal
./gradlew :arboretum-fabric:build
tools/fabric-boot-test.sh              # a real server grows and verifies the trees
```

The startup log names any file, field or template that is wrong, and why.
