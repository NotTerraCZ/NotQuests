package cz.jirka.notquests;

import cz.jirka.notquests.net.NQNetwork;
import cz.jirka.notquests.registry.ModItems;
import cz.jirka.notquests.server.QuestEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod(NotQuests.MOD_ID)
public class NotQuests {
    public static final String MOD_ID = "notquests";

    public NotQuests(IEventBus modBus, ModContainer container) {
        ModItems.register(modBus);
        modBus.addListener(NQNetwork::register);
        QuestEvents.register(NeoForge.EVENT_BUS);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            cz.jirka.notquests.client.NotQuestsClient.init(modBus, container);
        }
    }
}
