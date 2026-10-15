package com.hexmayhem.game;

import com.hexmayhem.hex.Hex;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 每个玩家的持久数据：职业、已选海克斯、计数器、待选海克斯。 */
public final class HexData {
	/** deathOnly：还没解锁，要阵亡一次才能选；阵亡后解锁，复活后也能继续选。 */
	public record Offer(int tier, List<String> ids, boolean deathOnly) {
		static final Codec<Offer> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("tier").forGetter(Offer::tier),
				Codec.STRING.listOf().fieldOf("ids").forGetter(Offer::ids),
				Codec.BOOL.optionalFieldOf("deathOnly", false).forGetter(Offer::deathOnly)
		).apply(i, Offer::new));
	}

	public static final int REROLLS_PER_GAME = 2;

	public static final Codec<HexData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("role", "").forGetter(d -> d.role),
			Codec.STRING.listOf().optionalFieldOf("hexes", List.of()).forGetter(d -> d.hexes),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("counters", Map.of()).forGetter(d -> d.counters),
			Offer.CODEC.listOf().optionalFieldOf("offers", List.of()).forGetter(d -> d.offers),
			Codec.INT.optionalFieldOf("rerolls", REROLLS_PER_GAME).forGetter(d -> d.rerolls),
			Codec.LONG.optionalFieldOf("game", 0L).forGetter(d -> d.gameId)
	).apply(i, HexData::new));

	public String role;
	public final List<String> hexes;
	public final Map<String, Integer> counters;
	public final List<Offer> offers;
	public int rerolls;
	public long gameId;

	public HexData() {
		this("", List.of(), Map.of(), List.of(), REROLLS_PER_GAME, 0L);
	}

	private HexData(String role, List<String> hexes, Map<String, Integer> counters, List<Offer> offers, int rerolls, long gameId) {
		this.role = role;
		this.hexes = new ArrayList<>(hexes);
		this.counters = new HashMap<>(counters);
		this.offers = new ArrayList<>(offers);
		this.rerolls = rerolls;
		this.gameId = gameId;
	}

	public void reset(long gameId) {
		role = "";
		hexes.clear();
		counters.clear();
		offers.clear();
		rerolls = REROLLS_PER_GAME;
		this.gameId = gameId;
	}

	/** 阵亡时调用：解锁所有待选的海克斯。 */
	public void unlockOffers() {
		offers.replaceAll(o -> o.deathOnly() ? new Offer(o.tier(), o.ids(), false) : o);
	}

	public Role role() {
		return Role.byId(role);
	}

	public boolean has(Hex hex) {
		return hexes.contains(hex.id);
	}

	public int get(String key) {
		return counters.getOrDefault(key, 0);
	}

	public void set(String key, int value) {
		counters.put(key, value);
	}

	public int stacks(Hex hex) {
		return get(hex.stackKey());
	}
}
