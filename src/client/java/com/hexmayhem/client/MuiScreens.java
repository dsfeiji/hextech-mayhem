package com.hexmayhem.client;

import com.hexmayhem.game.Role;
import com.hexmayhem.hex.Hex;
import com.hexmayhem.hex.Hexes;
import com.hexmayhem.hex.Tier;
import com.hexmayhem.net.HexPayloads;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.ImageView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * ModernUI 版的职业选择和海克斯三选一界面。
 * 只有安装了 ModernUI 时才会加载这个类（见 {@link HexMayhemClient#modernUi}）。
 * 注意：ModernUI 的视图在它自己的 UI 线程上创建和回调，发包 / 关界面要切回游戏主线程。
 */
public final class MuiScreens {
	private static Screen hexScreen;
	private static Screen roleScreen;

	static void openHexSelect() {
		hexScreen = open(new HexSelectFragment());
	}

	static void openRoleSelect() {
		roleScreen = open(new RoleSelectFragment());
	}

	static boolean isHexSelect(Screen screen) {
		return screen != null && screen == hexScreen;
	}

	static boolean isRoleSelect(Screen screen) {
		return screen != null && screen == roleScreen;
	}

	private static Screen open(CardFragment fragment) {
		Screen screen = MuiModApi.get().createScreen(fragment, fragment);
		Minecraft.getInstance().setScreen(screen);
		return screen;
	}

	// ------------------------------------------------------------------ 卡片数据

	private record CardData(String header, String title, List<String> lines, List<String> tags,
							HexIcons.Icon icon, int color, Runnable onPick) {
	}

	/** 三张（或若干张）横排卡片的通用界面。 */
	public abstract static class CardFragment extends Fragment implements ScreenCallback {
		private static final int CARD_W = 190;
		private static final int CARD_H = 330;
		private boolean picked;

		abstract String title();

		abstract String subtitle();

		/** 在游戏主线程上读取的数据快照。 */
		abstract List<CardData> cards();

		/** 卡片下方的额外控件（例如刷新按钮），可以为 null。 */
		View footer(Context context) {
			return null;
		}

		@Override
		public boolean isPauseScreen() {
			return false;
		}

		@Override
		public boolean shouldBlurBackground() {
			return true;
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
			Context context = requireContext();
			FrameLayout root = new FrameLayout(context);
			root.setClipChildren(false);

			LinearLayout content = new LinearLayout(context);
			content.setOrientation(LinearLayout.VERTICAL);
			content.setGravity(Gravity.CENTER_HORIZONTAL);
			content.setClipChildren(false);
			root.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
					ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

			TextView title = text(context, title(), 26, 0xFFFFFFFF);
			title.setTextStyle(Typeface.BOLD);
			content.addView(title, wrap());
			TextView subtitle = text(context, subtitle(), 13, 0xFFB0B4C0);
			LinearLayout.LayoutParams subParams = wrap();
			subParams.topMargin = content.dp(4);
			subParams.bottomMargin = content.dp(6);
			content.addView(subtitle, subParams);

			LinearLayout row = new LinearLayout(context);
			row.setOrientation(LinearLayout.HORIZONTAL);
			// 悬停时卡片会上浮 8dp、边框变粗：留出空间并关闭裁剪，避免被遮住
			row.setPadding(0, row.dp(12), 0, row.dp(4));
			row.setClipChildren(false);
			row.setClipToPadding(false);
			for (CardData data : cards()) {
				LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(row.dp(CARD_W), row.dp(CARD_H));
				params.leftMargin = row.dp(10);
				params.rightMargin = row.dp(10);
				row.addView(card(context, data), params);
			}
			content.addView(row, wrap());

			View footer = footer(context);
			if (footer != null) {
				LinearLayout.LayoutParams params = wrap();
				params.topMargin = content.dp(20);
				content.addView(footer, params);
			}
			TextView hint = text(context, "Esc 关闭 · 按 H 重新打开", 11, 0xFF707480);
			LinearLayout.LayoutParams hintParams = wrap();
			hintParams.topMargin = content.dp(10);
			content.addView(hint, hintParams);
			return root;
		}

		private View card(Context context, CardData data) {
			LinearLayout card = new LinearLayout(context);
			card.setOrientation(LinearLayout.VERTICAL);
			card.setGravity(Gravity.CENTER_HORIZONTAL);
			card.setPadding(card.dp(14), card.dp(12), card.dp(14), card.dp(12));
			card.setClickable(true);

			ShapeDrawable background = new ShapeDrawable();
			background.setCornerRadius(card.dp(14));
			background.setColor(0xF0141420);
			background.setStroke(card.dp(2), data.color);
			card.setBackground(background);

			TextView header = text(context, data.header, 11, data.color);
			header.setTextStyle(Typeface.BOLD);
			card.addView(header, wrap());

			// 圆形徽章 + 像素图标
			FrameLayout badge = new FrameLayout(context);
			ShapeDrawable circle = new ShapeDrawable();
			circle.setCornerRadius(card.dp(42));
			circle.setColor(data.color & 0x00FFFFFF | 0x33000000);
			circle.setStroke(card.dp(2), data.color);
			badge.setBackground(circle);
			Image image = Image.create(data.icon.namespace(), data.icon.path());
			if (image != null) {
				ImageDrawable drawable = new ImageDrawable(image);
				drawable.setFilter(false); // 像素风贴图放大时保持清晰
				ImageView icon = new ImageView(context);
				icon.setImageDrawable(drawable);
				badge.addView(icon, new FrameLayout.LayoutParams(card.dp(48), card.dp(48), Gravity.CENTER));
			}
			LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(card.dp(84), card.dp(84));
			badgeParams.topMargin = card.dp(12);
			badgeParams.bottomMargin = card.dp(10);
			card.addView(badge, badgeParams);

			TextView name = text(context, data.title, 19, data.color);
			name.setTextStyle(Typeface.BOLD);
			card.addView(name, wrap());

			View divider = new View(context);
			ShapeDrawable line = new ShapeDrawable();
			line.setColor(data.color & 0x00FFFFFF | 0x55000000);
			divider.setBackground(line);
			LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(card.dp(120), card.dp(1));
			dividerParams.topMargin = card.dp(8);
			dividerParams.bottomMargin = card.dp(10);
			card.addView(divider, dividerParams);

			for (String text : data.lines) {
				TextView desc = text(context, text, 12, 0xFFE2E4EA);
				desc.setLineSpacing(0, 1.15F);
				LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
						ViewGroup.LayoutParams.WRAP_CONTENT);
				params.bottomMargin = card.dp(4);
				card.addView(desc, params);
			}
			for (String tag : data.tags) {
				TextView tagView = text(context, tag, 11, 0xFF6BE07A);
				LinearLayout.LayoutParams params = wrap();
				params.topMargin = card.dp(4);
				card.addView(tagView, params);
			}

			View spacer = new View(context);
			card.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1F));
			TextView pick = text(context, "点击选择", 12, 0xFF8A8E9A);
			card.addView(pick, wrap());

			card.setOnHoverListener((v, event) -> {
				if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
					v.setTranslationY(-v.dp(8));
					background.setColor(0xF0222236);
					background.setStroke(v.dp(3), data.color);
					pick.setText("▶ 点击选择 ◀");
					pick.setTextColor(0xFFFFFFFF);
				} else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
					v.setTranslationY(0);
					background.setColor(0xF0141420);
					background.setStroke(v.dp(2), data.color);
					pick.setText("点击选择");
					pick.setTextColor(0xFF8A8E9A);
				}
				return false;
			});
			card.setOnClickListener(v -> {
				if (!picked) {
					picked = true;
					Minecraft.getInstance().execute(data.onPick);
				}
			});
			return card;
		}

		static TextView text(Context context, String value, int size, int color) {
			TextView view = new TextView(context);
			view.setText(value);
			view.setTextSize(size);
			view.setTextColor(color);
			view.setGravity(Gravity.CENTER_HORIZONTAL);
			return view;
		}

		static LinearLayout.LayoutParams wrap() {
			return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		}
	}

	// ------------------------------------------------------------------ 海克斯三选一

	public static final class HexSelectFragment extends CardFragment {
		private final Tier tier = Tier.byIndex(ClientHexState.offerTier);
		private final int rerolls = ClientHexState.rerolls;
		private final int pending = ClientHexState.pendingOffers;
		private final List<CardData> cards = new ArrayList<>();
		private boolean rerolled;

		public HexSelectFragment() {
			List<String> ids = ClientHexState.offerIds;
			for (int i = 0; i < ids.size(); i++) {
				Hex hex = Hexes.get(ids.get(i));
				if (hex == null || tier == null) {
					continue;
				}
				List<String> tags = new ArrayList<>();
				for (Hexes.HexSet set : Hexes.SETS) {
					if (set.members().contains(hex)) {
						tags.add("套装【" + set.name() + "】");
					}
				}
				int index = i;
				cards.add(new CardData(tier.displayName + " · 海克斯", hex.name, List.of(hex.description), tags,
						HexIcons.of(hex), tier.color,
						() -> ClientPlayNetworking.send(new HexPayloads.PickHex(index))));
			}
		}

		@Override
		String title() {
			return "选择一个海克斯";
		}

		@Override
		String subtitle() {
			String text = tier == null ? "" : "【" + tier.displayName + "】";
			return pending > 1 ? text + "  还有 " + (pending - 1) + " 次待选" : text;
		}

		@Override
		List<CardData> cards() {
			return cards;
		}

		@Override
		View footer(Context context) {
			Button reroll = new Button(context);
			reroll.setText(rerolls > 0 ? "刷新  ·  剩余 " + rerolls + " 次" : "没有刷新次数了");
			reroll.setTextSize(13);
			reroll.setTextColor(rerolls > 0 ? 0xFFFFFFFF : 0xFF808080);
			reroll.setPadding(reroll.dp(22), reroll.dp(8), reroll.dp(22), reroll.dp(8));
			ShapeDrawable background = new ShapeDrawable();
			background.setCornerRadius(reroll.dp(18));
			background.setColor(rerolls > 0 ? 0xFF2D3A5C : 0xFF2A2A2E);
			background.setStroke(reroll.dp(1), rerolls > 0 ? 0xFF6C86C8 : 0xFF444448);
			reroll.setBackground(background);
			reroll.setEnabled(rerolls > 0);
			reroll.setOnClickListener(v -> {
				if (!rerolled) {
					rerolled = true;
					Minecraft.getInstance().execute(() -> ClientPlayNetworking.send(new HexPayloads.Reroll()));
				}
			});
			return reroll;
		}
	}

	// ------------------------------------------------------------------ 职业选择

	public static final class RoleSelectFragment extends CardFragment {
		private final List<CardData> cards = new ArrayList<>();

		public RoleSelectFragment() {
			for (Role role : Role.values()) {
				cards.add(new CardData("职业", role.displayName, role.description, List.of(), HexIcons.of(role),
						role.color, () -> ClientPlayNetworking.send(new HexPayloads.ChooseRole(role.id))));
			}
		}

		@Override
		String title() {
			return "选择你的职业";
		}

		@Override
		String subtitle() {
			return "所有职业都能拿全部海克斯";
		}

		@Override
		List<CardData> cards() {
			return cards;
		}
	}

	private MuiScreens() {
	}
}
