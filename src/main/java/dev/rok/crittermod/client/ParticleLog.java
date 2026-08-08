package dev.rok.crittermod.client;

import dev.rok.crittermod.data.Critter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records which particles the server sends, and which critters they land on.
 *
 * <p>Written to answer one question: what marks a sparkling critter. The mod currently
 * assumes the name tag says so, which is a guess; the particles around one are the real
 * evidence, and nothing in the game or in any other mod names them. So this counts them.
 *
 * <p>The useful column is not how many of each type arrive — the Safari is full of
 * ambient particles — but how many arrive <em>on a critter</em>. A type that only ever
 * appears within a couple of blocks of one, and only sometimes, is the candidate.
 *
 * <p>Off unless switched on, and it stops on its own: a particle packet arrives many
 * times a second and keeping them all would be a leak with a nice name.
 */
public final class ParticleLog {

	/** How close a particle has to be to a critter to be counted as on it. */
	private static final double NEAR_CRITTER = 2.5;
	/** Recording stops itself after this long, in case it is left on. */
	private static final long MAX_WATCH_MILLIS = 5 * 60 * 1000L;

	/** Particle type -> what has been seen of it. */
	private static final Map<String, Tally> tallies = new LinkedHashMap<>();

	private static boolean watching;
	private static long startedAt;

	/** What one particle type has been seen doing. */
	public static final class Tally {
		private int total;
		private int nearCritter;
		private final Map<String, Integer> species = new LinkedHashMap<>();

		public int total() {
			return total;
		}

		public int nearCritter() {
			return nearCritter;
		}

		/** Which species it was seen on, commonest first. */
		public List<String> species() {
			return species.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.map(e -> e.getKey() + " x" + e.getValue())
				.toList();
		}
	}

	private ParticleLog() {
	}

	/** Starts recording, clearing whatever was recorded before. */
	public static void start() {
		tallies.clear();
		watching = true;
		startedAt = System.currentTimeMillis();
	}

	public static void stop() {
		watching = false;
	}

	public static boolean watching() {
		return watching;
	}

	/** How long recording has been running, in seconds. */
	public static long secondsWatching() {
		return watching ? (System.currentTimeMillis() - startedAt) / 1000 : 0;
	}

	/** Everything seen, the types that landed on a critter first. */
	public static Map<String, Tally> tallies() {
		Map<String, Tally> sorted = new LinkedHashMap<>();
		tallies.entrySet().stream()
			.sorted((a, b) -> {
				int onCritter = b.getValue().nearCritter - a.getValue().nearCritter;
				return onCritter != 0 ? onCritter : b.getValue().total - a.getValue().total;
			})
			.forEach(e -> sorted.put(e.getKey(), e.getValue()));
		return sorted;
	}

	/**
	 * Called from the packet mixin for every particle the server sends.
	 *
	 * <p>Returns immediately unless recording, so the mixin costs a field read per
	 * packet the rest of the time.
	 */
	public static void onParticle(ClientboundLevelParticlesPacket packet) {
		if (!watching) return;
		if (System.currentTimeMillis() - startedAt > MAX_WATCH_MILLIS) {
			watching = false;
			return;
		}

		Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(packet.getParticle().getType());
		if (id == null) return;

		Tally tally = tallies.computeIfAbsent(id.toString(), key -> new Tally());
		tally.total++;

		Critter on = critterAt(new Vec3(packet.getX(), packet.getY(), packet.getZ()));
		if (on != null) {
			tally.nearCritter++;
			tally.species.merge(on.name(), 1, Integer::sum);
		}
	}

	/** The critter a particle landed on, or null if it landed on nothing in particular. */
	private static Critter critterAt(Vec3 where) {
		Critter best = null;
		double bestSq = NEAR_CRITTER * NEAR_CRITTER;
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			Entity body = sighting.body();
			double distanceSq = body.position().distanceToSqr(where);
			if (distanceSq >= bestSq) continue;
			bestSq = distanceSq;
			best = sighting.critter();
		}
		return best;
	}

	/** Lines for the report, one per particle type. */
	public static List<String> describe() {
		List<String> lines = new ArrayList<>();
		tallies().forEach((id, tally) -> {
			String on = tally.nearCritter() == 0 ? ""
				: "  on critters: %d  (%s)".formatted(tally.nearCritter(),
					String.join(", ", tally.species()));
			lines.add("  %-34s x%-6d%s".formatted(id, tally.total(), on));
		});
		return lines;
	}
}
