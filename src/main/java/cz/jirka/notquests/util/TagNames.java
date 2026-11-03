package cz.jirka.notquests.util;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Names of tags for players: "#c:ingots/iron" becomes "Iron Ingots", "#minecraft:logs" becomes "Logs".
 * Uses the tag translations that NeoForge ships (tag.item.c.ingots.iron) and builds a name from the tag path otherwise.
 */
public final class TagNames {
    private TagNames() {
    }

    /** Plural name of a tag, e.g. "Iron Ingots", "Logs", "Skeletons", "Forest". */
    public static String plural(ResourceKey<? extends Registry<?>> registry, String tagId) {
        ResourceLocation rl = TextUtil.rl(tagId);
        if (rl == null) {
            return tagId;
        }
        String key = "tag." + registry.location().getPath().replace('/', '.') + "." + rl.getNamespace() + "." + rl.getPath().replace('/', '.');
        Language language = Language.getInstance();
        if (language.has(key)) {
            return language.getOrDefault(key);
        }
        return fromPath(registry, rl.getPath());
    }

    /** "Any Log", "Any Iron Ingot" - for a single item of a tag. Languages other than English just get the plural name. */
    public static String anyOne(ResourceKey<? extends Registry<?>> registry, String tagId) {
        String plural = plural(registry, tagId);
        if (!SidedHooks.languageCode.get().startsWith("en")) {
            return plural;
        }
        return "Any " + singular(plural);
    }

    /** "ingots/iron" -> "Iron Ingots", "is_forest" -> "Forest", "logs_that_burn" -> "Logs That Burn". */
    private static String fromPath(ResourceKey<? extends Registry<?>> registry, String path) {
        String[] parts = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = parts.length - 1; i >= 0; i--) {
            String part = parts[i];
            if (registry == Registries.BIOME && part.startsWith("is_")) {
                part = part.substring(3);
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(TextUtil.prettyId(part));
        }
        return sb.toString();
    }

    /** English singular of the last word: Logs -> Log, Berries -> Berry, Torches -> Torch, Leaves -> Leaf, Glass -> Glass. */
    static String singular(String plural) {
        int space = plural.lastIndexOf(' ');
        String head = space < 0 ? "" : plural.substring(0, space + 1);
        String word = space < 0 ? plural : plural.substring(space + 1);
        String lower = word.toLowerCase(Locale.ROOT);
        String single;
        if (lower.equals("leaves")) {
            single = word.substring(0, word.length() - 3) + "f";
        } else if (lower.endsWith("ies") && lower.length() > 4) {
            single = word.substring(0, word.length() - 3) + "y";
        } else if (lower.endsWith("sses") || lower.endsWith("shes") || lower.endsWith("ches") || lower.endsWith("xes") || lower.endsWith("zes")) {
            single = word.substring(0, word.length() - 2);
        } else if (lower.endsWith("ss") || lower.endsWith("us") || lower.endsWith("is")) {
            single = word;
        } else if (lower.endsWith("s") && lower.length() > 2) {
            single = word.substring(0, word.length() - 1);
        } else {
            single = word;
        }
        return head + single;
    }
}
