package cz.jirka.notquests.quest;

public enum DependencyRequirement implements NamedEnum {
    ALL_COMPLETED("all_completed", false, true),
    ONE_COMPLETED("one_completed", true, true),
    ALL_STARTED("all_started", false, false),
    ONE_STARTED("one_started", true, false);

    public static final DependencyRequirement[] VALUES = values();

    private final String id;
    public final boolean one;
    public final boolean completed;

    DependencyRequirement(String id, boolean one, boolean completed) {
        this.id = id;
        this.one = one;
        this.completed = completed;
    }

    @Override
    public String id() {
        return id;
    }

    public static DependencyRequirement byId(String id) {
        return NamedEnum.byId(VALUES, id, ALL_COMPLETED);
    }
}
