package dev.rok.crittermod.client;

import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Sends outgoing chat lines one at a time with a gap between them.
 *
 * <p>Hypixel rate-limits chat and will swallow — or mute for — a burst of messages
 * sent in the same tick. A four-biome report is four lines, so they are queued and
 * drained on a timer instead of being fired at once.
 */
public final class ChatQueue {

	/** Hypixel drops messages sent closer together than roughly a second. */
	private static final long GAP_MILLIS = 1200;
	/** Server-side chat limit; anything longer is rejected outright. */
	private static final int MAX_LENGTH = 250;

	private static final Deque<String> pending = new ArrayDeque<>();
	private static long nextSendMillis;

	private ChatQueue() {
	}

	/**
	 * Queues one line.
	 *
	 * @param command true to send it as a command (no leading slash), false for plain chat
	 */
	public static void enqueue(String line, boolean command) {
		String trimmed = line.length() > MAX_LENGTH ? line.substring(0, MAX_LENGTH) : line;
		pending.addLast((command ? "/" : "") + trimmed);
	}

	public static int pendingCount() {
		return pending.size();
	}

	public static void clear() {
		pending.clear();
	}

	/** Drains at most one queued line per call; wired to the client tick. */
	public static void tick() {
		if (pending.isEmpty()) return;

		long now = System.currentTimeMillis();
		if (now < nextSendMillis) return;

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.connection == null) {
			pending.clear();
			return;
		}

		String line = pending.pollFirst();
		if (line.startsWith("/")) {
			client.player.connection.sendCommand(line.substring(1));
		} else {
			client.player.connection.sendChat(line);
		}
		nextSendMillis = now + GAP_MILLIS;
	}
}
