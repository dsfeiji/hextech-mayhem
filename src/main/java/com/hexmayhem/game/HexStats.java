package com.hexmayhem.game;

import com.hexmayhem.net.HexPayloads;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 战绩：击杀 / 死亡 / 助攻 / 对玩家伤害，并定时广播给所有客户端（右上角战绩和 Tab 战绩表）。 */
public final class HexStats {
	public static final String KILLS = "stat:kills";
	public static final String DEATHS = "stat:deaths";
	public static final String ASSISTS = "stat:assists";
	public static final String DAMAGE = "stat:damage";
	/** 死亡前多少刻内造成过伤害算助攻（10 秒）。 */
	private static final int ASSIST_WINDOW = 200;
	private static final int MULTI_KILL_WINDOW = 200;
	/** 击杀 / 助攻玩家获得的经验点（1→7 级共需约 84 点）。 */
	private static final int KILL_XP = 40;
	private static final int ASSIST_XP = 15;
	private static final int MULTI_KILL_PENTA_WINDOW = 600;
	/** 本局是否已经有人拿到一血（开局时重置）。 */
	private static boolean firstBloodTaken;

	/** 受害者 → (攻击者 → 最后一次造成伤害的游戏刻)。 */
	private static final Map<UUID, Map<UUID, Long>> RECENT_ATTACKERS = new HashMap<>();

	static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer victim) {
				onPlayerDeath(victim, source);
			}
		});
	}

	static void clear() {
		RECENT_ATTACKERS.clear();
		firstBloodTaken = false;
	}

	/** 玩家对玩家造成伤害（包括海克斯的附加伤害）。 */
	static void recordDamage(ServerPlayer attacker, ServerPlayer victim, float amount) {
		if (!HexGame.active() || attacker == victim || amount <= 0) {
			return;
		}
		HexData data = HexGame.data(attacker);
		data.set(DAMAGE, data.get(DAMAGE) + Math.round(amount));
		RECENT_ATTACKERS.computeIfAbsent(victim.getUUID(), u -> new HashMap<>())
				.put(attacker.getUUID(), victim.level().getGameTime());
	}

	private static void onPlayerDeath(ServerPlayer victim, DamageSource source) {
		if (!HexGame.active()) {
			return;
		}
		HexRespawn.onDeath(victim);
		MinecraftServer server = victim.server;
		long now = victim.level().getGameTime();
		HexData victimData = HexGame.data(victim);
		victimData.set(DEATHS, victimData.get(DEATHS) + 1);

		Map<UUID, Long> attackers = RECENT_ATTACKERS.remove(victim.getUUID());
		if (attackers == null) {
			attackers = Map.of();
		}
		// 击杀归属：直接击杀者；摔死 / 烧死等情况归给 10 秒内最后伤害他的玩家
		ServerPlayer killer = source.getEntity() instanceof ServerPlayer p && p != victim ? p : null;
		if (killer == null) {
			UUID last = null;
			long lastTime = Long.MIN_VALUE;
			for (Map.Entry<UUID, Long> e : attackers.entrySet()) {
				if (now - e.getValue() <= ASSIST_WINDOW && e.getValue() > lastTime) {
					last = e.getKey();
					lastTime = e.getValue();
				}
			}
			killer = last == null ? null : server.getPlayerList().getPlayer(last);
		}
		if (killer != null) {
			HexData killerData = HexGame.data(killer);
			killerData.set(KILLS, killerData.get(KILLS) + 1);
			killer.giveExperiencePoints(KILL_XP);
		}
		int assists = 0;
		for (Map.Entry<UUID, Long> e : attackers.entrySet()) {
			if (now - e.getValue() > ASSIST_WINDOW || killer != null && e.getKey().equals(killer.getUUID())) {
				continue;
			}
			ServerPlayer helper = server.getPlayerList().getPlayer(e.getKey());
			if (helper != null) {
				HexData helperData = HexGame.data(helper);
				helperData.set(ASSISTS, helperData.get(ASSISTS) + 1);
				helper.giveExperiencePoints(ASSIST_XP);
				assists++;
			}
		}
		announce(server, killer, victim, assists, now);
		broadcast(server);
	}

	/** 击杀播报：计算多杀 / 连杀 / 一血 / 终结，发给所有客户端。 */
	private static void announce(MinecraftServer server, ServerPlayer killer, ServerPlayer victim, int assists, long now) {
		HexRuntime victimRt = HexGame.runtime(victim);
		boolean shutdown = killer != null && victimRt.killStreak >= 3;
		victimRt.killStreak = 0;
		victimRt.multiKill = 0;

		int multi = 0;
		int streak = 0;
		boolean firstBlood = false;
		if (killer != null) {
			HexRuntime rt = HexGame.runtime(killer);
			// 和英雄联盟一样：双杀~四杀要求两次击杀间隔不超过 10 秒，五杀放宽到 30 秒
			long window = rt.multiKill == 4 ? MULTI_KILL_PENTA_WINDOW : MULTI_KILL_WINDOW;
			rt.multiKill = now - rt.lastKillTick <= window && rt.multiKill < 5 ? rt.multiKill + 1 : 1;
			rt.lastKillTick = now;
			rt.killStreak++;
			multi = rt.multiKill;
			streak = rt.killStreak;
			firstBlood = !firstBloodTaken;
			firstBloodTaken = true;
		}

		HexPayloads.KillFeed payload = new HexPayloads.KillFeed(
				killer == null ? null : killer.getUUID(),
				killer == null ? "" : killer.getGameProfile().getName(),
				killer == null ? "" : HexGame.data(killer).role,
				victim.getUUID(), victim.getGameProfile().getName(), HexGame.data(victim).role,
				assists, multi, streak, firstBlood, shutdown);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, HexPayloads.KillFeed.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	/** 把所有在线玩家的战绩和海克斯发给所有客户端。 */
	public static void broadcast(MinecraftServer server) {
		HexGame.State st = HexGame.state(server);
		List<HexPayloads.Scoreboard.Entry> entries = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			HexData data = HexGame.data(player);
			entries.add(new HexPayloads.Scoreboard.Entry(player.getUUID(), player.getGameProfile().getName(), data.role,
					List.copyOf(data.hexes), data.get(KILLS), data.get(DEATHS), data.get(ASSISTS), data.get(DAMAGE),
					player.experienceLevel, (data.get(HexRespawn.RESPAWN_LEFT) + 19) / 20));
		}
		HexPayloads.Scoreboard payload = new HexPayloads.Scoreboard(st.active ? (int) st.ticks : -1, entries);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, HexPayloads.Scoreboard.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	private HexStats() {
	}
}
