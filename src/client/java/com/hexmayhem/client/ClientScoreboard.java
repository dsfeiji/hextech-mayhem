package com.hexmayhem.client;

import com.hexmayhem.net.HexPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 客户端缓存的全体战绩（来自服务端 Scoreboard 包）。 */
final class ClientScoreboard {
	static List<HexPayloads.Scoreboard.Entry> entries = List.of();
	/** 对局已进行的刻数，客户端每刻自增；-1 表示没有对局。 */
	static int gameTicks = -1;

	static void apply(HexPayloads.Scoreboard payload) {
		entries = payload.entries();
		boolean wasActive = active();
		gameTicks = payload.gameTicks();
		if (wasActive && !active()) {
			HexMayhemClient.onModDisabled();
		}
	}

	static void reset() {
		entries = List.of();
		gameTicks = -1;
	}

	static boolean active() {
		return gameTicks >= 0;
	}

	static HexPayloads.Scoreboard.Entry self() {
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return null;
		}
		for (HexPayloads.Scoreboard.Entry entry : entries) {
			if (entry.id().equals(player.getUUID())) {
				return entry;
			}
		}
		return null;
	}

	/** 和本地玩家同一个原版队伍（或就是自己）算己方，其余都是敌方。 */
	static boolean isAlly(HexPayloads.Scoreboard.Entry entry) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return false;
		}
		if (entry.id().equals(mc.player.getUUID())) {
			return true;
		}
		PlayerTeam mine = mc.level.getScoreboard().getPlayersTeam(mc.player.getScoreboardName());
		PlayerTeam theirs = mc.level.getScoreboard().getPlayersTeam(entry.name());
		return mine != null && Objects.equals(mine, theirs);
	}

	/** 己方 / 敌方列表，按击杀、助攻、伤害排序。 */
	static List<HexPayloads.Scoreboard.Entry> team(boolean allies) {
		List<HexPayloads.Scoreboard.Entry> list = new ArrayList<>();
		for (HexPayloads.Scoreboard.Entry entry : entries) {
			if (isAlly(entry) == allies) {
				list.add(entry);
			}
		}
		list.sort(Comparator.comparingInt(HexPayloads.Scoreboard.Entry::kills)
				.thenComparingInt(HexPayloads.Scoreboard.Entry::assists)
				.thenComparingInt(HexPayloads.Scoreboard.Entry::damage).reversed());
		return list;
	}

	static int teamKills(boolean allies) {
		int kills = 0;
		for (HexPayloads.Scoreboard.Entry entry : team(allies)) {
			kills += entry.kills();
		}
		return kills;
	}

	static String time() {
		int seconds = Math.max(0, gameTicks) / 20;
		return String.format("%02d:%02d", seconds / 60, seconds % 60);
	}

	private ClientScoreboard() {
	}
}
