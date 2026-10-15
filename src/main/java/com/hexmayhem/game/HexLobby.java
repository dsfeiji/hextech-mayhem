package com.hexmayhem.game;

import com.hexmayhem.net.HexPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 开局前的自定义房间（类似英雄联盟自定义对局）：蓝 / 红两队各 5 个位置，没选位置的玩家在“待定”区。
 * 房主（执行 /hex start 的人）或管理员可以随机分队、把人移出位置、解散房间、开始游戏。
 * 开始时待定的玩家自动补到人少的一方，然后写入原版队伍（/team），对局里的己方 / 敌方判断全部基于原版队伍。
 */
public final class HexLobby {
	public static final int TEAM_SIZE = 5;
	private static final String[] TEAM_IDS = {"hex_blue", "hex_red"};
	private static final String[] TEAM_NAMES = {"蓝色方", "红色方"};
	private static final ChatFormatting[] TEAM_COLORS = {ChatFormatting.BLUE, ChatFormatting.RED};

	private static boolean open;
	private static UUID host;
	private static final UUID[][] seats = new UUID[2][TEAM_SIZE];
	/** 开局前玩家所在的其他原版队伍（记分板名 → 队伍名），结束时放回去。 */
	private static final Map<String, String> previousTeams = new HashMap<>();

	public static boolean isOpen() {
		return open;
	}

	/** /hex start：打开房间；已经打开时只给执行者重新弹出界面。 */
	public static boolean open(MinecraftServer server, ServerPlayer owner) {
		if (HexGame.isActive(server)) {
			if (owner != null) {
				owner.sendSystemMessage(Component.literal("对局正在进行中，请先 /hex stop").withStyle(ChatFormatting.RED));
			}
			return false;
		}
		if (open) {
			if (owner != null) {
				send(owner, true);
			}
			return true;
		}
		open = true;
		host = owner == null ? null : owner.getUUID();
		clearSeats();
		HexGame.broadcast(server, Component.literal("自定义房间已开启：选择蓝色方或红色方的位置，房主点击“开始游戏”开局。按 H 可重新打开房间。")
				.withStyle(ChatFormatting.GOLD));
		broadcast(server, true);
		return true;
	}

	// ------------------------------------------------------------------ 客户端操作

	public static void handle(ServerPlayer player, HexPayloads.LobbyAction action) {
		if (!open) {
			return;
		}
		MinecraftServer server = player.server;
		switch (action.action()) {
			case HexPayloads.LobbyAction.SEAT -> {
				int team = action.team(), slot = action.slot();
				if (team < 0 || team > 1 || slot < 0 || slot >= TEAM_SIZE || seats[team][slot] != null) {
					return;
				}
				unseat(player.getUUID());
				seats[team][slot] = player.getUUID();
			}
			case HexPayloads.LobbyAction.UNSEAT -> unseat(player.getUUID());
			case HexPayloads.LobbyAction.KICK -> {
				if (!canManage(player) || action.target() == null) {
					return;
				}
				unseat(action.target());
			}
			case HexPayloads.LobbyAction.SHUFFLE -> {
				if (!canManage(player)) {
					return;
				}
				shuffle(server);
			}
			case HexPayloads.LobbyAction.DISBAND -> {
				if (!canManage(player)) {
					return;
				}
				open = false;
				host = null;
				clearSeats();
				broadcast(server, false);
				HexGame.broadcast(server, Component.literal("自定义房间已解散。").withStyle(ChatFormatting.GRAY));
				return;
			}
			case HexPayloads.LobbyAction.START -> {
				if (canManage(player)) {
					start(server, player);
				}
				return;
			}
			default -> {
				return;
			}
		}
		broadcast(server, false);
	}

	private static boolean canManage(ServerPlayer player) {
		return player.getUUID().equals(host) || player.hasPermissions(2);
	}

	private static void unseat(UUID id) {
		for (UUID[] team : seats) {
			for (int i = 0; i < TEAM_SIZE; i++) {
				if (id.equals(team[i])) {
					team[i] = null;
				}
			}
		}
	}

	private static void clearSeats() {
		for (UUID[] team : seats) {
			java.util.Arrays.fill(team, null);
		}
	}

	/** 返回玩家所在队伍（0 蓝 / 1 红），没有位置返回 -1。 */
	private static int teamOf(UUID id) {
		for (int t = 0; t < 2; t++) {
			for (UUID seat : seats[t]) {
				if (id.equals(seat)) {
					return t;
				}
			}
		}
		return -1;
	}

