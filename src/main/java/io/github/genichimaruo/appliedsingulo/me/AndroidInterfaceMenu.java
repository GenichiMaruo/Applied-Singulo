package io.github.genichimaruo.appliedsingulo.me;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.MenuOpener;
import appeng.menu.guisync.GuiSync;
import appeng.menu.locator.MenuLocator;
import io.github.genichimaruo.appliedsingulo.registry.ModContent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/**
 * AE2 menu of the Android ME Interface (block or panel): a read-only status display synchronised as one
 * {@link InterfaceStatus}, AE2's standard priority sub-screen, and a "cancel all orders" action.
 */
public final class AndroidInterfaceMenu extends AEBaseMenu {
    private static final String ACTION_CANCEL_ALL = "cancelAll";
    private static final String ACTION_SET_STOCK = "setStock";

    /** Client action payload (serialised by AE2 as JSON). */
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
    /** Legacy AE2's MenuTypeBuilder only registers in ae2's namespace. Keep our type in our own registry. */
    public static net.minecraft.world.inventory.MenuType<AndroidInterfaceMenu> createType() {
        var type = net.minecraftforge.common.extensions.IForgeMenuType.create((int id, Inventory inventory, net.minecraft.network.FriendlyByteBuf data) -> {
            var locator = appeng.menu.locator.MenuLocators.readFromPacket(data);
            var located = locator.locate(inventory.player, AndroidInterfaceHost.class);
            if (located == null) throw new IllegalStateException("Android interface menu host unavailable");
            var menu = new AndroidInterfaceMenu(id, inventory, located);
            menu.setReturnedFromSubScreen(data.readBoolean());
            return menu;
        });
        MenuOpener.addOpener(type, (player, locator, returned) -> {
            if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return false;
            var located = locator.locate(player, AndroidInterfaceHost.class);
            if (located == null || !located.logic().mayUse(player)) return false;
            net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer, new net.minecraft.world.SimpleMenuProvider((id, inventory, p) -> {
                var menu = new AndroidInterfaceMenu(id, inventory, located);
                menu.setLocator(locator);
                return menu;
            }, located.hostIcon().getHoverName()), data -> {
                appeng.menu.locator.MenuLocators.writeToPacket(data, locator);
                data.writeBoolean(returned);
            });
            return true;
        });
        return type;
    }
    public static final int DEPOSIT_SLOTS = 9;

    /** Insert-only view of one output-buffer slot. */
    private static final class DepositSlot extends net.minecraftforge.items.SlotItemHandler {
        DepositSlot(net.minecraftforge.items.IItemHandler handler, int index) { super(handler, index, 0, 0); }
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
        registerClientAction(ACTION_SET_STOCK, StockAction.class, this::setStock);
    }

    public static void open(Player player, MenuLocator locator) {
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
        var id = net.minecraft.resources.ResourceLocation.tryParse(action.item);
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
