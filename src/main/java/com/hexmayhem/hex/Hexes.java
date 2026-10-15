package com.hexmayhem.hex;

import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.hexmayhem.hex.Tier.GOLD;
import static com.hexmayhem.hex.Tier.PRISMATIC;
import static com.hexmayhem.hex.Tier.SILVER;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE;

public final class Hexes {
	public static final Map<String, Hex> BY_ID = new LinkedHashMap<>();

	// ---------- 白银 ----------
	public static final Hex IRON_SKIN = reg(new Hex("iron_skin", SILVER, "铁皮", "护甲 +5，盔甲韧性 +2")
			.attr(Attributes.ARMOR, 5, ADD_VALUE)
			.attr(Attributes.ARMOR_TOUGHNESS, 2, ADD_VALUE));
	public static final Hex LIGHT_FEET = reg(new Hex("light_feet", SILVER, "轻装上阵", "移动速度 +12%")
			.attr(Attributes.MOVEMENT_SPEED, 0.12, ADD_MULTIPLIED_BASE));
	public static final Hex VAMPIRE = reg(new Hex("vampire", SILVER, "吸血鬼", "造成伤害的 6% 转化为生命回复"));
	public static final Hex COBWEB = reg(new Hex("cobweb", SILVER, "蛛网缠绕", "攻击有 25% 几率使目标获得 2 秒缓慢 I"));
	public static final Hex SLIME_BOOTS = reg(new Hex("slime_boots", SILVER, "史莱姆之靴", "跳跃高度 +50%，免疫摔落伤害")
			.attr(Attributes.JUMP_STRENGTH, 0.22, ADD_MULTIPLIED_BASE)
			.attr(Attributes.FALL_DAMAGE_MULTIPLIER, -1, ADD_MULTIPLIED_TOTAL));
	public static final Hex GLUTTON = reg(new Hex("glutton", SILVER, "饕餮", "进食速度 +50%，吃完食物后获得 4 秒生命恢复 I"));
	public static final Hex ENDER_APPRENTICE = reg(new Hex("ender_apprentice", SILVER, "末影学徒", "每 20 秒获得 1 颗末影珍珠（最多 4 颗），末影珍珠不再造成伤害"));
	public static final Hex BEDROCK = reg(new Hex("bedrock", SILVER, "磐石", "击退抗性 +50%")
			.attr(Attributes.KNOCKBACK_RESISTANCE, 0.5, ADD_VALUE));
	public static final Hex CRIT_MASTER = reg(new Hex("crit_master", SILVER, "暴击专精", "暴击伤害 +30%（跳劈暴击、满蓄力箭矢）"));
	public static final Hex HASTE = reg(new Hex("haste", SILVER, "急迫", "攻击速度 +15%，拉弓 / 装填速度 +15%")
			.attr(Attributes.ATTACK_SPEED, 0.15, ADD_MULTIPLIED_BASE));

	// ---------- 黄金 ----------
	public static final Hex TANK_ENGINE = reg(new Hex("tank_engine", GOLD, "坦克引擎", "每次击杀：最大生命 +2，体形 +5%（最多 10 层）")
			.stackAttr(Attributes.MAX_HEALTH, 2, ADD_VALUE)
			.stackAttr(Attributes.SCALE, 0.05, ADD_MULTIPLIED_BASE)
			.stacks(10));
	public static final Hex GIANT_SLAYER = reg(new Hex("giant_slayer", GOLD, "巨人杀手", "对最大生命比你高的目标，伤害 +20%"));
	public static final Hex THORNS = reg(new Hex("thorns", GOLD, "荆棘之甲", "受到伤害时，将 20% 反弹给攻击者"));
	public static final Hex UNDYING_TOTEM = reg(new Hex("undying_totem", GOLD, "不死图腾", "每局一次：受到致命伤害时保留 1 点生命，并获得 3 秒抗性提升 II"));
	public static final Hex WIND_MASTER = reg(new Hex("wind_master", GOLD, "风弹大师", "每 8 秒获得 1 个风弹（最多 4 个）"));
	public static final Hex BLAZE_HEART = reg(new Hex("blaze_heart", GOLD, "烈焰之心", "免疫火焰，攻击使目标着火 3 秒"));
	public static final Hex FROST_WALKER = reg(new Hex("frost_walker", GOLD, "冰霜行者", "连续命中同一目标 3 次后，将其冻结 1 秒"));
	public static final Hex BERSERK = reg(new Hex("berserk", GOLD, "狂暴", "生命低于 50% 时，伤害 +20%，移动速度 +10%"));
	public static final Hex HUNTER = reg(new Hex("hunter", GOLD, "猎手", "每次击杀获得 1 层，每层伤害 +3%（最多 10 层）")
			.stacks(10));
	public static final Hex HEAVY_HAMMER = reg(new Hex("heavy_hammer", GOLD, "重锤", "下落时攻击，每下落 1 格伤害 +1（最多 +10）"));

