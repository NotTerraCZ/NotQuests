package cz.jirka.notquests.config;

import cz.jirka.notquests.quest.NamedEnum;
import cz.jirka.notquests.quest.WeightedItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Ordered list of editable properties, filled by quest objects for the editor screen. */
public class ConfigGroup {
    public final Component title;
    public final List<ConfigValue<?>> values = new ArrayList<>();

    public ConfigGroup(Component title) {
        this.title = title;
    }

    public <T extends ConfigValue<?>> T add(T value) {
        values.add(value);
        return value;
    }

    public ConfigValue.StringValue addString(String id, String value, String def, Consumer<String> setter) {
        return add(new ConfigValue.StringValue(id, value, def, setter));
    }

    public ConfigValue.BoolValue addBool(String id, boolean value, boolean def, Consumer<Boolean> setter) {
        return add(new ConfigValue.BoolValue(id, value, def, setter));
    }

    public ConfigValue.IntValue addInt(String id, int value, int def, int min, int max, Consumer<Integer> setter) {
        return add(new ConfigValue.IntValue(id, value, def, min, max, setter));
    }

    public ConfigValue.LongValue addLong(String id, long value, long def, long min, long max, Consumer<Long> setter) {
        return add(new ConfigValue.LongValue(id, value, def, min, max, setter));
    }

    public ConfigValue.DoubleValue addDouble(String id, double value, double def, double min, double max, Consumer<Double> setter) {
        return add(new ConfigValue.DoubleValue(id, value, def, min, max, setter));
    }

    public <E extends Enum<E> & NamedEnum> ConfigValue.EnumValue<E> addEnum(String id, E value, E def, E[] options, Consumer<E> setter) {
        return add(new ConfigValue.EnumValue<>(id, value, def, options, setter));
    }

    public ConfigValue.ItemValue addItem(String id, ItemStack value, boolean allowEmpty, Consumer<ItemStack> setter) {
        return add(new ConfigValue.ItemValue(id, value, allowEmpty, setter));
    }

    public ConfigValue.TextListValue addTextList(String id, List<String> value, Consumer<List<String>> setter) {
        return add(new ConfigValue.TextListValue(id, value, setter));
    }

    public ConfigValue.IdValue addId(String id, String value, String def, IdKind kind, Consumer<String> setter) {
        return add(new ConfigValue.IdValue(id, value, def, kind, setter));
    }

    public ConfigValue.ChoiceValue addChoice(String id, long value, List<Long> ids, List<Component> names, Consumer<Long> setter) {
        return add(new ConfigValue.ChoiceValue(id, value, ids, names, setter));
    }

    public ConfigValue.WeightedItemsValue addWeightedItems(String id, List<WeightedItem> value, Consumer<List<WeightedItem>> setter) {
        return add(new ConfigValue.WeightedItemsValue(id, value, setter));
    }

    public void applyAll() {
        for (ConfigValue<?> v : values) {
            v.apply();
        }
    }
}
