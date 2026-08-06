# Critter Safari Tracker

A client-side Fabric mod for **Minecraft 26.1.2** that tracks your progress through
Hypixel SkyBlock's **Critter Safari**, one run at a time.

The Critterdex records what you've caught *ever*. This mod answers a different
question: **what has this party caught since we walked in?** — which is what you
actually need when four people split the island one biome each.

## What it counts

Everything derives from one table of "who caught what, this run":

| | Yours | Party (incl. loot share) |
|---|---|---|
| **Global** | unique /37 · total caught | unique /37 · total caught |
| **Per biome** | unique /9 · total caught | unique /9 · total caught |

Plus a per-player breakdown of unique catches per biome, so you can see at a glance
who is covering which biome and where the gaps are.

Species counts per biome: Forest 9, Cavern 9, Icy 9, Haunted 10 — **37** total.

A run starts when you enter the Critter Safari and ends when you leave. The finished
run stays viewable until the next one begins.

## On screen

**Top left** — run timer, party and personal dex bars, a progress bar per biome, and
a per-player coverage line. Columns are laid out from measured pixel widths, so they
line up despite Minecraft's proportional font.

**Top right** — what is still uncaught **in the biome you are standing in**: the
working list for whoever is assigned that biome. Names are coloured by rarity, and a
species you have already thrown capsules at is marked `n tried`, so "hasn't been
found yet" is distinguishable from "keeps escaping". Species caught by a partymate
count as done, since the goal is party-wide coverage.

**Wumpa alert** — a scaled banner, a sound and a chat line when the Icy Biome
encounter opens up. Hypixel announces it in three stages and the mod fires on each,
rising in pitch:

| Message | Alert |
|---|---|
| `A rumbling sound can be heard, and the door … opens...` | **WUMPA READY** — chamber open |
| `You hear the sound of massive footsteps …` | **WUMPA INCOMING** — wakes in ~30s |
| `The Wumpa has awoken.` | **WUMPA AWAKE** — fight live |

## Usage

| Command | Effect |
|---|---|
| `/critters` | This run: party and personal dex, overall and per biome |
| `/critters missing` | Species nobody has caught yet, grouped by biome |
| `/critters players` | Unique catches per player per biome |
| `/critters reset` | Clear the current run's tallies |
| `/critters hud` | Toggle the main overlay |
| `/critters panel` | Toggle the top-right missing list |
| `/critters wumpa` | Toggle the Wumpa alert |
| `/critters testalert` | Fire the Wumpa alert now, to check it anywhere |
| `/critters history` | Replay this instance's logs and list past runs |

The HUD appears automatically while you're in the Safari. Settings live in
`config/crittermod.json` (`hudEnabled`, `showPerPlayer`, `showMissing`, `wumpaAlert`,
`onlyInSafari`, `hudX`, `hudY`).

## How it works

The mod reads the catch messages Hypixel already sends to your client. Nothing is
sent anywhere, and no packets are injected.

```
CAPTURE! You caught a|an <C> and gained a|an|Nx <C> Shard!
CAPTURE! You found Hideyho, and as a reward he gave you a|Nx Hideyho Shard!
LOOT SHARE! You received a|an|Nx <C> Shard from <player> catching a|an <C>!
LOOT SHARE! You received a|Nx <C> Shard from <player> finding Hideyho!
LOOT SHARE! You received a Rainbow Feather and Nx <C> Shard from <player> catching a SPARKLING <C>!
```

Because `LOOT SHARE!` names the catcher, party-wide and per-player progress come for
free without any party API.

Rather than pinning one regex per wording, the parser keys off the `CAPTURE!` /
`LOOT SHARE!` prefix and resolves the species by roster lookup. Unseen wordings —
notably the self-catch form of a SPARKLING, which has never appeared in the sample
logs — still parse correctly.

The current biome comes from the SkyBlock sidebar (`⏣ Forest Biome`), which is also
how a run's start and end are detected.

### Log replay

`ChatParser`, the species registry and `SafariSession` have no Minecraft imports, so
past runs can be reconstructed from rotated chat logs without launching the game:

```bash
./gradlew replayLogs -Plogs=/path/to/instance/logs
```

This doubles as the parser's test harness. Against 3,325 real catch events it
reconstructed 43 runs and resolved all 37 species, matching raw log counts exactly
(1240 own catches, 2085 loot-shared).

Chat-compacting mods (chatpatches, enhanced_chat) append duplicate counters like
`(3)`, `(×3)` or `[x3]`; `ChatParser.clean` strips them. Live tracking hooks
`ClientReceiveMessageEvents.GAME`, which fires upstream of those mods anyway.

## Building

Requires JDK 25. `gradle.properties` pins `org.gradle.java.home` for a machine whose
default JDK is older — adjust or remove that line as needed.

```bash
./gradlew build
```

The jar is written to `build/libs/` and, if the directory exists, copied into the
AtLauncher instance at `deployToInstance`'s target. Override with
`-PdeployDir=/path/to/mods`.

## License

MIT