	// ---------- 棱彩 ----------
	public static final Hex GIANT = reg(new Hex("giant", PRISMATIC, "巨人化", "体形 +40%，最大生命 +16，触及距离 +1，移动速度 -5%")
			.attr(Attributes.SCALE, 0.4, ADD_MULTIPLIED_BASE)
			.attr(Attributes.MAX_HEALTH, 16, ADD_VALUE)
			.attr(Attributes.ENTITY_INTERACTION_RANGE, 1, ADD_VALUE)
			.attr(Attributes.BLOCK_INTERACTION_RANGE, 1, ADD_VALUE)
			.attr(Attributes.MOVEMENT_SPEED, -0.05, ADD_MULTIPLIED_BASE));
	public static final Hex SHRINK = reg(new Hex("shrink", PRISMATIC, "缩小术", "体形 -30%，移动速度 +15%，最大生命 -4")
			.attr(Attributes.SCALE, -0.3, ADD_MULTIPLIED_BASE)
			.attr(Attributes.MOVEMENT_SPEED, 0.15, ADD_MULTIPLIED_BASE)
			.attr(Attributes.MAX_HEALTH, -4, ADD_VALUE));
	public static final Hex WARDEN_HEART = reg(new Hex("warden_heart", PRISMATIC, "监守者之心", "每 15 秒自动释放音波尖啸，对周围 6 格敌人造成 6 点无视护甲的伤害并击退"));
	public static final Hex THUNDER = reg(new Hex("thunder", PRISMATIC, "雷霆之怒", "每第 4 次攻击，在目标身上召唤闪电（5 点伤害并点燃）"));
	public static final Hex CREEPER_SOUL = reg(new Hex("creeper_soul", PRISMATIC, "苦力怕之魂", "击杀敌人后，尸体在 1 秒后爆炸，对周围 4 格敌人造成最多 10 点伤害"));
	public static final Hex WITHER = reg(new Hex("wither", PRISMATIC, "凋零之力", "攻击使目标获得 3 秒凋零 II"));
	public static final Hex MULTI_STRIKE = reg(new Hex("multi_strike", PRISMATIC, "多重打击", "每次攻击额外命中目标周围 2 格内的 1 个敌人，造成 50% 伤害"));
	public static final Hex SECOND_LIFE = reg(new Hex("second_life", PRISMATIC, "第二条命", "每局一次：死亡时原地复活，并恢复 50% 生命"));
	public static final Hex TIME_WARP = reg(new Hex("time_warp", PRISMATIC, "时间扭曲", "获得时光沙漏：使用后回到 4 秒前的位置和生命值（冷却 25 秒）"));

	// ---------- 套装 ----------
	public record HexSet(String name, String description, List<Hex> members) {
		public boolean activeFor(Collection<String> owned) {
			return members.stream().allMatch(h -> owned.contains(h.id));
		}
	}

	public static final HexSet GIANT_SET = new HexSet("巨人套", "坦克引擎层数上限提高到 30",
			List.of(TANK_ENGINE, GIANT, IRON_SKIN));
	public static final HexSet ELEMENT_SET = new HexSet("元素套", "5 秒内对同一目标触发着火、冻结和闪电时，额外造成 8 点魔法伤害",
			List.of(BLAZE_HEART, FROST_WALKER, THUNDER));
	public static final HexSet ASSASSIN_SET = new HexSet("刺客套", "击杀后获得 1 颗末影珍珠",
			List.of(LIGHT_FEET, ENDER_APPRENTICE, VAMPIRE));
	public static final List<HexSet> SETS = List.of(GIANT_SET, ELEMENT_SET, ASSASSIN_SET);

	private static Hex reg(Hex hex) {
		BY_ID.put(hex.id, hex);
		return hex;
	}

	public static Hex get(String id) {
		return BY_ID.get(id);
	}

	public static List<Hex> ofTier(Tier tier) {
		return BY_ID.values().stream().filter(h -> h.tier == tier).toList();
	}

	private Hexes() {
	}
}
