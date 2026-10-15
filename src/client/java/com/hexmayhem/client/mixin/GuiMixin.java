package com.hexmayhem.client.mixin;

import com.hexmayhem.client.HexHud;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 对局中隐藏原版爱心，改由 HexHud 画整条血条。 */
@Mixin(Gui.class)
abstract class GuiMixin {
	@Inject(method = "renderHearts", at = @At("HEAD"), cancellable = true)
	private void hexmayhem$hideHearts(GuiGraphics graphics, Player player, int x, int y, int rowHeight, int regenIndex,
									  float maxHealth, int health, int displayHealth, int absorption, boolean blink,
									  CallbackInfo ci) {
		if (HexHud.replacesHearts()) {
			ci.cancel();
		}
	}

	/** 原版按最大生命计算爱心行数来上移护甲栏；血条只有一行，所以按 20 点计算。 */
	@ModifyVariable(method = "renderPlayerHealth", at = @At("STORE"), ordinal = 0)
	private float hexmayhem$singleRowMaxHealth(float maxHealth) {
		return HexHud.replacesHearts() ? 20F : maxHealth;
	}

	@ModifyExpressionValue(method = "renderPlayerHealth",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getAbsorptionAmount()F"))
	private float hexmayhem$noAbsorptionRows(float absorption) {
		return HexHud.replacesHearts() ? 0F : absorption;
	}
}
