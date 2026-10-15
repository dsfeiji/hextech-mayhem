package com.hexmayhem.client.mixin;

import com.hexmayhem.client.OverheadBars;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在其他玩家头顶画血条。 */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
	@Shadow
	@Final
	protected EntityRenderDispatcher entityRenderDispatcher;

	// 用 HEAD：原方法在不显示名字时会提前 return，TAIL 只能捕获最后一个 return
	@Inject(method = "render", at = @At("HEAD"))
	private void hexmayhem$renderHealthBar(Entity entity, float yaw, float partialTick, PoseStack pose,
										   MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (entity instanceof Player player) {
			OverheadBars.render(player, entityRenderDispatcher, pose, buffers);
		}
	}
}
