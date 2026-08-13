package dev.rok.crittermod.client;

import com.google.gson.stream.JsonReader;
import dev.rok.crittermod.CritterMod;
import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.data.Critters;
import dev.rok.crittermod.session.RunRecord;
import dev.rok.crittermod.session.SafariSession;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * What a run's shards are worth, at live bazaar prices.
 *
 * <p>Every species' shard is a bazaar product, so a run's value is simply the shards it
 * gave priced one species at a time. Catch counts cannot stand in for that: a catch gives
 * a random number of shards, and the shards themselves are worth wildly different amounts.
 *
 * <p>Prices come from Hypixel's public bazaar endpoint, the same one SkyHanni polls — no
 * key, no account, nothing about the player in the request. This is the only outbound
 * traffic this mod makes that the player did not type, so it is behind a setting, and it
 * only runs while there is somewhere to show the number: at the Safari, or with the run
 * screen open.
 *
 * <p>Two prices are kept per shard, and both are read off the <em>top of the order
 * book</em> rather than out of {@code quick_status}. Hypixel's quick status is a
 * volume-weighted average over the whole book, so one enormous junk order at one coin
 * drags a shard's reported price to nothing — SkyHanni reads the summaries for the same
 * reason.
 *
 * <ul>
 *   <li><b>Instant sell</b> — the best standing buy order ({@code sell_summary}), what
 *       you get for selling to the bazaar right now.</li>
 *   <li><b>Sell offer</b> — the cheapest standing sell offer ({@code buy_summary}), which
 *       is what you would list at and wait for.</li>
 * </ul>
 */
public final class BazaarPrices {

	private static final URI ENDPOINT = URI.create("https://api.hypixel.net/v2/skyblock/bazaar");

	/** SkyHanni's interval. The endpoint itself only recomputes about that often. */
	private static final long REFRESH_MILLIS = 120_000;
	/** A failure is usually a blip, so the first few retries come quickly. */
	private static final long RETRY_MILLIS = 15_000;
	/** After that it is the API being down, and hammering it helps nobody. */
	private static final long BACK_OFF_MILLIS = 900_000;
	private static final int QUIET_FAILURES = 3;

	/** Bazaar tax on the sell side, which both price modes are. */
	private static final double TAX = 0.0125;

