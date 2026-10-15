package com.hexmayhem.mixin;

import com.hexmayhem.game.HexGame;
import com.hexmayhem.hex.Hexes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(Player.class)
abstract class PlayerMixin {
	/** 暴击专精：原版近战暴击倍率 1.5 → 1.95。 */
	@ModifyConstant(method = "attack", constant = @Constant(floatValue = 1.5F))
	private float hexmayhem$critMultiplier(float multiplier) {
		if ((Object) this instanceof ServerPlayer player && HexGame.has(player, Hexes.CRIT_MASTER)) {
			return multiplier * 1.3F;
		}
		return multiplier;
	}
}
