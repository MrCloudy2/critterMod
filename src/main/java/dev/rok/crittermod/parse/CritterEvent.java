package dev.rok.crittermod.parse;

import dev.rok.crittermod.data.Critter;

/**
 * A single parsed Critter Safari chat event.
 *
 * @param type      what happened
 * @param critter   the species involved, never {@code null} for catch/attempt events
 * @param catcher   who caught it; {@code null} when {@link #self()} is true
 * @param shards    shards awarded (1 unless the message said {@code Nx})
 * @param sparkling whether this was a SPARKLING variant
 */
public record CritterEvent(Type type, Critter critter, String catcher, int shards, boolean sparkling) {

	public enum Type {
		/** {@code CAPTURE! You caught a Foxtrot ...} — a catch credited to the local player. */
		OWN_CATCH,
		/** {@code LOOT SHARE! ... from <player> catching a Foxtrot!} — a partymate's catch. */
		SHARED_CATCH,
		/** {@code You threw a Critter Capsule at the Foxtrot!} */
		ATTEMPT,
		/** {@code The Foxtrot escaped your Critter Capsule!} or dodged it. */
		FAILED,
		/** The local player entered the Critter Safari — starts a new session. */
		ENTERED_SAFARI
	}

	/** True when this catch belongs to the local player rather than a partymate. */
	public boolean self() {
		return type == Type.OWN_CATCH;
	}

	/** True for the two event types that add to a catch tally. */
	public boolean isCatch() {
		return type == Type.OWN_CATCH || type == Type.SHARED_CATCH;
	}
}
