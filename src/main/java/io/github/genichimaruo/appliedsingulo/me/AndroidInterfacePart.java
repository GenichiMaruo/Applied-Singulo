package io.github.genichimaruo.appliedsingulo.me;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.util.AECableType;
import appeng.menu.locator.MenuLocators;
import appeng.parts.AEBasePart;
import java.util.List;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Android ME Interface panel: the interface as a cable part (like AE2's flat interface). It behaves like the block,
 * except that only the block in front of the panel can see the ME network as Singulo storage.
 */
public final class AndroidInterfacePart extends AEBasePart implements AndroidInterfaceHost, IGridTickable {
    private final AndroidInterfaceLogic logic;

    public AndroidInterfacePart(IPartItem<?> item) {
        super(item);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(2.0).addService(IGridTickable.class, this);
        logic = new AndroidInterfaceLogic(this, getMainNode());
    }

    // ------------------------------------------------------------------ host

    @Override public AndroidInterfaceLogic logic() { return logic; }
    @Nullable @Override public Level hostLevel() { return getLevel(); }
    @Override public BlockPos hostPos() { return getBlockEntity().getBlockPos(); }
    @Override public boolean exposesStorageTowards(Direction side) { return side == getSide(); }
    @Override public ItemStack hostIcon() { return new ItemStack(getPartItem()); }
    @Override public void saveChanges() { if (getHost() != null) getHost().markForSave(); }

    @Nullable public IItemHandler storageView() { return logic.storageView(getSide()); }

    // ------------------------------------------------------------------ AE2 part lifecycle

    @Override
    public void addToWorld() {
        super.addToWorld();
        if (getLevel() instanceof ServerLevel level) logic.onMainNodeCreated(level, hostPos());
    }

    @Override
    public void removeFromWorld() {
        super.removeFromWorld();
        logic.destroy();
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        logic.onMainNodeStateChanged();
    }

    @Override
    public void onPlacement(Player player) {
        super.onPlacement(player);
        if (!isClientSide()) logic.onPlaced(player);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) { return new TickingRequest(1, 1, false); }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (getLevel() instanceof ServerLevel level) logic.tick(level);
        return TickRateModulation.SAME;
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!isClientSide()) {
            if (!logic.mayUse(player)) {
                player.sendOverlayMessage(Component.translatable("message.applied_singulo.device.not_owner"));
            } else {
                if (logic.queue().owner() == null) logic.setOwningPlayer(player);
                AndroidInterfaceMenu.open(player, MenuLocators.forPart(this));
            }
        }
        return true;
    }

    @Override
    public boolean onUseItemOn(ItemStack stack, Player player, InteractionHand hand, Vec3 pos) {
        return logic.useCard(stack, player) || super.onUseItemOn(stack, player, hand, pos);
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        drops.addAll(logic.breakDown());
    }

    // ------------------------------------------------------------------ shape and model (like AE2's interface panel)

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(2, 2, 14, 14, 14, 16);
        bch.addBox(5, 5, 12, 11, 11, 14);
    }

    @Override public float getCableConnectionLength(AECableType cable) { return 4; }


    // ------------------------------------------------------------------ persistence

    @Override
    public void readFromNBT(net.minecraft.world.level.storage.ValueInput input) {
        var registries = input.lookup();
        var tag = input.read(io.github.genichimaruo.appliedsingulo.compat.TagBridge.ROOT).orElseGet(CompoundTag::new);
        super.readFromNBT(input);
        logic.load(tag, registries);
    }

    @Override
    public void writeToNBT(net.minecraft.world.level.storage.ValueOutput output) {
        var registries = getLevel().registryAccess();
        var tag = new CompoundTag();
        super.writeToNBT(output);
        logic.save(tag, registries);
        output.store(io.github.genichimaruo.appliedsingulo.compat.TagBridge.ROOT, tag);
    }
}
