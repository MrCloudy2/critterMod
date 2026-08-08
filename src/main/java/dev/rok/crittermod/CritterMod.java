package dev.rok.crittermod;

import dev.rok.crittermod.client.ChatQueue;
import dev.rok.crittermod.client.CritterCommand;
import dev.rok.crittermod.client.CritterHighlighter;
import dev.rok.crittermod.client.CritterHud;
import dev.rok.crittermod.client.CritterSpotter;
import dev.rok.crittermod.client.MissingHud;
import dev.rok.crittermod.client.MoundTracker;
import dev.rok.crittermod.client.NestTracker;
import dev.rok.crittermod.client.SafariLocation;
import dev.rok.crittermod.client.TradeHud;
import dev.rok.crittermod.client.TraderWatch;
import dev.rok.crittermod.client.WaypointRenderer;
import dev.rok.crittermod.client.EncounterAlerts;
import dev.rok.crittermod.parse.ChatParser;
import dev.rok.crittermod.session.SessionManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side tracker for Hypixel SkyBlock's Critter Safari.
 *
 * <p>Listens to Hypixel's own catch messages and tallies, for the current run,
 * how many of the 37 species you and your party have caught — overall and per
 * biome. Nothing is sent anywhere; it only reads chat the client already receives.
 */
public class CritterMod implements ClientModInitializer {

	public static final String MOD_ID = "crittermod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		// Hypixel sends catch messages as system chat, which is what GAME covers.
		// This fires upstream of chat-compacting mods, so the duplicate counters
		// they append never reach the parser.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (overlay) return;
			// Hypixel sends banners such as the "entered Critter Safari!" notice as a
			// single multi-line component, so each line has to be handled separately
			// or the interesting one never matches on its own.
			for (String part : message.getString().split("\\r?\\n|\\\\n")) {
				String line = ChatParser.clean(part);
				if (line.isEmpty()) continue;
				SafariLocation.onChatMessage(line);
				SessionManager.onChatMessage(line);
				EncounterAlerts.onChatMessage(line);
				TraderWatch.onChatMessage(line);
				MoundTracker.onChatMessage(line);
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// First, and only here: everything below asks it where the player is.
			SafariLocation.tick();
			SessionManager.tick();
			CritterSpotter.tick();
			NestTracker.tick();
			CritterHighlighter.tick();
			ChatQueue.tick();
		});

		// Hypixel never says you have left the Safari, but moving island reconnects, so
		// this is the one moment the chat-driven flag is known to be stale.
		ClientPlayConnectionEvents.JOIN.register(
			(handler, sender, client) -> SafariLocation.onWorldChange());
		ClientPlayConnectionEvents.DISCONNECT.register(
			(handler, client) -> SafariLocation.onWorldChange());

		// Punching a bee nest changes nothing about the block, so the punch itself is
		// the only signal that it has been done.
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			NestTracker.onAttack(pos);
			return InteractionResult.PASS;
		});

		ClientCommandRegistrationCallback.EVENT.register(
			(dispatcher, registryAccess) -> CritterCommand.register(dispatcher));

		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "safari_progress"),
			new CritterHud());
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "safari_missing"),
			new MissingHud());
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "hunter_trades"),
			new TradeHud());
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "encounter_alerts"),
			new EncounterAlerts());

		WaypointRenderer.register();

		LOGGER.info("Critter Safari tracker ready");
	}
}
