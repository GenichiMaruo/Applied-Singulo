package io.github.genichimaruo.appliedsingulo.order;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * One Singulo escrow request, built from one or more AE2 pattern pushes of the same output.
 *
 * <p>QUEUED: still open; the AE2 materials are held here ({@link #materials}) and further pushes add to it.
 * On acceptance ({@code AndroidEscrows.submit(...).acceptedNow()}) the materials are relinquished to Singulo's
 * per-request custody. From then on products and refunds are claimed back by request id only, so two jobs for
 * the same item never mix. A job is forgotten only after Singulo acknowledged the empty, terminal request.
 */
public final class OrderJob {
    public enum State { QUEUED, RUNNING, COMPLETED, CANCELLED, FAILED, LOST;
        public boolean terminal() { return this != QUEUED && this != RUNNING; }
    }

    public final UUID request;
    public final BlockPos console;
    private final ItemStack target;
    long count;
    int pushes;
    State state = State.QUEUED;
    /** Held AE2 materials while QUEUED (or returned to ME after a local cancel). Empty once relinquished. */
    final List<ItemStack> materials = new ArrayList<>();
    /** True once Singulo has taken custody: never offer these materials again. */
    boolean submitted;
    long delivered;
    long claimedProducts;
    long claimedMaterials;
    long firstPush;
    long lastPush;
    long retryAt;
    long finishedAt = -1;
    boolean quarantined;
    @Nullable String reason;

    public OrderJob(UUID request, BlockPos console, ItemStack target, long count) {
        this.request = request;
        this.console = console.immutable();
        this.target = target.copyWithCount(1);
        this.count = count;
        this.pushes = 1;
    }

    public ItemStack target() { return target.copy(); }
    public long count() { return count; }
    public int pushes() { return pushes; }
    public State state() { return state; }
    public boolean submitted() { return submitted; }
    public long delivered() { return delivered; }
    public long claimedProducts() { return claimedProducts; }
    public long claimedMaterials() { return claimedMaterials; }
    @Nullable public String reason() { return reason; }
    public List<ItemStack> heldMaterials() { return materials.stream().map(ItemStack::copy).toList(); }

    @Override
    public String toString() {
        return "OrderJob[" + target.getItem() + " x" + count + " " + state + (submitted ? " submitted" : " held")
            + " delivered=" + delivered + " claimed=" + claimedProducts + "+" + claimedMaterials
            + (reason == null ? "" : " reason=" + reason) + "]";
    }

    boolean mergeable(ItemStack stack, BlockPos console) {
        return state == State.QUEUED && !submitted && ItemStack.isSameItemSameComponents(target, stack)
            && this.console.equals(console);
    }

    /** Number of item stacks {@code extra} would occupy when merged into {@link #materials}. */
    int stacksWith(List<ItemStack> extra) {
        List<ItemStack> copy = new ArrayList<>(materials.stream().map(ItemStack::copy).toList());
        for (var s : extra) merge(copy, s.copy());
        return copy.size();
    }

    static void merge(List<ItemStack> into, ItemStack stack) {
        var fluid = io.github.genichimaruo.singulo.api.android.AndroidEscrows.fluidOf(stack);
        if (!fluid.isEmpty()) {   // fluid samples: one per fluid, amounts in mB
            for (int i = 0; i < into.size(); i++) {
                var held = io.github.genichimaruo.singulo.api.android.AndroidEscrows.fluidOf(into.get(i));
                if (!held.isEmpty() && net.neoforged.neoforge.fluids.FluidStack.isSameFluidSameComponents(held, fluid)
                        && (long) held.getAmount() + fluid.getAmount() <= Integer.MAX_VALUE) {
                    into.set(i, io.github.genichimaruo.singulo.api.android.AndroidEscrows.fluidSample(
                        held.copyWithAmount(held.getAmount() + fluid.getAmount())));
                    return;
                }
            }
            into.add(stack.copy());
            return;
        }
        for (var held : into) {
            if (stack.isEmpty()) return;
            if (!ItemStack.isSameItemSameComponents(held, stack)) continue;
            int move = Math.min(stack.getCount(), held.getMaxStackSize() - held.getCount());
            if (move <= 0) continue;
            held.grow(move);
            stack.shrink(move);
        }
        while (!stack.isEmpty()) {
            int n = Math.min(stack.getCount(), stack.getMaxStackSize());
            into.add(stack.copyWithCount(n));
            stack.shrink(n);
        }
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putUUID("request", request);
        tag.putLong("console", console.asLong());
        tag.put("target", target.save(registries));
        tag.putLong("count", count);
        tag.putInt("pushes", pushes);
        tag.putString("state", state.name());
        tag.putBoolean("submitted", submitted);
        var list = new ListTag();
        for (var s : materials) if (!s.isEmpty()) list.add(s.save(registries));
        tag.put("materials", list);
        tag.putLong("delivered", delivered);
        tag.putLong("claimedProducts", claimedProducts);
        tag.putLong("claimedMaterials", claimedMaterials);
        tag.putLong("firstPush", firstPush);
        tag.putLong("lastPush", lastPush);
        tag.putLong("retryAt", retryAt);
        tag.putLong("finishedAt", finishedAt);
        tag.putBoolean("quarantined", quarantined);
        if (reason != null) tag.putString("reason", reason);
        return tag;
    }

    @Nullable
    public static OrderJob load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.hasUUID("request")) return null;
        var target = ItemStack.parseOptional(registries, tag.getCompound("target"));
        long count = tag.getLong("count");
        if (target.isEmpty() || count <= 0) return null;
        var job = new OrderJob(tag.getUUID("request"), BlockPos.of(tag.getLong("console")), target, count);
        job.submitted = tag.getBoolean("submitted");
        try {
            job.state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException unknown) {
            // Re-query Singulo rather than guessing. An unsubmitted job may simply be offered again: a replay
            // of an accepted request never transfers materials twice.
            job.state = job.submitted ? State.RUNNING : State.QUEUED;
        }
        for (Tag t : tag.getList("materials", Tag.TAG_COMPOUND)) {
            var s = ItemStack.parseOptional(registries, (CompoundTag) t);
            if (!s.isEmpty()) job.materials.add(s);
        }
        job.pushes = Math.max(1, tag.getInt("pushes"));
        job.delivered = tag.getLong("delivered");
        job.claimedProducts = tag.getLong("claimedProducts");
        job.claimedMaterials = tag.getLong("claimedMaterials");
        job.firstPush = tag.getLong("firstPush");
        job.lastPush = tag.getLong("lastPush");
        job.retryAt = tag.getLong("retryAt");
        job.finishedAt = tag.getLong("finishedAt");
        job.quarantined = tag.getBoolean("quarantined");
        job.reason = tag.contains("reason") ? tag.getString("reason") : null;
        return job;
    }
}
