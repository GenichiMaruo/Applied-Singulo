package io.github.genichimaruo.appliedsingulo.me;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import io.github.genichimaruo.appliedsingulo.order.AndroidOrderQueue;
import io.github.genichimaruo.appliedsingulo.order.OrderJob;
import io.github.genichimaruo.appliedsingulo.order.SinguloLinks;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Everything an Android ME Interface does, shared by the block and the cable panel (like AE2's InterfaceLogic).
 *
 * <ul>
 * <li>Single-step patterns are offered on the host's main node at the configured priority; raw-materials chains on a
 *     second internal node at priority + 1, so AE2 always tries the chain first (AE2 orders one item's patterns only
 *     by provider priority).</li>
 * <li>Pushes are batched per output into Singulo escrow requests ({@link AndroidOrderQueue}); products and refunds
 *     are claimed back by request and inserted into ME.</li>
 * <li>Faces touching the owner's Supply Port / Work Console see the ME network as Singulo storage ({@link MeStorageView}).</li>
 * </ul>
 */
public final class AndroidInterfaceLogic {
    static final int PATTERN_REFRESH = 100;

    private final AndroidInterfaceHost host;
    private final IManagedGridNode mainNode;
    private final AndroidOrderQueue queue;
    private final StepProvider stepProvider = new StepProvider();
    private final ChainProvider chainProvider = new ChainProvider();
    private final IManagedGridNode chainNode;
    @Nullable private IGridConnection chainLink;
    private int priority;
    private List<AndroidRecipePattern> patterns = List.of();
    @Nullable private BlockPos patternConsole;
    /** Game time of the last pattern refresh; -1 = refresh on the next tick. */
    private long lastRefresh = -1;
    /** Diagnostics: AE2 pushes since load, per product and kind. Not saved. */
    private final LinkedHashMap<String, InterfaceStatus.Push> recentPushes = new LinkedHashMap<>();
    private final EnumMap<Direction, MeStorageView> storageViews = new EnumMap<>(Direction.class);

    // ------------------------------------------------------------------ keep in stock (ME level maintenance)
    public static final int STOCK_SLOTS = 9;
    static final int STOCK_INTERVAL = 40;
    private final AEItemKey[] stockItems = new AEItemKey[STOCK_SLOTS];
    private final long[] stockAmounts = new long[STOCK_SLOTS];
    @SuppressWarnings("unchecked")
    private final java.util.concurrent.Future<appeng.api.networking.crafting.ICraftingPlan>[] stockCalcs =
        new java.util.concurrent.Future[STOCK_SLOTS];
    private final String[] stockState = new String[STOCK_SLOTS];
    private final long[] stockRequested = new long[STOCK_SLOTS];
    private final appeng.api.networking.IGrid[] stockGrids = new appeng.api.networking.IGrid[STOCK_SLOTS];

    private void cancelStockCalculation(int slot) {
        var pending = stockCalcs[slot];
        if (pending != null) pending.cancel(true);
        stockCalcs[slot] = null;
        stockGrids[slot] = null;
        stockRequested[slot] = 0;
    }

    /** @param mainNode the host's main node, not created yet: the step provider is added as a service here. */
    public AndroidInterfaceLogic(AndroidInterfaceHost host, IManagedGridNode mainNode) {
        this.host = host;
        this.mainNode = mainNode;
        this.queue = new AndroidOrderQueue(host::saveChanges);
        mainNode.addService(ICraftingProvider.class, stepProvider);
        this.chainNode = GridHelper.createManagedNode(this, ChainListener.INSTANCE)
            .setIdlePowerUsage(0)
            .setInWorldNode(false)
            .setTagName("android_me_interface_chain")
            .addService(ICraftingProvider.class, chainProvider);
    }

    public AndroidInterfaceHost host() { return host; }
    public AndroidOrderQueue queue() { return queue; }
    public IManagedGridNode mainNode() { return mainNode; }
    public List<AndroidRecipePattern> patterns() { return patterns; }
    public int priority() { return priority; }

    @Nullable public AEItemKey stockItem(int slot) { return stockItems[slot]; }
    public long stockAmount(int slot) { return stockAmounts[slot]; }

    /** Keep at least {@code amount} of {@code item} in ME by ordering AE2 crafting jobs (null or 0 clears the slot). */
    public void setStock(int slot, @Nullable AEItemKey item, long amount) {
        if (slot < 0 || slot >= STOCK_SLOTS) return;
        stockItems[slot] = amount > 0 ? item : null;
        stockAmounts[slot] = item == null ? 0 : Math.max(0, Math.min(amount, 1_000_000L));
        cancelStockCalculation(slot);
        stockState[slot] = null;
        host.saveChanges();
    }

    /**
     * For each keep-in-stock slot: if ME holds less than the target and AE2 is not already crafting it, calculate and
     * submit an AE2 crafting job for the difference (CRAFT_LESS: as many as the materials allow). The job runs through
     * this interface's patterns, i.e. the androids.
     */
    void updateStock(ServerLevel level) {
        var grid = mainNode.getGrid();
        if (grid == null || !mainNode.isActive()) {
            for (int i = 0; i < STOCK_SLOTS; i++) cancelStockCalculation(i);
            return;
        }
        var crafting = grid.getCraftingService();
        var stored = grid.getStorageService().getCachedInventory();
        var source = IActionSource.ofMachine(host);
        var handled = new java.util.HashMap<AEItemKey, Integer>();
        for (int i = 0; i < STOCK_SLOTS; i++) {
            var key = stockItems[i];
            if (key == null || stockAmounts[i] <= 0) {
                cancelStockCalculation(i);
                stockState[i] = null;
                continue;
            }
            // Multiple slots are one target, not independent orders. Honour the largest target.
            var primary = handled.putIfAbsent(key, i);
            if (primary != null) { cancelStockCalculation(i); stockState[i] = stockState[primary]; continue; }
            long target = stockAmounts[i];
            for (int j = i + 1; j < STOCK_SLOTS; j++)
                if (key.equals(stockItems[j])) target = Math.max(target, stockAmounts[j]);
            long needed = Math.max(0, target - stored.get(key));
            if (needed == 0 || crafting.isRequesting(key)) {
                cancelStockCalculation(i);
                stockState[i] = needed == 0 ? "ok" : "crafting";
                continue;
            }
            var calc = stockCalcs[i];
            // A calculation belongs to one grid and one deficit; never submit a stale plan.
            if (calc != null && (stockGrids[i] != grid || stockRequested[i] != needed)) {
                cancelStockCalculation(i);
                calc = null;
            }
            if (calc != null) {
                if (!calc.isDone()) continue;
                stockCalcs[i] = null;
                stockGrids[i] = null;
                stockRequested[i] = 0;
                try {
                    var plan = calc.get();
                    if (plan.simulation() || plan.patternTimes().isEmpty()) {
                        stockState[i] = "missing";
                    } else {
                        var result = crafting.submitJob(plan, null, null, false, source);
                        stockState[i] = result.successful() ? "crafting" : "no_cpu";
                    }
                } catch (Exception failed) {
                    stockState[i] = "missing";
                }
                continue;
            }
            if (crafting.getCraftingFor(key).isEmpty()) { stockState[i] = "no_pattern"; continue; }
            appeng.api.networking.crafting.ICraftingSimulationRequester requester = () -> source;
            stockRequested[i] = needed;
            stockGrids[i] = grid;
            stockCalcs[i] = crafting.beginCraftingCalculation(level, requester, key, needed,
                appeng.api.networking.crafting.CalculationStrategy.CRAFT_LESS);
        }
    }

    public void setPriority(int priority) {
        if (this.priority == priority) return;
        this.priority = priority;
        host.saveChanges();
        ICraftingProvider.requestUpdate(mainNode);
        ICraftingProvider.requestUpdate(chainNode);
    }

    // ------------------------------------------------------------------ node lifecycle

    /** Call right after the host created its main node (server side). */
    public void onMainNodeCreated(Level level, BlockPos pos) {
        chainNode.create(level, pos);
        var main = mainNode.getNode();
        var chain = chainNode.getNode();
        if (main == null || chain == null || chainLink != null) return;
        try {
            chainLink = GridHelper.createConnection(main, chain);
        } catch (RuntimeException failed) {
            chainLink = null;   // chains are then not offered; single steps still work
        }
    }

    public void onMainNodeStateChanged() { ICraftingProvider.requestUpdate(mainNode); }

    /** Host removed or unloaded. */
    public void destroy() {
        for (int i = 0; i < STOCK_SLOTS; i++) cancelStockCalculation(i);
        chainLink = null;
        chainNode.destroy();
    }

    public void setOwningPlayer(Player player) {
        queue.setOwner(player.getUUID());
        chainNode.setOwningPlayer(player);
    }

    private enum ChainListener implements IGridNodeListener<AndroidInterfaceLogic> {
        INSTANCE;
        @Override public void onSaveChanges(AndroidInterfaceLogic logic, IGridNode node) { logic.host.saveChanges(); }
        @Override public void onStateChanged(AndroidInterfaceLogic logic, IGridNode node, State state) {
            ICraftingProvider.requestUpdate(logic.chainNode);
        }
    }

    // ------------------------------------------------------------------ AE2 crafting

    private final class StepProvider implements ICraftingProvider {
        @Override public List<IPatternDetails> getAvailablePatterns() {
            return patterns.stream().filter(p -> !p.chained()).map(p -> (IPatternDetails) p).toList();
        }
        @Override public int getPatternPriority() { return priority; }
        @Override public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) { return push(details, inputs); }
        @Override public boolean isBusy() { return !queue.hasRoom(); }
    }

    private final class ChainProvider implements ICraftingProvider {
        @Override public List<IPatternDetails> getAvailablePatterns() {
            return patterns.stream().filter(AndroidRecipePattern::chained).map(p -> (IPatternDetails) p).toList();
        }
        @Override public int getPatternPriority() { return priority == Integer.MAX_VALUE ? priority : priority + 1; }
        @Override public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) { return push(details, inputs); }
        @Override public boolean isBusy() { return !queue.hasRoom(); }
    }

    /** AE2 inputs as item stacks (fluids as Singulo fluid samples), or null for any other kind of input. */
    @Nullable
    public static List<ItemStack> materials(KeyCounter[] inputs) {
        List<ItemStack> materials = new ArrayList<>();
        for (var counter : inputs) {
            for (var entry : counter) {
                if (entry.getLongValue() <= 0) continue;
                if (entry.getKey() instanceof appeng.api.stacks.AEFluidKey fluid) {
                    for (long left = entry.getLongValue(); left > 0; ) {
                        int n = (int) Math.min(left, Integer.MAX_VALUE);
                        materials.add(io.github.genichimaruo.singulo.api.android.AndroidEscrows.fluidSample(fluid.toStack(n)));
                        left -= n;
                    }
                    continue;
                }
                if (!(entry.getKey() instanceof AEItemKey item)) return null;
                for (long left = entry.getLongValue(); left > 0; ) {
                    int n = (int) Math.min(left, item.getMaxStackSize());
                    materials.add(item.toStack(n));
                    left -= n;
                }
            }
        }
        return materials;
    }

    public boolean push(IPatternDetails details, KeyCounter[] inputs) {
        if (!(host.hostLevel() instanceof ServerLevel server) || !mainNode.isActive()) return false;
        if (!(details instanceof AndroidRecipePattern pattern) || !patterns.contains(pattern))
            return queue.reject("unknown_pattern");
        var console = queue.console();
        if (console == null) return queue.reject("no_console");
        if (queue.owner() == null) return queue.reject("no_owner");
        var access = SinguloLinks.checkAccess(server, console, queue.owner());
        if (access != null) return queue.reject(access);
        var materials = materials(inputs);
        if (materials == null) return queue.reject("non_item_input");
        if (!queue.accept(pattern.output(), pattern.outputCount(), materials, console, server.getGameTime())) return false;
        String key = BuiltInRegistries.ITEM.getKey(pattern.output().getItem()) + (pattern.chained() ? "#chain" : "#step");
        var before = recentPushes.remove(key);
        recentPushes.put(key, new InterfaceStatus.Push(BuiltInRegistries.ITEM.getKey(pattern.output().getItem()).toString(),
            pattern.chained(), before == null ? 1 : before.count() + 1));
        while (recentPushes.size() > 6) recentPushes.remove(recentPushes.keySet().iterator().next());
        return true;
    }

    /** Rebuild the auto-patterns from the console's Recipe Racks; tell AE2 only when they changed. */
    public void refreshPatterns() {
        if (!(host.hostLevel() instanceof ServerLevel server)) return;
        var console = queue.console();
        List<AndroidRecipePattern> next = new ArrayList<>();
        if (console != null && queue.owner() != null && server.isLoaded(console)) {
            for (var recipe : SinguloLinks.recipes(server, console, queue.owner())) {
                var pattern = AndroidRecipePattern.of(recipe);
                if (pattern != null && !next.contains(pattern)) {
                    next.add(pattern);
                }
            }
        }
        replacePatterns(console, next);
    }

    /** Compare full definition keys, never their display-only toString representation. */
    private void replacePatterns(@Nullable BlockPos console, List<AndroidRecipePattern> next) {
        if (!Objects.equals(patternConsole, console) || !patterns.equals(next)) {
            patternConsole = console;
            patterns = List.copyOf(next);
            ICraftingProvider.requestUpdate(mainNode);
            ICraftingProvider.requestUpdate(chainNode);
        }
    }

    // ------------------------------------------------------------------ ticking

    @Nullable
    private UnaryOperator<ItemStack> meSink() {
        var grid = mainNode.getGrid();
        if (grid == null || !mainNode.isActive()) return null;
        var storage = grid.getStorageService().getInventory();
        var source = IActionSource.ofMachine(host);
        return stack -> {
            var fluid = io.github.genichimaruo.singulo.api.android.AndroidEscrows.fluidOf(stack);
            if (!fluid.isEmpty()) {
                long inserted = storage.insert(appeng.api.stacks.AEFluidKey.of(fluid), fluid.getAmount(), Actionable.MODULATE, source);
                return inserted >= fluid.getAmount() ? ItemStack.EMPTY
                    : io.github.genichimaruo.singulo.api.android.AndroidEscrows.fluidSample(fluid.copyWithAmount((int) (fluid.getAmount() - inserted)));
            }
            var key = AEItemKey.of(stack);
            if (key == null) return stack;
            long inserted = storage.insert(key, stack.getCount(), Actionable.MODULATE, source);
            return stack.copyWithCount((int) (stack.getCount() - inserted));
        };
    }

    /** Call every server tick. */
    public void tick(ServerLevel level) {
        long time = level.getGameTime();
        if (!Objects.equals(patternConsole, queue.console())) lastRefresh = -1;
        if (lastRefresh < 0 || time - lastRefresh >= PATTERN_REFRESH) { lastRefresh = time; refreshPatterns(); }
        queue.tick(level, time, host.hostPos().hashCode());
        if (time % 40 == 0 && queue.owner() != null)
            io.github.genichimaruo.appliedsingulo.order.OrphanedEscrows.get(level)
                .recover(level, queue.owner(), host.hostPos(), queue);
        if (time % 5 == 0) queue.sendOutputs(meSink());
        if ((time + host.hostPos().hashCode()) % STOCK_INTERVAL == 0) updateStock(level);
    }

    // ------------------------------------------------------------------ storage view

    /** The ME network as Singulo storage towards {@code side}, or null for faces the host does not expose. */
    @Nullable
    public IItemHandler storageView(@Nullable Direction side) {
        if (side == null || !host.exposesStorageTowards(side)) return null;
        return storageViews.computeIfAbsent(side, d -> new MeStorageView(this, d));
    }

    // ------------------------------------------------------------------ player interaction

    public boolean mayUse(Player player) {
        var owner = queue.owner();
        return owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2);
    }

    /** Placed by {@code player}: owner, and the owner's Work Console next to {@code pos} becomes the order target. */
    public void onPlaced(Player player) {
        setOwningPlayer(player);
        if (!(host.hostLevel() instanceof ServerLevel server)) return;
        var console = SinguloLinks.ownedNeighbourConsole(server, host.hostPos(), player.getUUID());
        if (console != null) {
            queue.setConsole(console);
            player.displayClientMessage(Component.translatable("message.applied_singulo.device.console_set", console.toShortString()), true);
        } else {
            player.displayClientMessage(Component.translatable("message.applied_singulo.device.console_hint"), true);
        }
    }

    /** A Work Location Card used on the host: point it at the card's console. Returns false for other items. */
    public boolean useCard(ItemStack stack, Player player) {
        if (!SinguloLinks.isLocationCard(stack)) return false;
        if (!(host.hostLevel() instanceof ServerLevel server)) return true;
        var owner = queue.owner();
        var console = SinguloLinks.cardTarget(stack, server);
        if (owner != null && !owner.equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.applied_singulo.device.not_owner"), true);
        } else if (console == null) {
            player.displayClientMessage(Component.translatable("message.applied_singulo.device.card_empty"), true);
        } else {
            var access = SinguloLinks.checkAccess(server, console, player.getUUID());
            if (access != null && !"console_receipt_ledger_full".equals(access)) {
                player.displayClientMessage(Component.translatable("message.applied_singulo.device.console_refused",
                    console.toShortString(), access), true);
            } else {
                if (owner == null) setOwningPlayer(player);
                queue.setConsole(console);
                player.displayClientMessage(Component.translatable("message.applied_singulo.device.console_set",
                    console.toShortString()), true);
            }
        }
        return true;
    }

    public int cancelAll() {
        return host.hostLevel() instanceof ServerLevel server ? queue.cancelAll(server, server.getGameTime()) : 0;
    }

    /** Host broken: items it physically holds (to drop); requests in Singulo custody go to orphan recovery. */
    public List<ItemStack> breakDown() {
        var level = host.hostLevel();
        if (level == null) return List.of();
        // Fluids remain in persistent recovery custody until this owner replaces an interface here.
        return queue.breakDown(level, host.hostPos());
    }

    // ------------------------------------------------------------------ status

    public InterfaceStatus status() {
        var node = mainNode.getNode();
        var link = mainNode.isActive() ? InterfaceStatus.Link.ONLINE
            : node != null && mainNode.isPowered() ? InterfaceStatus.Link.NO_CHANNEL : InterfaceStatus.Link.OFFLINE;
        var console = queue.console();
        String consoleProblem = "";
        if (console != null && host.hostLevel() instanceof ServerLevel server && queue.owner() != null) {
            var access = SinguloLinks.checkAccess(server, console, queue.owner());
            if (access != null) consoleProblem = access;
        }
        List<InterfaceStatus.Job> jobs = new ArrayList<>();
        for (var job : queue.jobs()) {
            if (jobs.size() >= 9) break;
            jobs.add(new InterfaceStatus.Job(BuiltInRegistries.ITEM.getKey(job.target().getItem()).toString(), job.count(),
                job.state().name(), job.submitted(), job.delivered(), job.claimedProducts(), job.pushes(),
                job.reason() == null ? "" : job.reason()));
        }
        java.util.LinkedHashMap<String, Long> waiting = new java.util.LinkedHashMap<>();
        for (var stack : queue.outputContents())
            waiting.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), (long) stack.getCount(), Long::sum);
        List<InterfaceStatus.Stack> waitingList = waiting.entrySet().stream().limit(9)
            .map(e -> new InterfaceStatus.Stack(e.getKey(), e.getValue())).toList();
        List<InterfaceStatus.Stock> stock = new ArrayList<>();
        var grid = mainNode.getGrid();
        var cached = grid == null ? null : grid.getStorageService().getCachedInventory();
        for (int i = 0; i < STOCK_SLOTS; i++) {
            var key = stockItems[i];
            stock.add(key == null ? InterfaceStatus.Stock.EMPTY : new InterfaceStatus.Stock(
                BuiltInRegistries.ITEM.getKey(key.getItem()).toString(), stockAmounts[i], cached == null ? 0 : cached.get(key),
                stockState[i] == null ? "" : stockState[i]));
        }
        int chains = (int) patterns.stream().filter(AndroidRecipePattern::chained).count();
        return new InterfaceStatus(link, priority, console == null ? "" : console.toShortString(), consoleProblem,
            patterns.size() - chains, chains, jobs, (int) queue.count(OrderJob.State.RUNNING), (int) queue.count(OrderJob.State.QUEUED),
            queue.heldStacks(), AndroidOrderQueue.HOLD_STACKS, queue.outputContents().size(), AndroidOrderQueue.OUTPUT_SLOTS,
            queue.outputBlocked(), queue.problem() == null ? "" : queue.problem(), List.copyOf(recentPushes.values()), waitingList, stock);
    }

    /** Text lines for the {@code /appliedsingulo interface} command. */
    public List<Component> describe() {
        List<Component> lines = new ArrayList<>();
        var s = status();
        lines.add(host.hostIcon().getHoverName().copy().withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" @ " + host.hostPos().toShortString())));
        lines.add(Component.translatable("gui.applied_singulo.link." + s.link().name().toLowerCase(java.util.Locale.ROOT)));
        lines.add(s.console().isEmpty()
            ? Component.translatable("message.applied_singulo.device.no_console").withStyle(ChatFormatting.RED)
            : Component.translatable("gui.applied_singulo.console", s.console()));
        lines.add(Component.translatable("gui.applied_singulo.recipes", s.patterns(), s.chainPatterns()));
        lines.add(Component.translatable("gui.applied_singulo.priority", s.priority()));
        queue.describe(lines);
        return lines;
    }

    // ------------------------------------------------------------------ persistence

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        queue.save(tag, registries);
        chainNode.saveToNBT(tag);
        tag.putInt("priority", priority);
        var stock = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < STOCK_SLOTS; i++) {
            if (stockItems[i] == null) continue;
            var entry = new CompoundTag();
            entry.putInt("slot", i);
            entry.put("item", stockItems[i].toTagGeneric(registries));
            entry.putLong("amount", stockAmounts[i]);
            stock.add(entry);
        }
        tag.put("stock", stock);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        queue.load(tag, registries);
        chainNode.loadFromNBT(tag);
        priority = tag.getInt("priority");
        for (int i = 0; i < STOCK_SLOTS; i++) cancelStockCalculation(i);
        java.util.Arrays.fill(stockState, null);
        java.util.Arrays.fill(stockItems, null);
        java.util.Arrays.fill(stockAmounts, 0);
        for (var raw : tag.getList("stock", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            int slot = entry.getInt("slot");
            if (slot < 0 || slot >= STOCK_SLOTS) continue;
            if (appeng.api.stacks.AEKey.fromTagGeneric(registries, entry.getCompound("item")) instanceof AEItemKey key) {
                stockItems[slot] = key;
                stockAmounts[slot] = entry.getLong("amount");
            }
        }
    }
}
