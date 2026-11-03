package cz.jirka.notquests.quest;

/**
 * LINEAR: tasks can only make progress once the quest's dependencies are complete.
 * FLEXIBLE: tasks can make progress at any time, the quest still completes only after its dependencies.
 */
public enum ProgressionMode implements NamedEnum {
    DEFAULT("default"),
    LINEAR("linear"),
    FLEXIBLE("flexible");

    public static final ProgressionMode[] VALUES = values();
    public static final ProgressionMode[] REAL = {LINEAR, FLEXIBLE};

    private final String id;

    ProgressionMode(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    public static ProgressionMode byId(String id) {
        return NamedEnum.byId(VALUES, id, DEFAULT);
    }
}
