package cz.jirka.notquests.quest.task;

import cz.jirka.notquests.progress.Team;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.server.ServerQuestManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CheckmarkTask extends Task {
    public CheckmarkTask(Quest quest) {
        super(quest);
    }

    @Override
    public TaskType getType() {
        return TaskTypes.CHECKMARK;
    }

    @Override
    public boolean isSubmittable() {
        return true;
    }

    @Override
    public void submit(ServerQuestManager manager, Team team, ServerPlayer player) {
        manager.setTaskProgress(team, this, 1L);
    }

    @Override
    public Component getAltTitle() {
        return Component.translatable("notquests.task.checkmark");
    }
}
