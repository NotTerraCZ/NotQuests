package cz.jirka.notquests.client;

import com.mojang.blaze3d.platform.InputConstants;
import cz.jirka.notquests.NotQuests;
import cz.jirka.notquests.client.gui.InventoryBookButton;
import cz.jirka.notquests.util.SidedHooks;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

public final class NotQuestsClient {
    public static final KeyMapping OPEN_KEY = new KeyMapping("key.notquests.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, "key.categories.notquests");

    private NotQuestsClient() {
    }

    public static void init(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(NotQuestsClient::registerKeys);
        modBus.addListener(NotQuestsClient::registerLayers);
        NeoForge.EVENT_BUS.addListener(NotQuestsClient::clientTick);
        NeoForge.EVENT_BUS.addListener(NotQuestsClient::screenInit);
        NeoForge.EVENT_BUS.addListener(NotQuestsClient::loggingOut);
        NeoForge.EVENT_BUS.addListener(cz.jirka.notquests.client.compat.RecipeViewers::onScreenOpening);
        SidedHooks.advancementDisplay = NotQuestsClient::advancementDisplay;
        SidedHooks.languageCode = () -> Minecraft.getInstance().getLanguageManager().getSelected();
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_KEY);
    }

    private static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, ResourceLocation.fromNamespaceAndPath(NotQuests.MOD_ID, "pinned_quests"), PinnedHud::render);
    }

    private static void clientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_KEY.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                ClientQuestData.openQuestScreen();
            }
        }
        ObservationTracker.tick();
        if (AutoTest.ENABLED) {
            AutoTest.tick();
        }
    }

    private static void screenInit(ScreenEvent.Init.Post event) {
        if (!ClientConfig.INVENTORY_BUTTON.get()) {
            return;
        }
        if (event.getScreen() instanceof InventoryScreen || event.getScreen() instanceof CreativeModeInventoryScreen) {
            // middle of the left screen edge
            event.addListener(new InventoryBookButton(4, event.getScreen().height / 2 - 10));
        }
    }

    private static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientQuestData.clear();
        ObservationTracker.clear();
    }

    private static Optional<SidedHooks.AdvancementDisplay> advancementDisplay(ResourceLocation id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return Optional.empty();
        }
        AdvancementNode node = mc.getConnection().getAdvancements().getTree().get(id);
        if (node == null) {
            return Optional.empty();
        }
        return node.holder().value().display().map(d -> new SidedHooks.AdvancementDisplay(d.getTitle(), d.getIcon()));
    }
}
