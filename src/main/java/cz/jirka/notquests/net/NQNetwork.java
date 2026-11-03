package cz.jirka.notquests.net;

import com.mojang.logging.LogUtils;
import cz.jirka.notquests.NotQuests;
import cz.jirka.notquests.server.ServerQuestManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** All NotQuests packets. Client handlers live in {@code client.ClientHandlers} and are only touched on the client. */
public final class NQNetwork {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String VERSION = "1";

    private NQNetwork() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(NotQuests.MOD_ID, name));
    }

    // ================================================================== server -> client

    /** The whole quest file, gzipped NBT. */
    public record SyncQuests(byte[] data) implements CustomPacketPayload {
        public static final Type<SyncQuests> TYPE = typeOf("sync_quests");
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncQuests> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeByteArray(p.data), buf -> new SyncQuests(buf.readByteArray()));

        @Override
        public Type<SyncQuests> type() {
            return TYPE;
        }
    }

    /** Progress and members of the receiving player's team. */
    public record SyncTeam(CompoundTag data) implements CustomPacketPayload {
        public static final Type<SyncTeam> TYPE = typeOf("sync_team");
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncTeam> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeNbt(p.data), buf -> new SyncTeam(orEmpty(buf.readNbt())));

        @Override
        public Type<SyncTeam> type() {
            return TYPE;
        }
    }

    /** A quest, chapter or the whole file was completed by the player's team. */
    public record ObjectCompleted(long id) implements CustomPacketPayload {
        public static final Type<ObjectCompleted> TYPE = typeOf("completed");
        public static final StreamCodec<RegistryFriendlyByteBuf, ObjectCompleted> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeLong(p.id), buf -> new ObjectCompleted(buf.readLong()));

        @Override
        public Type<ObjectCompleted> type() {
            return TYPE;
        }
    }

    public record Toast(Component title, Component text, ItemStack icon) implements CustomPacketPayload {
        public static final Type<Toast> TYPE = typeOf("toast");
        public static final StreamCodec<RegistryFriendlyByteBuf, Toast> CODEC = StreamCodec.of(
                (buf, p) -> {
                    ComponentSerialization.STREAM_CODEC.encode(buf, p.title);
                    ComponentSerialization.STREAM_CODEC.encode(buf, p.text);
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.icon);
                },
                buf -> new Toast(ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));

        @Override
        public Type<Toast> type() {
            return TYPE;
        }
    }

    public record EditMode(boolean editing) implements CustomPacketPayload {
        public static final Type<EditMode> TYPE = typeOf("edit_mode");
        public static final StreamCodec<RegistryFriendlyByteBuf, EditMode> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeBoolean(p.editing), buf -> new EditMode(buf.readBoolean()));

        @Override
        public Type<EditMode> type() {
            return TYPE;
        }
    }

    public record OpenBook(long focus) implements CustomPacketPayload {
        public static final Type<OpenBook> TYPE = typeOf("open_book");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenBook> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeLong(p.focus), buf -> new OpenBook(buf.readLong()));

        @Override
        public Type<OpenBook> type() {
            return TYPE;
        }
    }

    public record RegistryList(String kind, List<String> ids) implements CustomPacketPayload {
        public static final Type<RegistryList> TYPE = typeOf("registry_list");
        public static final StreamCodec<RegistryFriendlyByteBuf, RegistryList> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.kind);
                    buf.writeVarInt(p.ids.size());
                    for (String s : p.ids) {
                        buf.writeUtf(s);
                    }
                },
                buf -> {
                    String kind = buf.readUtf();
                    int n = buf.readVarInt();
                    List<String> ids = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        ids.add(buf.readUtf());
                    }
                    return new RegistryList(kind, ids);
                });

        @Override
        public Type<RegistryList> type() {
            return TYPE;
        }
    }

    // ================================================================== client -> server

    public record SubmitTask(long taskId) implements CustomPacketPayload {
        public static final Type<SubmitTask> TYPE = typeOf("submit_task");
        public static final StreamCodec<RegistryFriendlyByteBuf, SubmitTask> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeLong(p.taskId), buf -> new SubmitTask(buf.readLong()));

        @Override
        public Type<SubmitTask> type() {
            return TYPE;
        }
    }

    public record ClaimReward(long rewardId, int choice) implements CustomPacketPayload {
        public static final Type<ClaimReward> TYPE = typeOf("claim_reward");
        public static final StreamCodec<RegistryFriendlyByteBuf, ClaimReward> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeLong(p.rewardId);
                    buf.writeVarInt(p.choice + 1);
                }, buf -> new ClaimReward(buf.readLong(), buf.readVarInt() - 1));

        @Override
        public Type<ClaimReward> type() {
            return TYPE;
        }
    }

    public record ClaimAll(long scope) implements CustomPacketPayload {
        public static final Type<ClaimAll> TYPE = typeOf("claim_all");
        public static final StreamCodec<RegistryFriendlyByteBuf, ClaimAll> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeLong(p.scope), buf -> new ClaimAll(buf.readLong()));

        @Override
        public Type<ClaimAll> type() {
            return TYPE;
        }
    }

    public record Observed(long taskId) implements CustomPacketPayload {
        public static final Type<Observed> TYPE = typeOf("observed");
        public static final StreamCodec<RegistryFriendlyByteBuf, Observed> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeLong(p.taskId), buf -> new Observed(buf.readLong()));

        @Override
        public Type<Observed> type() {
            return TYPE;
        }
    }

    public record PinQuest(long questId, boolean pin) implements CustomPacketPayload {
        public static final Type<PinQuest> TYPE = typeOf("pin_quest");
        public static final StreamCodec<RegistryFriendlyByteBuf, PinQuest> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeLong(p.questId);
                    buf.writeBoolean(p.pin);
                }, buf -> new PinQuest(buf.readLong(), buf.readBoolean()));

        @Override
        public Type<PinQuest> type() {
            return TYPE;
        }
    }

    /** Editor: new properties for an existing object. */
    public record EditObject(long id, CompoundTag data) implements CustomPacketPayload {
        public static final Type<EditObject> TYPE = typeOf("edit_object");
        public static final StreamCodec<RegistryFriendlyByteBuf, EditObject> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeLong(p.id);
                    buf.writeNbt(p.data);
                }, buf -> new EditObject(buf.readLong(), orEmpty(buf.readNbt())));

        @Override
        public Type<EditObject> type() {
            return TYPE;
        }
    }

    /** Editor: create an object (its new id is inside {@code data}). */
    public record CreateObject(int objectType, long parent, String subType, CompoundTag data) implements CustomPacketPayload {
        public static final Type<CreateObject> TYPE = typeOf("create_object");
        public static final StreamCodec<RegistryFriendlyByteBuf, CreateObject> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.objectType);
                    buf.writeLong(p.parent);
                    buf.writeUtf(p.subType);
                    buf.writeNbt(p.data);
                }, buf -> new CreateObject(buf.readVarInt(), buf.readLong(), buf.readUtf(), orEmpty(buf.readNbt())));

        @Override
        public Type<CreateObject> type() {
            return TYPE;
        }
    }

    /** Editor: simple actions on an object, see {@link Actions}. */
    public record EditAction(int action, long id, long arg) implements CustomPacketPayload {
        public static final Type<EditAction> TYPE = typeOf("edit_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, EditAction> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.action);
                    buf.writeLong(p.id);
                    buf.writeLong(p.arg);
                }, buf -> new EditAction(buf.readVarInt(), buf.readLong(), buf.readLong()));

        @Override
        public Type<EditAction> type() {
            return TYPE;
        }
    }

    public record MoveQuests(long[] ids, double[] xs, double[] ys) implements CustomPacketPayload {
        public static final Type<MoveQuests> TYPE = typeOf("move_quests");
        public static final StreamCodec<RegistryFriendlyByteBuf, MoveQuests> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.ids.length);
                    for (int i = 0; i < p.ids.length; i++) {
                        buf.writeLong(p.ids[i]);
                        buf.writeDouble(p.xs[i]);
                        buf.writeDouble(p.ys[i]);
                    }
                },
                buf -> {
                    int n = Math.min(buf.readVarInt(), 10000);
                    long[] ids = new long[n];
                    double[] xs = new double[n];
                    double[] ys = new double[n];
                    for (int i = 0; i < n; i++) {
                        ids[i] = buf.readLong();
                        xs[i] = buf.readDouble();
                        ys[i] = buf.readDouble();
                    }
                    return new MoveQuests(ids, xs, ys);
                });

        @Override
        public Type<MoveQuests> type() {
            return TYPE;
        }
    }

    public record RequestRegistryList(String kind) implements CustomPacketPayload {
        public static final Type<RequestRegistryList> TYPE = typeOf("request_registry_list");
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestRegistryList> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.kind), buf -> new RequestRegistryList(buf.readUtf()));

        @Override
        public Type<RequestRegistryList> type() {
            return TYPE;
        }
    }

    public static final class Actions {
        public static final int DELETE = 0;
        public static final int MOVE_UP = 1;
        public static final int MOVE_DOWN = 2;
        public static final int MOVE_QUEST_TO_CHAPTER = 3;
        public static final int COMPLETE = 4;
        public static final int RESET = 5;
        public static final int ADD_DEPENDENCY = 6;
        public static final int REMOVE_DEPENDENCY = 7;
        public static final int SET_EDIT_MODE = 8;
        public static final int CLEAR_DEPENDENCIES = 9;

        private Actions() {
        }
    }

    // ================================================================== registration

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(VERSION);

        r.playToClient(SyncQuests.TYPE, SyncQuests.CODEC, (p, ctx) -> cz.jirka.notquests.client.ClientHandlers.syncQuests(p));
        r.playToClient(SyncTeam.TYPE, SyncTeam.CODEC, (p, ctx) -> cz.jirka.notquests.client.ClientHandlers.syncTeam(p));
        r.playToClient(ObjectCompleted.TYPE, ObjectCompleted.CODEC, (p, ctx) -> cz.jirka.notquests.client.ClientHandlers.completed(p));
        r.playToClient(Toast.TYPE, Toast.CODEC, (p, ctx) -> cz.jirka.notquests.client.ClientHandlers.toast(p));
        r.playToClient(EditMode.TYPE, EditMode.CODEC, (p, ctx) -> cz.jirka.notquests.client.ClientHandlers.editMode(p));
        r.playToClient(OpenBook.TYPE, OpenBook.CODEC, (p, ctx) -> cz.jirka.notquests.client.ClientHandlers.openBook(p));
        r.playToClient(RegistryList.TYPE, RegistryList.CODEC, (p, ctx) -> cz.jirka.notquests.client.ClientHandlers.registryList(p));

        r.playToServer(SubmitTask.TYPE, SubmitTask.CODEC, (p, ctx) -> server(ctx, (m, pl) -> m.handleSubmit(pl, p.taskId())));
        r.playToServer(ClaimReward.TYPE, ClaimReward.CODEC, (p, ctx) -> server(ctx, (m, pl) -> m.handleClaim(pl, p.rewardId(), p.choice())));
        r.playToServer(ClaimAll.TYPE, ClaimAll.CODEC, (p, ctx) -> server(ctx, (m, pl) -> m.handleClaimAll(pl, p.scope())));
        r.playToServer(Observed.TYPE, Observed.CODEC, (p, ctx) -> server(ctx, (m, pl) -> m.handleObserved(pl, p.taskId())));
        r.playToServer(PinQuest.TYPE, PinQuest.CODEC, (p, ctx) -> server(ctx, (m, pl) -> m.handlePin(pl, p.questId(), p.pin())));
        r.playToServer(EditObject.TYPE, EditObject.CODEC, (p, ctx) -> server(ctx, (m, pl) -> m.handleEditObject(pl, p.id(), p.data())));
        r.playToServer(CreateObject.TYPE, CreateObject.CODEC, (p, ctx) -> server(ctx, (m, pl) -> m.handleCreate(pl, p.objectType(), p.parent(), p.subType(), p.data())));
        r.playToServer(EditAction.TYPE, EditAction.CODEC, (p, ctx) -> server(ctx, (m, pl) -> m.handleAction(pl, p.action(), p.id(), p.arg())));
        r.playToServer(MoveQuests.TYPE, MoveQuests.CODEC, (p, ctx) -> server(ctx, (m, pl) -> m.handleMove(pl, p.ids(), p.xs(), p.ys())));
        r.playToServer(RequestRegistryList.TYPE, RequestRegistryList.CODEC, (p, ctx) -> server(ctx, (m, pl) -> m.handleRegistryRequest(pl, p.kind())));
    }

    private static void server(IPayloadContext ctx, BiConsumer<ServerQuestManager, ServerPlayer> action) {
        if (ctx.player() instanceof ServerPlayer player) {
            ServerQuestManager m = ServerQuestManager.get();
            if (m != null) {
                try {
                    action.accept(m, player);
                } catch (Exception e) {
                    LOGGER.error("Error handling NotQuests packet from {}", player.getGameProfile().getName(), e);
                }
            }
        }
    }

    private static CompoundTag orEmpty(CompoundTag tag) {
        return tag == null ? new CompoundTag() : tag;
    }

    // ================================================================== helpers

    public static byte[] compress(CompoundTag tag) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static CompoundTag decompress(byte[] data) {
        try {
            return NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void sendToast(ServerPlayer player, Component title, Component text, ItemStack icon) {
        PacketDistributor.sendToPlayer(player, new Toast(title, text, icon));
    }
}
