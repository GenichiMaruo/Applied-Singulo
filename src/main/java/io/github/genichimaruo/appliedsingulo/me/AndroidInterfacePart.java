package io.github.genichimaruo.appliedsingulo.me;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.util.AECableType;
import appeng.menu.locator.MenuLocators;
import appeng.parts.AEBasePart;
import io.github.genichimaruo.appliedsingulo.AppliedSingulo;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;

/**
 * Android ME Interface panel: the interface as a cable part (like AE2's flat interface). It behaves like the block,
 * except that only the block in front of the panel can see the ME network as Singulo storage.
 */
public final class AndroidInterfacePart extends AEBasePart implements AndroidInterfaceHost, IGridTickable {
    public static final ResourceLocation MODEL_BASE = new ResourceLocation(AppliedSingulo.MOD_ID,
        "part/android_me_interface_panel_base");
    private static final ResourceLocation STATUS_OFF = new ResourceLocation("ae2", "part/interface_off");
    private static final ResourceLocation STATUS_ON = new ResourceLocation("ae2", "part/interface_on");
    private static final ResourceLocation STATUS_CHANNEL = new ResourceLocation("ae2", "part/interface_has_channel");
    private static final IPartModel MODELS_OFF = new Models(List.of(MODEL_BASE, STATUS_OFF));
    private static final IPartModel MODELS_ON = new Models(List.of(MODEL_BASE, STATUS_ON));
    private static final IPartModel MODELS_HAS_CHANNEL = new Models(List.of(MODEL_BASE, STATUS_CHANNEL));

    /** Models to register with {@code PartModels.registerModels} (the status ones are AE2's). */
    public static final List<ResourceLocation> MODELS = List.of(MODEL_BASE);

    private record Models(List<ResourceLocation> getModels) implements IPartModel { }

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
    public TickingRequest getTickingRequest(IGridNode node) { return new TickingRequest(1, 1, false, false); }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (getLevel() instanceof ServerLevel level) logic.tick(level);
        return TickRateModulation.SAME;
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public boolean onPartActivate(Player player, InteractionHand hand, Vec3 pos) {
        if (logic.useCard(player.getItemInHand(hand), player)) return true;
        if (!isClientSide()) {
            if (!logic.mayUse(player)) {
                player.displayClientMessage(Component.translatable("message.applied_singulo.device.not_owner"), true);
            } else {
                if (logic.queue().owner() == null) logic.setOwningPlayer(player);
                AndroidInterfaceMenu.open(player, MenuLocators.forPart(this));
            }
        }
        return true;
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

    @Override
    public IPartModel getStaticModels() {
        if (isActive() && isPowered()) return MODELS_HAS_CHANNEL;
        return isPowered() ? MODELS_ON : MODELS_OFF;
    }

    @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> cap) {
        if (cap == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER) {
            var view = storageView();
            if (view != null) return net.minecraftforge.common.util.LazyOptional.of(() -> view).cast();
        }
        return super.getCapability(cap);
    }
    // ------------------------------------------------------------------ persistence

    @Override
    public void readFromNBT(CompoundTag tag) {
        super.readFromNBT(tag);
        logic.load(tag);
    }

    @Override
    public void writeToNBT(CompoundTag tag) {
        super.writeToNBT(tag);
        logic.save(tag);
    }
}
