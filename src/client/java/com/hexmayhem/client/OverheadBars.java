package com.hexmayhem.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

/** 其他玩家头顶的血条：队友蓝色，敌人红色。由 EntityRendererMixin 调用。 */
public final class OverheadBars {
	private static final float W = 40;
	private static final float H = 4;
	private static final int ALLY = 0xFF4A9BFF;
	private static final int ALLY_DARK = 0xFF1F4E9A;
	private static final int ENEMY = 0xFFFF4A4A;
	private static final int ENEMY_DARK = 0xFF8E1C1C;

	public static void render(Player target, EntityRenderDispatcher dispatcher, PoseStack pose, MultiBufferSource buffers) {
		Minecraft mc = Minecraft.getInstance();
		Player self = mc.player;
		if (self == null || target == self || !ClientHexState.inGame() || mc.options.hideGui
				|| target.isSpectator() || target.isInvisibleTo(self) || !target.isAlive()
				|| dispatcher.distanceToSqr(target) > 48 * 48) {
			return;
		}
		float health = target.getHealth();
		float max = target.getMaxHealth();
		float absorption = target.getAbsorptionAmount();
		float total = Math.max(max, health) + absorption;
		boolean ally = self.isAlliedTo(target);

		pose.pushPose();
		// 名字标签在 bbHeight + 0.5 处，血条放在名字上方
		pose.translate(0, target.getBbHeight() + 0.75, 0);
		pose.mulPose(dispatcher.cameraOrientation());
		pose.scale(0.025F, -0.025F, 0.025F);
		Matrix4f m = pose.last().pose();
		VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());

		float left = -W / 2;
		float healthEnd = left + W * health / total;
		float absorbEnd = Math.min(left + W, healthEnd + W * absorption / total);
		// 外框（四条边，互不重叠，避免深度冲突）
		quad(vc, m, left - 1, -1, left + W + 1, 0, 0xE0000000);
		quad(vc, m, left - 1, H, left + W + 1, H + 1, 0xE0000000);
		quad(vc, m, left - 1, 0, left, H, 0xE0000000);
		quad(vc, m, left + W, 0, left + W + 1, H, 0xE0000000);
		// 生命（上半亮色，下半暗色，做出立体感）
		quad(vc, m, left, 0, healthEnd, H / 2, ally ? ALLY : ENEMY);
		quad(vc, m, left, H / 2, healthEnd, H, ally ? ALLY_DARK : ENEMY_DARK);
		if (absorbEnd > healthEnd) {
			quad(vc, m, healthEnd, 0, absorbEnd, H, 0xFFFFD34A);
		}
		quad(vc, m, absorbEnd, 0, left + W, H, 0xC0202020);
		pose.popPose();
	}

	private static void quad(VertexConsumer vc, Matrix4f m, float x1, float y1, float x2, float y2, int color) {
		if (x2 <= x1) {
			return;
		}
		int light = LightTexture.FULL_BRIGHT;
		// 正反两面都画，不受背面剔除影响
		vc.addVertex(m, x1, y1, 0).setColor(color).setLight(light);
		vc.addVertex(m, x1, y2, 0).setColor(color).setLight(light);
		vc.addVertex(m, x2, y2, 0).setColor(color).setLight(light);
		vc.addVertex(m, x2, y1, 0).setColor(color).setLight(light);
		vc.addVertex(m, x2, y1, 0).setColor(color).setLight(light);
		vc.addVertex(m, x2, y2, 0).setColor(color).setLight(light);
		vc.addVertex(m, x1, y2, 0).setColor(color).setLight(light);
		vc.addVertex(m, x1, y1, 0).setColor(color).setLight(light);
	}

	private OverheadBars() {
	}
}
