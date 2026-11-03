package cz.jirka.notquests.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

public final class TextUtil {
    private static final String CODES = "0123456789abcdefklmnorABCDEFKLMNOR";

    private TextUtil() {
    }

    /** Converts FTB-style &amp; color codes to section signs. "\&amp;" stays a literal ampersand. */
    public static String colorize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length() && s.charAt(i + 1) == '&') {
                sb.append('&');
                i++;
            } else if (c == '&' && i + 1 < s.length() && CODES.indexOf(s.charAt(i + 1)) >= 0) {
                sb.append('§');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Text typed by quest authors: &amp; color codes, or {translation.key} for a translatable text. */
    public static MutableComponent parse(String s) {
        if (s.length() > 2 && s.startsWith("{") && s.endsWith("}") && !s.contains(" ")) {
            return Component.translatable(s.substring(1, s.length() - 1));
        }
        return Component.literal(colorize(s));
    }

    public static String stripCodes(String s) {
        return colorize(s).replaceAll("§.", "");
    }

    /** "minecraft:the_nether" -> "The Nether" (the translated dimension name when there is one). */
    public static Component dimensionName(String id) {
        ResourceLocation rl = rl(id);
        if (rl == null) {
            return Component.literal(id);
        }
        return Component.translatableWithFallback("dimension." + rl.getNamespace() + "." + rl.getPath().replace('/', '.'), prettyId(id));
    }

    /** Name of an advancement the client does not know yet: vanilla's own title ("Diamonds!"), else made from the id. */
    public static Component advancementName(String id) {
        ResourceLocation rl = rl(id);
        if (rl == null) {
            return Component.literal(id);
        }
        if (rl.getNamespace().equals("minecraft")) {
            return Component.translatableWithFallback("advancements." + rl.getPath().replace('/', '.') + ".title", prettyId(id));
        }
        return Component.literal(prettyId(id));
    }

    /** "minecraft:story/mine_stone" -> "Mine Stone" */
    public static String prettyId(String id) {
        String path = id;
        int colon = path.indexOf(':');
        if (colon >= 0) {
            path = path.substring(colon + 1);
        }
        int slash = path.lastIndexOf('/');
        if (slash >= 0) {
            path = path.substring(slash + 1);
        }
        StringBuilder sb = new StringBuilder();
        boolean upper = true;
        for (char c : path.toCharArray()) {
            if (c == '_' || c == '-' || c == '.') {
                sb.append(' ');
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return sb.toString();
    }

    public static String prettyEnum(String id) {
        return prettyId(id.toLowerCase(Locale.ROOT));
    }

    public static ResourceLocation rl(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        return ResourceLocation.tryParse(s);
    }

    public static String formatLong(long l) {
        if (l >= 1_000_000_000L) {
            return trim(l / 1_000_000_000D) + "B";
        } else if (l >= 1_000_000L) {
            return trim(l / 1_000_000D) + "M";
        } else if (l >= 10_000L) {
            return trim(l / 1_000D) + "K";
        }
        return Long.toString(l);
    }

    private static String trim(double d) {
        String s = String.format(Locale.ROOT, "%.1f", d);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
