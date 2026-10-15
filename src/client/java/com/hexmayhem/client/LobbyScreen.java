package com.hexmayhem.client;

import com.hexmayhem.game.HexLobby;
import com.hexmayhem.net.HexPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 开局前的自定义房间（仿英雄联盟自定义对局）：左蓝右红各 5 个位置，中间 VS，底部是待定区和操作按钮。
 * 所有状态来自服务端 Lobby 包，界面只负责显示和发送操作。
 */
public final class LobbyScreen extends Screen {
	// 海克斯科技配色
	private static final int GOLD = 0xFFC8AA6E;
	private static final int GOLD_DARK = 0xFF785A28;
	private static final int GOLD_LIGHT = 0xFFF0E6D2;
	private static final int PANEL = 0xD0091018;
	private static final int ROW = 0xC0111A26;
	private static final int ROW_EMPTY = 0x80101722;
	private static final int TEXT = 0xFFE8EAF0;
	private static final int MUTED = 0xFF8A8F9C;
	private static final int[] TEAM_COLOR = {0xFF4A9BFF, 0xFFFF5050};
	private static final int[] TEAM_DARK = {0xFF12345C, 0xFF5C1717};
	private static final String[] TEAM_NAME = {"蓝色方", "红色方"};

	private static final int HEADER_H = 22;
	private static final int VS_GAP = 36;
	/** 除 5 行位置以外的固定高度：标题、队伍标题、待定区、按钮、提示。 */
	private static final int FIXED_H = 140;
	/** 位置行高 / 行距随屏幕高度伸缩，小窗口也能完整显示。 */
	private int rowH = 22;
	private int rowGap = 3;

	/** 当前房间状态，null 表示没有打开的房间。 */
	static HexPayloads.Lobby state;

	private record Hit(int x, int y, int w, int h, Runnable action) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	/** 本帧可点击的区域，后加入的在上层（优先命中）。 */
	private final List<Hit> hits = new ArrayList<>();
	private Component tooltip;

	public LobbyScreen() {
		super(Component.literal("自定义对局"));
	}

	static void apply(HexPayloads.Lobby payload) {
		Minecraft mc = Minecraft.getInstance();
		state = payload.open() ? payload : null;
		if (state == null) {
			if (mc.screen instanceof LobbyScreen) {
				mc.setScreen(null);
			}
		} else if (payload.popup() && !(mc.screen instanceof LobbyScreen)) {
			mc.setScreen(new LobbyScreen());
		}
	}

	// ------------------------------------------------------------------ 布局

	private int panelW() {
		return Math.max(120, Math.min(190, (width - 32 - VS_GAP) / 2));
	}

	private int left() {
		return (width - panelW() * 2 - VS_GAP) / 2;
	}

	private int totalH() {
		return FIXED_H + HexLobby.TEAM_SIZE * (rowH + rowGap) - rowGap;
	}

	private int top() {
		return Math.max(4, (height - totalH()) / 2);
	}

	@Override
	protected void init() {
		int step = Math.max(16, Math.min(25, (height - 8 - FIXED_H) / HexLobby.TEAM_SIZE));
		rowGap = step >= 22 ? 3 : 2;
		rowH = step - rowGap;
	}

	// ------------------------------------------------------------------ 绘制

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		hits.clear();
		tooltip = null;
		if (state == null) {
			return;
		}
		UUID self = minecraft.player == null ? null : minecraft.player.getUUID();
		int pw = panelW(), left = left(), top = top();
		int fullW = pw * 2 + VS_GAP;

		HexPayloads.Lobby.Member[][] seats = new HexPayloads.Lobby.Member[2][HexLobby.TEAM_SIZE];
		List<HexPayloads.Lobby.Member> pending = new ArrayList<>();
		HexPayloads.Lobby.Member me = null;
		String hostName = null;
		for (HexPayloads.Lobby.Member m : state.members()) {
			if (m.team() >= 0 && m.slot() >= 0 && m.slot() < HexLobby.TEAM_SIZE) {
				seats[m.team()][m.slot()] = m;
			} else {
				pending.add(m);
			}
			if (m.id().equals(self)) {
				me = m;
			}
			if (m.id().equals(state.host())) {
				hostName = m.name();
			}
		}

