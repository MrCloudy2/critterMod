package dev.rok.crittermod.client;

import com.mojang.brigadier.CommandDispatcher;
import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.data.Critters;
import dev.rok.crittermod.data.SafariBiome;
import dev.rok.crittermod.importer.LogScanner;
import dev.rok.crittermod.session.MissingReport;
import dev.rok.crittermod.session.RunHistory;
import dev.rok.crittermod.session.SafariSession;
import dev.rok.crittermod.session.SessionManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeMap;
import java.util.Map;

/** {@code /critters} — reports the current run in chat. */
public final class CritterCommand {

	private CritterCommand() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		var root = ClientCommands.literal("critters")
			.executes(ctx -> {
				openScreen();
				return 1;
			})
			.then(ClientCommands.literal("text").executes(ctx -> {
				summary(ctx.getSource());
				return 1;
			}))
			.then(ClientCommands.literal("missing").executes(ctx -> {
				missing(ctx.getSource());
				return 1;
			}))
			.then(ClientCommands.literal("players").executes(ctx -> {
				players(ctx.getSource());
				return 1;
			}))
			.then(ClientCommands.literal("copy").executes(ctx -> {
				copy(ctx.getSource());
				return 1;
			}))
			.then(ClientCommands.literal("share").executes(ctx -> {
				share(ctx.getSource());
				return 1;
			}))
			.then(ClientCommands.literal("reset").executes(ctx -> {
				SessionManager.reset();
				ctx.getSource().sendFeedback(prefixed("Session reset.", ChatFormatting.YELLOW));
				return 1;
			}))
			.then(ClientCommands.literal("hud").executes(ctx -> {
				CritterConfig config = ConfigManager.get();
				config.display.hudEnabled = !config.display.hudEnabled;
				ConfigManager.save();
				ctx.getSource().sendFeedback(prefixed(
					"HUD " + (config.display.hudEnabled ? "enabled" : "disabled") + ".", ChatFormatting.YELLOW));
				return 1;
			}))
			.then(ClientCommands.literal("panel").executes(ctx -> {
				CritterConfig config = ConfigManager.get();
				config.display.showMissing = !config.display.showMissing;
				ConfigManager.save();
				ctx.getSource().sendFeedback(prefixed("Top-right missing panel "
					+ (config.display.showMissing ? "enabled" : "disabled") + ".", ChatFormatting.YELLOW));
				return 1;
			}))
			// The three encounters are settable one at a time in the GUI; these quick
			// toggles move all three together, which is what wanting silence means.
			.then(ClientCommands.literal("alerts").executes(ctx -> {
				CritterConfig config = ConfigManager.get();
				boolean on = !(config.alerts.gemzieAlert
					&& config.alerts.wumpaAlert && config.alerts.doomspiralAlert);
				config.alerts.gemzieAlert = on;
				config.alerts.wumpaAlert = on;
				config.alerts.doomspiralAlert = on;
				ConfigManager.save();
				ctx.getSource().sendFeedback(prefixed("Encounter alerts "
					+ (on ? "enabled" : "disabled") + ".", ChatFormatting.YELLOW));
				return 1;
			}))
			.then(ClientCommands.literal("notify").executes(ctx -> {
				CritterConfig config = ConfigManager.get();
				boolean silent = config.party.gemzie() == CritterConfig.Broadcast.NONE
					&& config.party.wumpa() == CritterConfig.Broadcast.NONE
					&& config.party.doomspiral() == CritterConfig.Broadcast.NONE;
				int to = silent ? CritterConfig.Broadcast.PARTY.ordinal()
					: CritterConfig.Broadcast.NONE.ordinal();
				config.party.gemzieBroadcast = to;
				config.party.wumpaBroadcast = to;
				config.party.doomspiralBroadcast = to;
				ConfigManager.save();
				ctx.getSource().sendFeedback(prefixed("Encounter announcements "
					+ (silent ? "posting to party chat" : "off") + ".", ChatFormatting.YELLOW));
				return 1;
			}))
			.then(ClientCommands.literal("biomedone").executes(ctx -> {
				CritterConfig config = ConfigManager.get();
				config.alerts.biomeDoneNotify = !config.alerts.biomeDoneNotify;
				ConfigManager.save();
				ctx.getSource().sendFeedback(prefixed("Biome-complete alerts "
					+ (config.alerts.biomeDoneNotify ? "enabled" : "disabled") + ".", ChatFormatting.YELLOW));
				return 1;
			}))
			.then(ClientCommands.literal("testalert").executes(ctx -> {
				// Walks every stage without waiting for a real encounter, so banners,
				// sounds and party messages can all be checked anywhere.
				EncounterAlerts.whileTesting(() -> {
					EncounterAlerts.onChatMessage("A rumbling sound can be heard, and the door at the back of the chamber opens...");
					EncounterAlerts.onChatMessage("You hear the sound of massive footsteps echoing through the Icy Biome... What could it be?");
					EncounterAlerts.onChatMessage("The Wumpa has awoken.");
					EncounterAlerts.onChatMessage("The cave opens up again...");
					EncounterAlerts.onChatMessage("Your ritual summoned a Doomspiral into this world. Stay still.");
					EncounterAlerts.onChatMessage("The Doomspiral retreats back underground...");
					for (int i = 0; i < 3; i++) EncounterAlerts.onCatch("Gemzie");
					EncounterAlerts.onBiomeComplete(SafariBiome.CAVERN);
					EncounterAlerts.onAllButMacaw();
					EncounterAlerts.onAllDone();
				});
				TraderWatch.onChatMessage("[NPC] Hunter Harry: Say, do you have a use for a Nozzlenose Shard? I found it lying around...");
				TraderWatch.onChatMessage("[NPC] Hunter Harry: I'll give you it in exchange for a Yogi Berry!");
				BirdfeederWatch.onChatMessage("A Bluebird was attracted to the Birdfeeder!");
				BirdfeederWatch.onChatMessage("Two Macaws were attracted to the Birdfeeder!");
				HotspotWatch.onChatMessage("HOTSPOT! Your Hunting Hotspot is the Icy Biome!");
				FullScreenAlert.show("SPARKLING!", "Rockmite", "Cavern -96 40 42",
					FullScreenAlert.SPARKLING);
				// As a partymate running this mod would announce it, so the reading side
				// can be checked without a second player.
				TraderWatch.onChatMessage(
					"Party > [VIP] Partymate: Hunter Dennis (Icy -106 87 -7): Gimmiegold Shard for Purple Gem");
				return 1;
			}))
			.then(ClientCommands.literal("prices").executes(ctx -> {
				prices(ctx.getSource());
				return 1;
			}))
			.then(ClientCommands.literal("history").executes(ctx -> {
				history(ctx.getSource());
				return 1;
			}))
			.then(ClientCommands.literal("import").executes(ctx -> {
				importRuns(ctx.getSource());
				return 1;
			}))
			.then(ClientCommands.literal("waypoints").executes(ctx -> {
				waypoints(ctx.getSource());
				return 1;
			}));

