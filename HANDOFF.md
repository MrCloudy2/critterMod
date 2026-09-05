# Handoff

What this mod is, how it is put together, and the things that have already bitten
someone. Read the "Rules that were learned the hard way" section before changing
anything — most of it is not guessable from the code.

## What it is

A **client-side** Fabric mod for Hypixel SkyBlock's Critter Safari. It reads chat the
client already receives and entities the client already has, and shows what this party
has caught **since walking in** — which the in-game Critterdex cannot, because it counts
what you have caught ever.

Nothing about the player is sent anywhere. Two things leave the client: chat messages the
player opted into (`/pc`, `/ac`), queued through `ChatQueue`, and a GET of Hypixel's public
bazaar endpoint for shard prices — no key, no account, no player data, behind the **Profit
→ Track shard value** setting, and only while there is somewhere to show the number.

## Branches

| Branch | Minecraft | Status |
|---|---|---|
| `26.1.2` | 26.1.2 | **Primary**, and the repo default. Releases come from here. |
| `26.2` | 26.2 | Ported, compiles, **never run in game**. |

Fix on `26.1.2` first, then cherry-pick. The only file that genuinely diverges is
`WaypointRenderer` — 26.2 removed `MultiBufferSource` in favour of a submit-node
collector, so its rendering is written twice. Everything else cherry-picks cleanly.

## Toolchain

- **Minecraft 26.x ships deobfuscated** — there is no `mappings` dependency and code
  uses real Mojang names directly.
- Java 25. `org.gradle.java.home` is pinned in `gradle.properties` because the shell
  default JDK is older.
- **MoulConfig** (the settings GUI SkyHanni uses) is nested into the output jar with
  Fabric's `include`, so nothing extra needs installing. Its artifact declares itself as
  `libninepatch` in the mod list — that is MoulConfig's own packaging, not a bug here.
- `crittermod.accesswidener` uses namespace **`official`**, not `named`.

```bash
./gradlew build        # also copies the jar into the AtLauncher instance (deployToInstance)
./gradlew replayLogs   # offline: replays real chat logs through the parser, no game needed
```

`replayLogs` is the closest thing to a test suite. `parse/`, `data/` and `session/`
(except `SessionManager`) have no Minecraft imports so they run headless.

## How it fits together

**One tick pipeline**, in `CritterMod.onInitializeClient`, and the order matters:

```
SafariLocation.tick()    where the player is — everything below asks it
CritterEntities.tick()   one sweep of labelled critters — four features read it
SessionManager.tick()    opens and closes runs
… the watchers and trackers …
ConfigManager.tick()     saves the settings file when the settings screen closes
```

**One chat pipeline**, same method:

```
ChatParser.clean(line)         strips § codes and chat-compactor counters
ChatParser.playerSaid(line)    a player typed it → only TraderWatch sees it
… otherwise every watcher sees it …
```

### The load-bearing classes

| Class | Job |
|---|---|
| `SafariLocation` | **Single source of truth for where you are.** Answers `ELSEWHERE`/`ENTRANCE`/`INSIDE`, plus the biome. Everything else asks it; nothing else works it out. |
| `CritterEntities` | One sweep per 5 ticks producing `Sighting(critter, label, mob, sparkling, visible)`. The spawn counter, Bloodbat outlines, recatch pin and sparkling call all read it. `visible` is computed once per sweep so every reader agrees within it. |
| `Visibility` | Raycast from the player's eyes to a mob or a block position. The gate between "the client knows" and "the player could know". |
| `SessionManager` | Owns the live run. Opens on **arrival**, closes on island change. |
| `SafariSession` | The tally. Pure — no Minecraft imports, so it replays offline. |
| `RunHistory` | Finished runs, written to `config/crittermod-runs.json` when the next one starts. |
| `Markers` | The one place that decides what gets a box in the world, and in which biome. |
| `WaypointRenderer` | Draws those boxes. **Diverges between branches.** |
| `EncounterAlerts` | Banners, chat lines, and `post()` — the shared "send to party/all" helper. |
| `ChatParser` | Every chat pattern, and the player-said gate. |
| `TraderWatch` | Hunter offers: found, shared to party, and dropped once taken. `You have been given a X!` is the only NPC-independent completion line — each Hunter thanks you differently and Harry sometimes not at all. |
| `HideonwallSpots` | Eight preset perches, resolved by sight or by a capsule through them. Green when one is on it. |
| `BallDiagnostic` | `/critters entities` — 10s window reporting what appeared, no positions. How the capsule was identified. |
| `BazaarPrices` | The only network call. Polls the bazaar off-thread, prices a run's shards. |

