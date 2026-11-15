package io.github.genichimaruo.appliedsingulo.me;

import appeng.api.networking.security.IActionHost;
import appeng.helpers.IPriorityHost;
import appeng.menu.ISubMenu;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Something that hosts an {@link AndroidInterfaceLogic}: the Android ME Interface block or its cable panel (part).
 * It is also the AE2 menu host (status screen and AE2's standard priority screen).
 */
public interface AndroidInterfaceHost extends IPriorityHost, IActionHost {
    AndroidInterfaceLogic logic();

    @Nullable Level hostLevel();

    BlockPos hostPos();

    /** Whether the neighbour on {@code side} may see the ME network as Singulo storage (all sides for the block, the panel's own side). */
    boolean exposesStorageTowards(Direction side);

    /** The block or panel item, for menus and AE2's priority screen. */
    ItemStack hostIcon();

    void saveChanges();

    @Override default int getPriority() { return logic().priority(); }

    @Override default void setPriority(int priority) { logic().setPriority(priority); }

    @Override default ItemStack getMainMenuIcon() { return hostIcon(); }

    @Override
    default void returnToMainMenu(Player player, ISubMenu subMenu) {
        AndroidInterfaceMenu.open(player, subMenu.getLocator());
    }
}