		// 标题
		g.pose().pushPose();
		g.pose().translate(width / 2F, top, 0);
		g.pose().scale(2F, 2F, 1F);
		g.drawCenteredString(font, title, 0, 0, GOLD_LIGHT);
		g.pose().popPose();
		String sub = "海克斯大乱斗 · " + (hostName == null ? "管理员开局" : "房主 " + hostName) + " · " + state.members().size() + " 名玩家";
		g.drawCenteredString(font, sub, width / 2, top + 20, MUTED);
		int lineY = top + 31;
		g.fill(left, lineY, left + fullW, lineY + 1, GOLD_DARK);
		g.fill(width / 2 - 30, lineY, width / 2 + 30, lineY + 1, GOLD);

		// 两队
		int panelY = lineY + 6;
		int[] counts = new int[2];
		for (int t = 0; t < 2; t++) {
			int x = left + t * (pw + VS_GAP);
			for (HexPayloads.Lobby.Member m : seats[t]) {
				if (m != null) {
					counts[t]++;
				}
			}
			drawHeader(g, x, panelY, pw, t, counts[t]);
			for (int s = 0; s < HexLobby.TEAM_SIZE; s++) {
				int y = panelY + HEADER_H + 4 + s * (rowH + rowGap);
				drawSeat(g, x, y, pw, t, s, seats[t][s], self, mouseX, mouseY);
			}
		}
		int panelBottom = panelY + HEADER_H + 4 + HexLobby.TEAM_SIZE * (rowH + rowGap) - rowGap;
		drawVs(g, left + pw + VS_GAP / 2, (panelY + panelBottom) / 2);

		// 待定区
		int pendingY = panelBottom + 10;
		drawPending(g, left, pendingY, fullW, pending, self);

		// 按钮
		int buttonY = pendingY + 30;
		drawButtons(g, left, buttonY, fullW, me, counts, pending.size(), mouseX, mouseY);

