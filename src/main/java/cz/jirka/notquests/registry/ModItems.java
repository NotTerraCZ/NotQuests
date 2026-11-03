package cz.jirka.notquests.registry;

import cz.jirka.notquests.NotQuests;
import cz.jirka.notquests.item.QuestBookItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NotQuests.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NotQuests.MOD_ID);

    public static final DeferredItem<QuestBookItem> BOOK = ITEMS.registerItem("book", QuestBookItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    /** Only the icon of loot table rewards, so it is not in the creative tab. */
    public static final DeferredItem<Item> LOOT_TABLE_ICON = ITEMS.registerSimpleItem("loot_table_icon",
            new Item.Properties().stacksTo(1));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.notquests"))
            .icon(() -> new ItemStack(BOOK.get()))
            .displayItems((params, output) -> output.accept(BOOK.get()))
            .build());

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
