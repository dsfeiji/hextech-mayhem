package com.hexmayhem.client;

import com.hexmayhem.hex.Hex;
import com.hexmayhem.hex.Hexes;
import com.hexmayhem.hex.Tier;
import com.hexmayhem.net.HexPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** 海克斯三选一。 */
public class HexSelectScreen extends CardScreen {
	private final List<Card> cards = new ArrayList<>();
	private boolean waiting;

	public HexSelectScreen() {
		super(Component.literal("选择一个海克斯"));
		Tier tier = Tier.byIndex(ClientHexState.offerTier);
		for (String id : ClientHexState.offerIds) {
			Hex hex = Hexes.get(id);
			if (hex == null || tier == null) {
				continue;
			}
			List<String> lines = new ArrayList<>();
			lines.add(hex.description);
			for (Hexes.HexSet set : Hexes.SETS) {
				if (set.members().contains(hex)) {
					lines.add("§a套装【" + set.name() + "】");
				}
			}
			cards.add(new Card(tier.displayName, hex.name, lines, tier.color, tier == Tier.PRISMATIC, HexIcons.of(hex)));
		}
	}

	@Override
	protected void init() {
		Button reroll = Button.builder(Component.literal("刷新（剩余 " + ClientHexState.rerolls + " 次）"), b -> {
			if (!waiting) {
				waiting = true;
				ClientPlayNetworking.send(new HexPayloads.Reroll());
			}
		}).bounds(width / 2 - 70, buttonY(), 140, 20).build();
		reroll.active = ClientHexState.rerolls > 0;
		addRenderableWidget(reroll);
	}

	@Override
	protected String subtitle() {
		Tier tier = Tier.byIndex(ClientHexState.offerTier);
		String text = tier == null ? "" : "【" + tier.displayName + "】";
		if (ClientHexState.pendingOffers > 1) {
			text += "  还有 " + (ClientHexState.pendingOffers - 1) + " 次待选";
		}
		return text + "  ·  Esc 关闭，按 H 重新打开";
	}

	@Override
	protected List<Card> cards() {
		return cards;
	}

	@Override
	protected void onPick(int index) {
		if (!waiting) {
			waiting = true;
			ClientPlayNetworking.send(new HexPayloads.PickHex(index));
		}
	}
}
