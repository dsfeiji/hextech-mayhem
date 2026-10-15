package com.hexmayhem.client;

import com.hexmayhem.game.Role;
import com.hexmayhem.net.HexPayloads;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 击杀播报：屏幕上方的大字公告（一血 / 双杀~五杀 / 终结 / 连杀）+ 右侧击杀记录。 */
final class KillFeedHud {
	private static final long ANNOUNCE_MS = 3000;
	private static final long ANNOUNCE_FAST_MS = 1800;
	private static final long FEED_MS = 8000;
	private static final int FEED_MAX = 5;
	private static final int ALLY = 0xFF4A9BFF;
	private static final int ENEMY = 0xFFFF5050;

	private record Announcement(HexPayloads.KillFeed kill, String title, int titleColor, String subtitle) {
	}

	private record FeedLine(HexPayloads.KillFeed kill, long time) {
	}

	private static final ArrayDeque<Announcement> QUEUE = new ArrayDeque<>();
	private static Announcement current;
	private static long currentStart;
	private static final List<FeedLine> FEED = new ArrayList<>();

	static void onKill(HexPayloads.KillFeed kill) {
		FEED.add(0, new FeedLine(kill, Util.getMillis()));
		while (FEED.size() > FEED_MAX) {
			FEED.remove(FEED.size() - 1);
		}
		QUEUE.add(build(kill));
	}

	static void reset() {
		QUEUE.clear();
		FEED.clear();
		current = null;
	}

	private static Announcement build(HexPayloads.KillFeed kill) {
		String title = "";
		int color = 0xFFFFFFFF;
		if (kill.killer() != null) {
			switch (kill.multiKill()) {
				case 2 -> { title = "双杀"; color = 0xFF66CCFF; }
				case 3 -> { title = "三杀"; color = 0xFFB070FF; }
				case 4 -> { title = "四杀"; color = 0xFFFF9A3C; }
				case 5 -> { title = "五杀！"; color = 0xFFFF3B3B; }
				default -> {
					if (kill.firstBlood()) { title = "第一滴血"; color = 0xFFFF4040; }
					else if (kill.shutdown()) { title = "终结"; color = 0xFFFFD25A; }
				}
			}
		}
		return new Announcement(kill, title, color, streakName(kill.killStreak()));
	}

	/** 英雄联盟的连杀称号（本条命内击杀数）。 */
	private static String streakName(int streak) {
		return switch (streak) {
			case 0, 1, 2 -> "";
			case 3 -> "大杀特杀";
			case 4 -> "主宰比赛";
			case 5 -> "无人能挡";
			case 6 -> "横扫千军";
			case 7 -> "接近神了";
			default -> "超神！";
		};
	}

