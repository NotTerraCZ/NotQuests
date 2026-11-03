package cz.jirka.notquests.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import cz.jirka.notquests.net.NQNetwork;
import cz.jirka.notquests.progress.Team;
import cz.jirka.notquests.quest.Chapter;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.quest.QuestObjectBase;
import cz.jirka.notquests.util.IdUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /notquests ... and the short alias /quest ... */
public final class QuestCommands {
    private QuestCommands() {
    }

    private static ServerQuestManager manager() throws CommandSyntaxException {
        ServerQuestManager m = ServerQuestManager.get();
        if (m == null) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("NotQuests is not running")).create();
        }
        return m;
    }

    private static final SuggestionProvider<CommandSourceStack> OBJECTS = (ctx, builder) -> {
        ServerQuestManager m = ServerQuestManager.get();
        List<String> list = new ArrayList<>();
        if (m != null) {
            for (Chapter c : m.file.chapters) {
                list.add(c.filename);
                list.add(c.getCodeString());
            }
            for (Quest q : m.file.allQuests) {
                list.add(q.getCodeString());
            }
        }
        return SharedSuggestionProvider.suggest(list, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> TEAMS = (ctx, builder) -> {
        ServerQuestManager m = ServerQuestManager.get();
        return SharedSuggestionProvider.suggest(m == null ? List.of() : m.partyTeamNames(), builder);
    };

    private static final SuggestionProvider<CommandSourceStack> MEMBERS = (ctx, builder) -> {
        ServerQuestManager m = ServerQuestManager.get();
        List<String> list = new ArrayList<>();
        if (m != null && ctx.getSource().getPlayer() != null) {
            Team t = m.getTeam(ctx.getSource().getPlayer());
            for (UUID u : t.members) {
                list.add(m.data.nameOf(u));
            }
        }
        return SharedSuggestionProvider.suggest(list, builder);
    };

    /** The same command tree is registered as /notquests and as the shorter /quest. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(tree("notquests"));
        dispatcher.register(tree("quest"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        return Commands.literal(name)
                .then(Commands.literal("open_book")
                        .executes(ctx -> openBook(List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("players", EntityArgument.players())
                                .requires(s -> s.hasPermission(2))
                                .executes(ctx -> openBook(EntityArgument.getPlayers(ctx, "players")))))
                .then(Commands.literal("editing_mode")
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> editingMode(ctx.getSource(), ctx.getSource().getPlayerOrException(), null))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> editingMode(ctx.getSource(), ctx.getSource().getPlayerOrException(), BoolArgumentType.getBool(ctx, "enabled")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> editingMode(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), BoolArgumentType.getBool(ctx, "enabled"))))))
                .then(Commands.literal("reload")
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> reload(ctx.getSource())))
                .then(Commands.literal("change_progress")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.literal("reset")
                                        // "*" can't be typed into a word argument, so the whole book has its own literal
                                        .then(Commands.literal("*")
                                                .executes(ctx -> changeProgress(ctx.getSource(), EntityArgument.getPlayers(ctx, "players"), "*", false)))
                                        .then(Commands.argument("quest_object", StringArgumentType.word()).suggests(OBJECTS)
                                                .executes(ctx -> changeProgress(ctx.getSource(), EntityArgument.getPlayers(ctx, "players"), StringArgumentType.getString(ctx, "quest_object"), false))))
                                .then(Commands.literal("complete")
                                        .then(Commands.literal("*")
                                                .executes(ctx -> changeProgress(ctx.getSource(), EntityArgument.getPlayers(ctx, "players"), "*", true)))
                                        .then(Commands.argument("quest_object", StringArgumentType.word()).suggests(OBJECTS)
                                                .executes(ctx -> changeProgress(ctx.getSource(), EntityArgument.getPlayers(ctx, "players"), StringArgumentType.getString(ctx, "quest_object"), true))))))
                .then(Commands.literal("team")
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> reply(ctx.getSource(), manager().createTeam(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "name").trim())))))
                        .then(Commands.literal("invite")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> reply(ctx.getSource(), manager().invite(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player"))))))
                        .then(Commands.literal("join")
                                .then(Commands.argument("name", StringArgumentType.greedyString()).suggests(TEAMS)
                                        .executes(ctx -> reply(ctx.getSource(), manager().join(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "name").trim())))))
                        .then(Commands.literal("leave")
                                .executes(ctx -> reply(ctx.getSource(), manager().leave(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("kick")
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(MEMBERS)
                                        .executes(ctx -> kick(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                        .then(Commands.literal("rename")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> reply(ctx.getSource(), manager().rename(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "name").trim())))))
                        .then(Commands.literal("info")
                                .executes(ctx -> reply(ctx.getSource(), manager().info(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listTeams(ctx.getSource()))));
    }

    private static int reply(CommandSourceStack source, Component message) {
        source.sendSystemMessage(message);
        return 1;
    }

    private static int openBook(Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            PacketDistributor.sendToPlayer(p, new NQNetwork.OpenBook(0L));
        }
        return players.size();
    }

    private static int editingMode(CommandSourceStack source, ServerPlayer player, Boolean enabled) throws CommandSyntaxException {
        ServerQuestManager m = manager();
        boolean value = enabled != null ? enabled : !m.data.editing.contains(player.getUUID());
        m.setEditMode(player, value);
        source.sendSuccess(() -> Component.translatable(value ? "notquests.message.editing_on" : "notquests.message.editing_off", player.getGameProfile().getName()), true);
        return 1;
    }

    private static int reload(CommandSourceStack source) throws CommandSyntaxException {
        ServerQuestManager m = manager();
        m.reload();
        source.sendSuccess(() -> Component.translatable("notquests.message.reloaded", m.file.chapters.size(), m.file.allQuests.size()), true);
        return 1;
    }

    private static int changeProgress(CommandSourceStack source, Collection<ServerPlayer> players, String objectId, boolean complete) throws CommandSyntaxException {
        ServerQuestManager m = manager();
        QuestObjectBase o = null;
        if (objectId.equals("*")) {
            o = m.file;
        } else {
            long id = IdUtil.parseFileId(objectId);
            if (id != 0L) {
                o = m.file.get(id);
            }
            if (o == null) {
                for (Chapter c : m.file.chapters) {
                    if (c.filename.equalsIgnoreCase(objectId)) {
                        o = c;
                        break;
                    }
                }
            }
        }
        if (o == null) {
            source.sendFailure(Component.translatable("notquests.message.unknown_object", objectId));
            return 0;
        }
        java.util.Set<Team> done = new java.util.HashSet<>();
        for (ServerPlayer p : players) {
            Team t = m.getTeam(p);
            if (done.add(t)) {
                if (complete) {
                    m.forceComplete(t, o);
                } else {
                    m.forceReset(t, o);
                }
            }
        }
        QuestObjectBase target = o;
        source.sendSuccess(() -> Component.translatable(complete ? "notquests.message.progress_completed" : "notquests.message.progress_reset", target.getTitle(), done.size()), true);
        return done.size();
    }

    private static int kick(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerQuestManager m = manager();
        ServerPlayer p = source.getPlayerOrException();
        UUID target = null;
        for (Map.Entry<UUID, String> e : m.data.playerNames.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) {
                target = e.getKey();
                break;
            }
        }
        if (target == null) {
            source.sendFailure(Component.translatable("notquests.team.not_member", name));
            return 0;
        }
        return reply(source, m.kick(p, target, name));
    }

    private static int listTeams(CommandSourceStack source) throws CommandSyntaxException {
        ServerQuestManager m = manager();
        List<String> names = m.partyTeamNames();
        if (names.isEmpty()) {
            source.sendSystemMessage(Component.translatable("notquests.team.none").withStyle(ChatFormatting.GRAY));
        } else {
            source.sendSystemMessage(Component.translatable("notquests.team.list", String.join(", ", names)).withStyle(ChatFormatting.AQUA));
        }
        return names.size();
    }
}
