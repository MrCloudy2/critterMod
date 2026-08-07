package dev.rok.crittermod.client;

import com.mojang.brigadier.CommandDispatcher;
import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.data.Critters;
import dev.rok.crittermod.data.SafariBiome;
import dev.rok.crittermod.importer.LogScanner;
import dev.rok.crittermod.session.MissingReport;
import dev.rok.crittermod.session.SafariSession;
import dev.rok.crittermod.session.SessionManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.Map;

/** {@code /critters} — reports the current run in chat. */
public final class CritterCommand {

	/** Wide enough to cover a biome's worth of spawns without scanning the whole map. */
	private static final double ENTITY_SCAN_RADIUS = 48.0;
	/** Close enough that whatever you are stood on is at the top of the list. */
	private static final double NEARBY_SCAN_RADIUS = 8.0;

	private CritterCommand() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommands.literal("critters")
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
			.then(ClientCommands.literal("alerts").executes(ctx -> {
				CritterConfig config = ConfigManager.get();
				config.alerts.bossAlerts = !config.alerts.bossAlerts;
				ConfigManager.save();
				ctx.getSource().sendFeedback(prefixed("Encounter alerts "
					+ (config.alerts.bossAlerts ? "enabled" : "disabled") + ".", ChatFormatting.YELLOW));
				return 1;
			}))
			.then(ClientCommands.literal("notify").executes(ctx -> {
				CritterConfig config = ConfigManager.get();
				config.party.bossPartyNotify = !config.party.bossPartyNotify;
				ConfigManager.save();
				ctx.getSource().sendFeedback(prefixed("Party-chat notifications "
					+ (config.party.bossPartyNotify ? "enabled" : "disabled") + ".", ChatFormatting.YELLOW));
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
				TraderWatch.onChatMessage("[NPC] Hunter Harry: Say, do you have a use for a Nozzlenose Shard? I found it lying around...");
				TraderWatch.onChatMessage("[NPC] Hunter Harry: I'll give you it in exchange for a Yogi Berry!");
				return 1;
			}))
			.then(ClientCommands.literal("history").executes(ctx -> {
				history(ctx.getSource());
				return 1;
			}))
			.then(ClientCommands.literal("debug").executes(ctx -> {
				debug(ctx.getSource());
				return 1;
			}))
			.then(ClientCommands.literal("entities").executes(ctx -> {
				entities(ctx.getSource());
				return 1;
			}))
			.then(ClientCommands.literal("nearby").executes(ctx -> {
				nearby(ctx.getSource());
				return 1;
			})));

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

		if (!session.sparklings().isEmpty()) {
			source.sendFeedback(Component.literal("  SPARKLING: " + session.sparklings().stream()
				.map(Critter::name).reduce((a, b) -> a + ", " + b).orElse(""))
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		source.sendFeedback(Component.literal("  /ct run screen · /cm settings · text · missing · copy · share · players · reset · history")
			.withStyle(ChatFormatting.DARK_GRAY));
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

		List<String> sidebar = AreaDetector.sidebarLines();
		source.sendFeedback(Component.literal("  sidebar (" + sidebar.size() + " lines):")
			.withStyle(ChatFormatting.YELLOW));
		for (String line : sidebar) {
			source.sendFeedback(Component.literal("    | " + line).withStyle(ChatFormatting.GRAY));
		}

		// Most tab entries are player names; only metadata rows matter here.
		List<String> interesting = AreaDetector.tabListEntries().stream()
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
			.formatted(AreaDetector.distanceToNearestNode(), SafariAreaMap.nodeCount()))
			.withStyle(ChatFormatting.GRAY));

		source.sendFeedback(Component.literal("  resolved:").withStyle(ChatFormatting.YELLOW));
		source.sendFeedback(Component.literal("    from text     " + nameOf(AreaDetector.biomeFromText()))
			.withStyle(ChatFormatting.GRAY));
		source.sendFeedback(Component.literal("    from position " + nameOf(AreaDetector.biomeFromPosition()))
			.withStyle(ChatFormatting.GRAY));
		source.sendFeedback(Component.literal("    currentBiome  " + nameOf(AreaDetector.currentBiome()))
			.withStyle(ChatFormatting.WHITE));
		source.sendFeedback(Component.literal("    inSafari      " + AreaDetector.inSafari()
			+ "  (chat flag: " + SafariPresence.inSafari() + ")").withStyle(ChatFormatting.WHITE));
	}

	private static String nameOf(SafariBiome biome) {
		return biome == null ? "none" : biome.displayName();
	}

	/**
	 * Dumps nearby entities so critter mobs can be identified from real data.
	 *
	 * <p>Groundwork for counting how many of a species actually spawned this run: most
	 * species spawn a randomised number, so no static table can say how many there are
	 * to catch. Seeing them as entities could.
	 */
	private static void entities(FabricClientCommandSource source) {
		Minecraft client = source.getClient();
		if (client.level == null) {
			source.sendError(prefixed("No world loaded.", ChatFormatting.RED));
			return;
		}

		Vec3 origin = source.getPlayer().position();
		List<String> matched = new ArrayList<>();
		// Named things that are not a species are the interesting ones: shells and the
		// like have to announce themselves somehow, and this is where they would show.
		Map<String, Integer> otherNames = new TreeMap<>();
		Map<String, Integer> byType = new TreeMap<>();
		int total = 0;

		for (Entity entity : client.level.entitiesForRendering()) {
			double distance = Math.sqrt(entity.position().distanceToSqr(origin));
			if (distance > ENTITY_SCAN_RADIUS) continue;
			total++;

			String type = entity.getType().toString();
			type = type.substring(type.lastIndexOf('.') + 1);
			byType.merge(type, 1, Integer::sum);

			String custom = entity.hasCustomName() ? stripCodes(entity.getCustomName().getString()) : "";
			String display = stripCodes(entity.getDisplayName().getString());
			String label = !custom.isEmpty() ? custom : display;
			if (label.isEmpty()) continue;

			Critter critter = Critters.byName(label);
			if (critter != null) {
				matched.add("  %-13s %-9s %.0fm  uuid %s".formatted(
					critter.name(), type, distance, entity.getUUID().toString().substring(0, 8)));
				continue;
			}
			// Unnamed entities fall back to their type as a display name; that is noise.
			if (label.equalsIgnoreCase(type.replace('_', ' '))) continue;
			otherNames.merge(label + "  [" + type + "]", 1, Integer::sum);
		}

		source.sendFeedback(header("Entities within %.0f blocks: %d".formatted(ENTITY_SCAN_RADIUS, total)));
		source.sendFeedback(Component.literal("  species name tags: " + matched.size())
			.withStyle(ChatFormatting.YELLOW));
		matched.stream().limit(14).forEach(line ->
			source.sendFeedback(Component.literal(line).withStyle(ChatFormatting.WHITE)));

		source.sendFeedback(Component.literal("  other named entities: " + otherNames.size())
			.withStyle(ChatFormatting.YELLOW));
		otherNames.entrySet().stream().limit(20).forEach(e ->
			source.sendFeedback(Component.literal("  %dx %s".formatted(e.getValue(), e.getKey()))
				.withStyle(ChatFormatting.AQUA)));

		source.sendFeedback(Component.literal("  types: " + byType).withStyle(ChatFormatting.DARK_GRAY));
	}

	/**
	 * Dumps the closest entities of any type, so the objects critters hide inside can
	 * be identified, such as the shells Rockmites sit in.
	 *
	 * <p>{@code /critters entities} only sees named spawns within its radius, so a
	 * species missing from it may simply have been out of range rather than spawning
	 * some other way — one 48-block snapshot per biome covers very little of the map.
	 * Where a species really does arrive from an object instead, such as a Rockmite in
	 * a shell, that object shows up here as an unnamed interaction or display entity.
	 * Stand on one and run this to see what it is.
	 */
	private static void nearby(FabricClientCommandSource source) {
		Minecraft client = source.getClient();
		if (client.level == null) {
			source.sendError(prefixed("No world loaded.", ChatFormatting.RED));
			return;
		}

		Vec3 origin = source.getPlayer().position();
		record Near(double distance, String line) {
		}
		List<Near> found = new ArrayList<>();

		for (Entity entity : client.level.entitiesForRendering()) {
			double distance = Math.sqrt(entity.position().distanceToSqr(origin));
			if (distance > NEARBY_SCAN_RADIUS) continue;

			String type = entity.getType().toString();
			type = type.substring(type.lastIndexOf('.') + 1);
			String custom = entity.hasCustomName() ? stripCodes(entity.getCustomName().getString()) : "";
			String display = stripCodes(entity.getDisplayName().getString());
			// The display name falls back to the type for unnamed entities, which is
			// noise here; only show it when it says something the type does not.
			String label = !custom.isEmpty() ? custom
				: display.equalsIgnoreCase(type.replace('_', ' ')) ? "" : display;

			found.add(new Near(distance, "  %-16s %4.1fm  %d %d %d  %s".formatted(
				type, distance,
				entity.blockPosition().getX(), entity.blockPosition().getY(),
				entity.blockPosition().getZ(),
				label.isEmpty() ? "-" : label)));
		}

		found.sort((a, b) -> Double.compare(a.distance(), b.distance()));
		source.sendFeedback(header("Closest entities within %.0f blocks".formatted(NEARBY_SCAN_RADIUS)));
		found.stream().limit(18).forEach(n ->
			source.sendFeedback(Component.literal(n.line()).withStyle(ChatFormatting.WHITE)));
		if (found.isEmpty()) {
			source.sendFeedback(Component.literal("  nothing in range").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private static String stripCodes(String text) {
		return text.replaceAll("\u00a7.", "").replaceAll("[\\p{Cf}\\p{Co}]", "").trim();
	}

	/** Replays this instance's log directory and reports past runs. */
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
