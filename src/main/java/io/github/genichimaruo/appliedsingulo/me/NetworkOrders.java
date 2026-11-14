package io.github.genichimaruo.appliedsingulo.me;

import appeng.api.networking.IGrid;
import io.github.genichimaruo.appliedsingulo.order.OrderJob;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Order state of every Android ME Interface on one ME network (block or panel), for the status emitter and the
 * order monitor. Interfaces are found through their internal chain node, whose owner is the {@link AndroidInterfaceLogic}.
 */
public record NetworkOrders(int interfaces, int running, int batching, boolean stuck,
                            String topItem, long topClaimed, long topCount) {
    public static final NetworkOrders NONE = new NetworkOrders(0, 0, 0, false, "", 0, 0);

    public boolean busy() { return running + batching > 0; }

    public static NetworkOrders of(@Nullable IGrid grid) {
        if (grid == null) return NONE;
        int interfaces = 0, running = 0, batching = 0;
        boolean stuck = false;
        OrderJob top = null;
        for (var logic : grid.getMachines(AndroidInterfaceLogic.class)) {
            interfaces++;
            var queue = logic.queue();
            running += (int) queue.count(OrderJob.State.RUNNING);
            batching += (int) queue.count(OrderJob.State.QUEUED);
            stuck |= queue.outputBlocked() || queue.console() == null;
            for (var job : queue.jobs()) {
                stuck |= job.reason() != null && !job.state().terminal();
                if (top == null || (job.state() == OrderJob.State.RUNNING && top.state() != OrderJob.State.RUNNING)) top = job;
            }
        }
        if (top == null) return new NetworkOrders(interfaces, running, batching, stuck, "", 0, 0);
        return new NetworkOrders(interfaces, running, batching, stuck,
            BuiltInRegistries.ITEM.getKey(top.target().getItem()).toString(), top.claimedProducts(), top.count());
    }
}
