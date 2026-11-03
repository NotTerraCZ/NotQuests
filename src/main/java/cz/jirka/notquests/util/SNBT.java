package cz.jirka.notquests.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Human readable SNBT files (tabs, one key per line, sorted keys) that vanilla's TagParser can read back.
 */
public final class SNBT {
    private static final Pattern SIMPLE_KEY = Pattern.compile("[A-Za-z0-9._+-]+");

    private SNBT() {
    }

    public static CompoundTag read(Path path) throws IOException {
        return parse(Files.readString(path, StandardCharsets.UTF_8), path.getFileName().toString());
    }

    /**
     * Parses SNBT. On top of vanilla's syntax this accepts what people (and AI tools) tend to write by hand:
     * {@code //} and {@code /* ... *}{@code /} comments, a line break instead of a comma between two entries,
     * {@code \n}, {@code \t} and {@code \}{@code uXXXX} escapes inside quoted strings and a UTF-8 BOM.
     * Errors report the line and column of the original text.
     */
    public static CompoundTag parse(String text, String name) throws IOException {
        Prepared prepared = prepare(text);
        try {
            return TagParser.parseTag(prepared.text());
        } catch (CommandSyntaxException e) {
            throw new IOException("Invalid SNBT in " + name + describe(text, prepared, e), e);
        }
    }

    private record Prepared(String text, int[] origin) {
    }