	/** The 37 product ids, so the parser can skip the other ~2,000 without building them. */
	private static final Set<String> WANTED = wantedIds();

	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "crittermod-bazaar");
		thread.setDaemon(true);
		return thread;
	});

	private static final AtomicBoolean fetching = new AtomicBoolean();

	private static volatile Map<String, Price> prices = Map.of();
	private static volatile long fetchedAt;
	private static volatile long nextFetchAt;
	private static volatile String lastError;
	/** Set by a manual refresh, so it fetches even from outside the Safari. */
	private static volatile boolean manual;
	private static volatile HttpClient http;
	/** Written by the worker, reset by a manual refresh from the client thread. */
	private static volatile int failures;

	/** One shard's two prices, both top-of-book; {@code 0} where the book is empty. */
	public record Price(double instantSell, double sellOffer) {
	}

	private BazaarPrices() {
	}

	private static Set<String> wantedIds() {
		Set<String> ids = new HashSet<>();
		for (Critter critter : Critters.all()) ids.add(critter.bazaarId());
		return Set.copyOf(ids);
	}

	// --- fetching ------------------------------------------------------------

	/**
	 * Refreshes the prices when they are stale and there is a reason to have them.
	 *
	 * <p>Called from the tick pipeline. Everything after the gate happens off-thread; the
	 * client thread only ever hands the job over.
	 */
	public static void tick() {
		if (!ConfigManager.get().profit.enabled) return;

		long now = System.currentTimeMillis();
		if (now < nextFetchAt) return;
		// Nowhere to show a price means no reason to ask for one.
		if (!manual && SafariLocation.where() == SafariLocation.Where.ELSEWHERE) return;

		// Held off before the job starts, so a slow request cannot queue a second one.
		nextFetchAt = now + REFRESH_MILLIS;
		manual = false;
		if (!fetching.compareAndSet(false, true)) return;
		WORKER.execute(BazaarPrices::fetch);
	}

	/** Asks for a fetch on the next tick if the prices are stale — used when a view opens. */
	public static void refreshIfStale() {
		if (!ConfigManager.get().profit.enabled) return;
		if (fetchedAt != 0 && System.currentTimeMillis() - fetchedAt < REFRESH_MILLIS) return;
		manual = true;
		nextFetchAt = 0;
	}

	/** Fetches on the next tick whatever the state, for {@code /critters prices}. */
	public static void refreshNow() {
		failures = 0;
		manual = true;
		nextFetchAt = 0;
	}

	private static void fetch() {
		try {
			HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
				.timeout(Duration.ofSeconds(20))
				.header("User-Agent", CritterMod.MOD_ID)
				.GET().build();
			HttpResponse<InputStream> response = client()
				.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());

			Map<String, Price> parsed;
			try (InputStream body = response.body()) {
				parsed = parse(body);
			}
			// An empty result is a shape change, not an update: the old prices are still
			// the best thing known, so they are kept rather than blanked.
			if (parsed.isEmpty()) throw new IOException("no shard products in the response");

			prices = Map.copyOf(parsed);
			fetchedAt = System.currentTimeMillis();
			lastError = null;
			failures = 0;
			nextFetchAt = fetchedAt + REFRESH_MILLIS;
		} catch (Exception failed) {
			failures++;
			lastError = failed.getClass().getSimpleName()
				+ (failed.getMessage() == null ? "" : ": " + failed.getMessage());
			nextFetchAt = System.currentTimeMillis()
				+ (failures <= QUIET_FAILURES ? RETRY_MILLIS : BACK_OFF_MILLIS);
			// Quiet at first — a dropped request during a server hop is not news. Said once
			// when it stops looking like a blip, and not repeated every retry after that.
			if (failures == QUIET_FAILURES + 1) {
				CritterMod.LOGGER.warn("Bazaar prices unavailable ({}), backing off", lastError);
			}
		} finally {
			fetching.set(false);
		}
	}

	private static HttpClient client() {
		HttpClient existing = http;
		if (existing != null) return existing;
		HttpClient built = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
		http = built;
		return built;
	}

	/**
	 * Reads the response as a stream, keeping only the 37 products this mod cares about.
	 *
	 * <p>The whole payload is several megabytes of order books for two thousand products.
	 * Skipping the rest as they go past costs nothing and never builds them.
	 */
	private static Map<String, Price> parse(InputStream body) throws IOException {
		Map<String, Price> found = new HashMap<>();
		try (JsonReader reader = new JsonReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
			reader.beginObject();
			while (reader.hasNext()) {
				if (!reader.nextName().equals("products")) {
					reader.skipValue();
					continue;
				}
				reader.beginObject();
				while (reader.hasNext()) {
					String id = reader.nextName();
					if (!WANTED.contains(id)) {
						reader.skipValue();
						continue;
					}
					found.put(id, product(reader));
				}
				reader.endObject();
			}
			reader.endObject();
		}
		return found;
	}

	private static Price product(JsonReader reader) throws IOException {
		double instantSell = 0;
		double sellOffer = 0;
		reader.beginObject();
		while (reader.hasNext()) {
			switch (reader.nextName()) {
				// Hypixel's naming is the reverse of what it reads like: sell_summary holds
				// the buy orders you can sell into, buy_summary the sell offers you buy from.
				case "sell_summary" -> instantSell = topOfBook(reader, true);
				case "buy_summary" -> sellOffer = topOfBook(reader, false);
				default -> reader.skipValue();
			}
		}
		reader.endObject();
		return new Price(instantSell, sellOffer);
	}

	/** The best price in one summary — highest for buy orders, lowest for sell offers. */
	private static double topOfBook(JsonReader reader, boolean highest) throws IOException {
		double best = 0;
		reader.beginArray();
		while (reader.hasNext()) {
			double price = 0;
			reader.beginObject();
			while (reader.hasNext()) {
				if (reader.nextName().equals("pricePerUnit")) price = reader.nextDouble();
				else reader.skipValue();
			}
			reader.endObject();
			if (price <= 0) continue;
			best = best == 0 ? price : highest ? Math.max(best, price) : Math.min(best, price);
		}
		reader.endArray();
		return best;
	}

	// --- pricing -------------------------------------------------------------

	/** True once a fetch has landed, so a view can say "not yet" instead of "nothing". */
	public static boolean known() {
		return fetchedAt != 0 && !prices.isEmpty();
	}

	/** How long ago the prices were fetched, or {@code -1} if they never have been. */
	public static long ageMillis() {
		return fetchedAt == 0 ? -1 : System.currentTimeMillis() - fetchedAt;
	}

	/** The last fetch failure, or {@code null} while things are working. */
	public static String lastError() {
		return lastError;
	}

	/** What one {@code critter} shard is worth under the chosen mode, tax taken off. */
	public static double price(Critter critter) {
		Price price = prices.get(critter.bazaarId());
		if (price == null) return 0;
		double raw = ConfigManager.get().profit.priceSource() == CritterConfig.PriceSource.SELL_OFFER
			? price.sellOffer() : price.instantSell();
		return ConfigManager.get().profit.subtractTax ? raw * (1 - TAX) : raw;
	}

	/** What a pile of shards is worth. Species with no price simply add nothing. */
	public static long valueOf(Map<Critter, Integer> shards) {
		double total = 0;
		for (Map.Entry<Critter, Integer> entry : shards.entrySet()) {
			total += price(entry.getKey()) * entry.getValue();
		}
		return Math.round(total);
	}

	/** What the shards of a live or just-finished run are worth. */
	public static long valueOf(SafariSession session) {
		return session == null ? 0 : valueOf(session.shardCounts());
	}

	/** What a saved run's shards are worth, or {@code 0} if it kept no breakdown. */
	public static long valueOf(RunRecord run) {
		if (run == null || !run.hasShardData()) return 0;
		double total = 0;
		for (Map.Entry<String, Integer> entry : run.shards.entrySet()) {
			// A saved run holds species by name, so one dropped from the roster since is
			// simply skipped — the same thing the rest of the history does with it.
			Critter critter = Critters.byName(entry.getKey());
			if (critter != null) total += price(critter) * entry.getValue();
		}
		return Math.round(total);
	}

	/** Every priceable run added up. Runs with no breakdown are left out, not zeroed. */
	public static long totalValue(List<RunRecord> runs) {
		long total = 0;
		for (RunRecord run : runs) total += valueOf(run);
		return total;
	}

	/** How many of {@code runs} can be priced at all — the divisor for an average. */
	public static int pricedRuns(List<RunRecord> runs) {
		return (int) runs.stream().filter(RunRecord::hasShardData).count();
	}

	/**
	 * Coins as they are said out loud: {@code 1.2M}, {@code 340k}, {@code 8,120}.
	 *
	 * <p>A run is worth millions and the screen has a column's width to say so in.
	 */
	public static String format(long coins) {
		long value = Math.abs(coins);
		if (value >= 1_000_000_000L) return "%.2fB".formatted(coins / 1_000_000_000.0);
		if (value >= 1_000_000L) return "%.2fM".formatted(coins / 1_000_000.0);
		if (value >= 10_000L) return "%.0fk".formatted(coins / 1_000.0);
		return "%,d".formatted(coins);
	}
}
