package com.hexmayhem.client;

import com.hexmayhem.game.Role;
import com.hexmayhem.hex.Hex;
import com.hexmayhem.hex.Hexes;
import com.hexmayhem.net.HexPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.List;

/** 右上角个人战绩 + 按住 Tab 时的全体战绩表（类似英雄联盟）。 */
final class ScoreboardHud {
	private static final int ALLY = 0xFF4A9BFF;
	private static final int ENEMY = 0xFFFF5050;
	private static final int GOLD = 0xFFFFD25A;
	private static final int TEXT = 0xFFE8EAF0;
	private static final int MUTED = 0xFF9A9EAA;

	// ------------------------------------------------------------------ 右上角

	/** 返回战绩框下边缘的 y，击杀记录接在下面。 */
	static int renderCorner(GuiGraphics g, Minecraft mc) {
		HexPayloads.Scoreboard.Entry self = ClientScoreboard.self();
		if (self == null) {
			return 3 + effectRowsOffset(mc);
		}
		Font font = mc.font;
		int w = 118;
		int h = 30;
		int x = g.guiWidth() - w - 3;
		int y = 3 + effectRowsOffset(mc);

		g.fill(x, y, x + w, y + h, 0xB0101018);
		g.fill(x, y, x + w, y + 1, 0x60FFFFFF);

		// 第一行：队伍比分 + 时间
		String ally = String.valueOf(ClientScoreboard.teamKills(true));
		String enemy = String.valueOf(ClientScoreboard.teamKills(false));
		int cx = x + 40;
		g.drawString(font, ally, cx - 8 - font.width(ally), y + 4, ALLY);
		g.drawCenteredString(font, "⚔", cx, y + 4, MUTED);
		g.drawString(font, enemy, cx + 8, y + 4, ENEMY);
		String time = ClientScoreboard.time();
		g.drawString(font, time, x + w - 5 - font.width(time), y + 4, TEXT);

		// 第二行：个人 K / D / A
		g.drawString(font, "KDA", x + 5, y + 17, MUTED);
		String kda = self.kills() + " / " + self.deaths() + " / " + self.assists();
		g.drawString(font, kda, x + w - 5 - font.width(kda), y + 17, GOLD);
		return y + h + 3;
	}

	/** 原版状态效果图标也在右上角：有增益图标下移一行，有减益再下移一行。 */
	private static int effectRowsOffset(Minecraft mc) {
		boolean beneficial = false;
		boolean harmful = false;
		for (MobEffectInstance effect : mc.player.getActiveEffects()) {
			if (!effect.showIcon()) {
				continue;
			}
			if (effect.getEffect().value().isBeneficial()) {
				beneficial = true;
			} else {
				harmful = true;
			}
		}
		return harmful ? 52 : beneficial ? 26 : 0;
	}

	// ------------------------------------------------------------------ Tab 战绩表

	private static final int W = 380;
	private static final int ROW_H = 20;
	private static final int COL_HEX = 108;
	private static final int COL_KDA = 290;
	private static final int COL_DMG = 345;

	static void renderTab(GuiGraphics g, Minecraft mc) {
		Font font = mc.font;
		List<HexPayloads.Scoreboard.Entry> allies = ClientScoreboard.team(true);
		List<HexPayloads.Scoreboard.Entry> enemies = ClientScoreboard.team(false);
		int height = 20 + section(allies) + 4 + section(enemies);
		// 屏幕放不下时整体缩小
		float scale = Math.min(1F, Math.min((g.guiHeight() - 16F) / height, (g.guiWidth() - 8F) / W));
		g.pose().pushPose();
		g.pose().translate(g.guiWidth() / 2F, 8, 0);
		g.pose().scale(scale, scale, 1);
		int x = -W / 2;
		int y = 0;

		g.fill(x - 1, y - 1, x + W + 1, y + height + 1, 0xFF05050A);
		g.fill(x, y, x + W, y + height, 0xE0121218);

		// 标题栏
		g.fillGradient(x, y, x + W, y + 18, 0xFF2A2440, 0xFF1A1828);
		g.drawString(font, "海克斯大乱斗 · 战绩", x + 8, y + 5, GOLD);
		String time = "对局时间 " + ClientScoreboard.time();
		g.drawString(font, time, x + W - 8 - font.width(time), y + 5, TEXT);
		y += 20;

		y = drawSection(g, font, x, y, "己方", allies, ALLY, ClientScoreboard.teamKills(true));
		y += 4;
		drawSection(g, font, x, y, "敌方", enemies, ENEMY, ClientScoreboard.teamKills(false));
		g.pose().popPose();
	}

