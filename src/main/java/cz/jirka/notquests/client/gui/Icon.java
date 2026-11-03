package cz.jirka.notquests.client.gui;

/** 16x16 icons in textures/gui/icons.png (white, tinted when drawn). */
public enum Icon {
    CHECK,
    CROSS,
    EXCLAMATION,
    LOCK,
    PIN,
    PENCIL,
    GEAR,
    PLUS,
    GIFT,
    LIST,
    ARROW_UP,
    ARROW_DOWN,
    COLLAPSED,
    EXPANDED,
    CENTER,
    TRASH,
    LINK;

    /** icons.png has room for 32 icons of 32px in one row. */
    private static final float SLOTS = 32F;

    public float u0() {
        return ordinal() / SLOTS;
    }

    public float u1() {
        return (ordinal() + 1) / SLOTS;
    }
}
