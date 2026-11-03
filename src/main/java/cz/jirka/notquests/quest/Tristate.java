package cz.jirka.notquests.quest;

public enum Tristate implements NamedEnum {
    DEFAULT("default"),
    TRUE("true"),
    FALSE("false");

    public static final Tristate[] VALUES = values();

    private final String id;

    Tristate(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    public boolean get(boolean def) {
        return this == DEFAULT ? def : this == TRUE;
    }

    public static Tristate byId(String id) {
        return NamedEnum.byId(VALUES, id, DEFAULT);
    }
}
