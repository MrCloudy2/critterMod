# Critter Safari Tracker

A client-side Fabric mod for **Minecraft 26.2** that tracks a party's progress through
Hypixel SkyBlock's **Critter Safari**, one run at a time.

## Installing

1. Install **Fabric Loader 0.19 or newer** for Minecraft **26.2**.
2. Put these in your `mods` folder:
   - this mod's jar
   - [Fabric API](https://modrinth.com/mod/fabric-api)
   - [fabric-language-kotlin](https://modrinth.com/mod/fabric-language-kotlin)
   - optionally [Mod Menu](https://modrinth.com/mod/modmenu), for a settings button
3. Launch the game and go to the Critter Safari. The panels appear on their own.
4. `/cm` opens the settings, `/ct` opens the run screen.

MoulConfig is bundled inside the jar — nothing to install for it.

## Features

**Tracking**

- Per-run counters: unique and total, yours and the party's, globally and per biome
- Per-player breakdown of who has caught what, from `LOOT SHARE!`
- Species that spawn a fixed number of times tracked to their quota, not just the first catch
- Capsule throws counted, so "keeps escaping" reads differently from "not found yet"
- Species a run can no longer produce marked as such
- Runs saved to disk when the next one starts
- History tab: every saved run, with length, dex, catches and what it was worth
- Stats tab: totals per species across every run, time played, catches a run, full-dex runs
- Shard value: what a run gave, priced per species at live bazaar prices — coins now, coins
  a run, coins an hour, and the total across every saved run
- Instant sell by default, sell offers if you would rather list and wait, or both side by
  side — they are a long way apart
- Runs saved before shard tracking existed are priced by running `/critters import` once,
  which reads their shards back out of the chat logs
- Log replay: reconstruct past runs from chat logs, in game or offline

**On screen**

- Progress panel: run timer, party and personal dex bars, a bar per biome, and what the
  run has earned so far
- Missing panel: what is still uncaught in the biome you are standing in
- Hunter trade box: the offers found this run, nearest first
- Hotspot line: which biome the run made yours
- Counts of Snooper walls, Troodon walls and Rockmite mounds left
- Counts of what is in sight around you, optional
- Every box movable, resizable and scaleable, with a drag-to-place editor
- Shown only in the Safari, in the Safari and its entrance, or everywhere

**Marks in the world** — each separately toggleable, all off by default

- Snooper walls, in the Cavern
- Troodon walls, in the Icy biome
- The eleven bee spots, in the Forest — look at one and the mark says whether it is a nest
- Rockmite mounds, in the Cavern
- The eight Hideonwall perches, in the Haunted biome
- Hunter trade spots
- Floor drops, highlighted where you can see them
- Recatch spots: where a critter was when you threw at it
- Bloodbat boxes, on by default
- A colour picker per kind, with alpha and chroma

**Alerts**

- Gemzie, Wumpa and Doomspiral tracked ready → started → done, each settable on its own
- Encounter calls kept to the biome they are in
- Biome complete, everything except Macaw, everything done
- Macaw spawns, full-screen — and the Bluebird and Parakeet too, optionally
- Sparkling critters, full-screen
- Hunter trade offers, with the biome and coordinates
- Your Hunting Hotspot
- Sound, off by default

**Party**

- Every announcement chooses its own audience: nobody, party chat or all chat
- The missing list, copied to the clipboard or posted to chat
- Trades announced by other people running this mod read straight into your tracker

**Other**

- Settings GUI built with MoulConfig, reachable from Mod Menu
- Warden darkness removed while at the Safari, optional
- Bazaar prices from Hypixel's public endpoint — no key, no account, nothing about you in
  the request, and it can be turned off

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
| `/critters prices` | Refresh the bazaar prices and price this run's shards |
| `/critters text` | The run summary as chat text |
| `/critters reset` | Clear the current run |
| `/critters history` | Replay this instance's logs, list past runs |
| `/critters import` | Fill the saved history from those logs, and backfill older runs' shards so they can be priced |
| `/critters testalert` | Fire every alert now, to check them |
| `/critters waypoints` | What is marked right now |
| `/critters debug` | Dump area detection sources |

Quick toggles: `/critters hud` · `panel` · `alerts` · `notify` · `biomedone`.

## Building

Requires JDK 25.

```bash
./gradlew build
```

## License

MIT. See [LICENSE](LICENSE).