## Rules that were learned the hard way

**Match, do not contain.** Three separate bugs came from asking whether text *contains*
a phrase: `"Entry To Critter Safari"` contains `"Safari"` so the entrance counted as the
Safari; a partymate pasting their hotspot line set the reader's hotspot; a capsule
carrying a critter's name counted as that critter. Anchor patterns with `^…$`, or match
exactly.

**Anything a player can type, a player can quote.** Watchers that scan for a phrase must
sit behind `ChatParser.playerSaid`. The one exception is the shared-trade reader, which
is deliberately looking for this mod's own announcement in party chat.

**Detected things are found by sight, then remembered.** Mounds and beehives both work
this way: nothing is taken up without a clear line to it, but once you have looked, the
mark persists through terrain and is only dropped when you *see* it has gone. Seeing
where something is and then keeping it is not the same as being told what is behind a
wall — and requiring it stay in view to stay marked would take back information you had
legitimately. Removal needs range as well as sight: outside the sweep's reach a mound is
absent from it whether or not it is still standing.

**Preset is free, detected is not.** A fixed position — a wall, a Hideonwall perch — can
be marked through terrain, because it is map knowledge anyone could write down. Anything
*read out of the world* is different: `entitiesForRendering()` hands back everything the
server tracks regardless of walls, and blocks load far past sight, so reporting either
one raw tells the player what is behind a wall. Those go through `Visibility` first,
which asks two different questions — `canSee` (on screen *and* unobstructed) for
anything that announces, `hasLineTo` (unobstructed, any facing) for anything merely
counted, since a number that emptied when you turned round would read as a bug. This is
SkyHanni's split: `EntityUtils.canBeSeen` does frustum-then-ray with an `ignoreFrustum`
escape hatch, and the frustum comes off the game's own camera rather than a hand-rolled
dot product. This is why the sparkling call, the spawn counts and the walls' broken state are
all sight-gated while their positions are not, and why floor drops highlight but never
waypoint. Do not add a feature that reports detected state without deciding which side
of this line it is on.

**Hypixel builds everything out of zero-size props, and the item name is the only
label.** A thrown capsule is an `item_display` showing an item called "Critter Capsule" —
not a `Projectile`, which is why an earlier version of the perch code never fired once.
A critter's body is often an armour stand; its name tag is a separate zero-size stand.
`getDeltaMovement()` reads **flat zero** for all of it, because Hypixel moves props by
teleport packet, so any speed test has to use position deltas between ticks. Capsules
travel 2.0–2.6 blocks a tick, measured, so hit tests against their path must be swept.

**A capture spawns a decoy wearing the critter's name.** Mid-throw, an armour stand named
for the species appears at the capture site next to one named `CAPTURING`. It reads as the
critter being back in the world somewhere else, which retired the recatch pin before the
throw resolved. `RecatchSpots` therefore holds everything between the throw and its
outcome. Both stands were confirmed from a real capture with `/critters entities`.

**Paintings block sight but not rays.** Hideonwalls sit behind paintings, and a painting is
an entity, so `level.clip` goes straight through one. `Visibility` tests painting boxes
separately; without that it reports a clear view of things nothing can be seen of.

**The entry banner names whoever warped the party in, not you.** It is not a reliable
"I entered" signal — a non-leader never sees their own name. Runs open on *arrival* at
the Safari instead.

**Config fields need `@Expose`.** MoulConfig serialises with Gson's
`excludeFieldsWithoutExposeAnnotation`; a field without it is silently never saved and
never loaded. It also **never saves by itself** — `ConfigManager.tick()` writes the file
when the settings screen closes. Values are keyed by field name, so **renaming a field
resets that setting** for everyone.

**Line vertices need a width.** `RenderTypes.LINES` uses
`POSITION_COLOR_NORMAL_LINE_WIDTH`; omitting `.setLineWidth()` is a hard crash, not a
default.

**Fail open on "cannot tell".** Where a gate depends on knowing the biome or the
location, let the thing fire when it cannot be determined. Silence looks identical to
the mod being broken.

**A catch is not a shard, and a shard is not a coin.** Every catch gives a random number
of shards, and each species' shard trades for a wildly different amount, so nothing about
money can be derived from the catch counts. `SafariSession` keeps shards per species and
`RunRecord` saves that map; runs saved before it existed carry only a shard total, and
`hasShardData()` is how the views leave them out of the money instead of counting them as
free zeroes. `/critters import` backfills the breakdown onto those runs, which is the only
way an old run ever gets priced — and it cannot help a run whose log has rotated away.

