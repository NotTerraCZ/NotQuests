package cz.jirka.notquests.config;

import cz.jirka.notquests.quest.NamedEnum;
import cz.jirka.notquests.quest.WeightedItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A single editable property shown in the in-game editor. Values are applied to the edited object
 * only when the editor screen is saved.
 */
public abstract class ConfigValue<T> {
    public final String id;
    public T value;
    public final T defaultValue;
    private final Consumer<T> setter;
    private Component label;

    protected ConfigValue(String id, T value, T defaultValue, Consumer<T> setter) {
        this.id = id;
        this.value = value;
        this.defaultValue = defaultValue;
        this.setter = setter;
    }

    public Component getLabel() {
        return label != null ? label : Component.translatable("notquests.config." + id);
    }

    public ConfigValue<T> label(Component c) {
        this.label = c;
        return this;
    }

    public String tooltipKey() {
        return "notquests.config." + id + ".tooltip";
    }

    public void apply() {
        setter.accept(value);
    }

    public static class StringValue extends ConfigValue<String> {
        public StringValue(String id, String value, String def, Consumer<String> setter) {
            super(id, value, def, setter);
        }
    }

    public static class BoolValue extends ConfigValue<Boolean> {
        public BoolValue(String id, boolean value, boolean def, Consumer<Boolean> setter) {
            super(id, value, def, setter);
        }
    }

    public static class IntValue extends ConfigValue<Integer> {
        public final int min;
        public final int max;

        public IntValue(String id, int value, int def, int min, int max, Consumer<Integer> setter) {
            super(id, value, def, setter);
            this.min = min;
            this.max = max;
        }
    }

    public static class LongValue extends ConfigValue<Long> {
        public final long min;
        public final long max;

        public LongValue(String id, long value, long def, long min, long max, Consumer<Long> setter) {
            super(id, value, def, setter);
            this.min = min;
            this.max = max;
        }
    }

    public static class DoubleValue extends ConfigValue<Double> {
        public final double min;
        public final double max;

        public DoubleValue(String id, double value, double def, double min, double max, Consumer<Double> setter) {
            super(id, value, def, setter);
            this.min = min;
            this.max = max;
        }
    }

    public static class EnumValue<E extends Enum<E> & NamedEnum> extends ConfigValue<E> {
        public final E[] options;

        public EnumValue(String id, E value, E def, E[] options, Consumer<E> setter) {
            super(id, value, def, setter);
            this.options = options;
        }

        public void cycle(boolean back) {
            int i = 0;
            for (int j = 0; j < options.length; j++) {
                if (options[j] == value) {
                    i = j;
                    break;
                }
            }
            i = (i + (back ? options.length - 1 : 1)) % options.length;
            value = options[i];
        }
    }

    public static class ItemValue extends ConfigValue<ItemStack> {
        public final boolean allowEmpty;
        /** Shown when no item is set. */
        public String emptyLabel = "notquests.none";
        public java.util.function.Supplier<ItemStack> emptyPreview = () -> ItemStack.EMPTY;

        public ItemValue(String id, ItemStack value, boolean allowEmpty, Consumer<ItemStack> setter) {
            super(id, value.copy(), ItemStack.EMPTY, setter);
            this.allowEmpty = allowEmpty;
        }
    }

    public static class TextListValue extends ConfigValue<List<String>> {
        public TextListValue(String id, List<String> value, Consumer<List<String>> setter) {
            super(id, new ArrayList<>(value), new ArrayList<>(), setter);
        }
    }

    public static class IdValue extends ConfigValue<String> {
        public final IdKind kind;

        public IdValue(String id, String value, String def, IdKind kind, Consumer<String> setter) {
            super(id, value, def, setter);
            this.kind = kind;
        }
    }

    /** Pick one of a few objects (reward tables, chapter groups) by id. */
    public static class ChoiceValue extends ConfigValue<Long> {
        public final List<Long> ids;
        public final List<Component> names;

        public ChoiceValue(String id, long value, List<Long> ids, List<Component> names, Consumer<Long> setter) {
            super(id, value, 0L, setter);
            this.ids = ids;
            this.names = names;
        }

        public Component currentName() {
            int i = ids.indexOf(value);
            return i >= 0 ? names.get(i) : Component.literal("-");
        }

        public void cycle(boolean back) {
            if (ids.isEmpty()) {
                return;
            }
            int i = Math.max(0, ids.indexOf(value));
            i = (i + (back ? ids.size() - 1 : 1)) % ids.size();
            value = ids.get(i);
        }
    }

    public static class WeightedItemsValue extends ConfigValue<List<WeightedItem>> {
        public WeightedItemsValue(String id, List<WeightedItem> value, Consumer<List<WeightedItem>> setter) {
            super(id, copyList(value), new ArrayList<>(), setter);
        }

        private static List<WeightedItem> copyList(List<WeightedItem> list) {
            List<WeightedItem> l = new ArrayList<>();
            for (WeightedItem w : list) {
                l.add(w.copy());
            }
            return l;
        }
    }
}
