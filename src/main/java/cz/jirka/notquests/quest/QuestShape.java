package cz.jirka.notquests.quest;

public enum QuestShape implements NamedEnum {
    DEFAULT("default"),
    CIRCLE("circle"),
    SQUARE("square"),
    RSQUARE("rsquare"),
    DIAMOND("diamond"),
    PENTAGON("pentagon"),
    HEXAGON("hexagon"),
    OCTAGON("octagon"),
    HEART("heart"),
    GEAR("gear");

    public static final QuestShape[] VALUES = values();
    /** Shapes that can be picked as a file default (everything except DEFAULT). */
    public static final QuestShape[] REAL = {CIRCLE, SQUARE, RSQUARE, DIAMOND, PENTAGON, HEXAGON, OCTAGON, HEART, GEAR};

    private final String id;

    QuestShape(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    public static QuestShape byId(String id) {
        return NamedEnum.byId(VALUES, id, DEFAULT);
    }
}
