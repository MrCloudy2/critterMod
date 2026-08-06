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
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** {@code /critters} — reports the current run in chat. */
public final class CritterCommand {

	private CritterCommand() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommands.literal("critters")
			.executes(ctx -> {
				summary(ctx.getSource());
				return 1;
			})
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
				CritterConfig config = CritterConfig.get();
				config.hudEnabled = !config.hudEnabled;
				config.save();
				ctx.getSource().sendFeedback(prefixed(
					"HUD " + (config.hudEnabled ? "enabled" : "disabled") + ".", ChatFormatting.YELLOW));
				return 1;
			}))
			.then(ClientCommands.literal("panel").executes(ctx -> {
				CritterConfig config = CritterConfig.get();
				config.showMissing = !config.showMissing;
				config.save();
				ctx.getSource().sendFeedback(prefixed("Top-right missing panel "
					+ (config.showMissing ? "enabled" : "disabled") + ".", ChatFormatting.YELLOW));
				return 1;
			}))
			.then(ClientCommands.literal("wumpa").executes(ctx -> {
				CritterConfig config = CritterConfig.get();
				config.wumpaAlert = !config.wumpaAlert;
				config.save();
				ctx.getSource().sendFeedback(prefixed("Wumpa alert "
					+ (config.wumpaAlert ? "enabled" : "disabled") + ".", ChatFormatting.YELLOW));
				return 1;
			}))
			.then(ClientCommands.literal("testalert").executes(ctx -> {
				// Fires the alert without waiting for a real Wumpa, so its banner,
				// sound and placement can be checked anywhere.
				WumpaAlert.onChatMessage("A rumbling sound can be heard, and the door at the back of the chamber opens...");
				return 1;
			}))
			.then(ClientCommands.literal("history").executes(ctx -> {
				history(ctx.getSource());
				return 1;
			})));
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
		source.sendFeedback(Component.literal("  /critters missing · copy · share · players · reset · hud · panel · wumpa · history")
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

		String channel = CritterConfig.get().shareCommand;
		boolean asCommand = channel != null && !channel.isBlank();
		for (String line : lines) {
			ChatQueue.enqueue(asCommand ? channel.trim() + " " + line : line, asCommand);
		}

		source.sendFeedback(prefixed("Posting %d line(s) to %s…".formatted(
			lines.size(), asCommand ? "/" + channel.trim() : "chat"), ChatFormatting.YELLOW));
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
