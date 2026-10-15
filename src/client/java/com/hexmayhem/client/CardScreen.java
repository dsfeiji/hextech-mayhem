package com.hexmayhem.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/** 横排卡片选择界面（职业选择 / 海克斯三选一共用）。 */
abstract class CardScreen extends Screen {
	record Card(String header, String title, List<String> lines, int color, boolean rainbow, HexIcons.Icon icon) {
	}

	private static final int CARD_W = 130;
	private static final int CARD_H = 200;
	private static final int GAP = 14;

	protected CardScreen(Component title) {
		super(title);
	}

	protected abstract List<Card> cards();

	protected abstract void onPick(int index);

	protected String subtitle() {
		return "";
	}

	private int cardsLeft(int count) {
		return (width - (count * CARD_W + (count - 1) * GAP)) / 2;
	}

	private int cardsTop() {
		return Math.max(36, (height - CARD_H) / 2 - 6);
	}

	protected int buttonY() {
		return cardsTop() + CARD_H + 12;
	}

	private int hovered(double mouseX, double mouseY) {
		List<Card> cards = cards();
		int left = cardsLeft(cards.size());
		int top = cardsTop();
		for (int i = 0; i < cards.size(); i++) {
			int x = left + i * (CARD_W + GAP);
			if (mouseX >= x && mouseX < x + CARD_W && mouseY >= top && mouseY < top + CARD_H) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		List<Card> cards = cards();
		int top = cardsTop();
		g.drawCenteredString(font, title, width / 2, top - 26, 0xFFFFFFFF);
		String sub = subtitle();
		if (!sub.isEmpty()) {
			g.drawCenteredString(font, sub, width / 2, top - 14, 0xFFAAAAAA);
		}

		int hover = hovered(mouseX, mouseY);
		int left = cardsLeft(cards.size());
		long time = System.currentTimeMillis();
		for (int i = 0; i < cards.size(); i++) {
			Card card = cards.get(i);
			int x = left + i * (CARD_W + GAP);
			int y = top - (i == hover ? 4 : 0);
			int color = card.rainbow()
					? 0xFF000000 | Mth.hsvToRgb(((time / 20 + i * 40) % 360) / 360F, 0.55F, 1F)
					: card.color();

			g.fill(x, y, x + CARD_W, y + CARD_H, i == hover ? 0xF0202030 : 0xE0101018);
			g.renderOutline(x, y, CARD_W, CARD_H, color);
			if (i == hover) {
				g.renderOutline(x + 1, y + 1, CARD_W - 2, CARD_H - 2, color);
			}
			g.fill(x + 1, y + 1, x + CARD_W - 1, y + 16, (color & 0x00FFFFFF) | 0x50000000);

			g.drawCenteredString(font, card.header(), x + CARD_W / 2, y + 5, color);
			g.pose().pushPose();
			HexIcons.draw(g, card.icon(), x + CARD_W / 2 - 18, y + 22, 36, color);
			g.pose().translate(x + CARD_W / 2F, y + 64, 0);
			g.pose().scale(1.5F, 1.5F, 1F);
			g.drawCenteredString(font, card.title(), 0, 0, color);
			g.pose().popPose();

			int lineY = y + 84;
			for (String line : card.lines()) {
				for (FormattedCharSequence wrapped : font.split(Component.literal(line), CARD_W - 16)) {
					g.drawString(font, wrapped, x + 8, lineY, 0xFFDDDDDD);
					lineY += 11;
				}
				lineY += 3;
			}
			g.drawCenteredString(font, i == hover ? "▶ 点击选择 ◀" : "点击选择", x + CARD_W / 2, y + CARD_H - 14,
					i == hover ? 0xFFFFFFFF : 0xFF777777);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int index = hovered(mouseX, mouseY);
		if (button == 0 && index >= 0) {
			onPick(index);
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
