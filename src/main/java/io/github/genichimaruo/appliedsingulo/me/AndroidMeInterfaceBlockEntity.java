package io.github.genichimaruo.appliedsingulo.me;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import io.github.genichimaruo.appliedsingulo.order.AndroidOrderQueue;
import io.github.genichimaruo.appliedsingulo.registry.ModContent;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;

/** Android ME Interface block: an in-world ME node hosting {@link AndroidInterfaceLogic} on all six sides. */
public final class AndroidMeInterfaceBlockEntity extends BlockEntity implements AndroidInterfaceHost, IInWorldGridNodeHost {
    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, Listener.INSTANCE)
        .setFlags(GridFlags.REQUIRE_CHANNEL)
        .setIdlePowerUsage(2.0)
        .setInWorldNode(true)
        .setExposedOnSides(EnumSet.allOf(Direction.class))
        .setTagName("android_me_interface");
    private final AndroidInterfaceLogic logic = new AndroidInterfaceLogic(this, mainNode);

    public AndroidMeInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.ANDROID_ME_INTERFACE_ENTITY.get(), pos, state);
        mainNode.setVisualRepresentation(ModContent.ANDROID_ME_INTERFACE_ITEM.get());
    }

    // ------------------------------------------------------------------ host

    @Override public AndroidInterfaceLogic logic() { return logic; }
    @Nullable @Override public Level hostLevel() { return level; }
    @Override public BlockPos hostPos() { return worldPosition; }
    @Override public boolean exposesStorageTowards(Direction side) { return true; }
    @Override public ItemStack hostIcon() { return ModContent.ANDROID_ME_INTERFACE_ITEM.get().getDefaultInstance(); }
    @Override public void saveChanges() { setChanged(); }

    // convenience accessors (tests, commands)
    public AndroidOrderQueue queue() { return logic.queue(); }
    public List<AndroidRecipePattern> patterns() { return logic.patterns(); }
    @Nullable public BlockPos console() { return logic.queue().console(); }
    public IManagedGridNode mainNode() { return mainNode; }
    public void refreshPatterns() { logic.refreshPatterns(); }
    @Nullable public IItemHandler storageView(@Nullable Direction side) { return logic.storageView(side); }

    public void onPlaced(Player player) {
        mainNode.setOwningPlayer(player);
        logic.onPlaced(player);
    }

    // ------------------------------------------------------------------ AE2 node lifecycle

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide)
            GridHelper.onFirstTick(this, be -> {
                be.mainNode.create(be.level, be.worldPosition);
                be.logic.onMainNodeCreated(be.level, be.worldPosition);
            });
    }

    private void destroyNodes() {
        logic.destroy();
        mainNode.destroy();
    }

    @Override public void setRemoved() { super.setRemoved(); destroyNodes(); }
    @Override public void onChunkUnloaded() { super.onChunkUnloaded(); destroyNodes(); }

    @Nullable @Override public IGridNode getGridNode(Direction dir) { return mainNode.getNode(); }
    @Nullable @Override public IGridNode getActionableNode() { return mainNode.getNode(); }

    private enum Listener implements IGridNodeListener<AndroidMeInterfaceBlockEntity> {
        INSTANCE;
        @Override public void onSaveChanges(AndroidMeInterfaceBlockEntity be, IGridNode node) { be.setChanged(); }
        @Override public void onStateChanged(AndroidMeInterfaceBlockEntity be, IGridNode node, State state) {
            be.logic.onMainNodeStateChanged();
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AndroidMeInterfaceBlockEntity be) {
        be.logic.tick((ServerLevel) level);
    }

    private net.minecraftforge.common.util.LazyOptional<IInWorldGridNodeHost> gridCapability = net.minecraftforge.common.util.LazyOptional.of(() -> this);
    private final java.util.Map<Direction, net.minecraftforge.common.util.LazyOptional<IItemHandler>> itemCapabilities = new java.util.EnumMap<>(Direction.class);
    @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> cap, @Nullable Direction side) {
        if (isRemoved()) return net.minecraftforge.common.util.LazyOptional.empty();
        if (cap == appeng.capabilities.Capabilities.IN_WORLD_GRID_NODE_HOST) return gridCapability.cast();
        if (cap == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER && side != null) {
            var view = storageView(side);
            if (view != null) return itemCapabilities.computeIfAbsent(side, d -> net.minecraftforge.common.util.LazyOptional.of(() -> view)).cast();
        }
        return super.getCapability(cap, side);
    }
    @Override public void reviveCaps() { super.reviveCaps(); gridCapability = net.minecraftforge.common.util.LazyOptional.of(() -> this); }
    @Override public void invalidateCaps() { super.invalidateCaps(); gridCapability.invalidate(); itemCapabilities.values().forEach(net.minecraftforge.common.util.LazyOptional::invalidate); itemCapabilities.clear(); }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        logic.save(tag);
        mainNode.saveToNBT(tag);
        tag.putInt("schema", 3);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        logic.load(tag);
        mainNode.loadFromNBT(tag);
    }
}
