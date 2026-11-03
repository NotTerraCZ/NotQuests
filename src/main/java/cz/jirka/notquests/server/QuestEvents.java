package cz.jirka.notquests.server;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Game events that drive quest progress. Registered on the NeoForge event bus. */
public final class QuestEvents {
    private QuestEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(QuestEvents::onServerStarting);
        bus.addListener(QuestEvents::onServerStopped);
        bus.addListener(QuestEvents::onServerTick);
        bus.addListener(QuestEvents::onLogin);
        bus.addListener(QuestEvents::onLogout);
        bus.addListener(QuestEvents::onDeath);
        bus.addListener(QuestEvents::onCraft);
        bus.addListener(QuestEvents::onAdvancement);
        bus.addListener(QuestEvents::onCommands);
    }

    private static void onServerStarting(ServerStartingEvent event) {
        ServerQuestManager.start(event.getServer());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        ServerQuestManager.stop();
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        ServerQuestManager m = ServerQuestManager.get();
        if (m != null) {
            m.tick();
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        ServerQuestManager m = ServerQuestManager.get();
        if (m != null && event.getEntity() instanceof ServerPlayer p) {
            m.onLogin(p);
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerQuestManager m = ServerQuestManager.get();
        if (m != null && event.getEntity() instanceof ServerPlayer p) {
            m.onLogout(p);
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        ServerQuestManager m = ServerQuestManager.get();
        if (m != null && !event.getEntity().level().isClientSide() && event.getSource().getEntity() instanceof ServerPlayer p && !(p.isFakePlayer())) {
            m.onKill(p, event.getEntity());
        }
    }

    private static void onCraft(PlayerEvent.ItemCraftedEvent event) {
        ServerQuestManager m = ServerQuestManager.get();
        if (m != null && event.getEntity() instanceof ServerPlayer p) {
            m.onCraft(p, event.getCrafting());
        }
    }

    private static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        ServerQuestManager m = ServerQuestManager.get();
        if (m != null && event.getEntity() instanceof ServerPlayer p) {
            m.onAdvancement(p);
        }
    }

    private static void onCommands(RegisterCommandsEvent event) {
        QuestCommands.register(event.getDispatcher());
    }
}
