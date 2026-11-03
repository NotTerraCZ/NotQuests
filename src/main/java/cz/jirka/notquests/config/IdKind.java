package cz.jirka.notquests.config;

/**
 * What kind of id a text field in the editor expects. Server side kinds are requested from the server,
 * because the client does not know e.g. structures or loot tables.
 */
public enum IdKind {
    ENTITY(true, true, false),
    BLOCK(true, true, false),
    ITEM_TAG(false, true, false),
    BIOME(true, true, false),
    DIMENSION(true, false, false),
    STRUCTURE(true, true, true),
    ADVANCEMENT(true, false, true),
    STAT(true, false, false),
    LOOT_TABLE(true, false, true),
    SOUND(true, false, false);

    public static final IdKind[] VALUES = values();

    public final boolean ids;
    public final boolean tags;
    public final boolean serverSide;

    IdKind(boolean ids, boolean tags, boolean serverSide) {
        this.ids = ids;
        this.tags = tags;
        this.serverSide = serverSide;
    }

    public static IdKind byName(String s) {
        for (IdKind k : VALUES) {
            if (k.name().equalsIgnoreCase(s)) {
                return k;
            }
        }
        return ENTITY;
    }
}
