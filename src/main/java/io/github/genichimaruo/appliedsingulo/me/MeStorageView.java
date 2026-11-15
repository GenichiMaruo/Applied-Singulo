package io.github.genichimaruo.appliedsingulo.me;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import io.github.genichimaruo.appliedsingulo.order.SinguloLinks;
import io.github.genichimaruo.singulo.api.android.AndroidSharedStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * One face of the Android ME Interface seen as a Singulo storage: the items stored in its ME network, one slot per
 * item type plus one empty slot for insertion (like Singulo's QIO view).
 *
 * <ul>
 * <li>Only a face that touches a Supply Port linked to a Work Console owned by the interface's owner, or such a console
 *     itself, shows anything. Every other face (pipes, hoppers, someone else's factory) sees no slots and cannot insert.</li>
 * <li>{@link #sharedStorageKey()} is the same for every interface face of one ME grid, so Singulo counts a network once
 *     even when several interfaces touch the same console's Supply Ports.</li>
 * <li>Slot order is stable within a game tick; extractions and insertions adjust the counts of that tick's listing.</li>
 * </ul>
 */
final class MeStorageView implements IItemHandler, AndroidSharedStorage {
    private static final Map<IGrid, Long> GRID_KEYS = new WeakHashMap<>();
    private static final AtomicLong NEXT_KEY = new AtomicLong(1);

    private final AndroidInterfaceLogic host;
    private final Direction side;
    /** Unique key while there is no grid (nothing is shown then, but the key must not merge with a real grid). */
    private final long ownKey = -NEXT_KEY.getAndIncrement();
    private long builtAt = Long.MIN_VALUE;
    private boolean allowed;
    private final List<ItemStack> types = new ArrayList<>();
    private final List<AEItemKey> keys = new ArrayList<>();

    MeStorageView(AndroidInterfaceLogic host, Direction side) {
        this.host = host;
        this.side = side;
    }

    static synchronized long gridKey(IGrid grid) {
        return GRID_KEYS.computeIfAbsent(grid, g -> NEXT_KEY.getAndIncrement());
    }

    @Override
    public long sharedStorageKey() {
        var grid = host.mainNode().getGrid();
        return grid == null ? ownKey : gridKey(grid);
    }

    /** Rebuild once per tick: is this face allowed, and what does the network hold. */
    private void refresh() {
        if (!(host.host().hostLevel() instanceof ServerLevel level)) { allowed = false; types.clear(); keys.clear(); return; }
        long now = level.getGameTime();
        if (now == builtAt) return;
        builtAt = now;
        types.clear();
        keys.clear();
        var owner = host.queue().owner();
        var grid = host.mainNode().getGrid();
        allowed = owner != null && grid != null && host.mainNode().isActive()
            && SinguloLinks.ownedWorkBlock(level, host.host().hostPos().relative(side), owner);
        if (!allowed) return;
        for (var entry : grid.getStorageService().getCachedInventory()) {
            if (entry.getKey() instanceof AEItemKey item && entry.getLongValue() > 0) {
                keys.add(item);
                types.add(item.toStack((int) Math.min(Integer.MAX_VALUE, entry.getLongValue())));
            }
        }
    }

    private IActionSource source() { return IActionSource.ofMachine(host.host()); }

    @Override
    public int getSlots() {
        refresh();
        return allowed ? types.size() + 1 : 0;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        refresh();
        return allowed && slot >= 0 && slot < types.size() ? types.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        refresh();
        var grid = host.mainNode().getGrid();
        var key = AEItemKey.of(stack);
        if (!allowed || grid == null || key == null) return stack;
        long in = grid.getStorageService().getInventory().insert(key, stack.getCount(),
            simulate ? Actionable.SIMULATE : Actionable.MODULATE, source());
        if (in <= 0) return stack;
        if (!simulate) {
            int i = keys.indexOf(key);
            if (i >= 0) types.set(i, types.get(i).copyWithCount((int) Math.min(Integer.MAX_VALUE, types.get(i).getCount() + in)));
        }
        return in >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount((int) (stack.getCount() - in));
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        refresh();
        var grid = host.mainNode().getGrid();
        if (!allowed || grid == null || amount <= 0 || slot < 0 || slot >= types.size() || types.get(slot).isEmpty())
            return ItemStack.EMPTY;
        var type = types.get(slot);
        long out = grid.getStorageService().getInventory().extract(keys.get(slot), Math.min(amount, type.getMaxStackSize()),
            simulate ? Actionable.SIMULATE : Actionable.MODULATE, source());
        if (out <= 0) return ItemStack.EMPTY;
        if (!simulate) {
            int left = (int) Math.max(0, type.getCount() - out);
            types.set(slot, left > 0 ? type.copyWithCount(left) : ItemStack.EMPTY);
        }
        return type.copyWithCount((int) out);
    }

    @Override public int getSlotLimit(int slot) { return Integer.MAX_VALUE; }
    @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
}
