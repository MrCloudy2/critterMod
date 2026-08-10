# Critter Safari Tracker

A client-side Fabric mod for **Minecraft 26.1.2** that tracks a party's progress through
Hypixel SkyBlock's **Critter Safari**, one run at a time.

## Installing

1. Install **Fabric Loader 0.19 or newer** for Minecraft **26.1.2**.
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
- History tab: every saved run, with length, dex and catches
- Stats tab: totals per species across every run, time played, catches a run, full-dex runs
- Log replay: reconstruct past runs from chat logs, in game or offline

**On screen**

- Progress panel: run timer, party and personal dex bars, a bar per biome
- Missing panel: what is still uncaught in the biome you are standing in
- Hunter trade box: the offers found this run, nearest first
- Hotspot line: which biome the run made yours
- Counts of Snooper walls, Troodon walls, bee nests and Rockmite mounds left
- Nearby spawn counts, optional
- Every box movable, resizable and scaleable, with a drag-to-place editor
- Shown only in the Safari, in the Safari and its entrance, or everywhere

**Marks in the world** — each separately toggleable, all off by default

- Snooper walls, in the Cavern
- Troodon walls, in the Icy biome
- Bee nests you have not punched, in the Forest
- Rockmite mounds, in the Cavern
- The eight Hideonwall perches, in the Haunted biome
- Hunter trade spots
- Floor drops — nothing, a highlight, or a full waypoint
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
| `/critters import` | Fill the saved history from those logs, once |
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
