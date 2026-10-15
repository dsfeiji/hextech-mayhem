package com.hexmayhem.net;

import com.hexmayhem.HexMayhem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.UUID;

public final class HexPayloads {
	public static final int OPEN_NONE = 0;
	public static final int OPEN_ROLE = 1;
	public static final int OPEN_OFFER = 2;

	/**
	 * 服务端 → 客户端：自定义房间快照（有变化时发给所有人）。
	 * members 是所有在线玩家，team 0 蓝 / 1 红 / -1 待定；popup 为 true 时客户端弹出房间界面；host 可能为 null。
	 */
	public record Lobby(boolean open, boolean popup, boolean canManage, UUID host, List<Member> members)
			implements CustomPacketPayload {
		public record Member(UUID id, String name, int team, int slot) {
			private void write(FriendlyByteBuf buf) {
				buf.writeUUID(id);
				buf.writeUtf(name);
				buf.writeVarInt(team + 1);
				buf.writeVarInt(slot + 1);
			}

			private static Member read(FriendlyByteBuf buf) {
				return new Member(buf.readUUID(), buf.readUtf(), buf.readVarInt() - 1, buf.readVarInt() - 1);
			}
		}

		public static final Type<Lobby> TYPE = new Type<>(HexMayhem.id("lobby"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Lobby> CODEC = StreamCodec.ofMember(Lobby::write, Lobby::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeBoolean(open);
			buf.writeBoolean(popup);
			buf.writeBoolean(canManage);
			buf.writeBoolean(host != null);
			if (host != null) {
				buf.writeUUID(host);
			}
			buf.writeCollection(members, (b, m) -> m.write(b));
		}

		private static Lobby read(FriendlyByteBuf buf) {
			boolean open = buf.readBoolean(), popup = buf.readBoolean(), canManage = buf.readBoolean();
			UUID host = buf.readBoolean() ? buf.readUUID() : null;
			return new Lobby(open, popup, canManage, host, buf.readList(Member::read));
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** 客户端 → 服务端：房间操作。SEAT 用 team / slot，KICK 用 target，其余不需要参数。 */
	public record LobbyAction(int action, int team, int slot, UUID target) implements CustomPacketPayload {
		public static final int SEAT = 0;
		public static final int UNSEAT = 1;
		public static final int KICK = 2;
		public static final int SHUFFLE = 3;
		public static final int START = 4;
		public static final int DISBAND = 5;

		public static final Type<LobbyAction> TYPE = new Type<>(HexMayhem.id("lobby_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, LobbyAction> CODEC = StreamCodec.ofMember(LobbyAction::write, LobbyAction::read);

		public static LobbyAction of(int action) {
			return new LobbyAction(action, 0, 0, null);
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(action);
			buf.writeVarInt(team);
			buf.writeVarInt(slot);
			buf.writeBoolean(target != null);
			if (target != null) {
				buf.writeUUID(target);
			}
		}

		private static LobbyAction read(FriendlyByteBuf buf) {
			return new LobbyAction(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean() ? buf.readUUID() : null);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** 服务端 → 客户端：同步当前玩家的全部海克斯状态。 */
	/** offerLocked：当前三选一需要阵亡后才能选择，且玩家现在没有阵亡。 */
	public record Sync(String role, List<String> hexes, List<Integer> stacks, int offerTier, List<String> offerIds,
					   boolean offerLocked, int rerolls, int pendingOffers, int nextOfferLevel, int open)
			implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(HexMayhem.id("sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.ofMember(Sync::write, Sync::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeUtf(role);
			buf.writeCollection(hexes, FriendlyByteBuf::writeUtf);
			buf.writeCollection(stacks, FriendlyByteBuf::writeVarInt);
			buf.writeVarInt(offerTier + 1);
			buf.writeCollection(offerIds, FriendlyByteBuf::writeUtf);
			buf.writeBoolean(offerLocked);
			buf.writeVarInt(rerolls);
			buf.writeVarInt(pendingOffers);
			buf.writeVarInt(nextOfferLevel + 1);
			buf.writeVarInt(open);
		}

		private static Sync read(FriendlyByteBuf buf) {
			return new Sync(buf.readUtf(), buf.readList(FriendlyByteBuf::readUtf), buf.readList(FriendlyByteBuf::readVarInt),
					buf.readVarInt() - 1, buf.readList(FriendlyByteBuf::readUtf), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt() - 1, buf.readVarInt());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** 服务端 → 所有客户端：全体战绩（每秒一次，击杀时立即发送）。gameTicks 为 -1 表示没有进行中的对局。 */
	public record Scoreboard(int gameTicks, List<Entry> entries) implements CustomPacketPayload {
		public record Entry(java.util.UUID id, String name, String role, List<String> hexes,
							int kills, int deaths, int assists, int damage, int level, int respawnSeconds) {
			private void write(FriendlyByteBuf buf) {
				buf.writeUUID(id);
				buf.writeUtf(name);
				buf.writeUtf(role);
				buf.writeCollection(hexes, FriendlyByteBuf::writeUtf);
				buf.writeVarInt(kills);
				buf.writeVarInt(deaths);
				buf.writeVarInt(assists);
				buf.writeVarInt(damage);
				buf.writeVarInt(level);
				buf.writeVarInt(respawnSeconds);
			}

			private static Entry read(FriendlyByteBuf buf) {
				return new Entry(buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readList(FriendlyByteBuf::readUtf),
						buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
			}
		}

		public static final Type<Scoreboard> TYPE = new Type<>(HexMayhem.id("scoreboard"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Scoreboard> CODEC = StreamCodec.ofMember(Scoreboard::write, Scoreboard::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(gameTicks + 1);
			buf.writeCollection(entries, (b, e) -> e.write(b));
		}

		private static Scoreboard read(FriendlyByteBuf buf) {
			return new Scoreboard(buf.readVarInt() - 1, buf.readList(Entry::read));
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * 服务端 → 所有客户端：击杀播报。killer 为 null 表示没有击杀者（摔死、自杀等）。
	 * multiKill：1 普通击杀，2~5 双杀~五杀；killStreak：击杀者本条命内的连杀数；
	 * shutdown：被杀者死前正在连杀（≥3），即“终结”。
	 */
	public record KillFeed(java.util.UUID killer, String killerName, String killerRole,
						   java.util.UUID victim, String victimName, String victimRole,
						   int assists, int multiKill, int killStreak, boolean firstBlood, boolean shutdown)
			implements CustomPacketPayload {
		public static final Type<KillFeed> TYPE = new Type<>(HexMayhem.id("kill_feed"));
		public static final StreamCodec<RegistryFriendlyByteBuf, KillFeed> CODEC = StreamCodec.ofMember(KillFeed::write, KillFeed::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeBoolean(killer != null);
			if (killer != null) {
				buf.writeUUID(killer);
			}
			buf.writeUtf(killerName);
			buf.writeUtf(killerRole);
			buf.writeUUID(victim);
			buf.writeUtf(victimName);
			buf.writeUtf(victimRole);
			buf.writeVarInt(assists);
			buf.writeVarInt(multiKill);
			buf.writeVarInt(killStreak);
			buf.writeBoolean(firstBlood);
			buf.writeBoolean(shutdown);
		}

		private static KillFeed read(FriendlyByteBuf buf) {
			java.util.UUID killer = buf.readBoolean() ? buf.readUUID() : null;
			return new KillFeed(killer, buf.readUtf(), buf.readUtf(), buf.readUUID(), buf.readUtf(), buf.readUtf(),
					buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** 服务端 → 客户端：复活倒计时（刻），0 表示已复活。死亡时和之后每秒发送一次，客户端在两次之间自行倒数。 */
	public record Respawn(int ticksLeft, int totalTicks) implements CustomPacketPayload {
		public static final Type<Respawn> TYPE = new Type<>(HexMayhem.id("respawn"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Respawn> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Respawn::ticksLeft, ByteBufCodecs.VAR_INT, Respawn::totalTicks, Respawn::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** 客户端 → 服务端：选择职业。 */
	public record ChooseRole(String role) implements CustomPacketPayload {
		public static final Type<ChooseRole> TYPE = new Type<>(HexMayhem.id("choose_role"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ChooseRole> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, ChooseRole::role, ChooseRole::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** 客户端 → 服务端：从当前三选一中选择第 index 个。 */
	public record PickHex(int index) implements CustomPacketPayload {
		public static final Type<PickHex> TYPE = new Type<>(HexMayhem.id("pick_hex"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PickHex> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, PickHex::index, PickHex::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** 客户端 → 服务端：刷新当前三选一。 */
	public record Reroll() implements CustomPacketPayload {
		public static final Type<Reroll> TYPE = new Type<>(HexMayhem.id("reroll"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Reroll> CODEC = StreamCodec.unit(new Reroll());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private HexPayloads() {
	}
}
