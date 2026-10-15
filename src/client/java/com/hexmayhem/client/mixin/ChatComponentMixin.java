package com.hexmayhem.client.mixin;

import com.hexmayhem.client.HexHud;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 按住 Tab 看战绩、或阵亡倒计时期间暂时隐藏聊天栏，否则聊天会盖在上面。 */
@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void hexmayhem$hideDuringScoreboard(GuiGraphics graphics, int tickCount, int mouseX, int mouseY,
												boolean focused, CallbackInfo ci) {
		// 阵亡遮罩期间只隐藏平时的聊天消息，按 T 打开聊天（focused）时照常显示
		if (HexHud.showsTabScoreboard() || !focused && HexHud.showsDeathOverlay()) {
			ci.cancel();
		}
	}
}
