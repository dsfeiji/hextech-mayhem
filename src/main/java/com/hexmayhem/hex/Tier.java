package com.hexmayhem.hex;

import net.minecraft.ChatFormatting;

public enum Tier {
	SILVER("白银", 0xFFC0C8D0, ChatFormatting.GRAY),
	GOLD("黄金", 0xFFFFC940, ChatFormatting.GOLD),
	PRISMATIC("棱彩", 0xFFD070FF, ChatFormatting.LIGHT_PURPLE);

	public final String displayName;
	public final int color;
	public final ChatFormatting format;

	Tier(String displayName, int color, ChatFormatting format) {
		this.displayName = displayName;
		this.color = color;
		this.format = format;
	}

	public static Tier byIndex(int index) {
		return index >= 0 && index < values().length ? values()[index] : null;
	}
}
