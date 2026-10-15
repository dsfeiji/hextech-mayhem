package com.hexmayhem.game;

import com.hexmayhem.HexMayhem;
import com.hexmayhem.hex.Hex;
import com.hexmayhem.hex.Hexes;
import com.hexmayhem.hex.Tier;
import com.hexmayhem.mixin.LivingEntityAccessor;
import com.hexmayhem.net.HexPayloads;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 对局流程：开局、按等级发放海克斯、职业 / 海克斯选择、属性刷新、每刻效果。 */
public final class HexGame {
	public static final int WARDEN_INTERVAL = 15 * 20;
	/** 达到这些经验等级时发放海克斯（1 级 = 开局），品质每次随机（见 randomTier）。 */
	public static final int[] OFFER_LEVELS = {1, 7, 11, 15};
	/** 每升 1 级（相对 1 级）获得的最大生命 / 攻击力。 */
	public static final double HEALTH_PER_LEVEL = 0.5;
	public static final double DAMAGE_PER_LEVEL = 0.1;
	/** 被动经验：每隔多少刻给多少经验点（每 5 秒 3 点，约 2 分半到 7 级、5 分钟到 11 级、8 分半到 15 级）。 */
	private static final int PASSIVE_XP_INTERVAL = 100;
	private static final int PASSIVE_XP_POINTS = 3;
	private static final String OFFERS_GRANTED = "offers_granted";
	private static final int HISTORY_INTERVAL = 5;
	private static final int HISTORY_SIZE = 80 / HISTORY_INTERVAL + 1;

	private static MinecraftServer server;
	/** state(server).active 的缓存，伤害等高频逻辑每次都要判断。 */
	private static boolean active;
	private static final Map<UUID, HexRuntime> RUNTIME = new HashMap<>();
	private static final List<Scheduled> SCHEDULED = new ArrayList<>();

	private record Scheduled(long at, Runnable task) {
	}

	static final class State extends SavedData {
		boolean active;
		long ticks;
		long gameId;
		/** 开局前 keepInventory 的值（-1 表示未记录），结束时恢复。 */
		int savedKeepInventory = -1;
		/** 开局前 doImmediateRespawn 的值（-1 表示未记录），结束时恢复。 */
		int savedImmediateRespawn = -1;

