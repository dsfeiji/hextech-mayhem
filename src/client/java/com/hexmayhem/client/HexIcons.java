package com.hexmayhem.client;

import com.hexmayhem.HexMayhem;
import com.hexmayhem.game.Role;
import com.hexmayhem.hex.Hex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * 海克斯 / 职业图标。
 * <p>
 * 优先使用自定义贴图 {@code assets/hexmayhem/textures/hex/<id>.png}（任意正方形尺寸），
 * 没有的话使用下面映射的原版贴图。想换图标只要把 png 放进上面的目录即可。
 */
public final class HexIcons {
	/** 贴图路径（textures/ 下）及其像素尺寸。 */
	public record Icon(String namespace, String path, int size) {
		public ResourceLocation location() {
			return ResourceLocation.fromNamespaceAndPath(namespace, "textures/" + path);
		}
	}

	private static final Map<String, String> VANILLA = new HashMap<>();
	private static final Map<String, Icon> RESOLVED = new HashMap<>();

	static {
		VANILLA.put("iron_skin", "item/iron_chestplate");
		VANILLA.put("light_feet", "item/feather");
		VANILLA.put("vampire", "item/redstone");
		VANILLA.put("cobweb", "block/cobweb");
		VANILLA.put("slime_boots", "item/slime_ball");
		VANILLA.put("glutton", "item/cooked_beef");
		VANILLA.put("ender_apprentice", "item/ender_pearl");
		VANILLA.put("bedrock", "block/bedrock");
		VANILLA.put("crit_master", "item/iron_sword");
		VANILLA.put("haste", "mob_effect/haste");
		VANILLA.put("tank_engine", "mob_effect/health_boost");
		VANILLA.put("giant_slayer", "item/diamond_sword");
		VANILLA.put("thorns", "block/cactus_side");
		VANILLA.put("undying_totem", "item/totem_of_undying");
		VANILLA.put("wind_master", "item/wind_charge");
		VANILLA.put("blaze_heart", "item/blaze_powder");
		VANILLA.put("frost_walker", "item/snowball");
		VANILLA.put("berserk", "mob_effect/strength");
		VANILLA.put("hunter", "item/crossbow_standby");
		VANILLA.put("heavy_hammer", "item/mace");
		VANILLA.put("giant", "item/golden_apple");
		VANILLA.put("shrink", "item/rabbit_foot");
		VANILLA.put("warden_heart", "item/echo_shard");
		VANILLA.put("thunder", "block/lightning_rod");
		VANILLA.put("creeper_soul", "item/gunpowder");
		VANILLA.put("wither", "block/wither_rose");
		VANILLA.put("multi_strike", "item/netherite_sword");
		VANILLA.put("second_life", "item/heart_of_the_sea");
		VANILLA.put("time_warp", "item/clock_00");
		// 职业
		VANILLA.put("role_warrior", "item/iron_sword");
		VANILLA.put("role_tank", "item/iron_chestplate");
		VANILLA.put("role_archer", "item/bow");
	}

	public static Icon of(Hex hex) {
		return resolve(hex.id);
	}

	public static Icon of(Role role) {
		return resolve("role_" + role.id);
	}

	private static Icon resolve(String id) {
		return RESOLVED.computeIfAbsent(id, key -> {
			String custom = "hex/" + key + ".png";
			ResourceLocation customLocation = HexMayhem.id("textures/" + custom);
			var resource = Minecraft.getInstance().getResourceManager().getResource(customLocation);
			if (resource.isPresent()) {
				int size = 16;
				try (var stream = resource.get().open(); var image = com.mojang.blaze3d.platform.NativeImage.read(stream)) {
					size = image.getWidth();
				} catch (Exception ignored) {
				}
				return new Icon(HexMayhem.MOD_ID, custom, size);
			}
			String path = VANILLA.getOrDefault(key, "item/barrier");
			return new Icon("minecraft", path + ".png", path.startsWith("mob_effect/") ? 18 : 16);
		});
	}

	/** 资源包重载后重新查找自定义图标。 */
	static void clearCache() {
		RESOLVED.clear();
	}

	/** 原版 GUI：带品质边框的方形图标。 */
	public static void draw(GuiGraphics g, Icon icon, int x, int y, int size, int frameColor) {
		g.fill(x, y, x + size, y + size, 0xFF000000 | darken(frameColor));
		g.renderOutline(x, y, size, size, frameColor);
		int inner = size - 4;
		g.blit(icon.location(), x + 2, y + 2, inner, inner, 0, 0, icon.size(), icon.size(), icon.size(), icon.size());
	}

	static int darken(int color) {
		int r = (color >> 16 & 0xFF) / 5;
		int gr = (color >> 8 & 0xFF) / 5;
		int b = (color & 0xFF) / 5;
		return r << 16 | gr << 8 | b;
	}

	private HexIcons() {
	}
}
