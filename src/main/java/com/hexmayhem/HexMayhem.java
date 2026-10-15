package com.hexmayhem;

import com.hexmayhem.command.HexCommands;
import com.hexmayhem.game.HexCombat;
import com.hexmayhem.game.HexData;
import com.hexmayhem.game.HexGame;
import com.hexmayhem.item.TimeHourglassItem;
import com.hexmayhem.net.HexPayloads;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HexMayhem implements ModInitializer {
	public static final String MOD_ID = "hexmayhem";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final AttachmentType<HexData> DATA = AttachmentRegistry.<HexData>builder()
			.persistent(HexData.CODEC)
			.copyOnDeath()
			.initializer(HexData::new)
			.buildAndRegister(id("data"));

	public static final Item TIME_HOURGLASS = Registry.register(BuiltInRegistries.ITEM, id("time_hourglass"),
			new TimeHourglassItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.playS2C().register(HexPayloads.Sync.TYPE, HexPayloads.Sync.CODEC);
		PayloadTypeRegistry.playS2C().register(HexPayloads.Scoreboard.TYPE, HexPayloads.Scoreboard.CODEC);
		PayloadTypeRegistry.playS2C().register(HexPayloads.KillFeed.TYPE, HexPayloads.KillFeed.CODEC);
		PayloadTypeRegistry.playS2C().register(HexPayloads.Respawn.TYPE, HexPayloads.Respawn.CODEC);
		PayloadTypeRegistry.playS2C().register(HexPayloads.Lobby.TYPE, HexPayloads.Lobby.CODEC);
		PayloadTypeRegistry.playC2S().register(HexPayloads.LobbyAction.TYPE, HexPayloads.LobbyAction.CODEC);
		PayloadTypeRegistry.playC2S().register(HexPayloads.ChooseRole.TYPE, HexPayloads.ChooseRole.CODEC);
		PayloadTypeRegistry.playC2S().register(HexPayloads.PickHex.TYPE, HexPayloads.PickHex.CODEC);
		PayloadTypeRegistry.playC2S().register(HexPayloads.Reroll.TYPE, HexPayloads.Reroll.CODEC);

		HexGame.register();
		HexCombat.register();
		HexCommands.register();
		LOGGER.info("海克斯大乱斗已加载：{} 个海克斯", com.hexmayhem.hex.Hexes.BY_ID.size());
	}
}