	private static void shuffle(MinecraftServer server) {
		List<UUID> players = new ArrayList<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			players.add(p.getUUID());
		}
		Collections.shuffle(players);
		clearSeats();
		for (int i = 0; i < players.size() && i < TEAM_SIZE * 2; i++) {
			seats[i % 2][i / 2] = players.get(i);
		}
	}

	// ------------------------------------------------------------------ 开局 / 结束

	private static void start(MinecraftServer server, ServerPlayer starter) {
		if (HexGame.isActive(server)) {
			return;
		}
		List<List<ServerPlayer>> teams = List.of(new ArrayList<>(), new ArrayList<>());
		List<ServerPlayer> pending = new ArrayList<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			int team = teamOf(p.getUUID());
			(team < 0 ? pending : teams.get(team)).add(p);
		}
		// 待定的玩家补到人少的一方
		Collections.shuffle(pending);
		for (ServerPlayer p : pending) {
			teams.get(teams.get(0).size() <= teams.get(1).size() ? 0 : 1).add(p);
		}
		int total = teams.get(0).size() + teams.get(1).size();
		if (total >= 2 && (teams.get(0).isEmpty() || teams.get(1).isEmpty())) {
			starter.sendSystemMessage(Component.literal("两边都至少需要一名玩家才能开始").withStyle(ChatFormatting.RED));
			return;
		}

		Scoreboard scoreboard = server.getScoreboard();
		previousTeams.clear();
		for (int t = 0; t < 2; t++) {
			PlayerTeam team = createTeam(scoreboard, t);
			for (ServerPlayer p : teams.get(t)) {
				joinTeam(scoreboard, p, team);
			}
		}
		open = false;
		host = null;
		clearSeats();
		broadcast(server, false);
		HexGame.broadcast(server, Component.literal("蓝色方 " + names(teams.get(0))).withStyle(ChatFormatting.BLUE));
		HexGame.broadcast(server, Component.literal("红色方 " + names(teams.get(1))).withStyle(ChatFormatting.RED));
		HexGame.start(server);
	}

	private static String names(List<ServerPlayer> players) {
		return players.isEmpty() ? "（无）" : String.join("、", players.stream().map(p -> p.getGameProfile().getName()).toList());
	}

	private static PlayerTeam createTeam(Scoreboard scoreboard, int index) {
		PlayerTeam team = scoreboard.getPlayerTeam(TEAM_IDS[index]);
		if (team == null) {
			team = scoreboard.addPlayerTeam(TEAM_IDS[index]);
		}
		team.setDisplayName(Component.literal(TEAM_NAMES[index]));
		team.setColor(TEAM_COLORS[index]);
		team.setAllowFriendlyFire(false);
		team.setSeeFriendlyInvisibles(true);
		return team;
	}

	private static void joinTeam(Scoreboard scoreboard, ServerPlayer player, PlayerTeam team) {
		String name = player.getScoreboardName();
		PlayerTeam old = scoreboard.getPlayersTeam(name);
		if (old != null && !isHexTeam(old)) {
			previousTeams.put(name, old.getName());
		}
		scoreboard.addPlayerToTeam(name, team);
	}

	private static boolean isHexTeam(PlayerTeam team) {
		return team.getName().equals(TEAM_IDS[0]) || team.getName().equals(TEAM_IDS[1]);
	}

	/** 对局中途加入、还没有队伍的玩家：分到人少的一方。 */
	static void assignLate(ServerPlayer player) {
		Scoreboard scoreboard = player.server.getScoreboard();
		PlayerTeam current = scoreboard.getPlayersTeam(player.getScoreboardName());
		if (current != null && isHexTeam(current)) {
			return;
		}
		PlayerTeam blue = scoreboard.getPlayerTeam(TEAM_IDS[0]);
		PlayerTeam red = scoreboard.getPlayerTeam(TEAM_IDS[1]);
		if (blue == null || red == null) {
			return;
		}
		int index = online(player.server, blue) <= online(player.server, red) ? 0 : 1;
		joinTeam(scoreboard, player, index == 0 ? blue : red);
		HexGame.broadcast(player.server, Component.literal(player.getGameProfile().getName() + " 加入了" + TEAM_NAMES[index])
				.withStyle(TEAM_COLORS[index]));
	}

	private static int online(MinecraftServer server, PlayerTeam team) {
		int count = 0;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.getTeam() == team) {
				count++;
			}
		}
		return count;
	}

	/** /hex stop：关闭房间、删除对局队伍，把玩家放回开局前的原版队伍。 */
	public static void close(MinecraftServer server) {
		if (open) {
			open = false;
			broadcast(server, false);
		}
		host = null;
		clearSeats();
		Scoreboard scoreboard = server.getScoreboard();
		for (String id : TEAM_IDS) {
			PlayerTeam team = scoreboard.getPlayerTeam(id);
			if (team != null) {
				scoreboard.removePlayerTeam(team);
			}
		}
		previousTeams.forEach((name, teamName) -> {
			PlayerTeam old = scoreboard.getPlayerTeam(teamName);
			if (old != null && scoreboard.getPlayersTeam(name) == null) {
				scoreboard.addPlayerToTeam(name, old);
			}
		});
		previousTeams.clear();
	}

	public static void reset() {
		open = false;
		host = null;
		clearSeats();
		previousTeams.clear();
	}

	// ------------------------------------------------------------------ 进出服务器

	static void onJoin(ServerPlayer player) {
		if (open) {
			send(player, true);
			broadcast(player.server, false);
		}
	}

	static void onLeave(ServerPlayer player) {
		if (!open) {
			return;
		}
		unseat(player.getUUID());
		if (player.getUUID().equals(host)) {
			host = null;
		}
		// 断开事件里玩家还在列表中，下一刻再同步
		HexGame.schedule(1, () -> broadcast(player.server, false));
	}

	// ------------------------------------------------------------------ 同步

	private static void broadcast(MinecraftServer server, boolean popup) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			send(player, popup);
		}
	}

	private static void send(ServerPlayer player, boolean popup) {
		if (!ServerPlayNetworking.canSend(player, HexPayloads.Lobby.TYPE)) {
			return;
		}
		List<HexPayloads.Lobby.Member> members = new ArrayList<>();
		if (open) {
			for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
				int team = -1, slot = -1;
				for (int t = 0; t < 2 && team < 0; t++) {
					for (int i = 0; i < TEAM_SIZE; i++) {
						if (p.getUUID().equals(seats[t][i])) {
							team = t;
							slot = i;
						}
					}
				}
				members.add(new HexPayloads.Lobby.Member(p.getUUID(), p.getGameProfile().getName(), team, slot));
			}
		}
		ServerPlayNetworking.send(player, new HexPayloads.Lobby(open, popup, open && canManage(player), host, members));
	}

	private HexLobby() {
	}
}
