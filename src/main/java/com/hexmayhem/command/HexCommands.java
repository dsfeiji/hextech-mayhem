package com.hexmayhem.command;

import com.hexmayhem.game.HexData;
import com.hexmayhem.game.HexGame;
import com.hexmayhem.game.HexLobby;
import com.hexmayhem.game.Role;
import com.hexmayhem.hex.Hex;
import com.hexmayhem.hex.Hexes;
import com.hexmayhem.hex.Tier;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /hex start | stop                      开始 / 结束对局（管理员）
 * /hex role &lt;职业&gt; [玩家]                 设置职业（管理员）
 * /hex offer &lt;品质&gt; [玩家]                发放一次三选一（管理员）
 * /hex give | remove &lt;海克斯&gt; [玩家]       直接给予 / 移除海克斯（管理员）
 * /hex reset [玩家]                       清空职业和海克斯（管理员）
 * /hex info [玩家]                        查看已有海克斯
 * /hex list                              列出全部海克斯
 */
public final class HexCommands {
	private static final SuggestionProvider<CommandSourceStack> HEX_IDS =
			(ctx, builder) -> SharedSuggestionProvider.suggest(Hexes.BY_ID.keySet(), builder);
	private static final SuggestionProvider<CommandSourceStack> ROLE_IDS =
			(ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Role.values()).map(r -> r.id), builder);
	private static final SuggestionProvider<CommandSourceStack> TIER_IDS =
			(ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Tier.values()).map(t -> t.name().toLowerCase(Locale.ROOT)), builder);

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("hex")
						.then(Commands.literal("start").requires(s -> s.hasPermission(2)).executes(ctx -> {
							return HexLobby.open(ctx.getSource().getServer(), ctx.getSource().getEntity() instanceof ServerPlayer p ? p : null) ? 1 : 0;
						}))
						.then(Commands.literal("stop").requires(s -> s.hasPermission(2)).executes(ctx -> {
							HexGame.stop(ctx.getSource().getServer());
							return 1;
						}))
						.then(withTargets("role", "role", ROLE_IDS,
								(ctx, players) -> {
									Role role = Role.byId(StringArgumentType.getString(ctx, "role"));
									if (role == null) {
										fail(ctx, "未知职业");
										return;
									}
									players.forEach(p -> HexGame.setRole(p, role, false));
									ok(ctx, "已设置 " + players.size() + " 名玩家的职业为 " + role.displayName);
								}))
						.then(withTargets("offer", "tier", TIER_IDS,
								(ctx, players) -> {
									Tier tier = parseTier(StringArgumentType.getString(ctx, "tier"));
									if (tier == null) {
										fail(ctx, "未知品质：silver / gold / prismatic");
										return;
									}
									players.forEach(p -> HexGame.grantOffer(p, tier, true));
									ok(ctx, "已向 " + players.size() + " 名玩家发放【" + tier.displayName + "】选择");
								}))
						.then(withTargets("give", "hex", HEX_IDS,
								(ctx, players) -> {
									Hex hex = Hexes.get(StringArgumentType.getString(ctx, "hex"));
									if (hex == null) {
										fail(ctx, "未知海克斯");
										return;
									}
									players.forEach(p -> {
										HexGame.addHex(p, hex);
										HexGame.sync(p, 0);
									});
									ok(ctx, "已给予【" + hex.name + "】");
								}))
						.then(withTargets("remove", "hex", HEX_IDS,
								(ctx, players) -> {
									Hex hex = Hexes.get(StringArgumentType.getString(ctx, "hex"));
									if (hex == null) {
										fail(ctx, "未知海克斯");
										return;
									}
									players.forEach(p -> HexGame.removeHex(p, hex));
									ok(ctx, "已移除【" + hex.name + "】");
								}))
						.then(Commands.literal("reset").requires(s -> s.hasPermission(2))
								.executes(ctx -> {
									HexGame.resetPlayer(ctx.getSource().getPlayerOrException());
									return 1;
								})
								.then(Commands.argument("targets", EntityArgument.players()).executes(ctx -> {
									Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
									players.forEach(HexGame::resetPlayer);
									ok(ctx, "已重置 " + players.size() + " 名玩家");
									return players.size();
								})))
						.then(Commands.literal("info")
								.executes(ctx -> info(ctx, ctx.getSource().getPlayerOrException()))
								.then(Commands.argument("target", EntityArgument.player())
										.executes(ctx -> info(ctx, EntityArgument.getPlayer(ctx, "target")))))
						.then(Commands.literal("list").executes(HexCommands::list))
		));
	}

	private interface PlayersAction {
		void run(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) throws CommandSyntaxException;
	}

	/** 管理员命令 “/hex &lt;name&gt; &lt;arg&gt; [玩家]”：不写玩家时作用于自己。 */
	private static LiteralArgumentBuilder<CommandSourceStack> withTargets(
			String name, String argName, SuggestionProvider<CommandSourceStack> suggestions, PlayersAction action) {
		return Commands.literal(name).requires(s -> s.hasPermission(2))
				.then(Commands.argument(argName, StringArgumentType.word()).suggests(suggestions)
						.executes(ctx -> {
							if (!requireActive(ctx)) {
								return 0;
							}
							action.run(ctx, List.of(ctx.getSource().getPlayerOrException()));
							return 1;
						})
						.then(Commands.argument("targets", EntityArgument.players()).executes(ctx -> {
							if (!requireActive(ctx)) {
								return 0;
							}
							Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
							action.run(ctx, players);
							return players.size();
						})));
	}

	/** 模组关闭（没有进行中的对局）时，海克斯相关命令一律不执行。 */
	private static boolean requireActive(CommandContext<CommandSourceStack> ctx) {
		if (HexGame.active()) {
			return true;
		}
		fail(ctx, "海克斯大乱斗未开启，请先使用 /hex start");
		return false;
	}

	private static Tier parseTier(String name) {
		for (Tier tier : Tier.values()) {
			if (tier.name().equalsIgnoreCase(name)) {
				return tier;
			}
		}
		return null;
	}

	private static int info(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		HexData data = HexGame.data(player);
		Role role = data.role();
		MutableComponent text = Component.literal(player.getGameProfile().getName() + " · ")
				.append(role == null ? Component.literal("未选职业").withStyle(ChatFormatting.GRAY)
						: Component.literal(role.displayName).withColor(role.color));
		for (String id : data.hexes) {
			Hex hex = Hexes.get(id);
			if (hex == null) {
				continue;
			}
			text.append(Component.literal("\n【" + hex.name + "】").withStyle(hex.tier.format));
			if (hex.maxStacks > 0) {
				text.append(Component.literal(" " + data.stacks(hex) + " 层").withStyle(ChatFormatting.YELLOW));
			}
			text.append(Component.literal(" " + hex.description).withStyle(ChatFormatting.GRAY));
		}
		for (Hexes.HexSet set : Hexes.SETS) {
			if (set.activeFor(data.hexes)) {
				text.append(Component.literal("\n套装【" + set.name() + "】" + set.description()).withStyle(ChatFormatting.GREEN));
			}
		}
		ctx.getSource().sendSuccess(() -> text, false);
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		MutableComponent text = Component.literal("全部海克斯：");
		for (Hex hex : Hexes.BY_ID.values()) {
			text.append(Component.literal("\n[" + hex.tier.displayName + "] " + hex.name).withStyle(hex.tier.format))
					.append(Component.literal(" (" + hex.id + ") ").withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.literal(hex.description).withStyle(ChatFormatting.GRAY));
		}
		ctx.getSource().sendSuccess(() -> text, false);
		return Hexes.BY_ID.size();
	}

	private static void ok(CommandContext<CommandSourceStack> ctx, String message) {
		ctx.getSource().sendSuccess(() -> Component.literal(message), true);
	}

	private static void fail(CommandContext<CommandSourceStack> ctx, String message) {
		ctx.getSource().sendFailure(Component.literal(message));
	}

	private HexCommands() {
	}
}
