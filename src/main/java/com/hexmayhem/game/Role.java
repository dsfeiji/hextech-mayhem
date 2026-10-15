package com.hexmayhem.game;

import com.hexmayhem.hex.Hex;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE;

public enum Role {
	WARRIOR("warrior", "战士", 0xFFE05050, 0.0,
			List.of("近战输出、突进", "攻击力 +2", "初始：铁剑、铁胸甲")),
	TANK("tank", "坦克", 0xFF5090E0, 0.0,
			List.of("前排、控制", "最大生命 +10，护甲 +4", "击退抗性 +20%，移速 -10%", "初始：石剑、盾牌、铁胸甲、铁护腿")),
	ARCHER("archer", "弓箭手", 0xFF60C060, 0.25,
			List.of("远程消耗、风筝", "最大生命 -4，移速 +10%", "拉弓速度 +25%", "初始：弓、64 支箭、木剑、皮革胸甲"));

	public final String id;
	public final String displayName;
	public final int color;
	/** 额外拉弓 / 装填速度（0.25 = 快 25%）。 */
	public final double drawSpeedBonus;
	public final List<String> description;

	Role(String id, String displayName, int color, double drawSpeedBonus, List<String> description) {
		this.id = id;
		this.displayName = displayName;
		this.color = color;
		this.drawSpeedBonus = drawSpeedBonus;
		this.description = description;
	}

	public List<Hex.Mod> mods() {
		return switch (this) {
			case WARRIOR -> List.of(
					new Hex.Mod(Attributes.ATTACK_DAMAGE, 2, ADD_VALUE, false));
			case TANK -> List.of(
					new Hex.Mod(Attributes.MAX_HEALTH, 10, ADD_VALUE, false),
					new Hex.Mod(Attributes.ARMOR, 4, ADD_VALUE, false),
					new Hex.Mod(Attributes.KNOCKBACK_RESISTANCE, 0.2, ADD_VALUE, false),
					new Hex.Mod(Attributes.MOVEMENT_SPEED, -0.1, ADD_MULTIPLIED_BASE, false));
			case ARCHER -> List.of(
					new Hex.Mod(Attributes.MAX_HEALTH, -4, ADD_VALUE, false),
					new Hex.Mod(Attributes.MOVEMENT_SPEED, 0.1, ADD_MULTIPLIED_BASE, false));
		};
	}

	public void giveKit(ServerPlayer player) {
		switch (this) {
			case WARRIOR -> {
				give(player, new ItemStack(Items.IRON_SWORD));
				player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			}
			case TANK -> {
				give(player, new ItemStack(Items.STONE_SWORD));
				player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
				player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
				player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
			}
			case ARCHER -> {
				give(player, new ItemStack(Items.BOW));
				give(player, new ItemStack(Items.WOODEN_SWORD));
				give(player, new ItemStack(Items.ARROW, 64));
				player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
			}
		}
		give(player, new ItemStack(Items.COOKED_BEEF, 16));
	}

	static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	public static Role byId(String id) {
		for (Role role : values()) {
			if (role.id.equals(id)) {
				return role;
			}
		}
		return null;
	}
}
