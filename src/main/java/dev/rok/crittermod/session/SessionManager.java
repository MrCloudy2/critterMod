package dev.rok.crittermod.session;

import dev.rok.crittermod.client.ConfigManager;
import dev.rok.crittermod.client.EncounterAlerts;
import dev.rok.crittermod.client.FloorDrops;
import dev.rok.crittermod.client.HideyhoSolver;
import dev.rok.crittermod.client.SafariLocation;
import dev.rok.crittermod.client.TraderWatch;
import dev.rok.crittermod.client.MacawWatch;
import dev.rok.crittermod.client.MoundTracker;
import dev.rok.crittermod.client.NestTracker;
import dev.rok.crittermod.client.RecatchSpots;
import dev.rok.crittermod.client.WallTracker;
import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.parse.ChatParser;
import dev.rok.crittermod.data.Critters;
import dev.rok.crittermod.data.SafariBiome;
import dev.rok.crittermod.parse.CritterEvent;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
	/**
	 * A run is never closed while catches are still arriving, whatever the sidebar
	 * says. Guards against the area lines inside the Safari not matching expectations.
	 */
	private static final long RECENT_ACTIVITY_MILLIS = 60_000;

	private static SafariSession current;
	private static SafariSession lastSession;
	private static final List<SafariSession> history = new ArrayList<>();

	private static final java.util.EnumSet<SafariBiome> announcedBiomes =
		java.util.EnumSet.noneOf(SafariBiome.class);

	private static boolean announcedAllButMacaw;
	private static boolean announcedAllDone;

	private static int ticksOutsideSafari;
	/** When the last critter event landed, used to keep an active run from closing. */
	private static long lastEventMillis;

	private SessionManager() {
	}

	/** Called every client tick to open/close runs as the player moves around. */
	public static void tick() {
		TrackingMode.setUniqueOnly(ConfigManager.get().display.uniqueOnly);
		TrackingMode.setCountSpawns(ConfigManager.get().display.countSpawns);
		updateUnavailable();

		// Being at the Safari — including its entrance — keeps a run open. Runs are
		// opened by the entry message or the first catch, never from here: the
		// entrance counts as "at the Safari", so starting here would open an empty
		// run and archive the one just finished.
		if (SafariLocation.inSafari()) {
			ticksOutsideSafari = 0;
			return;
		}

		// A session can also be opened by a catch arriving, so closing must not depend
		// on this path having opened it.
		if (current == null) return;
		if (System.currentTimeMillis() - lastEventMillis < RECENT_ACTIVITY_MILLIS) return;
		if (++ticksOutsideSafari < LEAVE_GRACE_TICKS) return;
		endSession();
	}

	/**
	 * Works out what the run can no longer produce.
	 *
	 * <p>Snoozle comes from the breakable Cavern walls. Once all of them are confirmed
	 * broken and none has turned up, there is no way for one to appear, which is
	 * reportedly common — so it stops being listed as outstanding.
	 */
	private static void updateUnavailable() {
		if (current == null) {
			TrackingMode.setUnavailable(Set.of());
			return;
		}
		Critter snoozle = Critters.byName("Snoozle");
		boolean gone = snoozle != null
			&& WallTracker.SNOOPER.allConfirmedBroken()
			&& current.partyCatches(snoozle) == 0
			&& current.nearby(snoozle) == 0;
		TrackingMode.setUnavailable(gone ? Set.of(snoozle) : Set.of());
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
			SafariLocation.markEntered();
			ticksOutsideSafari = 0;
			return;
		}

		// A catch or capsule throw only happens inside the Safari, so it is proof of
		// presence in its own right. Relying on the entry banner alone would leave
		// the mod blind after joining mid-run or missing that one message.
		SafariLocation.markEntered();

		if (current == null) startSession();
		lastEventMillis = System.currentTimeMillis();
		current.record(event, lastEventMillis);

		if (event.isCatch()) {
			EncounterAlerts.onCatch(event.critter().name());
			announceNewlyCompleteBiomes();
			announceRunMilestones();
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

	/**
	 * Fires the two whole-run milestones, at most once each per run.
	 *
	 * <p>They are mutually exclusive: if a single catch completes the dex outright,
	 * only "Everything Done!" fires, and the weaker "except Macaw" message is marked
	 * as already announced so it cannot follow it.
	 */
	private static void announceRunMilestones() {
		if (!announcedAllDone && current.dexComplete()) {
			announcedAllDone = true;
			announcedAllButMacaw = true;
			EncounterAlerts.onAllDone();
			return;
		}
		if (!announcedAllButMacaw && current.allCaughtExcept(Critters.MACAW)) {
			announcedAllButMacaw = true;
			EncounterAlerts.onAllButMacaw();
		}
	}

	public static void startSession() {
		if (current != null && !current.isEmpty()) archive(current);
		current = new SafariSession(selfName(), System.currentTimeMillis());
		announcedBiomes.clear();
		announcedAllButMacaw = false;
		announcedAllDone = false;
		EncounterAlerts.reset();
		TraderWatch.reset();
		NestTracker.reset();
		MoundTracker.reset();
		RecatchSpots.reset();
		HideyhoSolver.reset();
		MacawWatch.reset();
		FloorDrops.reset();
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
