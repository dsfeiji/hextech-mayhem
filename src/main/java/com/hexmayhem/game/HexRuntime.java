package com.hexmayhem.game;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 不需要保存的每玩家临时状态（计数器、冷却、位置历史）。 */
final class HexRuntime {
	record Snapshot(ResourceKey<Level> dimension, Vec3 pos, float health) {
	}

	static final int ELEMENT_FIRE = 0;
	static final int ELEMENT_FROST = 1;
	static final int ELEMENT_THUNDER = 2;

	UUID frostTarget;
	int frostHits;
	int attackCount;
	int wardenCooldown = HexGame.WARDEN_INTERVAL;
	boolean berserkActive;
	double useCarry;
	/** 多杀：上次击杀玩家的游戏刻、当前连续多杀数；连杀：本条命内击杀数。 */
	long lastKillTick = Long.MIN_VALUE / 2;
	int multiKill;
	int killStreak;
	/** 上一刻的经验等级，用于检测升级。 */
	int lastLevel = -1;
	/** 复活：死亡地点（旁观位置）和原版重生点。 */
	ResourceKey<Level> deathDimension;
	Vec3 deathPos;
	ResourceKey<Level> respawnDimension;
	Vec3 respawnPos;
	final ArrayDeque<Snapshot> history = new ArrayDeque<>();
	/** 目标 UUID → 三种元素最近触发的游戏刻。 */
	final Map<UUID, long[]> elements = new HashMap<>();
}
