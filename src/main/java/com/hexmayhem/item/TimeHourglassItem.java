package com.hexmayhem.item;

import com.hexmayhem.game.HexGame;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 时间扭曲海克斯附带的物品：回到 4 秒前的位置和生命值。 */
public class TimeHourglassItem extends Item {
	public static final int COOLDOWN = 25 * 20;

	public TimeHourglassItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player instanceof ServerPlayer serverPlayer && HexGame.useTimeWarp(serverPlayer)) {
			player.getCooldowns().addCooldown(this, COOLDOWN);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}
}
