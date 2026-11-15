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
    private static final net.minecraft.world.level.saveddata.SavedDataType<OrphanedEscrows> TYPE =
        new net.minecraft.world.level.saveddata.SavedDataType<>(net.minecraft.resources.Identifier.fromNamespaceAndPath("applied_singulo", "orphaned_escrows"),
            OrphanedEscrows::migrateOrCreate, level -> CompoundTag.CODEC.xmap(
                tag -> load(tag, level.registryAccess()), data -> data.save(new CompoundTag(), level.registryAccess())));

    /** Import the old flat save without deleting it or replaying it over a newer save. */
    private static OrphanedEscrows migrateOrCreate(ServerLevel level) {
        var data = net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),
            level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)).resolve("data");
        return migrateOrCreate(data, level.registryAccess());
    }

    private static OrphanedEscrows migrateOrCreate(java.nio.file.Path data, HolderLookup.Provider registries) {
        var id = net.minecraft.resources.Identifier.fromNamespaceAndPath("applied_singulo", "orphaned_escrows");
        if (java.nio.file.Files.exists(id.withSuffix(".dat").resolveAgainst(data)))
            throw new IllegalStateException("Cannot decode existing orphaned escrow data; restore a verified backup instead of resetting custody");
        var legacy = data.resolve(NAME + ".dat");
        if (!java.nio.file.Files.exists(legacy)) return new OrphanedEscrows();
        try {
            var root = net.minecraft.nbt.NbtIo.readCompressed(legacy, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            var imported = load(root.getCompound("data").orElseThrow(), registries);
            imported.setDirty();
            return imported;
        } catch (java.io.IOException | RuntimeException failure) {
            throw new IllegalStateException("Cannot safely import legacy orphaned escrow data", failure);
        }
    }

    private record Entry(UUID owner, UUID request, BlockPos pos) { }
    private final List<Entry> entries = new ArrayList<>();
    private record BufferedFluid(UUID owner, BlockPos pos, ItemStack sample) { }
    private final List<BufferedFluid> fluids = new ArrayList<>();

    void keepFluid(UUID owner, BlockPos pos, ItemStack sample) {
        fluids.add(new BufferedFluid(owner, pos.immutable(), sample.copy()));
        setDirty();
    }

    /** Replacing an interface at the same position recovers this owner's fluid, including partial mB. */
    public void recover(ServerLevel level, UUID owner, BlockPos pos, AndroidOrderQueue queue) {
        for (var it = fluids.iterator(); it.hasNext(); ) {
            var f = it.next();
            if (!f.owner.equals(owner) || !f.pos.equals(pos)) continue;
            queue.recoverFluid(f.sample);
            it.remove(); setDirty();
        }
        for (var e : entries) {
            if (!e.owner.equals(owner) || !e.pos.equals(pos)) continue;
            var snap = AndroidEscrows.snapshot(level, owner, e.request);
            recoverFluidSlots(level, queue, e, true, snap.products());
            if ((snap.phase() == AndroidEscrows.Phase.COMPLETED || snap.phase() == AndroidEscrows.Phase.RECOVERABLE)
                    && !snap.workerPending()) recoverFluidSlots(level, queue, e, false, snap.materials());
        }
    }

    private static void recoverFluidSlots(ServerLevel level, AndroidOrderQueue queue, Entry e,
            boolean products, List<ItemStack> slots) {
        for (int slot = 0; slot < slots.size(); slot++) {
            var fluid = AndroidEscrows.fluidOf(slots.get(slot));
            if (fluid.isEmpty()) continue;
            var got = AndroidEscrows.claim(level, e.owner, e.request, products, slot, fluid.getAmount(), false);
            if (!got.isEmpty()) queue.recoverFluid(got);
        }
    }

    public static OrphanedEscrows get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    void add(UUID owner, UUID request, BlockPos pos) {
        entries.add(new Entry(owner, request, pos.immutable()));
        setDirty();
    }

    public int size() { return entries.size(); }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % INTERVAL != 0) return;
        // Initialize on the first scheduled tick so legacy-only saves migrate even without a new interface.
        var data = get(level);
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
            // A sample is not a physical fluid container. Keep fluid in escrow until owner replacement.
            if (slots.get(slot).isEmpty() || SinguloLinks.isFluid(slots.get(slot))) continue;
            var got = AndroidEscrows.claim(level, e.owner, e.request, products, slot, slots.get(slot).getCount(), false);
            if (!got.isEmpty()) Containers.dropItemStack(level, e.pos.getX() + 0.5, e.pos.getY() + 0.5, e.pos.getZ() + 0.5, got);
        }
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var e : entries) {
            var t = new CompoundTag();
            t.store("owner", net.minecraft.core.UUIDUtil.CODEC, e.owner);
            t.store("request", net.minecraft.core.UUIDUtil.CODEC, e.request);
            t.putLong("pos", e.pos.asLong());
            list.add(t);
        }
        tag.put("entries", list);
        var buffered = new ListTag();
        for (var f : fluids) {
            var t = new CompoundTag();
            t.store("owner", net.minecraft.core.UUIDUtil.CODEC, f.owner);
            t.putLong("pos", f.pos.asLong());
            t.put("sample", ItemStack.CODEC.encodeStart(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), f.sample).getOrThrow());
            buffered.add(t);
        }
        tag.put("fluids", buffered);
        return tag;
    }

    private static OrphanedEscrows load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new OrphanedEscrows();
        for (Tag raw : tag.getListOrEmpty("entries")) {
            var t = (CompoundTag) raw;
            if (t.read("owner", net.minecraft.core.UUIDUtil.CODEC).isPresent() && t.read("request", net.minecraft.core.UUIDUtil.CODEC).isPresent())
                data.entries.add(new Entry(t.read("owner", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), t.read("request", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), BlockPos.of(t.getLongOr("pos", 0L))));
        }
        for (Tag raw : tag.getListOrEmpty("fluids")) {
            var t = (CompoundTag) raw;
            var sample = ItemStack.CODEC.parse(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), t.getCompoundOrEmpty("sample")).result().orElse(ItemStack.EMPTY);
            if (t.read("owner", net.minecraft.core.UUIDUtil.CODEC).isPresent() && SinguloLinks.isFluid(sample))
                data.fluids.add(new BufferedFluid(t.read("owner", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), BlockPos.of(t.getLongOr("pos", 0L)), sample));
        }
        return data;
    }
}
