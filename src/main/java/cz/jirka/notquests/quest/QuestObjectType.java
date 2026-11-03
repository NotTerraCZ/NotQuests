package cz.jirka.notquests.quest;

public enum QuestObjectType {
    FILE,
    CHAPTER_GROUP,
    CHAPTER,
    QUEST,
    TASK,
    REWARD,
    REWARD_TABLE;

    public static final QuestObjectType[] VALUES = values();

    public static QuestObjectType byIndex(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : FILE;
    }
}