**A run is saved under two different start times.** A live run opens when you arrive; a
replayed one opens at its first chat line, seconds later or earlier. `RunHistory.sameRun`
therefore matches on overlapping windows, not on an equal `started` — the exact match it
used to do meant importing after playing added a second copy of every live-tracked run.
The overlap has to cover half the shorter run, because runs come back to back: one starts
seconds after the last one ended, and anything looser welds two of them together. Checked
against 57 real saved runs and 53 replayed ones.

**Bazaar prices come off the top of the book, not `quick_status`.** Hypixel's quick status
is a volume-weighted average over the whole order book, so a single junk order at one coin
drags a shard's reported price to nothing. `BazaarPrices` reads the best entry of
`sell_summary` (instant sell) and `buy_summary` (sell offer), which is what SkyHanni does
and why. The names are the reverse of what they read like.

**Verify data before trusting it.** The Hideonwall perches, the Snooper walls and the
biome map were all checked against `SafariAreaMap` or real logs before being shipped.
`tools/generate_safari_areas.py` regenerates the biome map from SkyHanni's island graph.

## Adding things

**A new waypoint.** Add the positions or a spotter, then one branch in `Markers.collect`
gated on `display.<yourToggle>` and the biome; add the toggle to `CritterConfig` and a
colour to the `COLOURS` accordion. The renderer needs no changes. **Default off** —
every in-world mark is.

**A new chat-driven alert.** Add an anchored pattern to your watcher, call it from the
chat loop in `CritterMod`, and reset it in `SessionManager.startSession`. For the
announcement use `EncounterAlerts.post(config.party.<yours>(), text)` so it gets the
same `Nobody / Party chat / All chat` choice as everything else. Add a line to
`/critters testalert` so it can be checked without waiting for the real event.

**A new setting.** Public field, `@ConfigOption`, an editor annotation, and `@Expose`.
Dropdowns are an `int` plus a small accessor returning an enum — see `Broadcast`,
`MarkStyle` and `Scope`.

**A new HUD panel.** Build a `HudPanel`, implement `HudElement`, register it in
`CritterMod`, and gate it on `config.display.showsHere()` so it obeys the **Show where**
setting.

## Releasing

1. Bump `mod_version` in `gradle.properties` (`0.7.3+mc26.1.2`).
2. `./gradlew build` — jar lands in `build/libs/` and in the test instance.
3. Commit, push, then `gh release create vX.Y.Z <jar> --target 26.1.2 --prerelease`.
   Everything ships as pre-release / beta while the mod is still being shaped.
4. Modrinth: `MODRINTH_TOKEN=… ./gradlew modrinth modrinthSyncBody` — uploads the jar
   and syncs the README as the project description. **Never commit a token**; the
   project is `critter-mod` and versions publish to the beta channel.

## Open threads

- **Sparkling detection** is read off the name tag, which is a guess. The particles
  around one would be firmer evidence; the likely candidate is `minecraft:wax_on`, the
  only vanilla particle that paints the happy-villager cross gold. A packet-tapping
  mixin to confirm it was written and then removed — see the commit history.
- **The capsule fix is half done.** Sightings with no mob under the name tag are
  ignored, which should exclude a capsule mid-capture. The sturdier half — pinning where
  the critter was *before* the throw — needed a `/critters entities` dump taken while a
  capsule is up, and that command is gone, so it wants another way of getting the data.
- **Bee spots are done but unplayed.** `BeeSpots` replaced the old `NestTracker`: eleven
  preset Forest positions, all marked from the start, each resolved only by looking at
  it — `BEE_NEST` recolours the mark, `BEEHIVE` or anything else clears it. **The nest is
  the one you want and the hive is the decoy**, which is the opposite of what the first
  version assumed, so do not "fix" it back. Note vanilla's inconsistent spelling:
  `Blocks.BEEHIVE` is one word, `Blocks.BEE_NEST` is two. All eleven were checked
  against `SafariAreaMap` and all resolve to Forest. What is untested is whether eleven
  is the complete set, and whether a spot ever holds neither block. A nest also clears
  on `A Honeybuzz appeared, angry at you for stealing its honey!` following a punch on
  that spot within 3s — the punch alone is not the signal, because a spent nest can be
  hit forever and gives nothing.
- **Mounds are sight-found and latched** as of the same pass, so `MoundSpotter.mounds()`
  is what you have looked at, not what is loaded. `describeAll()` still sweeps live and
  reports sizes only — no positions — so the two counts differ on purpose.
- **26.2 is untested.** The waypoints and the full-screen alert are the likeliest to be
  wrong, since both sit on the rendering that changed shape.
