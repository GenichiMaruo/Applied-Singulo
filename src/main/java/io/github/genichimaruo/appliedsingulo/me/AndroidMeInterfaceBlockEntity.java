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
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

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
    @Override public ItemStack hostIcon() { return ModContent.ANDROID_ME_INTERFACE_ITEM.toStack(); }
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

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        logic.save(tag, registries);
        mainNode.saveToNBT(tag);
        tag.putInt("schema", 3);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        logic.load(tag, registries);
        mainNode.loadFromNBT(tag);
    }
}
