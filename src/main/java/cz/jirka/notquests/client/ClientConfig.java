package cz.jirka.notquests.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    public enum HudPosition {
        TOP_LEFT,
        TOP_RIGHT,
        MIDDLE_LEFT,
        MIDDLE_RIGHT
    }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue INVENTORY_BUTTON;
    public static final ModConfigSpec.BooleanValue SHOW_PINNED;
    public static final ModConfigSpec.EnumValue<HudPosition> PINNED_POSITION;
    public static final ModConfigSpec.DoubleValue PINNED_SCALE;
    public static final ModConfigSpec.BooleanValue COMPLETION_SOUNDS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        INVENTORY_BUTTON = b.comment("Show a quest book button on the left side of the inventory screen").define("inventoryButton", true);
        SHOW_PINNED = b.comment("Show pinned quests on the screen").define("showPinnedQuests", true);
        PINNED_POSITION = b.comment("Where pinned quests are drawn").defineEnum("pinnedQuestsPosition", HudPosition.TOP_LEFT);
        PINNED_SCALE = b.comment("Scale of the pinned quest list").defineInRange("pinnedQuestsScale", 1D, 0.5D, 2D);
        COMPLETION_SOUNDS = b.comment("Play a sound when a quest or chapter is completed").define("completionSounds", true);
        SPEC = b.build();
    }

    private ClientConfig() {
    }
}
