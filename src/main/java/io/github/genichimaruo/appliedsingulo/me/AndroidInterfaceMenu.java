package io.github.genichimaruo.appliedsingulo.me;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.MenuOpener;
import appeng.menu.guisync.GuiSync;
import appeng.menu.locator.MenuHostLocator;
import io.github.genichimaruo.appliedsingulo.registry.ModContent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/**
 * AE2 menu of the Android ME Interface (block or panel): a read-only status display synchronised as one
 * {@link InterfaceStatus}, AE2's standard priority sub-screen, and a "cancel all orders" action.
 */
public final class AndroidInterfaceMenu extends AEBaseMenu {
    private static final appeng.menu.guisync.ClientActionKey<Void> ACTION_CANCEL_ALL = new appeng.menu.guisync.ClientActionKey<>("cancelAll");
    private static final appeng.menu.guisync.ClientActionKey<StockAction> ACTION_SET_STOCK = new appeng.menu.guisync.ClientActionKey<>("setStock");

    /** Client action payload (encoded with explicit bounded fields). */
    public static final class StockAction {
        public int slot;
        public String item = "";
        public long amount;

        public StockAction() { }

        public StockAction(int slot, String item, long amount) {
            this.slot = slot;
            this.item = item;
            this.amount = amount;
        }
    }
    public static final int DEPOSIT_SLOTS = 9;

    /** Insert-only view of one output-buffer slot. */
    private static final class DepositSlot extends net.neoforged.neoforge.items.SlotItemHandler {
        DepositSlot(net.neoforged.neoforge.items.IItemHandler handler, int index) { super(handler, index, 0, 0); }
        @Override public boolean mayPickup(Player player) { return false; }
    }

    private final AndroidInterfaceHost host;

    @GuiSync(1)
    public InterfaceStatus status = InterfaceStatus.EMPTY;

    public AndroidInterfaceMenu(int id, Inventory playerInventory, AndroidInterfaceHost host) {
        super(ModContent.ANDROID_INTERFACE_MENU.get(), id, playerInventory, host);
        this.host = host;
        // "Waiting for ME" row: the first 9 slots of the output buffer. Players may deposit (it all goes to ME) but
        // not take: these are products and refunds an AE2 crafting job is waiting for.
        var buffer = host.logic().queue().outputBuffer();
        for (int i = 0; i < DEPOSIT_SLOTS; i++) addSlot(new DepositSlot(buffer, i), SlotSemantics.STORAGE);
        createPlayerInventorySlots(playerInventory);
        registerClientAction(ACTION_CANCEL_ALL, this::cancelAll);
        registerClientAction(ACTION_SET_STOCK, net.minecraft.network.codec.StreamCodec.of((buf, action) -> { buf.writeVarInt(action.slot); buf.writeUtf(action.item, 32767); buf.writeVarLong(action.amount); }, buf -> new StockAction(buf.readVarInt(), buf.readUtf(32767), buf.readVarLong())), this::setStock);
    }

    public static void open(Player player, MenuHostLocator locator) {
        MenuOpener.open(ModContent.ANDROID_INTERFACE_MENU.get(), player, locator);
    }

    public AndroidInterfaceHost host() { return host; }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) status = host.logic().status();
        super.broadcastChanges();
    }

    /** Client: ask the server; server: set a keep-in-stock slot (owner or operator only). Empty item clears it. */
    public void setStock(StockAction action) {
        if (isClientSide()) {
            sendClientAction(ACTION_SET_STOCK, action);
            return;
        }
        if (!host.logic().mayUse(getPlayer())) return;
        var id = net.minecraft.resources.Identifier.tryParse(action.item);
        var item = id == null || action.item.isEmpty() ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        host.logic().setStock(action.slot, item == null ? null : appeng.api.stacks.AEItemKey.of(item), item == null ? 0 : action.amount);
    }

    /** Client: ask the server; server: cancel open and running requests (owner or operator only). */
    public void cancelAll() {
        if (isClientSide()) {
            sendClientAction(ACTION_CANCEL_ALL);
            return;
        }
        if (host.logic().mayUse(getPlayer())) host.logic().cancelAll();
    }
}