	private static int section(List<?> rows) {
		return 14 + Math.max(1, rows.size()) * ROW_H;
	}

	private static int drawSection(GuiGraphics g, Font font, int x, int y, String title,
								   List<HexPayloads.Scoreboard.Entry> rows, int color, int kills) {
		// 分组标题 + 列名
		g.fill(x, y, x + W, y + 14, color & 0x00FFFFFF | 0x40000000);
		g.fill(x, y, x + 2, y + 14, color);
		g.drawString(font, title + "  击杀 " + kills, x + 7, y + 3, color);
		g.drawString(font, "海克斯", x + COL_HEX, y + 3, MUTED);
		g.drawCenteredString(font, "K / D / A", x + COL_KDA, y + 3, MUTED);
		g.drawCenteredString(font, "伤害", x + COL_DMG, y + 3, MUTED);
		y += 14;

		if (rows.isEmpty()) {
			g.drawString(font, "（没有玩家）", x + 8, y + 6, MUTED);
			return y + ROW_H;
		}
		Minecraft mc = Minecraft.getInstance();
		for (int i = 0; i < rows.size(); i++) {
			HexPayloads.Scoreboard.Entry entry = rows.get(i);
			boolean self = mc.player != null && entry.id().equals(mc.player.getUUID());
			if (entry.respawnSeconds() > 0) {
				g.fill(x, y, x + W, y + ROW_H, 0x40000000); // 阵亡中：整行变暗
			} else if (i % 2 == 1) {
				g.fill(x, y, x + W, y + ROW_H, 0x18FFFFFF);
			}
			if (self) {
				g.fill(x, y, x + W, y + ROW_H, 0x22FFD25A);
				g.renderOutline(x, y, W, ROW_H, 0x80FFD25A);
			}

			Role role = Role.byId(entry.role());
			if (role != null) {
				HexIcons.draw(g, HexIcons.of(role), x + 4, y + 2, 16, role.color);
			} else {
				g.fill(x + 4, y + 2, x + 20, y + 18, 0xFF2A2A30);
			}
			// 等级角标（像英雄联盟头像右下角的等级）
			String level = String.valueOf(entry.level());
			g.pose().pushPose();
			g.pose().translate(0, 0, 50);
			int lw = font.width(level);
			g.fill(x + 21 - lw - 2, y + 12, x + 21, y + 20, 0xE0000000);
			g.drawString(font, level, x + 21 - lw - 1, y + 12, 0xFF8FD3FF, false);
			g.pose().popPose();
			String name = font.plainSubstrByWidth(entry.name(), 78);
			g.drawString(font, name, x + 24, y + 3, self ? GOLD : TEXT);
			if (entry.respawnSeconds() > 0) {
				g.drawString(font, "复活 " + entry.respawnSeconds() + "s", x + 24, y + 11, 0xFFFF6A6A, false);
			} else {
				g.drawString(font, role != null ? role.displayName : "未选职业", x + 24, y + 11, role != null ? role.color : MUTED, false);
			}

			int hx = x + COL_HEX;
			int shown = 0;
			for (String id : entry.hexes()) {
				Hex hex = Hexes.get(id);
				if (hex == null) {
					continue;
				}
				if (shown == 10) {
					g.drawString(font, "+" + (entry.hexes().size() - shown), hx + 1, y + 6, MUTED);
					break;
				}
				HexIcons.draw(g, HexIcons.of(hex), hx, y + 3, 14, hex.tier.color);
				hx += 16;
				shown++;
			}

			String kda = entry.kills() + " / " + entry.deaths() + " / " + entry.assists();
			g.drawCenteredString(font, kda, x + COL_KDA, y + 6, TEXT);
			g.drawCenteredString(font, String.valueOf(entry.damage()), x + COL_DMG, y + 6, 0xFFFF9A6A);
			y += ROW_H;
		}
		return y;
	}

	private ScoreboardHud() {
	}
}
