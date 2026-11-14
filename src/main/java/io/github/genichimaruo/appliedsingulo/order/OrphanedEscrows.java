package io.github.genichimaruo.appliedsingulo.order;

import io.github.genichimaruo.singulo.api.android.AndroidEscrows;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Requests whose device was broken while Singulo still held them. Their products and refunds are claimed as
 * soon as Singulo releases them and dropped where the device stood (only while that spot is loaded), then the
 * request is acknowledged. Nothing is guessed: a request stays here until Singulo reports it empty or unknown.
 */
public final class OrphanedEscrows extends SavedData {
    private static final String NAME = "applied_singulo_orphaned_escrows";
    static final int INTERVAL = 40;

    private record Entry(UUID owner, UUID request, BlockPos pos) { }
    private final List<Entry> entries = new ArrayList<>();

    public static OrphanedEscrows get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(OrphanedEscrows::new, OrphanedEscrows::load), NAME);
    }

    void add(UUID owner, UUID request, BlockPos pos) {
        entries.add(new Entry(owner, request, pos.immutable()));
        setDirty();
    }

    public int size() { return entries.size(); }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % INTERVAL != 0) return;
        var data = level.getDataStorage().get(new SavedData.Factory<>(OrphanedEscrows::new, OrphanedEscrows::load), NAME);
        if (data != null && !data.entries.isEmpty()) data.process(level);
    }

    public void process(ServerLevel level) {
        for (var it = entries.iterator(); it.hasNext(); ) {
            var e = it.next();
            var snap = AndroidEscrows.snapshot(level, e.owner, e.request);
            if (snap.phase() == AndroidEscrows.Phase.UNKNOWN || snap.phase() == AndroidEscrows.Phase.REJECTED) {
                it.remove(); setDirty(); continue;
            }
            if (!level.isLoaded(e.pos)) continue;
            dropAll(level, e, true, snap.products());
            boolean refundable = (snap.phase() == AndroidEscrows.Phase.COMPLETED || snap.phase() == AndroidEscrows.Phase.RECOVERABLE)
                && !snap.workerPending();
            if (!refundable) continue;
            dropAll(level, e, false, snap.materials());
            if (AndroidEscrows.acknowledge(level, e.owner, e.request)) { it.remove(); setDirty(); }
        }
    }

    private static void dropAll(ServerLevel level, Entry e, boolean products, List<ItemStack> slots) {
        for (int slot = 0; slot < slots.size(); slot++) {
            if (slots.get(slot).isEmpty()) continue;
            var got = AndroidEscrows.claim(level, e.owner, e.request, products, slot, slots.get(slot).getCount(), false);
            if (!got.isEmpty()) Containers.dropItemStack(level, e.pos.getX() + 0.5, e.pos.getY() + 0.5, e.pos.getZ() + 0.5, got);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var e : entries) {
            var t = new CompoundTag();
            t.putUUID("owner", e.owner);
            t.putUUID("request", e.request);
            t.putLong("pos", e.pos.asLong());
            list.add(t);
        }
        tag.put("entries", list);
        return tag;
    }

    private static OrphanedEscrows load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new OrphanedEscrows();
        for (Tag raw : tag.getList("entries", Tag.TAG_COMPOUND)) {
            var t = (CompoundTag) raw;
            if (t.hasUUID("owner") && t.hasUUID("request"))
                data.entries.add(new Entry(t.getUUID("owner"), t.getUUID("request"), BlockPos.of(t.getLong("pos"))));
        }
        return data;
    }
}
