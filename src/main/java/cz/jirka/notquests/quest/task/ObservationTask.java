package cz.jirka.notquests.quest.task;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.config.IdKind;
import cz.jirka.notquests.progress.TeamData;
import cz.jirka.notquests.quest.NamedEnum;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.util.TextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/** Look at a block or entity for a while. Detected on the client, confirmed by the server. */
public class ObservationTask extends Task {
    public enum ObserveType implements NamedEnum {
        BLOCK("block"),
        ENTITY("entity");

        public static final ObserveType[] VALUES = values();
        private final String id;

        ObserveType(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    public ObserveType observeType = ObserveType.BLOCK;
    public String target = "minecraft:diamond_ore";
    public long timer = 20L;

    public ObservationTask(Quest quest) {
        super(quest);
    }

    @Override
    public TaskType getType() {
        return TaskTypes.OBSERVATION;
    }

    public boolean matchesBlock(BlockState state) {
        if (observeType != ObserveType.BLOCK) {
            return false;
        }
        ResourceLocation rl = TextUtil.rl(target);
        if (rl == null) {
            return false;
        }
        if (target.startsWith("#")) {
            return state.is(TagKey.create(Registries.BLOCK, rl));
        }
        return rl.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    public boolean matchesEntity(Entity e) {
        if (observeType != ObserveType.ENTITY) {
            return false;
        }
        ResourceLocation rl = TextUtil.rl(target);
        if (rl == null) {
            return false;
        }
        if (target.startsWith("#")) {
            return e.getType().is(TagKey.create(Registries.ENTITY_TYPE, rl));
        }
        return rl.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
    }

    private Component targetName() {
        if (target.startsWith("#")) {
            // "#minecraft:diamond_ores" -> "Diamond Ores"
            return Component.literal(cz.jirka.notquests.util.TagNames.plural(
                    observeType == ObserveType.BLOCK ? Registries.BLOCK : Registries.ENTITY_TYPE, target));
        }
        ResourceLocation rl = TextUtil.rl(target);
        if (rl == null) {
            return Component.literal(target);
        }
        if (observeType == ObserveType.BLOCK) {
            return BuiltInRegistries.BLOCK.getOptional(rl).map(Block::getName).map(c -> (Component) c).orElse(Component.literal(target));
        }
        return BuiltInRegistries.ENTITY_TYPE.getOptional(rl).map(EntityType::getDescription).orElse(Component.literal(target));
    }

    @Override
    public Component getAltTitle() {
        return Component.translatable("notquests.task.observation.title", targetName());
    }

    @Override
    public ItemStack getAltIcon() {
        if (target.startsWith("#")) {
            ItemStack current = observeType == ObserveType.BLOCK
                    ? cz.jirka.notquests.util.ItemUtil.cycleBlockTag(target)
                    : cz.jirka.notquests.util.ItemUtil.cycleEntityTag(target);
            if (!current.isEmpty()) {
                return current;
            }
        }
        ResourceLocation rl = TextUtil.rl(target);
        if (rl != null && !target.startsWith("#")) {
            if (observeType == ObserveType.BLOCK) {
                ItemStack s = BuiltInRegistries.BLOCK.getOptional(rl).map(b -> new ItemStack(b.asItem())).orElse(ItemStack.EMPTY);
                if (!s.isEmpty()) {
                    return s;
                }
            } else {
                SpawnEggItem egg = BuiltInRegistries.ENTITY_TYPE.getOptional(rl).map(SpawnEggItem::byId).orElse(null);
                if (egg != null) {
                    return new ItemStack(egg);
                }
            }
        }
        return super.getAltIcon();
    }

    @Override
    public void addTooltip(List<Component> list, TeamData data, boolean editMode) {
        if (timer > 0L) {
            list.add(Component.translatable("notquests.task.observation.time", String.format(Locale.ROOT, "%.1f", timer / 20D)).withStyle(ChatFormatting.GRAY));
        }
        super.addTooltip(list, data, editMode);
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        tag.putString("observe_type", observeType.id());
        tag.putString("to_observe", target);
        putNumber(tag, "timer", timer);
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        observeType = NamedEnum.byId(ObserveType.VALUES, tag.getString("observe_type"), ObserveType.BLOCK);
        target = tag.contains("to_observe") ? tag.getString("to_observe") : "minecraft:diamond_ore";
        timer = tag.contains("timer") ? Math.max(0L, tag.getLong("timer")) : 20L; // same default as in the editor
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        group.addEnum("observe_type", observeType, ObserveType.BLOCK, ObserveType.VALUES, v -> observeType = v);
        group.addId("to_observe", target, "minecraft:diamond_ore", observeType == ObserveType.BLOCK ? IdKind.BLOCK : IdKind.ENTITY, v -> target = v);
        group.addLong("timer", timer, 20L, 0L, 72000L, v -> timer = v);
    }
}
