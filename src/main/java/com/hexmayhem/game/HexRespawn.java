package com.hexmayhem.game;

import com.hexmayhem.net.HexPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * 复活时间：对局中原版规则改为立即重生（doImmediateRespawn），由本模组限制复活时间——
 * 死亡后变成旁观模式留在死亡地点，倒计时结束后回到重生点并变回生存模式。
 * 复活时间随等级增加：4 秒 + 每级 2.5 秒，最多 50 秒。
 * 升级获得的海克斯要阵亡一次才解锁，解锁后复活了也能继续选（见 HexData.Offer#deathOnly）。
 */
public final class HexRespawn {
	public static final String RESPAWN_LEFT = "respawn_left";
	private static final String RESPAWN_TOTAL = "respawn_total";
	public static final int BASE_SECONDS = 4;
	public static final double SECONDS_PER_LEVEL = 2.5;
	public static final int MAX_SECONDS = 50;

	public static int respawnTicks(int level) {
		double seconds = BASE_SECONDS + SECONDS_PER_LEVEL * (Math.max(1, level) - 1);
		return (int) Math.round(Math.min(MAX_SECONDS, seconds) * 20);
	}

	public static boolean isDead(ServerPlayer player) {
		return HexGame.data(player).get(RESPAWN_LEFT) > 0;
	}

	/** 玩家死亡（AFTER_DEATH，对局中）：开始计时，记录死亡地点。 */
	static void onDeath(ServerPlayer victim) {
		HexData data = HexGame.data(victim);
		int ticks = respawnTicks(victim.experienceLevel);
		data.set(RESPAWN_LEFT, ticks);
		data.set(RESPAWN_TOTAL, ticks);
		data.unlockOffers();
		HexRuntime rt = HexGame.runtime(victim);
		rt.deathDimension = victim.level().dimension();
		rt.deathPos = victim.position();
	}

	/** 原版立即重生之后：记下重生点，然后变成旁观者回到死亡地点观战。 */
	static void afterRespawn(ServerPlayer player) {
		HexData data = HexGame.data(player);
		if (!HexGame.active() || data.get(RESPAWN_LEFT) <= 0) {
			return;
		}
		HexRuntime rt = HexGame.runtime(player);
		rt.respawnDimension = player.level().dimension();
		rt.respawnPos = player.position();
		player.setGameMode(GameType.SPECTATOR);
		if (rt.deathPos != null) {
			ServerLevel level = player.server.getLevel(rt.deathDimension);
			if (level != null) {
				player.teleportTo(level, rt.deathPos.x, rt.deathPos.y + 1.5, rt.deathPos.z, player.getYRot(), player.getXRot());
			}
		}
		send(player, data);
	}

	/** 每刻调用（对局中）。返回 true 表示玩家处于阵亡状态，跳过其他海克斯效果。 */
	static boolean tick(ServerPlayer player, HexData data) {
		int left = data.get(RESPAWN_LEFT);
		if (left <= 0) {
			return false;
		}
		left--;
		data.set(RESPAWN_LEFT, left);
		// 已经重生但还不是旁观者（例如断线重连、被人改了模式）：重新设为旁观
		if (player.isAlive() && !player.isSpectator() && left > 0) {
			player.setGameMode(GameType.SPECTATOR);
		}
		if (left <= 0) {
			revive(player);
		} else if (left % 20 == 0) {
			send(player, data);
		}
		return true;
	}

	/** 复活：回到重生点，变回生存模式，回满状态。 */
	public static void revive(ServerPlayer player) {
		HexData data = HexGame.data(player);
		data.set(RESPAWN_LEFT, 0);
		HexRuntime rt = HexGame.runtime(player);
		ServerLevel level = rt.respawnDimension == null ? null : player.server.getLevel(rt.respawnDimension);
		Vec3 pos = rt.respawnPos;
		if (level == null || pos == null) {
			// 服务器重启等情况丢失了重生点：回到世界出生点
			level = player.server.overworld();
			BlockPos spawn = level.getSharedSpawnPos();
			pos = Vec3.atBottomCenterOf(level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, spawn));
		}
		player.teleportTo(level, pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
		player.setGameMode(GameType.SURVIVAL);
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		player.clearFire();
		player.resetFallDistance();
		rt.deathPos = null;
		rt.respawnPos = null;
		send(player, data);
		player.displayClientMessage(Component.literal("你已复活！").withStyle(ChatFormatting.GREEN), true);
	}

	private static void send(ServerPlayer player, HexData data) {
		if (ServerPlayNetworking.canSend(player, HexPayloads.Respawn.TYPE)) {
			ServerPlayNetworking.send(player, new HexPayloads.Respawn(data.get(RESPAWN_LEFT), data.get(RESPAWN_TOTAL)));
		}
	}

	private HexRespawn() {
	}
}
