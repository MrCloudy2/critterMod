package dev.rok.crittermod.session;

import dev.rok.crittermod.client.AreaDetector;
import dev.rok.crittermod.client.EncounterAlerts;
import dev.rok.crittermod.client.SafariPresence;
import dev.rok.crittermod.parse.ChatParser;
import dev.rok.crittermod.data.SafariBiome;
import dev.rok.crittermod.parse.CritterEvent;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the live session: starts one on entering the Critter Safari, feeds it
 * parsed chat events, and closes it on the way out.
 *
 * <p>A run ends when the sidebar stops showing a Safari area. The finished run is
 * kept as {@link #lastSession()} so {@code /critters} still works after you leave.
 */
public final class SessionManager {

	private static final int MAX_HISTORY = 20;
	/** Sidebar can lag a tick or two on island transfer; require a few misses in a row. */
	private static final int LEAVE_GRACE_TICKS = 40;

	private static SafariSession current;
	private static SafariSession lastSession;
	private static final List<SafariSession> history = new ArrayList<>();

	private static final java.util.EnumSet<SafariBiome> announcedBiomes =
		java.util.EnumSet.noneOf(SafariBiome.class);

	private static boolean wasInSafari;
	private static int ticksOutsideSafari;

	private SessionManager() {
	}

	/** Called every client tick to open/close runs as the player moves islands. */
	public static void tick() {
		boolean inSafari = AreaDetector.inSafari();

		if (inSafari) {
			ticksOutsideSafari = 0;
			if (!wasInSafari) {
				startSession();
				wasInSafari = true;
			}
			return;
		}

		if (!wasInSafari) return;
		if (++ticksOutsideSafari < LEAVE_GRACE_TICKS) return;
		endSession();
		wasInSafari = false;
	}

	/** Feeds one raw chat line into the active run. */
	public static void onChatMessage(String rawText) {
		String line = ChatParser.clean(rawText);
		if (line.isEmpty()) return;

		CritterEvent event = ChatParser.parse(line, selfName());
		if (event == null) return;

		// The "entered Critter Safari!" banner usually beats the sidebar update, so
		// treat it as an explicit run start rather than waiting for the area change.
		if (event.type() == CritterEvent.Type.ENTERED_SAFARI) {
			startSession();
			wasInSafari = true;
			ticksOutsideSafari = 0;
			return;
		}

		// A catch or capsule throw only happens inside the Safari, so it is proof of
		// presence in its own right. Relying on the entry banner alone would leave
		// the mod blind after joining mid-run or missing that one message.
		SafariPresence.set(true);

		if (current == null) startSession();
		current.record(event, System.currentTimeMillis());

		if (event.isCatch()) {
			EncounterAlerts.onCatch(event.critter().name());
			announceNewlyCompleteBiomes();
		}
	}

	/**
	 * Fires a completion alert the moment a biome's last species is caught by anyone.
	 * Each biome announces at most once per run.
	 */
	private static void announceNewlyCompleteBiomes() {
		for (SafariBiome biome : SafariBiome.values()) {
			if (announcedBiomes.contains(biome)) continue;
			if (!current.biomeComplete(biome)) continue;
			announcedBiomes.add(biome);
			EncounterAlerts.onBiomeComplete(biome);
		}
	}

	public static void startSession() {
		if (current != null && !current.isEmpty()) archive(current);
		current = new SafariSession(selfName(), System.currentTimeMillis());
		announcedBiomes.clear();
		EncounterAlerts.reset();
	}

	private static void endSession() {
		if (current == null) return;
		if (!current.isEmpty()) archive(current);
		current = null;
	}

	private static void archive(SafariSession session) {
		lastSession = session;
		history.add(session);
		while (history.size() > MAX_HISTORY) history.removeFirst();
	}

	/** The run in progress, or {@code null} outside the Safari. */
	public static SafariSession current() {
		return current;
	}

	/** The run in progress if there is one, otherwise the most recent finished run. */
	public static SafariSession currentOrLast() {
		return current != null ? current : lastSession;
	}

	public static SafariSession lastSession() {
		return lastSession;
	}

	public static List<SafariSession> history() {
		return List.copyOf(history);
	}

	/** Wipes the active run's tallies without waiting to leave the island. */
	public static void reset() {
		current = new SafariSession(selfName(), System.currentTimeMillis());
	}

	private static String selfName() {
		return Minecraft.getInstance().getUser().getName();
	}
}
