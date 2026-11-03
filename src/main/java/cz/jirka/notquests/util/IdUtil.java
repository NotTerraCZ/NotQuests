package cz.jirka.notquests.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Quest object ids are random 64-bit numbers written as 16 hex digits, exactly like FTB Quests.
 */
public final class IdUtil {
    private IdUtil() {
    }

    public static String toHex(long id) {
        return String.format("%016X", id);
    }

    public static long parse(String s) {
        if (s == null) {
            return 0L;
        }
        s = s.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.isEmpty() || s.length() > 16) {
            return 0L;
        }
        try {
            return Long.parseUnsignedLong(s, 16);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Ids as written in quest files. 16 hex digits (what NotQuests and FTB Quests write) are read as they are.
     * Anything else, e.g. "nether_start", is a readable name: it is turned into a fixed id, so the same name
     * always means the same object and can be used in dependencies, table_id and group references.
     */
    public static long parseFileId(String s) {
        if (s == null) {
            return 0L;
        }
        String t = s.trim();
        if (t.startsWith("#")) {
            t = t.substring(1);
        }
        if (t.isEmpty() || t.chars().allMatch(c -> c == '0')) {
            return 0L;
        }
        if (t.length() == 16 && t.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
            return Long.parseUnsignedLong(t, 16);
        }
        long id = nameId(t);
        NAMES.putIfAbsent(id, t);
        return id;
    }

    /** Readable names seen in quest files, so saving writes "nether_start" back instead of its hex form. */
    private static final java.util.Map<Long, String> NAMES = new java.util.concurrent.ConcurrentHashMap<>();

    /** An id the way the quest files spell it: its readable name if it was written with one, else 16 hex digits. */
    public static String toFileId(long id) {
        String name = NAMES.get(id);
        return name != null ? name : toHex(id);
    }

    /**
     * Stable 64-bit id for a readable name (case-insensitive). Never 0 or 1.
     * NEVER CHANGE this or {@link #hash} - quests written with readable ids keep their player progress only
     * because the same name always gives the same id, in every version of the mod.
     */
    public static long nameId(String name) {
        return hash(name.trim().toLowerCase(java.util.Locale.ROOT));
    }

    /** Stable 64-bit id for any text (case-sensitive). Never 0 or 1. */
    public static long hash(String text) {
        long h = 0xCBF29CE484222325L;
        for (byte b : text.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            h ^= b & 0xFF;
            h *= 0x100000001B3L;
        }
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return h == 0L || h == 1L ? h + 0x4E51000000000000L : h;
    }

    /** Never returns 0 (= "none") or 1 (= the quest file itself). */
    public static long random() {
        long l;
        do {
            l = ThreadLocalRandom.current().nextLong();
        } while (l == 0L || l == 1L);
        return l;
    }
}