		g.drawCenteredString(font, "Esc 关闭 · 按 H 重新打开", width / 2, buttonY + 26, 0xFF5C606C);
		if (tooltip != null) {
			g.renderTooltip(font, tooltip, mouseX, mouseY);
		}
	}

	private void drawHeader(GuiGraphics g, int x, int y, int w, int team, int count) {
		int color = TEAM_COLOR[team];
		g.fillGradient(x, y, x + w, y + HEADER_H, TEAM_DARK[team] & 0x00FFFFFF | 0xE0000000, 0xE0091018);
		g.fill(x, y, x + w, y + 2, color);
		g.drawString(font, Component.literal(TEAM_NAME[team]).withStyle(ChatFormatting.BOLD), x + 8, y + 8, color);
		String text = count + " / " + HexLobby.TEAM_SIZE;
		g.drawString(font, text, x + w - 8 - font.width(text), y + 8, count > 0 ? TEXT : MUTED);
	}

	private void drawSeat(GuiGraphics g, int x, int y, int w, int team, int slot,
						  HexPayloads.Lobby.Member member, UUID self, int mouseX, int mouseY) {
		int color = TEAM_COLOR[team];
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + rowH;
		if (member == null) {
			g.fill(x, y, x + w, y + rowH, hover ? color & 0x00FFFFFF | 0x40000000 : ROW_EMPTY);
			g.renderOutline(x, y, w, rowH, hover ? color : 0x30FFFFFF);
			String label = hover ? "加入" + TEAM_NAME[team] : "+  空位";
			g.drawCenteredString(font, label, x + w / 2, y + (rowH - 8) / 2, hover ? 0xFFFFFFFF : 0xFF5C606C);
			hits.add(new Hit(x, y, w, rowH, () -> send(new HexPayloads.LobbyAction(HexPayloads.LobbyAction.SEAT, team, slot, null))));
			return;
		}

		boolean mine = member.id().equals(self);
		g.fill(x, y, x + w, y + rowH, mine ? color & 0x00FFFFFF | 0x50000000 : ROW);
		g.fill(x, y, x + 2, y + rowH, color);
		if (mine) {
			g.renderOutline(x, y, w, rowH, GOLD);
		}
		int face = Math.min(16, rowH - 4), faceY = y + (rowH - face) / 2;
		g.fill(x + 6, faceY - 1, x + 8 + face, faceY + face + 1, 0xFF000000);
		PlayerFaceRenderer.draw(g, skin(member.id()), x + 7, faceY, face);

		int textX = x + 29, textY = y + (rowH - 8) / 2;
		int right = x + w - 6;
		boolean kickable = state.canManage() && !mine;
		if (kickable && hover) {
			int bx = right - 12, by = y + (rowH - 12) / 2;
			boolean hoverX = mouseX >= bx && mouseX < bx + 12 && mouseY >= by && mouseY < by + 12;
			g.fill(bx, by, bx + 12, by + 12, hoverX ? 0xFFB33030 : 0x60000000);
			g.drawCenteredString(font, "×", bx + 6, by + 2, hoverX ? 0xFFFFFFFF : 0xFFFF8080);
			if (hoverX) {
				tooltip = Component.literal("将 " + member.name() + " 移到待定区");
			}
			hits.add(new Hit(bx, by, 12, 12, () -> send(new HexPayloads.LobbyAction(HexPayloads.LobbyAction.KICK, 0, 0, member.id()))));
			right = bx - 4;
		}
		if (member.id().equals(state.host())) {
			right = tag(g, right, textY - 1, "房主", 0xFF2A2210, GOLD);
		}
		if (mine) {
			right = tag(g, right, textY - 1, "你", 0xFF102A18, 0xFF6BE07A);
		}
		String name = font.plainSubstrByWidth(member.name(), right - textX - 2);
		g.drawString(font, name, textX, textY, mine ? GOLD_LIGHT : TEXT);
	}

	/** 右对齐的小标签，返回标签左边缘（再往左留 4 像素）。 */
	private int tag(GuiGraphics g, int right, int y, String text, int bg, int color) {
		int w = font.width(text) + 6;
		int x = right - w;
		g.fill(x, y, right, y + 10, bg);
		g.renderOutline(x, y, w, 10, color & 0x00FFFFFF | 0x90000000);
		g.drawString(font, text, x + 3, y + 1, color, false);
		return x - 4;
	}

	private void drawVs(GuiGraphics g, int cx, int cy) {
		g.fill(cx, cy - 50, cx + 1, cy - 14, GOLD_DARK);
		g.fill(cx, cy + 14, cx + 1, cy + 50, GOLD_DARK);
		g.pose().pushPose();
		g.pose().translate(cx + 0.5F, cy - 7, 0);
		g.pose().scale(2F, 2F, 1F);
		g.drawCenteredString(font, Component.literal("VS").withStyle(ChatFormatting.BOLD, ChatFormatting.ITALIC), 0, 0, GOLD);
		g.pose().popPose();
	}

	private void drawPending(GuiGraphics g, int x, int y, int w, List<HexPayloads.Lobby.Member> pending, UUID self) {
		int h = 20;
		g.fill(x, y, x + w, y + h, PANEL);
		g.renderOutline(x, y, w, h, 0x40C8AA6E);
		g.drawString(font, "待定", x + 6, y + 6, GOLD);
		int cx = x + 6 + font.width("待定") + 8;
		if (pending.isEmpty()) {
			g.drawString(font, "所有玩家都已就位", cx, y + 6, MUTED);
			return;
		}
		int limit = x + w - 6;
		for (int i = 0; i < pending.size(); i++) {
			HexPayloads.Lobby.Member m = pending.get(i);
			int chipW = 12 + 4 + font.width(m.name()) + 6;
			String more = "+" + (pending.size() - i);
			if (cx + chipW > limit - (i < pending.size() - 1 ? font.width(more) + 4 : 0)) {
				g.drawString(font, more, cx, y + 6, MUTED);
				break;
			}
			boolean mine = m.id().equals(self);
			g.fill(cx, y + 3, cx + chipW, y + 17, mine ? 0x60C8AA6E : 0x20FFFFFF);
			PlayerFaceRenderer.draw(g, skin(m.id()), cx + 1, y + 4, 12);
			g.drawString(font, m.name(), cx + 16, y + 6, mine ? GOLD_LIGHT : TEXT);
			cx += chipW + 4;
		}
	}

	private void drawButtons(GuiGraphics g, int x, int y, int w, HexPayloads.Lobby.Member me, int[] counts, int pending,
							 int mouseX, int mouseY) {
		if (me != null && me.team() >= 0) {
			button(g, x, y, 70, "移至待定", false, true, mouseX, mouseY,
					() -> send(HexPayloads.LobbyAction.of(HexPayloads.LobbyAction.UNSEAT)), null);
		}
		int right = x + w;
		if (!state.canManage()) {
			String dots = ".".repeat((int) (System.currentTimeMillis() / 400 % 4));
			String text = "等待房主开始游戏" + dots;
			g.drawString(font, text, right - font.width("等待房主开始游戏...") - 4, y + 6, MUTED);
			return;
		}
		int total = counts[0] + counts[1] + pending;
		boolean blocked = total >= 2 && pending == 0 && (counts[0] == 0 || counts[1] == 0);
		Component startTip = blocked ? Component.literal("两边都至少需要一名玩家")
				: pending > 0 ? Component.literal("待定的 " + pending + " 名玩家会自动分到人少的一方") : null;
		int startW = 84;
		button(g, right - startW, y, startW, "开始游戏", true, !blocked, mouseX, mouseY,
				() -> send(HexPayloads.LobbyAction.of(HexPayloads.LobbyAction.START)), startTip);
		button(g, right - startW - 6 - 64, y, 64, "随机分队", false, true, mouseX, mouseY,
				() -> send(HexPayloads.LobbyAction.of(HexPayloads.LobbyAction.SHUFFLE)), Component.literal("把所有在线玩家随机分到两边"));
		button(g, right - startW - 6 - 64 - 6 - 64, y, 64, "解散房间", false, true, mouseX, mouseY,
				() -> send(HexPayloads.LobbyAction.of(HexPayloads.LobbyAction.DISBAND)), Component.literal("关闭房间，不开始游戏"));
	}

	private void button(GuiGraphics g, int x, int y, int w, String label, boolean primary, boolean enabled,
						int mouseX, int mouseY, Runnable action, Component tip) {
		int h = 20;
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		int border, fill, text;
		if (!enabled) {
			border = 0xFF3A3D45;
			fill = 0xC0161A20;
			text = 0xFF5C606C;
		} else if (primary) {
			border = hover ? GOLD_LIGHT : GOLD;
			fill = hover ? 0xF0263446 : 0xF01A2533;
			text = hover ? 0xFFFFFFFF : GOLD_LIGHT;
		} else {
			border = hover ? GOLD : GOLD_DARK;
			fill = hover ? 0xE0222A36 : 0xD0141A22;
			text = hover ? GOLD_LIGHT : 0xFFB8B2A2;
		}
		g.fill(x, y, x + w, y + h, fill);
		g.renderOutline(x, y, w, h, border);
		if (primary && enabled) {
			g.renderOutline(x + 2, y + 2, w - 4, h - 4, border & 0x00FFFFFF | 0x50000000);
		}
		g.drawCenteredString(font, label, x + w / 2, y + 6, text);
		if (hover && tip != null) {
			tooltip = tip;
		}
		if (enabled) {
			hits.add(new Hit(x, y, w, h, action));
		}
	}

	private PlayerSkin skin(UUID id) {
		PlayerInfo info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(id);
		return info != null ? info.getSkin() : DefaultPlayerSkin.get(id);
	}

	private void send(HexPayloads.LobbyAction action) {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1F));
		ClientPlayNetworking.send(action);
	}

	// ------------------------------------------------------------------ 输入

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			for (int i = hits.size() - 1; i >= 0; i--) {
				if (hits.get(i).contains(mouseX, mouseY)) {
					hits.get(i).action().run();
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
