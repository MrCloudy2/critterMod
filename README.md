# Critter Safari Tracker

A client-side Fabric mod for **Minecraft 26.1.2** that tracks your progress through
Hypixel SkyBlock's **Critter Safari**, one run at a time.

The Critterdex records what you've caught *ever*. This mod answers a different
question: **what has this party caught since we walked in?** — which is what you
actually need when four people split the island one biome each.

## Commands

| Command | Effect |
|---|---|
| `/critters`, `/ct` | Open the run screen |
| `/crittermod`, `/cm` | Open the settings |
| `/cm gui` | Drag-to-place HUD editor |
| `/critters missing` | What nobody has caught yet, per biome |
| `/critters copy` | Copy that list to the clipboard |
| `/critters share` | Post it to party chat |
| `/critters players` | Unique catches per player per biome |
| `/critters text` | The run summary as chat text |
| `/critters reset` | Clear the current run |
| `/critters history` | Replay this instance's logs, list past runs |
| `/critters testalert` | Fire every alert now, to check them |
| `/critters debug` | Dump area detection sources |

Quick toggles, all also in the settings: `/critters hud` · `panel` · `alerts` ·
`notify` · `biomedone`.

## What you get

- **Per-run counters** — your unique/total and the party's, globally (/37) and per
  biome. Party numbers come from `LOOT SHARE!`, which names the catcher, so
  per-player progress works with no party API.
- **Run screen** (`/ct`) — every species by biome, colour-coded green/aqua/grey for
  caught-by-you / by-a-partymate / by-nobody.
- **Two HUD boxes** — overall progress, and what is still missing in the biome you are
  standing in. Both movable and resizable.
- **Encounter alerts** — Gemzie, Wumpa and Doomspiral tracked ready → started → done,
  with optional party-chat announcements.
- **Completion alerts** — optional `<Biome> Done!`, `Everything except Macaw done!`
  and `Everything Done!`.
- **Hunter trades** — reports the roaming NPCs' shard-for-item offers, with the biome,
  so a partymate holding the right item can go and take it.
- **Log replay** — reconstruct past runs from rotated chat logs, in game or offline.

Requires **Fabric Loader 0.19+**, **Fabric API**, **fabric-language-kotlin** and Java 25.
MoulConfig is bundled. Mod Menu is optional.

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

## The run screen

`/critters` or `/ct` opens a full view of the current run: one column per biome
listing every species, colour-coded by who has it — **green** you caught it, **aqua**
only a partymate did, **grey** nobody has. A species caught more than once shows `xN`;
one that has only been thrown at shows `Nt`, so "escaping" reads differently from
"not found". Below that, a unique-per-player-per-biome table, and buttons to copy or
share the missing list.

## On the HUD

**Top left** — run timer, party and personal dex bars, a progress bar per biome, and
a per-player coverage line. Columns are laid out from measured pixel widths, so they
line up despite Minecraft's proportional font.

**Top right** — what is still uncaught **in the biome you are standing in**: the
working list for whoever is assigned that biome. Names are coloured by rarity, and a
species you have already thrown capsules at is marked `n tried`, so "hasn't been
found yet" is distinguishable from "keeps escaping". Species caught by a partymate
count as done, since the goal is party-wide coverage.

**Encounter alerts** — a scaled banner, a sound and a chat line as each legendary
encounter moves through ready → started → done, with an option to announce the same
stages to party chat:

| Encounter | Ready | Started | Done |
|---|---|---|---|
| **Gemzie** (Cavern) | `A rumbling sound … the door … opens...` | — | after 3 catches by anyone |
| **Wumpa** (Icy) | `You hear the sound of massive footsteps …` | `The Wumpa has awoken.` | `The cave opens up again...` |
| **Doomspiral** (Haunted) | `You used the Soothing Incense to light the candle!` | `Your ritual summoned a Doomspiral …` | `The Doomspiral retreats back underground...` |

Gemzie has no end message — exactly three spawn per chamber, so three catches by
anyone closes it. Catching a Wumpa or Doomspiral also counts as done.

The Doomspiral ritual lights four candles, usually about two seconds apart, so a 20s
per-stage cooldown keeps that from firing four banners; measured across 78 real candle
intervals it suppresses 77 of them. Gemzie is exempt, since its chamber repeats every
few minutes and ready/done can be seconds apart.

**Completion alerts** — all off by default, in the Alerts settings:

| Alert | Fires when |
|---|---|
| `<Biome> Done!` | every species in a biome has been caught by someone |
| `Everything except Macaw done!` | the only species left is the Macaw |
| `Everything Done!` | all 37 caught by someone |

The Macaw only comes to the Birdfeeder and is not guaranteed to appear, so
"everything but the Macaw" is usually the real finish line. Across 48 replayed runs
that milestone was reached 7 times while a full 37/37 happened once — six runs ended a
single Macaw short. The two are mutually exclusive: if one catch completes the dex
outright, only `Everything Done!` fires.

## Settings