		// Diagnostics exist to work out how the Safari represents things and are noise
		// in a normal run, so they are only registered when asked for.
		// The old entities/nearby pair listed every loaded entity with its coordinates and
		// were the most direct advantage in the mod. What is here instead reports the mod's
		// own state, what the crosshair is already on, and — for identifying a thrown
		// capsule — what appears during a short window, with no positions attached.
		if (ConfigManager.get().advanced.debugCommands) {
			root.then(ClientCommands.literal("debug").executes(ctx -> {
					debug(ctx.getSource());
					return 1;
				}))
				.then(ClientCommands.literal("block").executes(ctx -> {
					block(ctx.getSource());
					return 1;
				}))
				.then(ClientCommands.literal("entities").executes(ctx -> {
					BallDiagnostic.start();
					return 1;
				}));
		}
		dispatcher.register(root);

		// Short alias, since this gets opened constantly mid-run.
		dispatcher.register(ClientCommands.literal("ct").executes(ctx -> {
			openScreen();
			return 1;
		}));

		// Settings live on their own command so the run view stays a single keystroke.
		dispatcher.register(ClientCommands.literal("crittermod")
			.executes(ctx -> {
				openSettings();
				return 1;
			})
			.then(ClientCommands.literal("gui").executes(ctx -> {
				HudEditorScreen.open();
				return 1;
			})));
		dispatcher.register(ClientCommands.literal("cm")
			.executes(ctx -> {
				openSettings();
				return 1;
			})
			.then(ClientCommands.literal("gui").executes(ctx -> {
				HudEditorScreen.open();
				return 1;
			})));
	}

	/**
	 * Reports what the waypoint marker is doing.
	 *
	 * <p>Separates "nothing was registered" from "registered but nothing is drawn": the
	 * second means the client has the markers and the locator bar is not showing them,
	 * which is not something this mod can fix from here.
	 */
	private static void waypoints(FabricClientCommandSource source) {
		CritterConfig config = ConfigManager.get();
		source.sendFeedback(header("Highlights"));
		source.sendFeedback(Component.literal("  on: %s%s%s%s%s%s%s%s".formatted(
			config.display.highlightSnooperWalls ? "snooper " : "",
			config.display.highlightTroodonWalls ? "troodon " : "",
			config.display.highlightBeeNests ? "beenests " : "",
			config.display.highlightTrades ? "trades " : "",
			config.display.highlightHideonwalls ? "perches " : "",
			config.display.recatchHelper ? "recatch " : "",
			config.display.floorDropStyle() == CritterConfig.MarkStyle.OFF ? ""
				: "drops:" + config.display.floorDropStyle().name().toLowerCase() + " ",
			config.display.highlightMounds ? "mounds" : ""))
			.withStyle(ChatFormatting.GREEN));
		// Everything but the trades is gated on the biome, so the biome is half the
		// answer to "why is nothing showing".
		source.sendFeedback(Component.literal("  in Safari   " + SafariLocation.inSafari()
			+ "  biome " + nameOf(SafariLocation.biome())).withStyle(ChatFormatting.GRAY));
		source.sendFeedback(Component.literal("  highlighted " + Markers.collect().size())
			.withStyle(ChatFormatting.WHITE));

		List<String> sizes = MoundSpotter.describeAll();
		// The sweep below is live and mound-sized; the count is what has been looked at
		// and logged. They differ on purpose, and the gap is the sight gate doing its job.
		source.sendFeedback(Component.literal("  interaction hitboxes nearby (logged mounds: "
			+ MoundSpotter.mounds().size() + ")").withStyle(ChatFormatting.YELLOW));
		sizes.stream().limit(12).forEach(line ->
			source.sendFeedback(Component.literal(line).withStyle(
				line.contains("<-") ? ChatFormatting.GREEN : ChatFormatting.WHITE)));

		source.sendFeedback(Component.literal(
			"  candidates  snooper %d · troodon %d · trades %d".formatted(
				WallTracker.SNOOPER.intactCount(), WallTracker.TROODON.intactCount(),
				TraderWatch.found().size())).withStyle(ChatFormatting.DARK_GRAY));
		// The projectile count is the thing to read here: perches are crossed off by a
		// capsule flying through them, and if Hypixel's capsule is not a Projectile at
		// all this stays at zero however many you throw. That separates "not hit yet"
		// from "cannot see the capsule".
		source.sendFeedback(Component.literal("  perches     " + HideonwallSpots.describe())
			.withStyle(ChatFormatting.DARK_GRAY));
		source.sendFeedback(Component.literal("  bee spots   " + BeeSpots.describe())
			.withStyle(ChatFormatting.DARK_GRAY));
		// Reported whether or not the solver is on, since "is it even loaded while
		// hidden" is the thing worth checking.
		// The farthest label loaded says what Hypixel's entity tracking range is, which
		// is the only thing deciding how close you have to get. Nothing here filters by
		// distance, so whatever this reports is the server's limit, not ours.
		double farthest = 0;
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			farthest = Math.max(farthest,
				source.getPlayer().position().distanceTo(sighting.body().position()));
		}
		source.sendFeedback(Component.literal("  critters    %d loaded, farthest %.0fm"
			.formatted(CritterEntities.all().size(), farthest)).withStyle(ChatFormatting.DARK_GRAY));
		source.sendFeedback(Component.literal("  floor drops " + FloorDrops.positions().size())
			.withStyle(ChatFormatting.DARK_GRAY));
		source.sendFeedback(Component.literal("  pinned      " + (RecatchSpots.pinned() == null
			? "nothing" : "%s at %.0fm".formatted(RecatchSpots.pinnedCritter().name(),
				RecatchSpots.distance()))).withStyle(ChatFormatting.DARK_GRAY));
	}

	private static void openSettings() {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.setScreen(ConfigManager.createScreen(null)));
	}

	/**
	 * Opens the run screen on the next tick. Setting it inline would be undone when
	 * the chat screen closes immediately after the command runs.
	 */
	private static void openScreen() {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.setScreen(new CritterScreen()));
	}

	private static void summary(FabricClientCommandSource source) {
		SafariSession session = SessionManager.currentOrLast();
		if (session == null) {
			source.sendFeedback(prefixed("No Critter Safari run tracked yet.", ChatFormatting.GRAY));
			return;
		}

		boolean live = SessionManager.current() != null;
		int total = Critters.total();

		source.sendFeedback(header(live ? "Critter Safari (live)" : "Critter Safari (last run)"));
		source.sendFeedback(Component.literal("  Party ")
			.withStyle(ChatFormatting.GRAY)
			.append(Component.literal("%d/%d unique".formatted(session.partyUnique(), total))
				.withStyle(session.dexComplete() ? ChatFormatting.GREEN : ChatFormatting.WHITE))
			.append(Component.literal("  ·  %d caught".formatted(session.partyTotal()))
				.withStyle(ChatFormatting.DARK_GRAY)));
		source.sendFeedback(Component.literal("  You   ")
			.withStyle(ChatFormatting.GRAY)
			.append(Component.literal("%d/%d unique".formatted(session.ownUnique(), total))
				.withStyle(ChatFormatting.WHITE))
			.append(Component.literal("  ·  %d caught".formatted(session.ownTotal()))
				.withStyle(ChatFormatting.DARK_GRAY)));

		for (SafariBiome biome : SafariBiome.values()) {
			int max = Critters.totalIn(biome);
			boolean complete = session.biomeComplete(biome);
			source.sendFeedback(Component.literal("  %-8s ".formatted(biome.displayName()))
				.withStyle(style(biome))
				.append(Component.literal("party %d/%d".formatted(session.partyUnique(biome), max))
					.withStyle(complete ? ChatFormatting.GREEN : ChatFormatting.WHITE))
				.append(Component.literal("  you %d/%d".formatted(session.ownUnique(biome), max))
					.withStyle(ChatFormatting.DARK_GRAY)));
		}

		if (ConfigManager.get().profit.enabled && BazaarPrices.known()) {
			source.sendFeedback(Component.literal("  Value ")
				.withStyle(ChatFormatting.GRAY)
				.append(Component.literal(BazaarPrices.coinsText(session))
					.withStyle(ChatFormatting.GOLD))
				.append(Component.literal("  ·  %d shards".formatted(session.totalShards()))
					.withStyle(ChatFormatting.DARK_GRAY)));
		}

		if (!session.sparklings().isEmpty()) {
			source.sendFeedback(Component.literal("  SPARKLING: " + session.sparklings().stream()
				.map(Critter::name).reduce((a, b) -> a + ", " + b).orElse(""))
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		source.sendFeedback(Component.literal("  /ct run screen · /cm settings · text · missing · copy · share · players · prices · reset · history")
			.withStyle(ChatFormatting.DARK_GRAY));
	}

	/**
	 * Forces a bazaar refresh and reports what the shards this run gave are worth.
	 *
	 * <p>The prices are fetched off-thread, so the report is of whatever is held right
	 * now — run it twice if it says they have never loaded.
	 */
	private static void prices(FabricClientCommandSource source) {
		CritterConfig config = ConfigManager.get();
		if (!config.profit.enabled) {
			source.sendFeedback(prefixed("Shard value is off — turn it on in /cm under Profit.",
				ChatFormatting.GRAY));
			return;
		}

		BazaarPrices.refreshNow();
		long age = BazaarPrices.ageMillis();
		source.sendFeedback(header("Shard prices"));
		source.sendFeedback(Component.literal("  %s, tax %s  ·  %s".formatted(
			config.profit.priceSource() == CritterConfig.PriceSource.SELL_OFFER
				? "sell offer" : "instant sell",
			config.profit.subtractTax ? "taken off" : "left in",
			age < 0 ? "never loaded" : "fetched %ds ago".formatted(age / 1000)))
			.withStyle(ChatFormatting.GRAY));
		if (BazaarPrices.lastError() != null) {
			source.sendFeedback(Component.literal("  last error: " + BazaarPrices.lastError())
				.withStyle(ChatFormatting.RED));
		}
		source.sendFeedback(Component.literal("  refreshing…").withStyle(ChatFormatting.DARK_GRAY));

		SafariSession session = SessionManager.currentOrLast();
		if (session == null || !BazaarPrices.known()) return;

		// Both sides here whatever the settings say: this is the command for asking what
		// the shards are worth, and the gap between selling now and listing an offer is
		// most of the answer.
		source.sendFeedback(Component.literal("  this run: %d shards".formatted(session.totalShards()))
			.withStyle(ChatFormatting.GRAY));
		for (CritterConfig.PriceSource side : CritterConfig.PriceSource.values()) {
			boolean chosen = side == config.profit.priceSource();
			source.sendFeedback(Component.literal("    %-13s %s%s".formatted(
				BazaarPrices.name(side).replace("as ", ""),
				BazaarPrices.format(BazaarPrices.valueOf(session, side)),
				chosen ? "  <-" : ""))
				.withStyle(chosen ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
		}
		// Sorted by what they were actually worth, since that is the question being asked.
		session.shardCounts().entrySet().stream()
			.sorted((a, b) -> Double.compare(
				BazaarPrices.price(b.getKey()) * b.getValue(),
				BazaarPrices.price(a.getKey()) * a.getValue()))
			.limit(8)
			.forEach(entry -> source.sendFeedback(Component.literal("    %-14s %2dx %s = %s".formatted(
				entry.getKey().name(), entry.getValue(),
				BazaarPrices.format(Math.round(BazaarPrices.price(entry.getKey()))),
				BazaarPrices.format(Math.round(BazaarPrices.price(entry.getKey()) * entry.getValue()))))
				.withStyle(ChatFormatting.WHITE)));
	}

	private static void missing(FabricClientCommandSource source) {
		SafariSession session = SessionManager.currentOrLast();
		if (session == null) {
			source.sendFeedback(prefixed("No Critter Safari run tracked yet.", ChatFormatting.GRAY));
			return;
		}

		source.sendFeedback(header("Still uncaught this run"));
		for (SafariBiome biome : SafariBiome.values()) {
			List<Critter> missing = session.missing(biome);
			Component names = missing.isEmpty()
				? Component.literal("complete").withStyle(ChatFormatting.GREEN)
				: Component.literal(missing.stream().map(Critter::name)
					.reduce((a, b) -> a + ", " + b).orElse("")).withStyle(ChatFormatting.WHITE);
			source.sendFeedback(Component.literal("  %-8s ".formatted(biome.displayName()))
				.withStyle(style(biome)).append(names));
		}
	}

	private static void players(FabricClientCommandSource source) {
		SafariSession session = SessionManager.currentOrLast();
		if (session == null) {
			source.sendFeedback(prefixed("No Critter Safari run tracked yet.", ChatFormatting.GRAY));
			return;
		}

		Map<String, Map<SafariBiome, Integer>> perPlayer = session.uniquePerPlayer();
		source.sendFeedback(header("Unique catches per player"));
		if (perPlayer.isEmpty()) {
			source.sendFeedback(Component.literal("  nobody has caught anything yet")
				.withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		perPlayer.forEach((player, counts) -> {
			StringBuilder line = new StringBuilder();
			for (SafariBiome biome : SafariBiome.values()) {
				line.append("%s %d  ".formatted(biome.displayName().charAt(0), counts.getOrDefault(biome, 0)));
			}
			source.sendFeedback(Component.literal("  %-17s ".formatted(player))
				.withStyle(ChatFormatting.AQUA)
				.append(Component.literal(line.toString()).withStyle(ChatFormatting.GRAY)));
		});
		source.sendFeedback(Component.literal("  F=Forest C=Cavern I=Icy H=Haunted")
			.withStyle(ChatFormatting.DARK_GRAY));
	}

	/** Puts the missing-species report on the clipboard, and previews it in chat. */
	private static void copy(FabricClientCommandSource source) {
		SafariSession session = SessionManager.currentOrLast();
		if (session == null) {
			source.sendFeedback(prefixed("No Critter Safari run tracked yet.", ChatFormatting.GRAY));
			return;
		}

		String report = MissingReport.text(session);
		source.getClient().keyboardHandler.setClipboard(report);

		source.sendFeedback(header("Copied to clipboard"));
		for (String line : report.split("\n")) {
			source.sendFeedback(Component.literal("  " + line).withStyle(ChatFormatting.WHITE));
		}
	}

	/** Posts the same report to party chat, one line at a time. */
	private static void share(FabricClientCommandSource source) {
		SafariSession session = SessionManager.currentOrLast();
		if (session == null) {
			source.sendFeedback(prefixed("No Critter Safari run tracked yet.", ChatFormatting.GRAY));
			return;
		}

		List<String> lines = MissingReport.lines(session);
		if (lines.isEmpty()) lines = List.of(MissingReport.text(session));

		String channel = ConfigManager.get().party.shareCommand();
		boolean asCommand = channel != null && !channel.isBlank();
		for (String line : lines) {
			ChatQueue.enqueue(asCommand ? channel.trim() + " " + line : line, asCommand);
		}

		source.sendFeedback(prefixed("Posting %d line(s) to %s…".formatted(
			lines.size(), asCommand ? "/" + channel.trim() : "chat"), ChatFormatting.YELLOW));
	}

	/**
	 * Dumps every area source so the biome detection can be pinned to real client
	 * data instead of assumptions. Run it while standing in a biome.
	 */
	private static void debug(FabricClientCommandSource source) {
		source.sendFeedback(header("Area sources"));

		// Shown with the § codes intact, as & — the area line is recognised by its
		// formatting, so a stripped dump would hide exactly what decides the answer.
		List<String> sidebar = SafariLocation.sidebarLines();
		source.sendFeedback(Component.literal("  sidebar (" + sidebar.size() + " lines):")
			.withStyle(ChatFormatting.YELLOW));
		for (String line : sidebar) {
			source.sendFeedback(Component.literal("    | " + line.replace('§', '&'))
				.withStyle(ChatFormatting.GRAY));
		}

		// Most tab entries are player names; only metadata rows matter here.
		List<String> interesting = SafariLocation.tabListEntries().stream()
			.filter(e -> e.contains("Area") || e.contains("Biome") || e.contains("⏣")
				|| e.contains("Safari") || e.contains("Zone"))
			.toList();
		source.sendFeedback(Component.literal("  tab list matches (" + interesting.size() + "):")
			.withStyle(ChatFormatting.YELLOW));
		for (String entry : interesting) {
			source.sendFeedback(Component.literal("    | " + entry).withStyle(ChatFormatting.GRAY));
		}

		Vec3 pos = source.getPlayer().position();
		source.sendFeedback(Component.literal("  position: %.1f %.1f %.1f"
			.formatted(pos.x, pos.y, pos.z)).withStyle(ChatFormatting.YELLOW));
		source.sendFeedback(Component.literal("    nearest mapped node: %.1f blocks (of %d)"
			.formatted(SafariLocation.distanceToNearestNode(), SafariAreaMap.nodeCount()))
			.withStyle(ChatFormatting.GRAY));

		source.sendFeedback(Component.literal("  resolved:").withStyle(ChatFormatting.YELLOW));
		source.sendFeedback(Component.literal("    area          "
			+ (SafariLocation.area() == null ? "not stated" : SafariLocation.area()))
			.withStyle(ChatFormatting.WHITE));
		source.sendFeedback(Component.literal("    decided by    " + SafariLocation.source())
			.withStyle(ChatFormatting.GRAY));
		source.sendFeedback(Component.literal("    from position " + nameOf(SafariLocation.biomeFromPosition()))
			.withStyle(ChatFormatting.GRAY));
		source.sendFeedback(Component.literal("    biome         " + nameOf(SafariLocation.biome()))
			.withStyle(ChatFormatting.WHITE));
		source.sendFeedback(Component.literal("    inSafari      " + SafariLocation.inSafari())
			.withStyle(ChatFormatting.WHITE));
		// Which trigger opened the live run: if a stale run is showing, this says whether
		// a reset was missed or one fired and something else is wrong.
		source.sendFeedback(Component.literal("    run opened by " + SessionManager.startedBy())
			.withStyle(ChatFormatting.WHITE));
	}

	private static String nameOf(SafariBiome biome) {
		return biome == null ? "none" : biome.displayName();
	}

	/**
	 * Reports the block under the crosshair, and what surrounds it.
	 *
	 * <p>Reports an entity or a block, whichever it is. A mound draws an entity outline
	 * and no block outline, so it is an entity of some kind — naming its type is the one
	 * thing needed before mounds can be counted.
	 */
	private static void block(FabricClientCommandSource source) {
		Minecraft client = source.getClient();
		if (client.level == null) {
			source.sendError(prefixed("No world loaded.", ChatFormatting.RED));
			return;
		}
		if (client.hitResult == null || client.hitResult.getType() == HitResult.Type.MISS) {
			source.sendError(prefixed("Look directly at something first.", ChatFormatting.RED));
			return;
		}

		// A mound draws an entity outline rather than a block outline, so whatever the
		// crosshair is on has to be reported either way.
		if (client.hitResult.getType() == HitResult.Type.ENTITY) {
			Entity hit = ((EntityHitResult) client.hitResult).getEntity();
			source.sendFeedback(header("Entity under crosshair"));
			source.sendFeedback(Component.literal("  type    "
				+ BuiltInRegistries.ENTITY_TYPE.getKey(hit.getType())).withStyle(ChatFormatting.WHITE));
			source.sendFeedback(Component.literal("  name    "
				+ (hit.hasCustomName() ? stripCodes(hit.getCustomName().getString()) : "(none)"))
				.withStyle(ChatFormatting.AQUA));
			source.sendFeedback(Component.literal("  display " + stripCodes(hit.getDisplayName().getString()))
				.withStyle(ChatFormatting.GRAY));
			source.sendFeedback(Component.literal("  at      %d %d %d   uuid %s".formatted(
				hit.blockPosition().getX(), hit.blockPosition().getY(), hit.blockPosition().getZ(),
				hit.getUUID().toString().substring(0, 8))).withStyle(ChatFormatting.GRAY));
			source.sendFeedback(Component.literal("  glowing %s   invisible %s   box %.2f x %.2f".formatted(
				hit.isCurrentlyGlowing(), hit.isInvisible(),
				hit.getBoundingBox().getXsize(), hit.getBoundingBox().getYsize()))
				.withStyle(ChatFormatting.DARK_GRAY));

			// A mound may well be a head on an armour stand, and they come in different
			// sizes — so the small flag, the scale attribute and what is worn on the
			// head are the three things that would identify one.
			if (hit instanceof LivingEntity living) {
				double scale = living.getAttributes().hasAttribute(Attributes.SCALE)
					? living.getAttributeValue(Attributes.SCALE) : 1.0;
				String small = hit instanceof ArmorStand stand
					? "small %s  marker %s".formatted(stand.isSmall(), stand.isMarker()) : "-";
				source.sendFeedback(Component.literal("  scale %.2f   %s".formatted(scale, small))
					.withStyle(ChatFormatting.DARK_GRAY));

				for (EquipmentSlot slot : EquipmentSlot.values()) {
					ItemStack worn = living.getItemBySlot(slot);
					if (worn.isEmpty()) continue;
					source.sendFeedback(Component.literal("  %-10s %s  x%d".formatted(
						slot.getName(), stripCodes(worn.getHoverName().getString()), worn.getCount()))
						.withStyle(ChatFormatting.AQUA));
				}
			}
			return;
		}

		BlockPos pos = ((BlockHitResult) client.hitResult).getBlockPos();
		BlockState state = client.level.getBlockState(pos);

		source.sendFeedback(header("Block at %d %d %d".formatted(pos.getX(), pos.getY(), pos.getZ())));
		source.sendFeedback(Component.literal("  id    " + BuiltInRegistries.BLOCK.getKey(state.getBlock()))
			.withStyle(ChatFormatting.WHITE));
		source.sendFeedback(Component.literal("  state " + state).withStyle(ChatFormatting.GRAY));

		// A mound is a cluster, so the neighbouring make-up says whether one block type
		// is the whole thing or just its surface.
		Map<String, Integer> around = new TreeMap<>();
		for (BlockPos near : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
			BlockState neighbour = client.level.getBlockState(near);
			if (neighbour.isAir()) continue;
			around.merge(BuiltInRegistries.BLOCK.getKey(neighbour.getBlock()).getPath(), 1, Integer::sum);
		}
		source.sendFeedback(Component.literal("  within 2 blocks: " + around)
			.withStyle(ChatFormatting.DARK_GRAY));
	}

	private static String stripCodes(String text) {
		return text.replaceAll("\u00a7.", "").replaceAll("[\\p{Cf}\\p{Co}]", "").trim();
	}

	/** Replays this instance's log directory and reports past runs. */
	/**
	 * Fills the saved history from the chat logs on disk.
	 *
	 * <p>The mod only starts recording runs once it is installed, but the logs go back
	 * as far as the instance does, and the same parser reads them. Runs already held are
	 * skipped on their start time, so running it twice changes nothing.
	 */
	private static void importRuns(FabricClientCommandSource source) {
		Path logs = source.getClient().gameDirectory.toPath().resolve("logs");
		if (!Files.isDirectory(logs)) {
			source.sendError(prefixed("No logs directory at " + logs, ChatFormatting.RED));
			return;
		}

		source.sendFeedback(prefixed("Reading past runs out of " + logs + " …", ChatFormatting.GRAY));
		new Thread(() -> {
			try {
				List<SafariSession> sessions = LogScanner.scan(logs, null);
				source.getClient().execute(() -> {
					RunHistory.Imported result = RunHistory.importRuns(sessions);
					// The logs have now been read, so the screens stop suggesting it.
					ConfigManager.get().profit.importedOnce = true;
					ConfigManager.save();
					source.sendFeedback(prefixed(
						"Imported %d new run(s), filled in the shards of %d; %d saved in total."
							.formatted(result.added(), result.filled(), RunHistory.size()),
						ChatFormatting.YELLOW));
					int unpriced = RunHistory.size() - RunHistory.pricedRuns();
					if (unpriced > 0) {
						// A run whose log has since been rotated away, which nothing here can
						// recover — said once, so it does not look like the import failed.
						source.sendFeedback(Component.literal(
							"  %d run(s) have no log left to read their shards from, so they stay unpriced."
								.formatted(unpriced)).withStyle(ChatFormatting.DARK_GRAY));
					}
				});
			} catch (Exception e) {
				source.getClient().execute(() ->
					source.sendError(prefixed("Log scan failed: " + e.getMessage(), ChatFormatting.RED)));
			}
		}, "crittermod-run-import").start();
	}

	private static void history(FabricClientCommandSource source) {
		Path logs = source.getClient().gameDirectory.toPath().resolve("logs");
		if (!Files.isDirectory(logs)) {
			source.sendError(prefixed("No logs directory at " + logs, ChatFormatting.RED));
			return;
		}

		source.sendFeedback(prefixed("Scanning " + logs + " …", ChatFormatting.GRAY));
		new Thread(() -> {
			try {
				List<SafariSession> sessions = LogScanner.scan(logs, null);
				source.getClient().execute(() -> {
					source.sendFeedback(header("Past runs: " + sessions.size()));
					int shown = 0;
					for (int i = sessions.size() - 1; i >= 0 && shown < 10; i--, shown++) {
						SafariSession past = sessions.get(i);
						source.sendFeedback(Component.literal(
							"  #%d  party %d/%d  you %d/%d  (%d caught)".formatted(
								i + 1, past.partyUnique(), Critters.total(),
								past.ownUnique(), Critters.total(), past.partyTotal()))
							.withStyle(ChatFormatting.GRAY));
					}
				});
			} catch (Exception e) {
				source.getClient().execute(() ->
					source.sendError(prefixed("Log scan failed: " + e.getMessage(), ChatFormatting.RED)));
			}
		}, "crittermod-log-scan").start();
	}

	private static Component header(String text) {
		return Component.literal("[Critters] ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal(text).withStyle(ChatFormatting.YELLOW));
	}

	private static Component prefixed(String text, ChatFormatting colour) {
		return Component.literal("[Critters] ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal(text).withStyle(colour));
	}

	private static ChatFormatting style(SafariBiome biome) {
		return switch (biome) {
			case FOREST -> ChatFormatting.GREEN;
			case CAVERN -> ChatFormatting.GOLD;
			case ICY -> ChatFormatting.AQUA;
			case HAUNTED -> ChatFormatting.DARK_PURPLE;
		};
	}
}