		static State load(CompoundTag tag, HolderLookup.Provider provider) {
			State state = new State();
			state.active = tag.getBoolean("active");
			state.ticks = tag.getLong("ticks");
			state.gameId = tag.getLong("gameId");
			state.savedKeepInventory = tag.contains("savedKeepInventory") ? tag.getInt("savedKeepInventory") : -1;
			state.savedImmediateRespawn = tag.contains("savedImmediateRespawn") ? tag.getInt("savedImmediateRespawn") : -1;
			return state;
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
			tag.putBoolean("active", active);
			tag.putLong("ticks", ticks);
			tag.putLong("gameId", gameId);
			tag.putInt("savedKeepInventory", savedKeepInventory);
			tag.putInt("savedImmediateRespawn", savedImmediateRespawn);
			return tag;
		}
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			active = state(s).active;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			server = null;
			RUNTIME.clear();
			SCHEDULED.clear();
			HexLobby.reset();
		});
		ServerTickEvents.END_SERVER_TICK.register(HexGame::tick);
		HexStats.register();
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
			onJoin(handler.player);
			HexLobby.onJoin(handler.player);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> HexLobby.onLeave(handler.player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			refreshAttributes(newPlayer, false);
			newPlayer.setHealth(newPlayer.getMaxHealth());
			HexRespawn.afterRespawn(newPlayer);
			// 阵亡后正是选择升级海克斯的时机：有可选的就直接打开
			sync(newPlayer, HexRespawn.isDead(newPlayer) && canPick(newPlayer) ? HexPayloads.OPEN_OFFER : HexPayloads.OPEN_NONE);
		});

		ServerPlayNetworking.registerGlobalReceiver(HexPayloads.ChooseRole.TYPE,
				(payload, context) -> chooseRole(context.player(), payload.role()));
		ServerPlayNetworking.registerGlobalReceiver(HexPayloads.PickHex.TYPE,
				(payload, context) -> pick(context.player(), payload.index()));
		ServerPlayNetworking.registerGlobalReceiver(HexPayloads.Reroll.TYPE,
				(payload, context) -> reroll(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(HexPayloads.LobbyAction.TYPE,
				(payload, context) -> HexLobby.handle(context.player(), payload));
	}

	// ------------------------------------------------------------------ 状态访问

	public static HexData data(ServerPlayer player) {
		return player.getAttachedOrCreate(HexMayhem.DATA);
	}

	static HexRuntime runtime(ServerPlayer player) {
		return RUNTIME.computeIfAbsent(player.getUUID(), u -> new HexRuntime());
	}

	static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(
				new SavedData.Factory<>(State::new, State::load, null), HexMayhem.MOD_ID);
	}

	/** 模组总开关：只有对局进行中（/hex start 之后、/hex stop 之前）海克斯的一切效果和界面才生效。 */
	public static boolean active() {
		return active;
	}

	public static boolean isActive(MinecraftServer server) {
		return state(server).active;
	}

	public static boolean has(ServerPlayer player, Hex hex) {
		return active && data(player).has(hex);
	}

	static void schedule(int delayTicks, Runnable task) {
		if (server != null) {
			SCHEDULED.add(new Scheduled(server.getTickCount() + delayTicks, task));
		}
	}

	// ------------------------------------------------------------------ 对局

	public static void start(MinecraftServer server) {
		State st = state(server);
		GameRules.BooleanValue keepInventory = server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
		GameRules.BooleanValue immediateRespawn = server.getGameRules().getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN);
		if (!st.active) {
			st.savedKeepInventory = keepInventory.get() ? 1 : 0;
			st.savedImmediateRespawn = immediateRespawn.get() ? 1 : 0;
		}
		st.active = true;
		active = true;
		st.ticks = 0;
		st.gameId = System.currentTimeMillis();
		st.setDirty();
		keepInventory.set(true, server);
		// 原版改为立即重生，复活时间由 HexRespawn 控制
		immediateRespawn.set(true, server);
		RUNTIME.clear();
		HexStats.clear();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			preparePlayer(player, st.gameId);
		}
		// 先广播“对局已开启”，客户端打开开关后才会显示职业选择等界面
		HexStats.broadcast(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sync(player, HexPayloads.OPEN_ROLE);
		}
		broadcast(server, Component.literal("海克斯大乱斗开始！请选择你的职业。").withStyle(ChatFormatting.GOLD));
	}

	public static void stop(MinecraftServer server) {
		HexLobby.close(server);
		State st = state(server);
		st.active = false;
		active = false;
		if (st.savedKeepInventory >= 0) {
			server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(st.savedKeepInventory == 1, server);
			st.savedKeepInventory = -1;
		}
		if (st.savedImmediateRespawn >= 0) {
			server.getGameRules().getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(st.savedImmediateRespawn == 1, server);
			st.savedImmediateRespawn = -1;
		}
		st.setDirty();
		SCHEDULED.clear();
		HexStats.clear();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (HexRespawn.isDead(player)) {
				HexRespawn.revive(player);
			}
			resetPlayer(player);
			removeModItems(player);
		}
		HexStats.broadcast(server);
		broadcast(server, Component.literal("海克斯大乱斗已结束。").withStyle(ChatFormatting.GOLD));
	}

	private static void preparePlayer(ServerPlayer player, long gameId) {
		if (HexRespawn.isDead(player)) {
			// 上一局阵亡中（旁观）就开了新局：先变回生存
			player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		}
		data(player).reset(gameId);
		RUNTIME.remove(player.getUUID());
		player.getInventory().clearContent();
		player.removeAllEffects();
		// 等级从 1 级开始，1 级时立刻获得第一次（白银）海克斯
		player.setExperienceLevels(1);
		player.setExperiencePoints(0);
		refreshAttributes(player, false);
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(5);
	}

	/** 清空某个玩家的职业与海克斯（不动背包）。 */
	public static void resetPlayer(ServerPlayer player) {
		data(player).reset(0);
		RUNTIME.remove(player.getUUID());
		refreshAttributes(player, false);
		sync(player, HexPayloads.OPEN_NONE);
	}

	private static void onJoin(ServerPlayer player) {
		State st = state(player.server);
		HexData data = data(player);
		if (st.active && data.gameId != st.gameId) {
			// 中途加入：分到人少的一方，从 1 级开始，海克斯由 tickPlayer 按等级发放
			HexLobby.assignLate(player);
			preparePlayer(player, st.gameId);
			HexStats.broadcast(player.server);
			sync(player, HexPayloads.OPEN_ROLE);
			return;
		}
		if (!st.active) {
			// 关闭状态下不保留任何海克斯数据（例如对局进行时下线、结束后才上线的玩家）
			if (HexRespawn.isDead(player)) {
				HexRespawn.revive(player);
			}
			if (!data.hexes.isEmpty() || data.role() != null) {
				removeModItems(player);
			}
			data.reset(0);
		}
		refreshAttributes(player, false);
		HexStats.broadcast(player.server);
		sync(player, st.active && data.role() == null ? HexPayloads.OPEN_ROLE : HexPayloads.OPEN_NONE);
	}

	/** 收回本模组发放的物品（时光沙漏）。 */
	private static void removeModItems(ServerPlayer player) {
		player.getInventory().clearOrCountMatchingItems(stack -> stack.is(HexMayhem.TIME_HOURGLASS), -1,
				player.inventoryMenu.getCraftSlots());
	}

	private static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		if (!SCHEDULED.isEmpty()) {
			List<Scheduled> due = new ArrayList<>();
			for (Iterator<Scheduled> it = SCHEDULED.iterator(); it.hasNext(); ) {
				Scheduled s = it.next();
				if (s.at <= now) {
					due.add(s);
					it.remove();
				}
			}
			due.forEach(s -> s.task.run());
		}

		State st = state(server);
		if (active && now % 20 == 0) {
			HexStats.broadcast(server);
		}
		if (st.active) {
			st.ticks++;
			st.setDirty();
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickPlayer(player, now);
		}
	}

	/** 下一次发放海克斯需要的等级；全部发完返回 -1。 */
	private static int nextOfferLevel(ServerPlayer player) {
		int granted = data(player).get(OFFERS_GRANTED);
		return active && granted < OFFER_LEVELS.length ? OFFER_LEVELS[granted] : -1;
	}

	/** 按经验等级发放海克斯。阵亡期间也会调用（例如死后靠助攻升级）。 */
	private static void grantLevelOffers(ServerPlayer player, HexData data) {
		while (data.get(OFFERS_GRANTED) < OFFER_LEVELS.length && player.experienceLevel >= OFFER_LEVELS[data.get(OFFERS_GRANTED)]) {
			int index = data.get(OFFERS_GRANTED);
			data.set(OFFERS_GRANTED, index + 1);
			// 开局（1 级）的海克斯立刻可选；之后升级获得的要阵亡一次才解锁，阵亡中获得的直接解锁
			grantOffer(player, null, true, index > 0 && !HexRespawn.isDead(player));
		}
	}

	/** 按经验等级发放海克斯，并在等级变化时刷新等级属性。由 tickPlayer 调用。 */
	private static void tickLevel(ServerPlayer player, HexData data, HexRuntime rt, long now) {
		int level = player.experienceLevel;
		grantLevelOffers(player, data);
		if (level != rt.lastLevel) {
			boolean levelUp = rt.lastLevel >= 1 && level > rt.lastLevel;
			rt.lastLevel = level;
			refreshAttributes(player, true);
			sync(player, HexPayloads.OPEN_NONE);
			if (levelUp) {
				int bonusLevels = Math.max(0, level - 1);
				player.displayClientMessage(Component.literal(String.format("升到 %d 级！最大生命 +%.1f，攻击力 +%.1f",
						level, bonusLevels * HEALTH_PER_LEVEL, bonusLevels * DAMAGE_PER_LEVEL)).withStyle(ChatFormatting.AQUA), true);
			}
		}
		if (data.role() != null && player.isAlive() && now % PASSIVE_XP_INTERVAL == 0) {
			player.giveExperiencePoints(PASSIVE_XP_POINTS);
		}
	}

	// ------------------------------------------------------------------ 海克斯发放与选择

	/** 给玩家加入一次立刻可选的三选一；tier 为 null 时随机品质。 */
	public static void grantOffer(ServerPlayer player, Tier tier, boolean notify) {
		grantOffer(player, tier, notify, false);
	}

	/** 给玩家加入一次三选一；tier 为 null 时随机品质；deathOnly 为 true 时只有阵亡期间才能选。 */
	public static void grantOffer(ServerPlayer player, Tier tier, boolean notify, boolean deathOnly) {
		HexData data = data(player);
		if (tier == null) {
			tier = randomTier(player.getRandom());
		}
		List<String> ids = roll(data, tier, Set.of(), player.getRandom());
		if (ids.isEmpty()) {
			return;
		}
		data.offers.add(new HexData.Offer(tier.ordinal(), ids, deathOnly));
		boolean openNow = data.offers.size() == 1 && canPick(player);
		sync(player, openNow ? HexPayloads.OPEN_OFFER : HexPayloads.OPEN_NONE);
		if (notify) {
			player.sendSystemMessage(Component.literal("获得一次 ")
					.append(Component.literal("【" + tier.displayName + "】").withStyle(tier.format))
					.append(deathOnly ?" 海克斯选择，阵亡后按 H 选择" : " 海克斯选择，按 H 打开"));
			player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
		}
	}

	/** 当前三选一是否还没解锁（要阵亡一次）。 */
	private static boolean offerLocked(ServerPlayer player) {
		HexData data = data(player);
		return !data.offers.isEmpty() && data.offers.get(0).deathOnly();
	}

	/** 玩家现在能否选择当前的三选一。 */
	static boolean canPick(ServerPlayer player) {
		HexData data = data(player);
		return active && !data.offers.isEmpty() && data.role() != null && !offerLocked(player);
	}

	private static Tier randomTier(RandomSource random) {
		int roll = random.nextInt(100);
		return roll < 20 ? Tier.SILVER : roll < 70 ? Tier.GOLD : Tier.PRISMATIC;
	}

	private static List<String> roll(HexData data, Tier tier, Set<String> exclude, RandomSource random) {
		Set<String> taken = new HashSet<>(data.hexes);
		taken.addAll(exclude);
		for (HexData.Offer offer : data.offers) {
			taken.addAll(offer.ids());
		}
		List<String> pool = new ArrayList<>();
		for (Hex hex : Hexes.ofTier(tier)) {
			if (!taken.contains(hex.id)) {
				pool.add(hex.id);
			}
		}
		net.minecraft.Util.shuffle(pool, random);
		return List.copyOf(pool.subList(0, Math.min(3, pool.size())));
	}

	private static void chooseRole(ServerPlayer player, String roleId) {
		Role role = Role.byId(roleId);
		if (!active || role == null || data(player).role() != null) {
			return;
		}
		setRole(player, role, true);
	}

	public static void setRole(ServerPlayer player, Role role, boolean giveKit) {
		HexData data = data(player);
		data.role = role.id;
		if (giveKit) {
			role.giveKit(player);
		}
		refreshAttributes(player, false);
		player.setHealth(player.getMaxHealth());
		player.sendSystemMessage(Component.literal("你的职业：").append(
				Component.literal(role.displayName).withColor(role.color)));
		sync(player, canPick(player) ? HexPayloads.OPEN_OFFER : HexPayloads.OPEN_NONE);
	}

	private static void pick(ServerPlayer player, int index) {
		HexData data = data(player);
		if (!canPick(player)) {
			return;
		}
		HexData.Offer offer = data.offers.get(0);
		if (index < 0 || index >= offer.ids().size()) {
			return;
		}
		Hex hex = Hexes.get(offer.ids().get(index));
		data.offers.remove(0);
		if (hex != null) {
			addHex(player, hex);
		}
		sync(player, canPick(player) ? HexPayloads.OPEN_OFFER : HexPayloads.OPEN_NONE);
	}

	private static void reroll(ServerPlayer player) {
		HexData data = data(player);
		if (!canPick(player) || data.rerolls <= 0) {
			return;
		}
		HexData.Offer current = data.offers.remove(0);
		Tier tier = Tier.byIndex(current.tier());
		List<String> ids = new ArrayList<>(roll(data, tier, new HashSet<>(current.ids()), player.getRandom()));
		if (ids.size() < 3) {
			// 池子不够时允许刷回原来的选项
			for (String id : roll(data, tier, new HashSet<>(ids), player.getRandom())) {
				if (ids.size() >= 3) {
					break;
				}
				if (!ids.contains(id)) {
					ids.add(id);
				}
			}
		}
		data.offers.add(0, new HexData.Offer(current.tier(), List.copyOf(ids), current.deathOnly()));
		data.rerolls--;
		sync(player, HexPayloads.OPEN_OFFER);
	}

	public static boolean addHex(ServerPlayer player, Hex hex) {
		HexData data = data(player);
		if (data.has(hex)) {
			return false;
		}
		List<Hexes.HexSet> before = Hexes.SETS.stream().filter(s -> s.activeFor(data.hexes)).toList();
		data.hexes.add(hex.id);
		if (hex == Hexes.TIME_WARP && player.getInventory().countItem(HexMayhem.TIME_HOURGLASS) == 0) {
			Role.give(player, new ItemStack(HexMayhem.TIME_HOURGLASS));
		}
		refreshAttributes(player, true);
		player.sendSystemMessage(Component.literal("获得海克斯 ")
				.append(Component.literal("【" + hex.name + "】").withStyle(hex.tier.format))
				.append(Component.literal("：" + hex.description).withStyle(ChatFormatting.GRAY)));
		for (Hexes.HexSet set : Hexes.SETS) {
			if (!before.contains(set) && set.activeFor(data.hexes)) {
				player.sendSystemMessage(Component.literal("套装激活【" + set.name() + "】：" + set.description())
						.withStyle(ChatFormatting.GREEN));
			}
		}
		player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.2F);
		return true;
	}

	public static boolean removeHex(ServerPlayer player, Hex hex) {
		HexData data = data(player);
		if (!data.hexes.remove(hex.id)) {
			return false;
		}
		data.counters.remove(hex.stackKey());
		refreshAttributes(player, false);
		sync(player, HexPayloads.OPEN_NONE);
		return true;
	}

	/** 增加叠层；返回是否成功（已满层则失败）。 */
	static boolean addStack(ServerPlayer player, Hex hex, int max) {
		HexData data = data(player);
		int stacks = data.stacks(hex);
		if (stacks >= max) {
			return false;
		}
		data.set(hex.stackKey(), stacks + 1);
		return true;
	}

	// ------------------------------------------------------------------ 属性

	public static void refreshAttributes(ServerPlayer player, boolean healGain) {
		float oldMax = player.getMaxHealth();
		for (Holder.Reference<Attribute> attribute : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
			AttributeInstance instance = player.getAttribute(attribute);
			if (instance == null) {
				continue;
			}
			for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
				if (modifier.id().getNamespace().equals(HexMayhem.MOD_ID)) {
					instance.removeModifier(modifier.id());
				}
			}
		}

		HexData data = data(player);
		Role role = data.role();
		if (role != null) {
			apply(player, "role/" + role.id, role.mods(), 1);
		}
		for (String id : data.hexes) {
			Hex hex = Hexes.get(id);
			if (hex != null) {
				apply(player, "hex/" + id, hex.mods, data.stacks(hex));
			}
		}
		// 等级成长：每升 1 级（相对 1 级）少量最大生命和攻击力，只在对局中生效
		if (active && data.gameId != 0 && player.experienceLevel > 1) {
			int bonusLevels = player.experienceLevel - 1;
			apply(player, "level", List.of(
					new Hex.Mod(Attributes.MAX_HEALTH, HEALTH_PER_LEVEL, AttributeModifier.Operation.ADD_VALUE, true),
					new Hex.Mod(Attributes.ATTACK_DAMAGE, DAMAGE_PER_LEVEL, AttributeModifier.Operation.ADD_VALUE, true)),
					bonusLevels);
		}
		if (data.has(Hexes.BERSERK) && runtime(player).berserkActive) {
			AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
			speed.addPermanentModifier(new AttributeModifier(HexMayhem.id("hex/berserk/active"), 0.1,
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}

		float newMax = player.getMaxHealth();
		if (healGain && newMax > oldMax) {
			player.heal(newMax - oldMax);
		}
		if (player.getHealth() > newMax) {
			player.setHealth(newMax);
		}
	}

	private static void apply(ServerPlayer player, String prefix, List<Hex.Mod> mods, int stacks) {
		for (int i = 0; i < mods.size(); i++) {
			Hex.Mod mod = mods.get(i);
			double amount = mod.perStack() ? mod.amount() * stacks : mod.amount();
			AttributeInstance instance = player.getAttribute(mod.attribute());
			if (amount == 0 || instance == null) {
				continue;
			}
			instance.addPermanentModifier(new AttributeModifier(HexMayhem.id(prefix + "/" + i), amount, mod.operation()));
		}
	}

	// ------------------------------------------------------------------ 每刻效果

	private static void tickPlayer(ServerPlayer player, long now) {
		if (!active) {
			return;
		}
		HexData data = data(player);
		HexRuntime rt = runtime(player);
		if (player.tickCount % HISTORY_INTERVAL == 0) {
			rt.history.addLast(new HexRuntime.Snapshot(player.level().dimension(), player.position(), player.getHealth()));
			while (rt.history.size() > HISTORY_SIZE) {
				rt.history.removeFirst();
			}
		}
		// 阵亡中：只走复活倒计时，不触发任何海克斯效果
		if (data.gameId != 0 && HexRespawn.tick(player, data)) {
			grantLevelOffers(player, data);
			return;
		}
		if (data.gameId != 0 && !player.isSpectator()) {
			tickLevel(player, data, rt, now);
		}
		if (data.hexes.isEmpty() && data.role() == null || !player.isAlive() || player.isSpectator()) {
			return;
		}

		if (data.has(Hexes.ENDER_APPRENTICE) && now % 400 == 0) {
			refill(player, Items.ENDER_PEARL, 4);
		}
		if (data.has(Hexes.WIND_MASTER) && now % 160 == 0) {
			refill(player, Items.WIND_CHARGE, 4);
		}
		if (data.has(Hexes.BLAZE_HEART)) {
			MobEffectInstance fire = player.getEffect(MobEffects.FIRE_RESISTANCE);
			if (fire == null || fire.getDuration() < 100) {
				player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 300, 0, true, false, true));
			}
		}
		if (data.has(Hexes.WARDEN_HEART) && --rt.wardenCooldown <= 0) {
			// 附近没有敌人时每秒重试，不浪费冷却
			rt.wardenCooldown = HexCombat.sonicBoom(player) ? WARDEN_INTERVAL : 20;
		}
		if (data.has(Hexes.BERSERK)) {
			boolean low = player.getHealth() < player.getMaxHealth() / 2;
			if (low != rt.berserkActive) {
				rt.berserkActive = low;
				refreshAttributes(player, false);
			}
		}

		// 拉弓 / 进食加速：服务端额外扣减使用时间
		if (player.isUsingItem()) {
			ItemStack using = player.getUseItem();
			double bonus = 0;
			if (using.getItem() instanceof BowItem || using.getItem() instanceof CrossbowItem) {
				Role role = data.role();
				bonus = (role != null ? role.drawSpeedBonus : 0) + (data.has(Hexes.HASTE) ? 0.15 : 0);
			} else if (data.has(Hexes.GLUTTON) && using.has(DataComponents.FOOD)) {
				bonus = 0.5;
			}
			rt.useCarry += bonus;
			while (rt.useCarry >= 1) {
				rt.useCarry -= 1;
				int remaining = player.getUseItemRemainingTicks();
				// 必须保留至少 1 刻，否则原版不会触发“使用完成”
				if (remaining > 1) {
					((LivingEntityAccessor) player).hexmayhem$setUseItemRemaining(remaining - 1);
				}
			}
		} else {
			rt.useCarry = 0;
		}
	}

	private static void refill(ServerPlayer player, Item item, int max) {
		if (player.getInventory().countItem(item) < max) {
			Role.give(player, new ItemStack(item));
		}
	}

	/** 饕餮：吃完食物。由 LivingEntityMixin 调用。 */
	public static void onFoodEaten(ServerPlayer player) {
		if (has(player, Hexes.GLUTTON)) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0));
		}
	}

	/** 时间扭曲：回到约 4 秒前的位置和生命值。 */
	public static boolean useTimeWarp(ServerPlayer player) {
		if (!has(player, Hexes.TIME_WARP)) {
			player.displayClientMessage(Component.literal("你没有【时间扭曲】海克斯").withStyle(ChatFormatting.RED), true);
			return false;
		}
		HexRuntime rt = runtime(player);
		HexRuntime.Snapshot snapshot = rt.history.peekFirst();
		if (snapshot == null || !snapshot.dimension().equals(player.level().dimension())) {
			return false;
		}
		var level = player.serverLevel();
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 40, 0.3, 0.6, 0.3, 0.05);
		player.teleportTo(snapshot.pos().x, snapshot.pos().y, snapshot.pos().z);
		player.resetFallDistance();
		if (snapshot.health() > 0) {
			player.setHealth(Math.min(snapshot.health(), player.getMaxHealth()));
		}
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 40, 0.3, 0.6, 0.3, 0.05);
		level.playSound(null, player.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1F, 0.8F);
		rt.history.clear();
		return true;
	}

	// ------------------------------------------------------------------ 网络

	public static void sync(ServerPlayer player, int open) {
		if (!ServerPlayNetworking.canSend(player, HexPayloads.Sync.TYPE)) {
			return;
		}
		HexData data = data(player);
		List<Integer> stacks = data.hexes.stream().map(id -> data.get("stacks:" + id)).toList();
		HexData.Offer offer = data.offers.isEmpty() ? null : data.offers.get(0);
		ServerPlayNetworking.send(player, new HexPayloads.Sync(data.role, List.copyOf(data.hexes), stacks,
				offer == null ? -1 : offer.tier(), offer == null ? List.of() : offer.ids(), offerLocked(player),
				data.rerolls, data.offers.size(), nextOfferLevel(player), open));
	}

	static void broadcast(MinecraftServer server, Component message) {
		server.getPlayerList().broadcastSystemMessage(message, false);
	}

	private HexGame() {
	}
}
