package com.hexmayhem.client;

import com.hexmayhem.game.Role;
import com.hexmayhem.net.HexPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

/** 开局职业选择。 */
public class RoleSelectScreen extends CardScreen {
	private static final List<Card> CARDS = Arrays.stream(Role.values())
			.map(r -> new Card("职业", r.displayName, r.description, r.color, false, HexIcons.of(r)))
			.toList();
	private boolean waiting;

	public RoleSelectScreen() {
		super(Component.literal("选择你的职业"));
	}

	@Override
	protected String subtitle() {
		return "所有职业都能拿全部海克斯 · Esc 关闭，按 H 重新打开";
	}

	@Override
	protected List<Card> cards() {
		return CARDS;
	}

	@Override
	protected void onPick(int index) {
		if (!waiting) {
			waiting = true;
			ClientPlayNetworking.send(new HexPayloads.ChooseRole(Role.values()[index].id));
		}
	}
}