    private static Prepared prepare(String src) {
        int n = src.length();
        StringBuilder out = new StringBuilder(n + 64);
        IntArrayList origin = new IntArrayList(n + 64);
        int i = 0;
        if (n > 0 && src.charAt(0) == '﻿') {
            i = 1;
        }
        char last = 0;
        boolean newline = false;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int end = src.indexOf("*/", i + 2);
                int stop = end < 0 ? n : end + 2;
                int lf = src.indexOf('\n', i);
                if (lf >= 0 && lf < stop) {
                    newline = true;
                }
                out.append(' ');
                origin.add(i);
                i = stop;
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (c == '\n') {
                    newline = true;
                }
                out.append(c);
                origin.add(i);
                i++;
                continue;
            }
            if (newline && last != 0 && "{[,:;".indexOf(last) < 0 && "}],:;".indexOf(c) < 0) {
                // two entries separated only by a line break - the FTB style without commas
                out.append(',');
                origin.add(i);
            }
            newline = false;
            last = c;
            if (c == '"' || c == '\'') {
                i = copyString(src, i, out, origin);
                continue;
            }
            out.append(c);
            origin.add(i);
            i++;
        }
        return new Prepared(out.toString(), origin.toIntArray());
    }

    /** Copies the quoted string starting at {@code start}, turning escapes TagParser does not know into plain characters. */
    private static int copyString(String src, int start, StringBuilder out, IntArrayList origin) {
        char quote = src.charAt(start);
        out.append(quote);
        origin.add(start);
        int n = src.length();
        int i = start + 1;
        while (i < n) {
            char c = src.charAt(i);
            if (c == quote) {
                out.append(c);
                origin.add(i);
                return i + 1;
            }
            if (c == '\\' && i + 1 < n) {
                char e = src.charAt(i + 1);
                int len = 2;
                char value;
                if (e == 'u' && i + 6 <= n && isHex(src, i + 2, i + 6)) {
                    value = (char) Integer.parseInt(src.substring(i + 2, i + 6), 16);
                    len = 6;
                } else {
                    value = switch (e) {
                        case 'n' -> '\n';
                        case 't' -> '\t';
                        case 'r' -> (char) 0;
                        case '"', '\'', '\\', '/' -> e;
                        default -> {
                            // unknown escape: keep the backslash, e.g. "\&" (a literal ampersand in quest text)
                            out.append("\\\\");
                            origin.add(i);
                            origin.add(i);
                            yield e;
                        }
                    };
                }
                if (value == quote || value == '\\') {
                    out.append('\\');
                    origin.add(i);
                }
                if (value != 0) {
                    out.append(value);
                    origin.add(i);
                }
                i += len;
                continue;
            }
            out.append(c);
            origin.add(i);
            i++;
        }
        return n;
    }

    private static boolean isHex(String s, int from, int to) {
        for (int i = from; i < to; i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String describe(String src, Prepared prepared, CommandSyntaxException e) {
        String msg = e.getRawMessage().getString();
        int cursor = e.getCursor();
        if (cursor < 0) {
            return ": " + msg;
        }
        int pos = cursor < prepared.origin().length ? prepared.origin()[cursor] : src.length();
        pos = Math.min(pos, src.length());
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < pos; i++) {
            if (src.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        int lineEnd = src.indexOf('\n', lineStart);
        String text = src.substring(lineStart, lineEnd < 0 ? src.length() : lineEnd).strip();
        if (text.length() > 80) {
            text = text.substring(0, 80) + "...";
        }
        return " at line " + line + ", column " + (pos - lineStart + 1) + ": " + msg + (text.isEmpty() ? "" : " -> " + text);
    }

    public static void write(Path path, CompoundTag tag) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        Files.writeString(tmp, write(tag), StandardCharsets.UTF_8);
        Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    // ------------------------------------------------------------------ writing
    //
    // The output looks like the example files on the mod page: the important keys first, small objects such as
    // tasks and rewards on one line, x and y next to each other and numbers without type letters where possible.

    /** Keys written first, in this order. */
    private static final List<String> KEYS_FIRST = List.of(
            "id", "type", "title", "subtitle", "description", "icon", "group", "order_index",
            "x", "y", "size", "shape", "dependencies", "dependency_requirement", "min_required_dependencies",
            "item", "item_tag", "entity", "custom_name", "dimension", "biome", "structure", "advancement", "criterion",
            "stat", "observe_type", "to_observe", "location", "count", "value", "weight", "table_id", "loot_table",
            "command", "xp", "xp_levels");
    /** Keys written last (the big lists). */
    private static final List<String> KEYS_LAST = List.of("chapter_groups", "quests", "tasks", "rewards");
    /** These lists always get one line per entry. */
    private static final Set<String> SPLIT_LISTS = Set.of("quests", "chapter_groups");
    /** Entries of these lists are always written over several lines. */
    private static final Set<String> MULTILINE_LISTS = Set.of("quests");
    /** Longest "key: value" that is still written on one line. */
    private static final int LINE_LIMIT = 120;

    public static String write(CompoundTag tag) {
        StringBuilder sb = new StringBuilder();
        writeCompound(sb, tag, 0);
        sb.append('\n');
        return sb.toString();
    }

    private static void indent(StringBuilder sb, int depth) {
        for (int i = 0; i < depth; i++) {
            sb.append('\t');
        }
    }

    private static List<String> orderedKeys(CompoundTag c) {
        List<String> rest = new ArrayList<>();
        for (String key : c.getAllKeys()) {
            if (!KEYS_FIRST.contains(key) && !KEYS_LAST.contains(key)) {
                rest.add(key);
            }
        }
        Collections.sort(rest);
        List<String> keys = new ArrayList<>();
        for (String key : KEYS_FIRST) {
            if (c.contains(key)) {
                keys.add(key);
            }
        }
        keys.addAll(rest);
        for (String key : KEYS_LAST) {
            if (c.contains(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static String key(String key) {
        return SIMPLE_KEY.matcher(key).matches() ? key : StringTag.quoteAndEscape(key);
    }

    private static boolean isPrimitive(Tag tag) {
        return !(tag instanceof CompoundTag) && !(tag instanceof ListTag);
    }

    /** One-line form of a tag, or null if it should not be written on one line. */
    private static String inline(Tag tag) {
        if (tag instanceof CompoundTag c) {
            if (c.isEmpty()) {
                return "{ }";
            }
            StringBuilder sb = new StringBuilder("{ ");
            boolean first = true;
            for (String k : orderedKeys(c)) {
                if (KEYS_LAST.contains(k)) {
                    return null; // quests, tasks, rewards... are never squeezed into one line
                }
                String v = inline(c.get(k));
                if (v == null) {
                    return null;
                }
                if (!first) {
                    sb.append(", ");
                }
                sb.append(key(k)).append(": ").append(v);
                first = false;
            }
            return sb.append(" }").toString();
        } else if (tag instanceof ListTag list) {
            if (list.isEmpty()) {
                return "[ ]";
            }
            boolean compounds = list.get(0) instanceof CompoundTag;
            if (compounds && list.size() > 1) {
                return null; // several tasks / rewards: one per line
            }
            StringBuilder sb = new StringBuilder(compounds ? "[ " : "[");
            for (int i = 0; i < list.size(); i++) {
                String v = inline(list.get(i));
                if (v == null) {
                    return null;
                }
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(v);
            }
            return sb.append(compounds ? " ]" : "]").toString();
        }
        return primitive(tag);
    }

    private static String primitive(Tag tag) {
        if (tag instanceof ByteTag b) {
            byte v = b.getAsByte();
            return v == 0 ? "false" : v == 1 ? "true" : v + "b";
        } else if (tag instanceof net.minecraft.nbt.DoubleTag d) {
            // "2.5" and "0.0" are read back as doubles, the d is not needed
            double v = d.getAsDouble();
            return Double.isFinite(v) ? Double.toString(v) : tag.toString();
        } else if (tag instanceof StringTag s) {
            return StringTag.quoteAndEscape(s.getAsString());
        }
        return tag.toString();
    }

    private static void writeCompound(StringBuilder sb, CompoundTag c, int depth) {
        if (c.isEmpty()) {
            sb.append("{ }");
            return;
        }
        sb.append("{\n");
        List<String> keys = orderedKeys(c);
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            Tag value = c.get(k);
            indent(sb, depth + 1);
            if (k.equals("x") && i + 1 < keys.size() && keys.get(i + 1).equals("y") && isPrimitive(value) && isPrimitive(c.get("y"))) {
                sb.append("x: ").append(primitive(value)).append(", y: ").append(primitive(c.get("y")));
                i++;
            } else {
                sb.append(key(k)).append(": ");
                writeValue(sb, k, value, depth + 1);
            }
            if (i < keys.size() - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        indent(sb, depth);
        sb.append('}');
    }

    private static void writeValue(StringBuilder sb, String key, Tag value, int depth) {
        String line = SPLIT_LISTS.contains(key) ? null : inline(value);
        if (line != null && key.length() + 2 + line.length() <= LINE_LIMIT) {
            sb.append(line);
        } else if (value instanceof CompoundTag c) {
            writeCompound(sb, c, depth);
        } else if (value instanceof ListTag list) {
            sb.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                Tag e = list.get(i);
                indent(sb, depth + 1);
                String one = MULTILINE_LISTS.contains(key) ? null : inline(e);
                if (one != null && one.length() <= LINE_LIMIT) {
                    sb.append(one);
                } else if (e instanceof CompoundTag ec) {
                    writeCompound(sb, ec, depth + 1);
                } else {
                    writeValue(sb, "", e, depth + 1);
                }
                if (i < list.size() - 1) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            indent(sb, depth);
            sb.append(']');
        } else {
            sb.append(primitive(value));
        }
    }
}
