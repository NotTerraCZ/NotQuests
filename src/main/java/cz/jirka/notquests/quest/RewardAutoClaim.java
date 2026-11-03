package cz.jirka.notquests.quest;

public enum RewardAutoClaim implements NamedEnum {
    DEFAULT("default"),
    DISABLED("disabled"),
    ENABLED("enabled"),
    NO_TOAST("no_toast"),
    INVISIBLE("invisible");

    public static final RewardAutoClaim[] VALUES = values();
    public static final RewardAutoClaim[] REAL = {DISABLED, ENABLED, NO_TOAST, INVISIBLE};

    private final String id;

    RewardAutoClaim(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    public boolean isAuto() {
        return this == ENABLED || this == NO_TOAST || this == INVISIBLE;
    }

    public static RewardAutoClaim byId(String id) {
        return NamedEnum.byId(VALUES, id, DEFAULT);
    }
}
