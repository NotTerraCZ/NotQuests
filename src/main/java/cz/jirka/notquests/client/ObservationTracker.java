package cz.jirka.notquests.client;

import cz.jirka.notquests.net.NQNetwork;
import cz.jirka.notquests.progress.TeamData;
import cz.jirka.notquests.quest.QuestFile;
import cz.jirka.notquests.quest.task.ObservationTask;
import cz.jirka.notquests.quest.task.Task;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/** Counts how long the player looks at the target of observation tasks. */
public final class ObservationTracker {
    private static final Long2IntOpenHashMap COUNTERS = new Long2IntOpenHashMap();
    private static final LongOpenHashSet SENT = new LongOpenHashSet();
    private static int resetTimer;

    private ObservationTracker() {
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        QuestFile file = ClientQuestData.file;
        if (mc.player == null || mc.level == null || file == null || mc.isPaused()) {
            return;
        }
        if (++resetTimer >= 100) {
            resetTimer = 0;
            SENT.clear();
        }
        TeamData d = ClientQuestData.team;
        HitResult hit = mc.hitResult;
        BlockState state = null;
        Entity entity = null;
        if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
            state = mc.level.getBlockState(bh.getBlockPos());
        } else if (hit instanceof EntityHitResult eh) {
            entity = eh.getEntity();
        }
        for (Task t : file.allTasks) {
            if (!(t instanceof ObservationTask ot)) {
                continue;
            }
            if (d.isComplete(ot) || !d.canStartTasks(ot.quest) || SENT.contains(ot.id)) {
                COUNTERS.remove(ot.id);
                continue;
            }
            boolean match = state != null ? ot.matchesBlock(state) : entity != null && ot.matchesEntity(entity);
            if (!match) {
                COUNTERS.remove(ot.id);
                continue;
            }
            int c = COUNTERS.get(ot.id) + 1;
            if (c >= ot.timer) {
                PacketDistributor.sendToServer(new NQNetwork.Observed(ot.id));
                SENT.add(ot.id);
                COUNTERS.remove(ot.id);
            } else {
                COUNTERS.put(ot.id, c);
            }
        }
    }

    public static void clear() {
        COUNTERS.clear();
        SENT.clear();
    }
}
