package com.hexmayhem.game;

import com.hexmayhem.hex.Hexes;
import com.hexmayhem.net.HexPayloads;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** 战斗相关的海克斯：伤害加成、命中效果、受击反伤、免死、击杀叠层。 */
public final class HexCombat {
	/** 正在结算海克斯造成的附加伤害时为 true，防止附加伤害再次触发命中效果（无限连锁）。 */
	private static boolean secondary;

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(HexCombat::allowDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register(HexCombat::allowDeath);
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, killer, killed) -> {
			if (killer instanceof ServerPlayer player) {
				onKill(player, killed);
			}
		});
	}

	/** 伤害来源是否算作“攻击”：近战命中或投射物命中。返回攻击者，否则 null。 */
	private static ServerPlayer attacker(DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			return null;
		}
		Entity direct = source.getDirectEntity();
		if (direct == player && source.is(DamageTypes.PLAYER_ATTACK)) {
			return player;
		}
		if (direct instanceof Projectile && source.is(DamageTypeTags.IS_PROJECTILE)) {
			return player;
		}
		return null;
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		// 末影学徒：末影珍珠的伤害是在重置摔落距离之后结算的，真正的摔落伤害此时摔落距离仍大于 0
		return !(entity instanceof ServerPlayer player && source.is(DamageTypes.FALL)
				&& player.fallDistance == 0 && HexGame.has(player, Hexes.ENDER_APPRENTICE));
	}

	/** LivingEntity.hurt 开头调用：修改伤害数值。 */
	public static float modifyIncoming(LivingEntity target, DamageSource source, float amount) {
		if (secondary || !HexGame.active()) {
			return amount;
		}
		ServerPlayer player = attacker(source);
		if (player == null || player == target) {
			return amount;
		}
		HexData data = HexGame.data(player);
		float multiplier = 1;
		float flat = 0;
		if (data.has(Hexes.GIANT_SLAYER) && target.getMaxHealth() > player.getMaxHealth()) {
			multiplier += 0.2F;
		}
		if (data.has(Hexes.BERSERK) && player.getHealth() < player.getMaxHealth() / 2) {
			multiplier += 0.2F;
		}
		if (data.has(Hexes.HUNTER)) {
			multiplier += 0.03F * data.stacks(Hexes.HUNTER);
		}
		if (data.has(Hexes.CRIT_MASTER) && source.getDirectEntity() instanceof AbstractArrow arrow && arrow.isCritArrow()) {
			multiplier += 0.3F; // 近战暴击在 PlayerMixin 里处理
		}
		if (data.has(Hexes.HEAVY_HAMMER) && player.fallDistance > 1) {
			flat += Math.min(10, (int) player.fallDistance);
		}
		return amount * multiplier + flat;
	}

	/** LivingEntity.hurt 结束时调用：命中 / 受击效果。 */
	public static void afterHurt(LivingEntity target, DamageSource source, float amount, boolean applied) {
		if (!HexGame.active()) {
			return;
		}
		// 战绩：对玩家伤害和助攻判定也统计海克斯附加伤害，所以放在 secondary 判断之前
		if (applied && target instanceof ServerPlayer victim && source.getEntity() instanceof ServerPlayer dealer) {
			HexStats.recordDamage(dealer, victim, amount);
		}
		if (secondary || !applied || amount <= 0 || !(target.level() instanceof ServerLevel level)) {
			return;
		}

		ServerPlayer player = attacker(source);
		if (player != null && player != target) {
			onHit(player, target, amount, level);
		}

		if (target instanceof ServerPlayer defender && source.getEntity() instanceof LivingEntity enemy
				&& enemy != defender && HexGame.has(defender, Hexes.THORNS)) {
			dealSecondary(enemy, level.damageSources().thorns(defender), amount * 0.2F);
		}
	}

	private static void onHit(ServerPlayer player, LivingEntity target, float amount, ServerLevel level) {
		HexData data = HexGame.data(player);
		HexRuntime rt = HexGame.runtime(player);

		if (data.has(Hexes.VAMPIRE)) {
			player.heal(amount * 0.06F);
		}
		if (data.has(Hexes.COBWEB) && player.getRandom().nextFloat() < 0.25F) {
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), player);
		}
		if (data.has(Hexes.WITHER)) {
			target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1), player);
		}
		if (data.has(Hexes.BLAZE_HEART)) {
			target.igniteForSeconds(3);
			markElement(player, rt, target, HexRuntime.ELEMENT_FIRE);
		}
		if (data.has(Hexes.FROST_WALKER)) {
			if (target.getUUID().equals(rt.frostTarget)) {
				rt.frostHits++;
			} else {
				rt.frostTarget = target.getUUID();
				rt.frostHits = 1;
			}
			if (rt.frostHits >= 3) {
				rt.frostHits = 0;
				freeze(target, level);
				markElement(player, rt, target, HexRuntime.ELEMENT_FROST);
			}
		}
		if (data.has(Hexes.THUNDER) && ++rt.attackCount % 4 == 0) {
			lightning(player, target, level);
			markElement(player, rt, target, HexRuntime.ELEMENT_THUNDER);
		}
		if (data.has(Hexes.MULTI_STRIKE)) {
			List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(2),
					e -> e != target && isEnemy(player, e));
			nearby.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(target))).ifPresent(extra -> {
				dealSecondary(extra, level.damageSources().playerAttack(player), amount * 0.5F);
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, extra.getX(), extra.getY(0.5), extra.getZ(), 1, 0, 0, 0, 0);
			});
		}
	}

	// ------------------------------------------------------------------ 效果实现

	private static void freeze(LivingEntity target, ServerLevel level) {
		target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + 20));
		target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 6));
		level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY(0.5), target.getZ(), 30, 0.4, 0.6, 0.4, 0.02);
		level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1F, 1F);
	}

	private static void lightning(ServerPlayer player, LivingEntity target, ServerLevel level) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt != null) {
			// 只做视觉效果，伤害手动结算，避免劈到身边的自己和队友
			bolt.moveTo(Vec3.atBottomCenterOf(target.blockPosition()));
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		dealSecondary(target, source(level, DamageTypes.LIGHTNING_BOLT, player), 5);
		target.igniteForSeconds(3);
	}

	/** 监守者之心：对周围敌人释放音波。没有敌人时返回 false。 */
	static boolean sonicBoom(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(6),
				e -> isEnemy(player, e) && e.distanceToSqr(player) <= 36);
		if (targets.isEmpty()) {
			return false;
		}
		Vec3 from = player.position().add(0, player.getBbHeight() * 0.6, 0);
		for (LivingEntity target : targets) {
			Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
			Vec3 step = to.subtract(from);
			int steps = Math.max(1, (int) (step.length() * 1.5));
			for (int i = 1; i <= steps; i++) {
				Vec3 p = from.add(step.scale(i / (double) steps));
				level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0);
			}
			dealSecondary(target, level.damageSources().sonicBoom(player), 6);
			target.knockback(1.2, player.getX() - target.getX(), player.getZ() - target.getZ());
			target.hurtMarked = true;
		}
		level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5F, 1F);
		return true;
	}

	private static void creeperBlast(ServerPlayer player, ServerLevel level, Vec3 pos) {
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 0.5, pos.z, 1, 0, 0, 0, 0);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2F, 1F);
		List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
				new net.minecraft.world.phys.AABB(pos, pos).inflate(4), e -> isEnemy(player, e));
		for (LivingEntity target : targets) {
			double distance = target.position().distanceTo(pos);
			if (distance > 4) {
				continue;
			}
			float damage = (float) Math.max(1, 10 * (1 - distance / 4));
			dealSecondary(target, level.damageSources().explosion(player, player), damage);
			target.knockback(0.8, pos.x - target.getX(), pos.z - target.getZ());
			target.hurtMarked = true;
		}
	}

	private static void markElement(ServerPlayer player, HexRuntime rt, LivingEntity target, int element) {
		if (!Hexes.ELEMENT_SET.activeFor(HexGame.data(player).hexes)) {
			return;
		}
		long now = target.level().getGameTime();
		long[] times = rt.elements.computeIfAbsent(target.getUUID(), u -> new long[]{-1000, -1000, -1000});
		times[element] = now;
		for (long t : times) {
			if (now - t > 100) {
				return;
			}
		}
		rt.elements.remove(target.getUUID());
		ServerLevel level = (ServerLevel) target.level();
		dealSecondary(target, level.damageSources().indirectMagic(player, player), 8);
		level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY(0.5), target.getZ(), 40, 0.4, 0.6, 0.4, 0.15);
		level.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.8F, 1.5F);
		rt.elements.keySet().removeIf(id -> level.getEntity(id) == null);
	}

	/** 结算附加伤害：不触发命中效果，并临时忽略无敌帧，否则紧跟在普通攻击后的附加伤害会被吞掉。 */
	private static void dealSecondary(LivingEntity target, DamageSource source, float amount) {
		if (amount <= 0 || !target.isAlive()) {
			return;
		}
		int invulnerable = target.invulnerableTime;
		secondary = true;
		try {
			target.invulnerableTime = 0;
			target.hurt(source, amount);
		} finally {
			secondary = false;
			target.invulnerableTime = Math.max(invulnerable, target.invulnerableTime);
		}
	}

	private static DamageSource source(ServerLevel level, ResourceKey<DamageType> type, Entity attacker) {
		return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), attacker);
	}

	/** 范围效果的敌人：其他非队友玩家，或敌对生物。 */
	static boolean isEnemy(ServerPlayer player, LivingEntity entity) {
		if (entity == player || !entity.isAlive() || entity instanceof ArmorStand) {
			return false;
		}
		if (entity instanceof Player other) {
			return !other.isSpectator() && !other.isCreative() && !player.isAlliedTo(other);
		}
		return entity instanceof Enemy;
	}

	// ------------------------------------------------------------------ 免死 / 击杀

	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!HexGame.active() || !(entity instanceof ServerPlayer player)) {
			return true;
		}
		HexData data = HexGame.data(player);
		if (data.has(Hexes.UNDYING_TOTEM) && data.get("used:undying_totem") == 0) {
			data.set("used:undying_totem", 1);
			player.setHealth(1);
			player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 1));
			player.level().broadcastEntityEvent(player, (byte) 35);
			player.sendSystemMessage(Component.literal("【不死图腾】救了你一命！").withStyle(ChatFormatting.GOLD));
			return false;
		}
		if (data.has(Hexes.SECOND_LIFE) && data.get("used:second_life") == 0) {
			data.set("used:second_life", 1);
			player.setHealth(player.getMaxHealth() * 0.5F);
			player.clearFire();
			player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 4));
			player.level().broadcastEntityEvent(player, (byte) 35);
			player.sendSystemMessage(Component.literal("【第二条命】你原地复活了！").withStyle(ChatFormatting.LIGHT_PURPLE));
			return false;
		}
		return true;
	}

	private static void onKill(ServerPlayer player, LivingEntity killed) {
		if (!HexGame.active() || killed instanceof ArmorStand || killed == player) {
			return;
		}
		HexData data = HexGame.data(player);
		boolean changed = false;

		if (data.has(Hexes.TANK_ENGINE)) {
			int max = Hexes.GIANT_SET.activeFor(data.hexes) ? 30 : Hexes.TANK_ENGINE.maxStacks;
			if (HexGame.addStack(player, Hexes.TANK_ENGINE, max)) {
				HexGame.refreshAttributes(player, true);
				player.displayClientMessage(Component.literal("坦克引擎 " + data.stacks(Hexes.TANK_ENGINE) + "/" + max)
						.withStyle(ChatFormatting.GOLD), true);
				changed = true;
			}
		}
		if (data.has(Hexes.HUNTER) && HexGame.addStack(player, Hexes.HUNTER, Hexes.HUNTER.maxStacks)) {
			player.displayClientMessage(Component.literal("猎手 " + data.stacks(Hexes.HUNTER) + "/" + Hexes.HUNTER.maxStacks)
					.withStyle(ChatFormatting.GOLD), true);
			changed = true;
		}
		if (data.has(Hexes.CREEPER_SOUL)) {
			ServerLevel level = player.serverLevel();
			Vec3 pos = killed.position();
			UUID id = player.getUUID();
			level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y + 0.5, pos.z, 20, 0.3, 0.3, 0.3, 0.02);
			level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.CREEPER_PRIMED, SoundSource.PLAYERS, 1F, 1F);
			HexGame.schedule(20, () -> {
				if (level.getPlayerByUUID(id) instanceof ServerPlayer owner) {
					creeperBlast(owner, level, pos);
				}
			});
		}
		if (Hexes.ASSASSIN_SET.activeFor(data.hexes)) {
			Role.give(player, new ItemStack(Items.ENDER_PEARL));
		}
		if (changed) {
			HexGame.sync(player, HexPayloads.OPEN_NONE);
		}
	}

	private HexCombat() {
	}
}
