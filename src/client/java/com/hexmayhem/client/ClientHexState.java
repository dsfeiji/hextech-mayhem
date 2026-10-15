package com.hexmayhem.client;

import com.hexmayhem.net.HexPayloads;

import java.util.List;

/** 客户端缓存的本地玩家海克斯状态（来自服务端 Sync 包）。 */
public final class ClientHexState {
	public static String role = "";
	public static List<String> hexes = List.of();
	public static List<Integer> stacks = List.of();
	public static int offerTier = -1;
	public static List<String> offerIds = List.of();
	/** 当前三选一要阵亡后才能选。 */
	public static boolean offerLocked;
	public static int rerolls;
	public static int pendingOffers;
	/** 下一次发放海克斯需要的经验等级；-1 表示已全部发完。 */
	public static int nextOfferLevel = -1;
	/** 复活倒计时（刻），客户端每刻自减；0 表示存活。 */
	public static int respawnTicks;
	public static int respawnTotal;

	static void apply(HexPayloads.Sync sync) {
		role = sync.role();
		hexes = sync.hexes();
		stacks = sync.stacks();
		offerTier = sync.offerTier();
		offerIds = sync.offerIds();
		offerLocked = sync.offerLocked();
		rerolls = sync.rerolls();
		pendingOffers = sync.pendingOffers();
		nextOfferLevel = sync.nextOfferLevel();
	}

	static void reset() {
		respawnTicks = 0;
		respawnTotal = 0;
		apply(new HexPayloads.Sync("", List.of(), List.of(), -1, List.of(), false, 0, 0, -1, 0));
	}

	public static boolean inGame() {
		return ClientScoreboard.active() && !role.isEmpty();
	}

	public static boolean hasOffer() {
		return offerTier >= 0 && !offerIds.isEmpty();
	}

	/** 有三选一且现在就能选（没被“阵亡后才能选”锁住）。 */
	public static boolean canPick() {
		return hasOffer() && !offerLocked;
	}

	private ClientHexState() {
	}
}
