package com.hexmayhem.hex;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个海克斯：只有一个主效果，所有职业拿到后效果相同。
 * 属性类效果直接声明在 {@link #mods} 里；机制类效果在 HexCombat / HexGame 中按 id 处理。
 */
public final class Hex {
	public record Mod(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation, boolean perStack) {
	}

	public final String id;
	public final Tier tier;
	public final String name;
	public final String description;
	public final List<Mod> mods = new ArrayList<>();
	/** 0 表示不可叠层。 */
	public int maxStacks;

	public Hex(String id, Tier tier, String name, String description) {
		this.id = id;
		this.tier = tier;
		this.name = name;
		this.description = description;
	}

	Hex attr(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
		mods.add(new Mod(attribute, amount, operation, false));
		return this;
	}

	Hex stackAttr(Holder<Attribute> attribute, double amountPerStack, AttributeModifier.Operation operation) {
		mods.add(new Mod(attribute, amountPerStack, operation, true));
		return this;
	}

	Hex stacks(int max) {
		this.maxStacks = max;
		return this;
	}

	public String stackKey() {
		return "stacks:" + id;
	}
}
