package cz.jirka.notquests.quest.task;

import cz.jirka.notquests.quest.Quest;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;
import java.util.function.Supplier;

public record TaskType(String id, Function<Quest, Task> factory, Supplier<ItemStack> icon) {
    public Component displayName() {
        return Component.translatable("notquests.task." + id);
    }

    public Task create(Quest quest) {
        return factory.apply(quest);
    }
}
