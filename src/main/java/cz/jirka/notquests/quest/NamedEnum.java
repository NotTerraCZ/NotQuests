package cz.jirka.notquests.quest;

import cz.jirka.notquests.util.TextUtil;
import net.minecraft.network.chat.Component;

public interface NamedEnum {
    String id();

    default Component displayName() {
        return Component.translatable("notquests.enum." + id());
    }

    static <E extends Enum<E> & NamedEnum> E byId(E[] values, String id, E def) {
        if (id == null || id.isBlank()) {
            return def;
        }
        String key = id.trim();
        for (E e : values) {
            if (e.id().equalsIgnoreCase(key)) {
                return e;
            }
        }
        StringBuilder valid = new StringBuilder();
        for (E e : values) {
            valid.append(valid.isEmpty() ? "" : ", ").append(e.id());
        }
        com.mojang.logging.LogUtils.getLogger().warn("Unknown {} '{}', using '{}'. Valid values: {}",
                def.getDeclaringClass().getSimpleName(), id, def.id(), valid);
        return def;
    }

    static String pretty(NamedEnum e) {
        return TextUtil.prettyEnum(e.id());
    }
}
