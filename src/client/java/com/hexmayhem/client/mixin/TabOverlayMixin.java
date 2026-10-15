package com.hexmayhem.client.mixin;

import com.hexmayhem.client.HexHud;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 对局中按 Tab 不显示原版玩家列表，改由 HexHud 画战绩表。 */
@Mixin(PlayerTabOverlay.class)
abstract class TabOverlayMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void hexmayhem$replaceTabList(GuiGraphics graphics, int width, Scoreboard scoreboard, Objective objective,
										  CallbackInfo ci) {
		if (HexHud.showsTabScoreboard()) {
			ci.cancel();
		}
	}
}
