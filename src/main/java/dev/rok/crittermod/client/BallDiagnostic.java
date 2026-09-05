package dev.rok.crittermod.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Answers one question: what entity is a thrown Critter Capsule?
 *
 * <p>{@link HideonwallSpots} retires a perch when a capsule flies through it, and matches
 * on the {@link Projectile} base class because that covers any re-skinned snowball, egg or
 * pearl. If Hypixel builds a capsule out of something else — a display entity, an armour
 * stand — that never fires, and there is no way to tell that apart from simply having
 * missed every throw.
 *
 * <p>A snapshot is no use here: a capsule is in the air for well under a second and is
 * gone by the time a command could be typed. So this watches a window instead. Start it,
 * throw, and it reports everything that <em>appeared</em> while it was running.
 *
 * <p>Deliberately narrower than the {@code /critters entities} this replaces, which listed
 * every loaded entity with its coordinates and was the most direct advantage in the mod.
 * Nothing here carries a position: only what a thing is, how big it is, and how fast it
 * was moving — which is all that identifying a capsule needs, and no use for finding
 * anything through a wall.
 */
public final class BallDiagnostic {

	/** Long enough to get a throw away after typing the command. */
	private static final long WINDOW_MILLIS = 10_000;
	/** Beyond this the list is unreadable in chat. */
	private static final int MAX_REPORTED = 14;

	private static long until;
	/** Everything seen so far, and how far each has moved in a tick at its fastest. */
	private static final Map<UUID, Tracked> known = new HashMap<>();

	/**
	 * One entity being watched.
	 *
	 * <p>{@code fastest} is measured from its actual position between ticks, not from
	 * {@code getDeltaMovement()}. Hypixel drives its props with teleport packets, so their
	 * reported velocity stays flat zero however fast they are really moving — which made
	 * the first version of this report every single thing as motionless.
	 */
	private static final class Tracked {
		String what;
		Vec3 last;
		double fastest;
		boolean isNew;
	}

	private BallDiagnostic() {
	}

	/** Opens the window. Throw a capsule while it is running. */
	public static void start() {
		Minecraft client = Minecraft.getInstance();
		known.clear();
		if (client.level != null) {
			// Recorded as not-new, so only what turns up after this shows in the report.
			for (Entity entity : client.level.entitiesForRendering()) track(entity, false);
		}
		until = System.currentTimeMillis() + WINDOW_MILLIS;
		say("Watching for new entities for %ds — throw a capsule now."
			.formatted(WINDOW_MILLIS / 1000), ChatFormatting.YELLOW);
	}

	public static void tick() {
		if (until == 0) return;

		Minecraft client = Minecraft.getInstance();
		if (client.level != null) {
			for (Entity entity : client.level.entitiesForRendering()) track(entity, true);
		}

		if (System.currentTimeMillis() < until) return;
		until = 0;
		report();
	}

	/** Records an entity, or updates how fast it has been seen to move. */
	private static void track(Entity entity, boolean isNew) {
		Tracked tracked = known.get(entity.getUUID());
		if (tracked == null) {
			tracked = new Tracked();
			tracked.what = describe(entity);
			tracked.isNew = isNew;
			known.put(entity.getUUID(), tracked);
		}
		Vec3 now = entity.position();
		if (tracked.last != null) {
			tracked.fastest = Math.max(tracked.fastest, tracked.last.distanceTo(now));
		}
		tracked.last = now;
	}

	/**
	 * What a thing is, without saying where it is.
	 *
	 * <p>The {@code projectile} flag is the answer being looked for: if a capsule shows up
	 * carrying it, the perch code already works and something else is wrong. If it shows up
	 * without it, that line names what a capsule actually is.
	 */
	private static String describe(Entity entity) {
		String type = entity.getType().toString();
		type = type.substring(type.lastIndexOf('.') + 1);

		String name = entity.hasCustomName()
			? SafariLocation.strip(entity.getCustomName().getString()) : "";

		return "%s%s%s  %.2fx%.2f%s".formatted(
			type,
			name.isEmpty() ? "" : " \"" + name + "\"",
			heldItem(entity),
			entity.getBoundingBox().getXsize(), entity.getBoundingBox().getYsize(),
			entity instanceof Projectile ? "  §aPROJECTILE" : "");
	}

	/**
	 * The item a thing is showing, which is the decisive clue.
	 *
	 * <p>The type alone cannot tell a capsule from scenery — every prop in the Safari is a
	 * zero-size {@code item_display} or {@code armor_stand} of something — but the item's
	 * name says outright what it is. Both are checked because a capsule could be either: a
	 * display of the item, or a marker armour stand wearing it.
	 */
	private static String heldItem(Entity entity) {
		if (entity instanceof Display.ItemDisplay display) {
			Display.ItemDisplay.ItemRenderState state = display.itemRenderState();
			return state == null ? "" : name(state.itemStack());
		}
		if (entity instanceof LivingEntity living) {
			// Whichever slot it is in; a prop wears its item wherever suits the model.
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				String found = name(living.getItemBySlot(slot));
				if (!found.isEmpty()) return found;
			}
		}
		return "";
	}

	private static String name(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return "";
		return " [" + SafariLocation.strip(stack.getHoverName().getString()) + "]";
	}

	private static void report() {
		// Grouped by what a thing is, keeping the fastest any of them was seen to move.
		Map<String, int[]> counts = new LinkedHashMap<>();
		Map<String, Double> fastest = new LinkedHashMap<>();
		for (Tracked tracked : known.values()) {
			if (!tracked.isNew) continue;
			counts.computeIfAbsent(tracked.what, k -> new int[1])[0]++;
			fastest.merge(tracked.what, tracked.fastest, Math::max);
		}

		if (counts.isEmpty()) {
			say("Nothing new appeared. Either no throw landed in the window, or a capsule "
				+ "is not a separate entity at all.", ChatFormatting.RED);
			return;
		}
		// Fastest first: a thrown capsule is the quickest thing in the list by a mile.
		say("Appeared while watching, fastest first:", ChatFormatting.YELLOW);
		counts.entrySet().stream()
			.sorted((a, b) -> Double.compare(fastest.get(b.getKey()), fastest.get(a.getKey())))
			.limit(MAX_REPORTED)
			.forEach(e -> say("  x%-3d %-46s %.2f b/t".formatted(
				e.getValue()[0], e.getKey(), fastest.get(e.getKey())), ChatFormatting.WHITE));
	}

	private static void say(String text, ChatFormatting colour) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui == null) return;
		client.gui.getChat().addClientSystemMessage(
			Component.literal("[Critters] ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(text).withStyle(colour)));
	}
}
