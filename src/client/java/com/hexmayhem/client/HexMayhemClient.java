package com.hexmayhem.client;

import com.hexmayhem.net.HexPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class HexMayhemClient implements ClientModInitializer {
	private static KeyMapping openKey;
	/** 安装了 ModernUI 就用它的界面，否则回退到原版界面。 */
	static boolean modernUi;

	@Override
	public void onInitializeClient() {
		modernUi = FabricLoader.getInstance().isModLoaded("modernui");
		openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.hexmayhem.open",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.hexmayhem"));

		ClientPlayNetworking.registerGlobalReceiver(HexPayloads.Sync.TYPE, (payload, context) -> onSync(payload, context.client()));
		ClientPlayNetworking.registerGlobalReceiver(HexPayloads.Lobby.TYPE, (payload, context) -> LobbyScreen.apply(payload));
		ClientPlayNetworking.registerGlobalReceiver(HexPayloads.Scoreboard.TYPE, (payload, context) -> ClientScoreboard.apply(payload));
		ClientPlayNetworking.registerGlobalReceiver(HexPayloads.Respawn.TYPE, (payload, context) -> {
			ClientHexState.respawnTicks = payload.ticksLeft();
			ClientHexState.respawnTotal = payload.totalTicks();
		});
		ClientPlayNetworking.registerGlobalReceiver(HexPayloads.KillFeed.TYPE, (payload, context) -> {
			if (ClientScoreboard.active()) {
				KillFeedHud.onKill(payload);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientHexState.reset();
			ClientScoreboard.reset();
			KillFeedHud.reset();
			LobbyScreen.state = null;
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> HexIcons.clearCache());
		ClientTickEvents.END_CLIENT_TICK.register(HexMayhemClient::tick);
		HudRenderCallback.EVENT.register(HexHud::render);
	}

	private static void openHexSelect(Minecraft client) {
		if (modernUi) {
			MuiScreens.openHexSelect();
		} else {
			client.setScreen(new HexSelectScreen());
		}
	}

	private static void openRoleSelect(Minecraft client) {
		if (modernUi) {
			MuiScreens.openRoleSelect();
		} else {
			client.setScreen(new RoleSelectScreen());
		}
	}

	private static boolean isHexScreen(Screen screen) {
		return screen instanceof HexSelectScreen || modernUi && MuiScreens.isHexSelect(screen);
	}

	private static boolean isRoleScreen(Screen screen) {
		return screen instanceof RoleSelectScreen || modernUi && MuiScreens.isRoleSelect(screen);
	}

	/** 模组关闭（/hex stop）：清掉所有客户端状态，关闭选择界面，HUD 由 HexHud 自行隐藏。 */
	static void onModDisabled() {
		ClientHexState.reset();
		KillFeedHud.reset();
		closeOurScreens(Minecraft.getInstance());
	}

	private static void closeOurScreens(Minecraft client) {
		if (isHexScreen(client.screen) || isRoleScreen(client.screen)) {
			client.setScreen(null);
		}
	}

	private static void onSync(HexPayloads.Sync payload, Minecraft client) {
		ClientHexState.apply(payload);
		if (!ClientScoreboard.active()) {
			closeOurScreens(client);
			return;
		}
		if (payload.open() == HexPayloads.OPEN_ROLE) {
			openRoleSelect(client);
		} else if (payload.open() == HexPayloads.OPEN_OFFER && ClientHexState.canPick()) {
			openHexSelect(client);
		} else if (isHexScreen(client.screen)) {
			// 正在看的三选一已经失效（被选走 / 刷新 / 重置）
			if (ClientHexState.canPick()) {
				openHexSelect(client);
			} else {
				client.setScreen(null);
			}
		} else if (isRoleScreen(client.screen) && ClientHexState.inGame()) {
			client.setScreen(null);
		}
	}

	private static void tick(Minecraft client) {
		if (ClientScoreboard.active() && client.level != null && !client.isPaused()) {
			ClientScoreboard.gameTicks++;
			if (ClientHexState.respawnTicks > 0) {
				ClientHexState.respawnTicks--;
			}
		}
		while (openKey.consumeClick()) {
			if (LobbyScreen.state != null) {
				client.setScreen(new LobbyScreen());
				continue;
			}
			if (client.player == null || !ClientScoreboard.active()) {
				continue;
			}
			if (ClientHexState.canPick() && ClientHexState.inGame()) {
				openHexSelect(client);
			} else if (ClientHexState.hasOffer() && ClientHexState.inGame()) {
				client.player.displayClientMessage(Component.literal("升级获得的海克斯要阵亡一次后才能选择"), true);
			} else if (!ClientHexState.inGame()) {
				openRoleSelect(client);
			} else {
				client.player.displayClientMessage(Component.literal("当前没有待选择的海克斯"), true);
			}
		}
	}
}