	private static void play(Announcement a) {
		SoundEvent sound;
		float pitch = 1F;
		if (a.kill.multiKill() >= 5) {
			sound = SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
		} else if (!a.title.isEmpty()) {
			sound = SoundEvents.ENDER_DRAGON_GROWL;
			pitch = 1.3F + 0.1F * a.kill.multiKill();
		} else {
			sound = SoundEvents.EXPERIENCE_ORB_PICKUP;
			pitch = 0.7F;
		}
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, a.title.isEmpty() ? 0.6F : 0.35F));
	}

	// ------------------------------------------------------------------ 渲染

	static void render(GuiGraphics g, Minecraft mc, int feedTop) {
		long now = Util.getMillis();
		long duration = QUEUE.isEmpty() ? ANNOUNCE_MS : ANNOUNCE_FAST_MS;
		if (current != null && now - currentStart > duration) {
			current = null;
		}
		if (current == null && !QUEUE.isEmpty()) {
			current = QUEUE.poll();
			currentStart = now;
			play(current);
		}
		if (current != null) {
			renderAnnouncement(g, mc.font, current, now - currentStart, duration);
		}
		renderFeed(g, mc.font, feedTop, now);
	}

	private static void renderAnnouncement(GuiGraphics g, Font font, Announcement a, long age, long duration) {
		// 淡入 150ms，最后 400ms 淡出；标题出现时有一个从大到小的“砸下来”效果
		float alpha = Math.min(1F, age / 150F) * Math.min(1F, (duration - age) / 400F);
		int a8 = Mth.clamp((int) (alpha * 255), 8, 255) << 24;
		int cx = g.guiWidth() / 2;
		int y = 34;

		if (!a.title.isEmpty()) {
			float pop = 1F + 0.6F * Math.max(0F, 1F - age / 200F);
			float scale = 2.5F * pop;
			g.pose().pushPose();
			g.pose().translate(cx, y, 0);
			g.pose().scale(scale, scale, 1);
			g.drawCenteredString(font, a.title, 0, 0, a.titleColor & 0xFFFFFF | a8);
			g.pose().popPose();
			y += 28;
		}

		// 横条：[击杀者图标] 击杀者  ⚔  被杀者 [被杀者图标]
		HexPayloads.KillFeed kill = a.kill;
		String killer = kill.killer() == null ? "" : kill.killerName();
		String middle = kill.killer() == null ? "阵亡" : "击杀了";
		String victim = kill.victimName();
		int width = (kill.killer() == null ? 0 : 20 + font.width(killer) + 8) + font.width(middle) + 8 + font.width(victim) + 20;
		int x = cx - width / 2;
		int bandAlpha = (int) (alpha * 0xB0) << 24;
		g.fillGradient(x - 10, y - 4, x + width + 10, y + 18, bandAlpha | 0x101018, bandAlpha | 0x1A1A26);
		g.fill(x - 10, y - 4, x + width + 10, y - 3, (int) (alpha * 0x90) << 24 | (a.titleColor & 0xFFFFFF));

		if (kill.killer() != null) {
			drawRoleIcon(g, kill.killerRole(), x, y - 1, alpha);
			x += 20;
			g.drawString(font, killer, x, y + 4, sideColor(kill.killer(), killer) & 0xFFFFFF | a8);
			x += font.width(killer) + 8;
			g.drawString(font, middle, x, y + 4, 0xBBBBBB | a8);
			x += font.width(middle) + 8;
			g.drawString(font, victim, x, y + 4, sideColor(kill.victim(), victim) & 0xFFFFFF | a8);
			x += font.width(victim) + 4;
			drawRoleIcon(g, kill.victimRole(), x, y - 1, alpha);
		} else {
			// 没有击杀者：[图标] 名字 阵亡
			drawRoleIcon(g, kill.victimRole(), x, y - 1, alpha);
			x += 20;
			g.drawString(font, victim, x, y + 4, sideColor(kill.victim(), victim) & 0xFFFFFF | a8);
			x += font.width(victim) + 8;
			g.drawString(font, middle, x, y + 4, 0xBBBBBB | a8);
		}
		y += 22;

		List<String> extra = new ArrayList<>();
		if (!a.subtitle.isEmpty()) {
			extra.add(kill.killerName() + " " + a.subtitle);
		}
		if (kill.shutdown() && !"终结".equals(a.title)) {
			extra.add("终结了 " + victim + " 的连杀");
		}
		if (kill.assists() > 0) {
			extra.add(kill.assists() + " 名玩家助攻");
		}
		for (String line : extra) {
			g.drawCenteredString(font, line, cx, y, 0xFFD25A | a8);
			y += 11;
		}
	}

	/** 右侧击杀记录，紧贴在右上角战绩框下面。 */
	private static void renderFeed(GuiGraphics g, Font font, int top, long now) {
		int right = g.guiWidth() - 3;
		int y = top;
		for (Iterator<FeedLine> it = FEED.iterator(); it.hasNext(); ) {
			FeedLine line = it.next();
			long age = now - line.time;
			if (age > FEED_MS) {
				it.remove();
				continue;
			}
			float alpha = Math.min(1F, (FEED_MS - age) / 600F);
			int a8 = Mth.clamp((int) (alpha * 255), 8, 255) << 24;
			HexPayloads.KillFeed kill = line.kill;
			String killer = kill.killer() == null ? "" : kill.killerName();
			String victim = kill.victimName();
			int width = (kill.killer() == null ? 0 : 14 + font.width(killer) + 4) + 12 + 14 + font.width(victim) + 6;
			int x = right - width;
			g.fill(x - 3, y, right, y + 14, (int) (alpha * 0xA0) << 24 | 0x101018);
			if (kill.killer() != null) {
				drawRoleIcon(g, kill.killerRole(), x, y + 1, alpha, 12);
				x += 14;
				g.drawString(font, killer, x, y + 3, sideColor(kill.killer(), killer) & 0xFFFFFF | a8);
				x += font.width(killer) + 4;
			}
			g.drawString(font, "⚔", x, y + 3, 0xCCCCCC | a8);
			x += 12;
			drawRoleIcon(g, kill.victimRole(), x, y + 1, alpha, 12);
			x += 14;
			g.drawString(font, victim, x, y + 3, sideColor(kill.victim(), victim) & 0xFFFFFF | a8);
			y += 16;
		}
	}

	private static void drawRoleIcon(GuiGraphics g, String roleId, int x, int y, float alpha) {
		drawRoleIcon(g, roleId, x, y, alpha, 16);
	}

	private static void drawRoleIcon(GuiGraphics g, String roleId, int x, int y, float alpha, int size) {
		Role role = Role.byId(roleId);
		if (role == null || alpha < 0.2F) {
			return;
		}
		HexIcons.draw(g, HexIcons.of(role), x, y, size, role.color);
	}

	/** 己方蓝色，敌方红色（同一个原版队伍或就是自己算己方）。 */
	private static int sideColor(UUID id, String name) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return ENEMY;
		}
		if (id.equals(mc.player.getUUID())) {
			return ALLY;
		}
		PlayerTeam mine = mc.level.getScoreboard().getPlayersTeam(mc.player.getScoreboardName());
		return mine != null && Objects.equals(mine, mc.level.getScoreboard().getPlayersTeam(name)) ? ALLY : ENEMY;
	}

	private KillFeedHud() {
	}
}
