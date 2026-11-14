package io.github.genichimaruo.appliedsingulo.order;

import io.github.genichimaruo.singulo.api.android.AndroidEscrows;
import io.github.genichimaruo.singulo.api.android.AndroidOrders;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Shared core of the bridge and the ME interface, on Singulo's private crafting escrow (AndroidEscrows, 0.19.0).
 *
 * <ol>
 * <li>Each AE2 push is held all-or-nothing in an open (QUEUED) job for its output; same-output pushes are
 *     batched until {@link #BATCH_DEBOUNCE} ticks pass without a push, {@link #BATCH_MAX_WAIT} ticks after the
 *     first, or a Singulo limit (4096 items, 128 offered stacks) is reached.</li>
 * <li>The batch is offered with {@code AndroidEscrows.submit}; only {@code acceptedNow} relinquishes the
 *     materials. A rejection keeps them and retries; an accepted replay never transfers them again.</li>
 * <li>Products and, after termination and worker return, unused materials are claimed back by request id,
 *     only as far as the return buffer has room, and sent to ME. The job is forgotten only after Singulo
 *     acknowledged the empty request.</li>
 * </ol>
 * Materials are never visible to other orders, and products are never matched by item across requests.
 */
public final class AndroidOrderQueue {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("AppliedSingulo");
    public static final int MAX_JOBS = 16;
    /** Total item stacks held for unsubmitted jobs across this device. */
    public static final int HOLD_STACKS = 256;
    /** Singulo's per-request offer limit. */
    public static final int OFFER_STACKS = 128;
    public static final int OUTPUT_SLOTS = 36;
    public static final long MAX_ORDER = 4096;
    static final long BATCH_DEBOUNCE = 10;
    static final long BATCH_MAX_WAIT = 100;
    static final long RETRY_DELAY = 100;
    static final int POLL_INTERVAL = 20;

    private final Runnable changed;
    @Nullable private UUID owner;
    @Nullable private BlockPos console;
    private final List<OrderJob> jobs = new ArrayList<>();
    @Nullable private String problem;
    private boolean outputBlocked;

    /** Claimed products and refunds waiting for ME. Claims never exceed its free room. */
    private final ItemStackHandler outputs = new ItemStackHandler(OUTPUT_SLOTS) {
        @Override protected void onContentsChanged(int slot) { changed.run(); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            // Recipe samples are descriptions, not player-owned fluid containers.
            return !SinguloLinks.isFluid(stack);
        }
    };

    public AndroidOrderQueue(Runnable changed) { this.changed = changed; }

    // ------------------------------------------------------------------ accessors

    @Nullable public UUID owner() { return owner; }
    public void setOwner(@Nullable UUID owner) { this.owner = owner; changed.run(); }
    /** The Work Console orders go to (same dimension; set by placement or a Work Location Card). */
    @Nullable public BlockPos console() { return console; }
    public void setConsole(@Nullable BlockPos console) { this.console = console == null ? null : console.immutable(); changed.run(); }
    @Nullable public String problem() { return problem; }
    public List<OrderJob> jobs() { return List.copyOf(jobs); }
    public long count(OrderJob.State state) { return jobs.stream().filter(j -> j.state == state).count(); }
    public boolean outputBlocked() { return outputBlocked; }
    /** The "waiting for ME" buffer (products/refunds, plus anything a player deposits; all of it goes to ME). */
    public ItemStackHandler outputBuffer() { return outputs; }
    public List<ItemStack> outputContents() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < outputs.getSlots(); i++)
            if (!outputs.getStackInSlot(i).isEmpty()) out.add(outputs.getStackInSlot(i).copy());
        return out;
    }
    /** Materials held for not-yet-accepted jobs. */
    public List<ItemStack> heldContents() {
        List<ItemStack> out = new ArrayList<>();
        for (var job : jobs) if (!job.submitted) out.addAll(job.heldMaterials());
        return out;
    }

    public boolean reject(String why) {
        problem = why;
        return false;
    }

    public int heldStacks() {
        int n = 0;
        for (var job : jobs) if (!job.submitted) n += job.materials.size();
        return n;
    }

    /** Whether another push could plausibly be accepted (AE2 acceptsPlans / isBusy). */
    public boolean hasRoom() {
        return owner != null && console != null && heldStacks() < HOLD_STACKS
            && (jobs.size() < MAX_JOBS || jobs.stream().anyMatch(j -> j.state == OrderJob.State.QUEUED && !j.submitted
                && j.count < MAX_ORDER));
    }

    // ------------------------------------------------------------------ accepting pushes

    /**
     * All-or-nothing: either every material is held and the outputs are queued, or nothing changes.
     * @param outputCount outputs produced by this one push
     */
    public boolean accept(ItemStack target, long outputCount, List<ItemStack> materials, BlockPos console, long now) {
        if (owner == null) return reject("no_owner");
        if (outputCount <= 0 || outputCount > MAX_ORDER) return reject("output_count_out_of_range");
        if (materials.isEmpty()) return reject("no_materials");

        OrderJob open = null;
        for (var job : jobs)
            if (job.mergeable(target, console) && job.count + outputCount <= MAX_ORDER
                    && job.stacksWith(materials) <= OFFER_STACKS) { open = job; break; }
        int added;
        if (open != null) {
            added = open.stacksWith(materials) - open.materials.size();
        } else {
            if (jobs.size() >= MAX_JOBS) return reject("too_many_orders");
            added = new OrderJob(UUID.randomUUID(), console, target, 1).stacksWith(materials);
            if (added > OFFER_STACKS) return reject("too_many_material_stacks");
        }
        if (heldStacks() + added > HOLD_STACKS) return reject("hold_full");

        if (open == null) {
            open = new OrderJob(UUID.randomUUID(), console, target, outputCount);
            open.firstPush = now;
            jobs.add(open);
        } else {
            open.count += outputCount;
            open.pushes++;
        }
        for (var stack : materials) OrderJob.merge(open.materials, stack.copy());
        open.lastPush = now;
        problem = null;
        changed.run();
        return true;
    }

    // ------------------------------------------------------------------ ticking

    /** Call every server tick. */
    public void tick(ServerLevel level, long now, int phase) {
        submitDue(level, now);
        if ((now + phase) % POLL_INTERVAL == 0) poll(level, now);
    }

    void submitDue(ServerLevel level, long now) {
        if (owner == null) return;
        for (var job : jobs) {
            if (job.state != OrderJob.State.QUEUED || job.submitted || now < job.retryAt) continue;
            boolean due = now - job.lastPush >= BATCH_DEBOUNCE || now - job.firstPush >= BATCH_MAX_WAIT
                || job.count >= MAX_ORDER;
            if (due) submit(level, job, now);
        }
    }

    private void submit(ServerLevel level, OrderJob job, long now) {
        var snapshot = AndroidOrders.recipes(level, job.console, owner);
        if (snapshot.state() == AndroidOrders.SnapshotState.AVAILABLE) {
            var matching = snapshot.recipes().stream().filter(r -> !r.outputs().isEmpty()
                && ItemStack.isSameItemSameComponents(r.outputs().getFirst().sample(), job.target())).toList();
            if (!matching.isEmpty() && matching.stream().noneMatch(SinguloLinks::escrowEligible)) {
                // A rack may have changed since AE2 obtained the pattern. Return held inputs before any transfer.
                job.state = OrderJob.State.FAILED;
                job.reason = "unsupported_escrow_recipe";
                job.finishedAt = now;
                returnHeld(job);
                changed.run();
                return;
            }
        }
        var offer = job.heldMaterials();
        var result = AndroidEscrows.submit(level, job.console, owner, job.request, job.target(), job.count, offer, null);
        var receipt = result.receipt();
        LOGGER.info("Android escrow request {}: {} x{} from {} AE2 push(es), offer {} -> acceptedNow={} {} {}",
            job.request, job.target().getItem(), job.count, job.pushes, offer.stream().map(s -> s.getCount() + " " + s.getItem()).toList(),
            result.acceptedNow(), receipt.state(), receipt.reason() == null ? "" : receipt.reason());
        if (result.acceptedNow()) {
            job.materials.clear();   // custody transferred to Singulo, exactly once
            job.submitted = true;
            job.state = OrderJob.State.RUNNING;
            job.reason = receipt.reason();
        } else if (receipt.state() == AndroidOrders.State.REJECTED || receipt.state() == AndroidOrders.State.UNAVAILABLE
                || receipt.state() == AndroidOrders.State.UNKNOWN) {
            job.reason = receipt.reason();
            job.retryAt = now + RETRY_DELAY;   // we keep the materials; AE2 keeps waiting
        } else {
            // Accepted earlier (e.g. our save lagged behind Singulo's): custody already transferred.
            // This stale local copy must NOT also be refunded to ME.
            job.materials.clear();
            job.submitted = true;
            job.state = OrderJob.State.RUNNING;
            job.reason = "replayed_accepted_request";
        }
        changed.run();
    }

    /** Ask Singulo about each submitted request; claim products/refunds; acknowledge empty terminal requests. */
    void poll(ServerLevel level, long now) {
        if (owner == null) return;
        for (var it = jobs.iterator(); it.hasNext(); ) {
            var job = it.next();
            if (!job.submitted) {
                // Locally cancelled before submission: give the held materials back to ME.
                if (job.state.terminal()) {
                    returnHeld(job);
                    if (job.materials.isEmpty()) { it.remove(); changed.run(); }
                }
                continue;
            }
            if (sync(level, job, now)) { it.remove(); changed.run(); }
        }
    }

    /** @return true when the request is finished on both sides and can be forgotten. */
    private boolean sync(ServerLevel level, OrderJob job, long now) {
        var snap = AndroidEscrows.snapshot(level, owner, job.request);
        switch (snap.phase()) {
            case UNKNOWN -> {
                // Nothing in custody under this id (already acknowledged, or never existed). Nothing to recover.
                job.state = job.state.terminal() ? job.state : OrderJob.State.LOST;
                job.reason = snap.receipt().reason();
                return true;
            }
            case REJECTED -> { job.reason = snap.receipt().reason(); changed.run(); return false; }
            case QUARANTINED -> { job.quarantined = true; job.reason = "escrow_quarantined"; changed.run(); return false; }
            default -> { }
        }
        var receipt = snap.receipt();
        job.delivered = receipt.delivered();
        if (receipt.terminal() && !job.state.terminal()) {
            job.state = switch (receipt.state()) {
                case COMPLETED -> OrderJob.State.COMPLETED;
                case CANCELLED -> OrderJob.State.CANCELLED;
                default -> OrderJob.State.FAILED;
            };
            job.finishedAt = now;
            if (receipt.reason() != null) job.reason = receipt.reason();
        }
        job.claimedProducts += claim(level, job.request, true, snap.products());
        boolean refundable = (snap.phase() == AndroidEscrows.Phase.COMPLETED || snap.phase() == AndroidEscrows.Phase.RECOVERABLE)
            && !snap.workerPending();
        if (refundable) job.claimedMaterials += claim(level, job.request, false, snap.materials());
        changed.run();
        if (!receipt.terminal() || !refundable) return false;
        var after = AndroidEscrows.snapshot(level, owner, job.request);
        if (!after.products().stream().allMatch(ItemStack::isEmpty) || !after.materials().stream().allMatch(ItemStack::isEmpty))
            return false;   // return buffer full: keep the rest in Singulo custody, try again later
        return AndroidEscrows.acknowledge(level, owner, job.request);
    }

    /** Claim as much of each slot as the return buffer can take right now. */
    private long claim(ServerLevel level, UUID request, boolean products, List<ItemStack> slots) {
        long claimed = 0;
        for (int slot = 0; slot < slots.size(); slot++) {
            var stack = slots.get(slot);
            if (stack.isEmpty()) continue;
            var fluid = AndroidEscrows.fluidOf(stack);
            if (!fluid.isEmpty()) {   // fluids (mB) are kept apart from the item buffer and go to ME as fluids
                var got = AndroidEscrows.claim(level, owner, request, products, slot, fluid.getAmount(), false);
                var gotFluid = AndroidEscrows.fluidOf(got);
                if (!gotFluid.isEmpty()) { OrderJob.merge(fluidOutputs, got); claimed += gotFluid.getAmount(); }
                continue;
            }
            int room = stack.getCount() - ItemHandlerHelper.insertItemStacked(outputs, stack.copy(), true).getCount();
            if (room <= 0) continue;
            var got = AndroidEscrows.claim(level, owner, request, products, slot, room, false);
            if (got.isEmpty()) continue;
            var rest = ItemHandlerHelper.insertItemStacked(outputs, got, false);
            claimed += got.getCount() - rest.getCount();
            // Simulated room was checked first; a remainder here would mean a concurrent change. Never lose it.
            if (!rest.isEmpty()) returnLater.add(rest);
        }
        return claimed;
    }

    private final List<ItemStack> returnLater = new ArrayList<>();
    /** Claimed fluids (Singulo fluid samples) waiting for ME. */
    private final List<ItemStack> fluidOutputs = new ArrayList<>();

    private void returnHeld(OrderJob job) {
        for (var it = job.materials.iterator(); it.hasNext(); ) {
            var stack = it.next();
            if (SinguloLinks.isFluid(stack)) {
                OrderJob.merge(fluidOutputs, stack.copy());
                it.remove();
                changed.run();
                continue;
            }
            var rest = ItemHandlerHelper.insertItemStacked(outputs, stack, false);
            if (rest.isEmpty()) it.remove(); else stack.setCount(rest.getCount());
        }
    }

    // ------------------------------------------------------------------ outputs

    /**
     * Send waiting products/refunds to ME. {@code sink} returns what it could not take; null means "no sink".
     * If ME is full they stay here (and further claims wait in Singulo custody): never discarded.
     */
    public void sendOutputs(@Nullable UnaryOperator<ItemStack> sink) {
        for (var it = returnLater.listIterator(); it.hasNext(); ) {
            var stack = it.next();
            if (SinguloLinks.isFluid(stack)) {
                OrderJob.merge(fluidOutputs, stack.copy());
                it.remove();
                changed.run();
                continue;
            }
            var rest = ItemHandlerHelper.insertItemStacked(outputs, stack.copy(), false);
            if (rest.isEmpty()) it.remove(); else it.set(rest);
            if (!ItemStack.matches(stack, rest)) changed.run();
        }
        boolean any = false, stuck = false;
        for (int i = 0; i < fluidOutputs.size(); i++) {
            any = true;
            if (sink == null) { stuck = true; continue; }
            var stack = fluidOutputs.get(i);
            var rest = sink.apply(stack.copy());
            fluidOutputs.set(i, rest);
            if (!ItemStack.matches(stack, rest)) changed.run();
            stuck |= !rest.isEmpty();
        }
        if (fluidOutputs.removeIf(ItemStack::isEmpty)) changed.run();
        for (int i = 0; i < outputs.getSlots(); i++) {
            var stack = outputs.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            any = true;
            if (sink == null) { stuck = true; continue; }
            var rest = sink.apply(stack.copy());
            if (!ItemStack.matches(stack, rest)) outputs.setStackInSlot(i, rest);
            stuck |= !rest.isEmpty();
        }
        boolean blocked = any && stuck;
        if (blocked != outputBlocked) { outputBlocked = blocked; changed.run(); }
    }

    /** Server-owned fluid custody only. Never accept player recipe samples through this path. */
    public void recoverFluid(ItemStack sample) {
        if (!SinguloLinks.isFluid(sample)) throw new IllegalArgumentException("Not a fluid");
        OrderJob.merge(fluidOutputs, sample.copy());
        changed.run();
    }

    // ------------------------------------------------------------------ cancellation / removal

    /** Cancel open and running requests. Unused materials come back through the normal claim path. */
    public int cancelAll(ServerLevel level, long now) {
        int cancelled = 0;
        for (var job : jobs) {
            if (job.state == OrderJob.State.QUEUED && !job.submitted) {
                job.state = OrderJob.State.CANCELLED;   // never offered: nothing to tell Singulo
                job.finishedAt = now;
                cancelled++;
            } else if (job.state == OrderJob.State.RUNNING && owner != null) {
                AndroidEscrows.cancel(level, owner, job.request);
                cancelled++;
            }
        }
        poll(level, now);
        changed.run();
        return cancelled;
    }

    /**
     * Device broken (not chunk unload): returns what it physically holds (to drop); requests already in Singulo
     * custody are cancelled and handed to {@link OrphanedEscrows}, which drops their products and refunds at
     * {@code pos} once Singulo releases them.
     */
    public List<ItemStack> breakDown(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server && owner != null) {
            for (var job : jobs) {
                if (!job.submitted) continue;
                if (!job.state.terminal()) AndroidEscrows.cancel(server, owner, job.request);
                OrphanedEscrows.get(server).add(owner, job.request, pos);
            }
        }
        List<ItemStack> drops = new ArrayList<>(heldContents());
        drops.addAll(outputContents());
        drops.addAll(returnLater);
        drops.addAll(fluidOutputs);
        if (level instanceof ServerLevel server && owner != null) {
            drops.removeIf(stack -> {
                if (!SinguloLinks.isFluid(stack)) return false;
                OrphanedEscrows.get(server).keepFluid(owner, pos, stack);
                return true;
            });
        }
        fluidOutputs.clear();
        jobs.clear();
        returnLater.clear();
        for (int i = 0; i < OUTPUT_SLOTS; i++) outputs.setStackInSlot(i, ItemStack.EMPTY);
        changed.run();
        return drops;
    }

    // ------------------------------------------------------------------ status

    public void describe(List<Component> lines) {
        lines.add(Component.translatable("gui.applied_singulo.orders",
            count(OrderJob.State.RUNNING), count(OrderJob.State.QUEUED)));
        for (var job : jobs.subList(0, Math.min(6, jobs.size()))) {
            lines.add(Component.literal(" - ").append(job.target().getHoverName())
                .append(Component.literal(" x" + job.count + " (" + job.pushes + " push) " + job.state
                    + " delivered=" + job.delivered + " claimed=" + job.claimedProducts
                    + (job.claimedMaterials > 0 ? " refunded=" + job.claimedMaterials : "")
                    + (job.reason == null ? "" : " [" + job.reason + "]"))));
        }
        lines.add(Component.translatable("gui.applied_singulo.buffers",
            heldStacks(), HOLD_STACKS, outputContents().size(), OUTPUT_SLOTS));
        if (outputBlocked)
            lines.add(Component.translatable("gui.applied_singulo.output_blocked").withStyle(ChatFormatting.YELLOW));
        if (problem != null)
            lines.add(Component.translatable("gui.applied_singulo.problem", problem).withStyle(ChatFormatting.YELLOW));
    }

    // ------------------------------------------------------------------ persistence

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        if (owner != null) tag.putUUID("owner", owner);
        if (console != null) tag.putLong("console", console.asLong());
        tag.put("outputs", outputs.serializeNBT(registries));
        var list = new ListTag();
        for (var job : jobs) list.add(job.save(registries));
        tag.put("jobs", list);
        var later = new ListTag();
        for (var s : returnLater) later.add(s.save(registries));
        tag.put("returnLater", later);
        var fluids = new ListTag();
        for (var s : fluidOutputs) fluids.add(s.save(registries));
        tag.put("fluidOutputs", fluids);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        console = tag.contains("console") ? BlockPos.of(tag.getLong("console")) : null;
        for (int i = 0; i < OUTPUT_SLOTS; i++) outputs.setStackInSlot(i, ItemStack.EMPTY);
        returnLater.clear();
        if (tag.contains("outputs")) {
            var loaded = new ItemStackHandler();
            loaded.deserializeNBT(registries, tag.getCompound("outputs"));
            for (int i = 0; i < loaded.getSlots(); i++) {
                var rest = ItemHandlerHelper.insertItemStacked(outputs, loaded.getStackInSlot(i).copy(), false);
                if (!rest.isEmpty()) returnLater.add(rest);
            }
        }
        fluidOutputs.clear();
        for (Tag t : tag.getList("fluidOutputs", Tag.TAG_COMPOUND)) {
            var s = ItemStack.parseOptional(registries, (CompoundTag) t);
            if (!s.isEmpty()) fluidOutputs.add(s);
        }
        for (Tag t : tag.getList("returnLater", Tag.TAG_COMPOUND)) {
            var s = ItemStack.parseOptional(registries, (CompoundTag) t);
            if (!s.isEmpty()) returnLater.add(s);
        }
        jobs.clear();
        for (Tag t : tag.getList("jobs", Tag.TAG_COMPOUND)) {
            var job = OrderJob.load((CompoundTag) t, registries);
            if (job != null) jobs.add(job);
        }
    }
}