The HUD appears automatically while you're in the Safari. `/cm` opens the settings,
built with **MoulConfig** — the same config GUI SkyHanni uses, by Moulberry and nea89 —
so it is the framed, searchable panel you already know. Also reachable from the Config
button in Mod Menu. Options are grouped into Display, Alerts and Party, and MoulConfig
owns saving, so edits persist as you make them. Values live in
`config/crittermod.json`.

**Edit positions** (in Display, or `/cm gui`) opens a drag-to-place editor for the
on-screen boxes: drag one to move it, scroll over it to resize. Boxes with nothing to
show appear as labelled placeholders, so positions can be set outside a run. Positions
are stored as fractions of the screen, so a box stays where you put it across
resolution and GUI-scale changes. Each box also has a scale slider in the settings, and
a Reset button restores the defaults.

### Hunter trades

Hunter NPCs roam the Safari, each offering one shard for one quest item. Their dialog
is only shown to whoever clicked them, so an offer routinely goes unused while someone
else in the party is carrying exactly the item it wants. The mod prints each complete
offer with the biome it was found in, and can post it to party chat:

```
[Critters] Hunter Harry (Icy): Nozzlenose Shard for a Yogi Berry
```

Four traders word both halves differently, and the shard and the price always arrive
as two separate lines, so an offer is paired with the next price line from the same
speaker rather than matched against eight sentence patterns:

| NPC | Offers | Asks |
|---|---|---|
| Hunter Billy | `I found this really cool <C> Shard …` | `I'll trade it to you in exchange for a <item>.` |
| Hunter Dennis | `I've got a <C> Shard you can h-h-have …` | `You can h-h-have it if you give m-m-me a <item>…` |
| Hunter Harry | `Say, do you have a use for a <C> Shard? …` | `I'll give you it in exchange for a <item>!` |
| Huntress Melissa | `Do you want this <C> Shard? …` | `How about I give you it in exchange for, say, a <item>?` |

Checked against 4,163 NPC lines in real logs: 27 trades resolved across all four
traders, for items including Yogi Berry, Bag of Seeds, Shining Coin, Lime/Orange/Purple
Gem, Wriggleworm, Icebreaker and Soothing Incense.

## Telling your team

`/critters copy` and `/critters share` produce the same report — one line per biome
that still has anything outstanding, complete biomes omitted:

```
Forest missing: Macaw
Cavern missing: Cavernfish Driftling Scrappy
Haunted missing: Bloodbat
```

`copy` puts it on the clipboard and prints a preview; `share` posts it. Lines are
queued and sent 1.2s apart, because Hypixel silently drops a burst of messages sent
in the same tick. **Post to** in the Party settings picks the channel — party chat (the
default), all chat, or normal chat.

Across 43 real runs the longest line came to 109 characters, well inside the 250-char
server limit, so the report never has to be split.

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

### Knowing which biome you're in

The sidebar does **not** name the Safari biome, so the mod resolves position instead.

Naively you might take the nearest of the four biome centres, but the biomes are not
convex — Forest and Haunted interleave around z≈0 and the cave sections fold over each
other — so that misclassifies about 3% of the map.

SkyHanni gets it right by walking its island path graph: find the graph node nearest
the player, then search along the edges for the nearest node tagged with an area name.
That search doesn't depend on the player, so `tools/generate_safari_areas.py` does it
once offline — a multi-source Dijkstra over `SAFARI.json` (1,327 nodes, 68 area tags)
that gives every node its graph-nearest area in one pass — and writes
`safari_areas.txt`, one `x y z biome` row per node.

At runtime the mod only does a nearest-node lookup, reproducing SkyHanni's answer
exactly **without** needing the graph, the edges, or SkyHanni installed. Verified
against the four points SkyHanni draws its own biome labels at: all four resolve
correctly, each within 3.3 blocks of a mapped node.

Run start and end come from chat instead: entering is announced, leaving is not, so
the exit signal is the `Sending you to server` transfer that always accompanies it.

`/critters debug` dumps every source and what each resolves to.

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

Requires JDK 25. MoulConfig is **nested into the output jar**, so nothing extra needs
installing — its artifact already ships a `fabric.mod.json`, which means Fabric's
`include` works and SkyHanni's shade-and-relocate dance is unnecessary. It is written
in Kotlin, so **fabric-language-kotlin** is a runtime dependency. **Mod Menu** is
optional and just adds the Config button. `gradle.properties` pins `org.gradle.java.home` for a machine whose
default JDK is older — adjust or remove that line as needed.

```bash
./gradlew build
```

The jar is written to `build/libs/` and, if the directory exists, copied into the
AtLauncher instance at `deployToInstance`'s target. Override with
`-PdeployDir=/path/to/mods`.

## Publishing to Modrinth

`./gradlew modrinth` uploads the built jar as a new version. The token is read from
the environment and never written into the repo:

```bash
MODRINTH_TOKEN=<your token> ./gradlew modrinth
```

Create one at https://modrinth.com/settings/pats with the **Create versions** scope.
`./gradlew modrinthSyncBody` separately pushes this README to the project description.

## License

MIT
