# Veinminer++

A performant, tool-aware vein miner for Minecraft 26.2 (Fabric + NeoForge).

**Hold Sneak while you break a block** and Veinminer++ mines the whole connected
vein — any block your tool can actually collect, not just ores — staggered across
ticks so it never freezes the server. Cycle between a connectivity Vein, box shapes
(3×3×3, plus opt-in 5×5×5 and 9×9×3), and an opt-in larger Spread; fell whole trees
(with a safety net for wooden builds); optionally void common materials; and tune
limits, durability cost, and tool requirements in-game.

Built around a shared `common/` code path so the mining logic is byte-identical
on both loaders.

## Usage

- **Activate:** hold **Sneak** (crouch) while breaking a block. The block you hit
  is mined by vanilla as usual; Veinminer++ adds the rest of the vein.
- **Cycle shape:** `[` previous / `]` next. The active shape is shown on the
  action bar, and box shapes briefly draw an xray-style edge guide.
- One vein runs at a time per player; it drains a few blocks per tick until done.

## Shapes

| Shape | What it does | Volume / cap |
|---|---|---|
| **Vein** *(default)* | Flood-fills connected matching blocks (26-connected, diagonals included). Follows ore veins and like-for-like blocks. | `veinMax` = 32 |
| **3×3×3** | An oriented cube in front of you — depth extends from the face you break. Mines every block in it you can collect. | full volume (27) |
| **5×5×5** *(opt-in)* | A larger oriented cube. Appears in the cycle only when **Enable extra shapes** is on. | full volume (125) |
| **9×9×3** *(opt-in)* | A wide, shallow slab (9 wide × 9 deep × 3 tall) for clearing floors and ceilings. Needs **Enable extra shapes**. | full volume (243) |
| **Spread** *(opt-in)* | The same connectivity flood as Vein, but with the larger cap. Needs **Enable Spread mode**. | `spreadMax` = 256 |

Box shapes break their full volume (no block cap); the per-tick stagger keeps even
a 243-block slab from spiking the server.

## Features

- **Tool-aware:** uses the held tool's own mining ability, so any vanilla *or
  modded* pickaxe/shovel/axe works. A block that wouldn't drop for you is never
  broken — an iron pickaxe won't waste obsidian, it just skips it.
- **Smart ore / fuzzy matching:** the connectivity flood groups related blocks by
  tag, so a vein of mixed variants (e.g. stone + deepslate ores of the same metal,
  or a single log species) is followed as one.
- **Optional toolless mining:** by default, soft blocks (dirt, sand, grass) can be
  vein-mined with your hand or any item — handy early game or to save durability.
  Flip **Require a tool** on to demand a tool for those too. Blocks that need a
  correct tool always need one regardless.
- **Tree felling:** breaking a log fells the connected logs (large cap for tall
  2×2 giant trees). A **smart-tree** safety net only fells when leaves are attached
  to the logs, so log- and wood-block *builds* (no leaves) lose just the one block.
- **Tidy leaves:** when an *isolated* tree is felled, its leaf canopy is cleared
  too, dropping the same loot leaves give on decay (saplings, sticks, apples) at no
  durability cost. Canopies shared with a neighbouring tree, or oversized merged
  canopies, are left to decay normally.
- **Void basic materials** *(opt-in):* turn it on and common blocks (stone, dirt,
  grass, cobblestone, deepslate, gravel, sand…) broken by vein-mining are deleted
  instead of dropped, so your inventory keeps only the loot you care about. The list
  is hand-editable in `config/veinminerplusplus/void-blocks.txt`; voided blocks
  still cost durability, and the block you break yourself is voided too.
- **Performant:** capped block counts and a lenient per-tick stagger spread big
  veins and trees across several ticks instead of a single frame spike.
- **Durability control:** per-block durability cost is a configurable percentage of
  vanilla (0 % = free, 100 % = normal, up to 150 %), accumulated across the vein so
  the percentage is exact even below 100 %. Unbreaking still applies, and vein-mining
  stops one hit before your tool would break.
- **In-game config:** a native, vanilla-style settings screen — opened via ModMenu
  on Fabric, or the built-in mod list (Config button) on NeoForge. No Cloth Config
  dependency.

## Configuration

All options live in the in-game config screen; both config files are in
`config/veinminerplusplus/` (`config.json`, plus the hand-edited `void-blocks.txt`).

| Option | Default | Range | Description |
|---|---|---|---|
| Vein cap | 32 | 1–128 | Max blocks broken in one Vein action. |
| Spread cap | 256 | 1–1024 | Max blocks for the larger, opt-in Spread mode. |
| Tree cap | 256 | 1–512 | Max logs felled from one tree. |
| Blocks broken per tick | 16 | 1–64 | Stagger rate — higher is faster but heavier on the server. |
| Require a tool | off | — | Require a tool in hand to vein-mine soft blocks (hard blocks always need the correct tool). |
| Durability cost (%) | 40 | 0–150 | Durability per block as a percentage of vanilla (accumulated, so the % is exact). |
| Void basic materials | off | — | Delete common blocks (listed in `void-blocks.txt`) instead of dropping them. |
| Smart tree detection | on | — | Only fell connected logs when leaves are attached (protects wooden builds). |
| Clear leaves of felled trees | on | — | Clear an isolated felled tree's canopy with decay-equivalent drops. |
| Enable Spread mode | off | — | Add Spread to the `[` / `]` cycle. |
| Enable extra shapes | off | — | Add the 5×5×5 and 9×9×3 boxes to the `[` / `]` cycle. |
| Always show shape guide | off | — | Keep the box edge guide visible while sneaking, not just after cycling. |

## Architecture

Multiloader with a shared `common/` source directory compiled into both loader
jars via `srcDir` (no separate `common` artifact). Loader-agnostic logic — the
vein flood-fill, shapes, tool checks, the staggered breaker, and the config model —
lives in `common/`; only thin loader seams (entrypoint, block-break hook,
networking, keybinds, rendering) are per-loader, kept at parity by construction.

## Build

```
./gradlew buildAll
```

Outputs to `fabric/build/libs/` and `neoforge/build/libs/`.

## Requirements

- Minecraft 26.2
- Java 25
- **Fabric:** Fabric Loader 0.18.4+ and Fabric API. ModMenu is optional (adds the
  config button).
- **NeoForge:** NeoForge 26.2+.

## License

[MIT](LICENSE) © 2026 Kestalkayden — free to use, modify, redistribute, and bundle in modpacks.
