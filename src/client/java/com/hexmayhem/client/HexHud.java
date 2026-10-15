package com.hexmayhem.client;

import com.hexmayhem.game.Role;
import com.hexmayhem.hex.Hex;
import com.hexmayhem.hex.Hexes;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/** HUD：底部整条血条（替代原版爱心）+ 左上角职业 / 海克斯图标列表。 */
public final class HexHud {
	private static final int BAR_W = 81;
	private static final int BAR_H = 9;

	/** 血条“掉血残影”，平滑追上当前生命值。 */
	private static float trail = -1;
	private static long lastFrame;

	/** 对局中且处于生存 / 冒险模式时，用血条替代原版爱心（GuiMixin 读取）。 */
	public static boolean replacesHearts() {
		Minecraft mc = Minecraft.getInstance();
		return ClientHexState.inGame() && mc.gameMode != null && mc.gameMode.canHurtPlayer();
	}

	static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		// 模组关闭（没有进行中的对局）时什么都不画
		if (mc.options.hideGui || mc.player == null || !ClientScoreboard.active()) {
			return;
		}
		if (replacesHearts()) {
			renderHealthBar(g, mc.player, mc.font);
		}
		if (mc.getDebugOverlay().showDebugScreen()) {
			return;
		}
		if (showsDeathOverlay()) {
			renderDeathOverlay(g, mc.font);
		}
		if (showsTabScoreboard()) {
			ScoreboardHud.renderTab(g, mc);
			return;
		}
		renderHexList(g, mc);
		if (ClientScoreboard.active()) {
			int feedTop = ScoreboardHud.renderCorner(g, mc);
			KillFeedHud.render(g, mc, feedTop);
		}
	}

	/** 对局中按住 Tab：显示海克斯战绩表，代替原版玩家列表（TabOverlayMixin 读取）。 */
	public static boolean showsTabScoreboard() {
		Minecraft mc = Minecraft.getInstance();
		return ClientScoreboard.active() && mc.options.keyPlayerList.isDown() && mc.screen == null;
	}

	// ------------------------------------------------------------------ 血条

	private static void renderHealthBar(GuiGraphics g, Player player, Font font) {
		int x = g.guiWidth() / 2 - 91;
		int y = g.guiHeight() - 39;
		float health = Math.max(0, player.getHealth());
		float max = player.getMaxHealth();
		float absorption = player.getAbsorptionAmount();
		float total = Math.max(max, health) + absorption;

		long now = Util.getMillis();
		float dt = lastFrame == 0 ? 0 : Math.min(0.1F, (now - lastFrame) / 1000F);
		lastFrame = now;
		if (trail < health || trail < 0) {
			trail = health;
		} else {
			trail = Math.max(health, trail - Math.max(2F, (trail - health) * 3F) * dt);
		}

		int healthW = Math.round(BAR_W * health / total);
		int trailW = Math.round(BAR_W * trail / total);
		int absorbW = Math.round(BAR_W * absorption / total);

		g.fill(x - 1, y - 1, x + BAR_W + 1, y + BAR_H + 1, 0xFF0B0B0F);
		g.fill(x, y, x + BAR_W, y + BAR_H, 0xFF2A1416);
		if (trailW > healthW) {
			g.fill(x + healthW, y, x + trailW, y + BAR_H, 0xFFF2E3B3);
		}
		int[] colors = healthColors(player);
		g.fillGradient(x, y, x + healthW, y + BAR_H, colors[0], colors[1]);
		if (absorbW > 0) {
			g.fillGradient(x + healthW, y, Math.min(x + BAR_W, x + healthW + absorbW), y + BAR_H, 0xFFFFE27A, 0xFFD9A21E);
		}
		g.fill(x, y, x + healthW + absorbW, y + 1, 0x50FFFFFF);
		drawSegments(g, x, y, total);

		String text = Mth.ceil(health) + (absorption > 0 ? "+" + Mth.ceil(absorption) : "") + " / " + Mth.ceil(max);
		g.pose().pushPose();
		g.pose().translate(x + BAR_W / 2F, y + 2, 0);
		g.pose().scale(0.75F, 0.75F, 1);
		g.drawCenteredString(font, text, 0, 0, 0xFFFFFFFF);
		g.pose().popPose();
	}

	/** 每 10 点生命一道刻度（每 50 点一道长刻度），和英雄联盟的血条一样方便估算。 */
	private static void drawSegments(GuiGraphics g, int x, int y, float total) {
		for (int hp = 10; hp < total; hp += 10) {
			int sx = x + Math.round(BAR_W * hp / total);
			boolean major = hp % 50 == 0;
			g.fill(sx, y, sx + 1, major ? y + BAR_H : y + BAR_H / 2, major ? 0xCC000000 : 0x88000000);
		}
	}

	/** {顶部颜色, 底部颜色}：中毒绿、凋零灰、冰冻蓝，平时红。 */
	static int[] healthColors(Player player) {
		if (player.hasEffect(MobEffects.WITHER)) {
			return new int[]{0xFF6A6A6A, 0xFF2A2A2A};
		}
		if (player.hasEffect(MobEffects.POISON)) {
			return new int[]{0xFF9CCC4A, 0xFF4E7A1E};
		}
		if (player.isFullyFrozen()) {
			return new int[]{0xFFA6E0F5, 0xFF3A8AB8};
		}
		return new int[]{0xFFFF6464, 0xFFB81E28};
	}

	// ------------------------------------------------------------------ 阵亡遮罩

	public static boolean showsDeathOverlay() {
		return ClientScoreboard.active() && ClientHexState.respawnTicks > 0;
	}

	/** 阵亡（旁观）期间：画面变灰 + 中间的复活倒计时和进度条。 */
	private static void renderDeathOverlay(GuiGraphics g, Font font) {
		int w = g.guiWidth();
		int h = g.guiHeight();
		g.fill(0, 0, w, h, 0x66303036);
		g.fillGradient(0, 0, w, h / 4, 0x90000000, 0x00000000);
		g.fillGradient(0, h - h / 4, w, h, 0x00000000, 0x90000000);

		int cx = w / 2;
		int cy = h / 2 - 30;
		g.pose().pushPose();
		g.pose().translate(cx, cy, 0);
		g.pose().scale(2F, 2F, 1F);
		g.drawCenteredString(font, "你已阵亡", 0, 0, 0xFFFF5A5A);
		g.pose().popPose();

		int seconds = (ClientHexState.respawnTicks + 19) / 20;
		g.pose().pushPose();
		g.pose().translate(cx, cy + 24, 0);
		g.pose().scale(3F, 3F, 1F);
		g.drawCenteredString(font, String.valueOf(seconds), 0, 0, 0xFFFFFFFF);
		g.pose().popPose();
		g.drawCenteredString(font, "秒后复活", cx, cy + 54, 0xFFDDDDDD);

		int barW = 160;
		int bx = cx - barW / 2;
		int by = cy + 68;
		float progress = ClientHexState.respawnTotal <= 0 ? 0
				: 1F - ClientHexState.respawnTicks / (float) ClientHexState.respawnTotal;
		g.fill(bx - 1, by - 1, bx + barW + 1, by + 5, 0xFF0B0B0F);
		g.fill(bx, by, bx + barW, by + 4, 0xFF2A2A30);
		g.fillGradient(bx, by, bx + Math.round(barW * progress), by + 4, 0xFF8FD3FF, 0xFF3A8AB8);
		g.drawCenteredString(font, "等级越高复活越慢（最多 50 秒）", cx, by + 10, 0xFF9A9EAA);
		if (ClientHexState.canPick()) {
			g.drawCenteredString(font, "按 H 选择海克斯", cx, by + 22, 0xFFFFFF55);
		}
	}

	// ------------------------------------------------------------------ 海克斯列表

	private static void renderHexList(GuiGraphics g, Minecraft mc) {
		Role role = Role.byId(ClientHexState.role);
		if (role == null && ClientHexState.hexes.isEmpty() && !ClientHexState.hasOffer()) {
			return;
		}
		Font font = mc.font;
		int x = 4;
		int y = 4;
		if (role != null) {
			HexIcons.draw(g, HexIcons.of(role), x, y, 16, role.color);
			g.drawString(font, role.displayName, x + 20, y + 4, role.color);
		} else {
			g.drawString(font, "未选职业", x, y + 4, 0xFFAAAAAA);
		}
		y += 20;
		for (int i = 0; i < ClientHexState.hexes.size(); i++) {
			Hex hex = Hexes.get(ClientHexState.hexes.get(i));
			if (hex == null) {
				continue;
			}
			HexIcons.draw(g, HexIcons.of(hex), x, y, 14, hex.tier.color);
			String text = hex.name;
			if (hex.maxStacks > 0 && i < ClientHexState.stacks.size()) {
				text += " ×" + ClientHexState.stacks.get(i);
			}
			g.drawString(font, text, x + 18, y + 3, hex.tier.color);
			y += 16;
		}
		for (Hexes.HexSet set : Hexes.SETS) {
			if (set.activeFor(ClientHexState.hexes)) {
				g.drawString(font, "★ " + set.name(), x, y + 2, 0xFF55FF55);
				y += 11;
			}
		}
		if (ClientHexState.canPick()) {
			boolean blink = (mc.player.tickCount / 10) % 2 == 0;
			g.drawString(font, "按 H 选择海克斯", x, y + 3, blink ? 0xFFFFFF55 : 0xFFFFAA00);
			y += 12;
		} else if (ClientHexState.hasOffer()) {
			g.drawString(font, "有待选海克斯（阵亡后选择）", x, y + 3, 0xFFAAAAAA);
			y += 12;
		}
		String level = "等级 " + mc.player.experienceLevel;
		if (ClientHexState.nextOfferLevel > 0) {
			level += "  ·  下一个海克斯：" + ClientHexState.nextOfferLevel + " 级";
		}
		g.drawString(font, level, x, y + 3, 0xFF8FD3FF);
	}

	private HexHud() {
	}
}
