package io.github.genichimaruo.appliedsingulo.me;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.util.AECableType;
import appeng.parts.AEBasePart;
import io.github.genichimaruo.appliedsingulo.AppliedSingulo;
import java.util.List;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Android Status Emitter: a cable part that emits a redstone signal from the order state of every Android ME
 * Interface on its ME network. Right-click cycles the condition (stuck / crafting / idle).
 */
public final class AndroidStatusEmitterPart extends AEBasePart implements IGridTickable {
    public enum Mode {
        /** An order is refused or waiting, ME is full, or an interface has no console. */
        STUCK,
        /** Any order is batching or crafting. */
        BUSY,
        /** No order at all. */
        IDLE;

        boolean test(NetworkOrders o) {
            return switch (this) {
                case STUCK -> o.stuck();
                case BUSY -> o.busy();
                case IDLE -> o.interfaces() > 0 && !o.busy();
            };
        }
    }

    private static final ResourceLocation MODEL_OFF = id("part/android_status_emitter_off");
    private static final ResourceLocation MODEL_ON = id("part/android_status_emitter_on");
    public static final List<ResourceLocation> MODELS = List.of(MODEL_OFF, MODEL_ON);
    private record Models(List<ResourceLocation> getModels) implements IPartModel { }
    private static final IPartModel OFF = new Models(List.of(MODEL_OFF));
    private static final IPartModel ON = new Models(List.of(MODEL_ON));

    private Mode mode = Mode.STUCK;
    private boolean emitting;

    private static ResourceLocation id(String path) { return new ResourceLocation(AppliedSingulo.MOD_ID, path); }

    public AndroidStatusEmitterPart(IPartItem<?> item) {
        super(item);
        getMainNode().setIdlePowerUsage(0.5).addService(IGridTickable.class, this);
    }

    public Mode mode() { return mode; }
    public boolean emitting() { return emitting; }

    /** Recompute the signal now (also called every 20 ticks). */
    public void update() {
        boolean next = getMainNode().isActive() && mode.test(NetworkOrders.of(getMainNode().getGrid()));
        if (next != emitting) {
            emitting = next;
            if (getHost() != null) {
                getHost().markForUpdate();
                getHost().notifyNeighbors();
            }
        }
    }

    @Override public TickingRequest getTickingRequest(IGridNode node) { return new TickingRequest(20, 20, false, false); }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        update();
        return TickRateModulation.SAME;
    }

    @Override
    public boolean onPartActivate(Player player, net.minecraft.world.InteractionHand hand, Vec3 pos) {
        if (!isClientSide()) {
            mode = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
            if (getHost() != null) getHost().markForSave();
            update();
            player.displayClientMessage(Component.translatable("message.applied_singulo.emitter.mode",
                Component.translatable("gui.applied_singulo.emitter." + mode.name().toLowerCase(Locale.ROOT))), true);
        }
        return true;
    }

    @Override public boolean canConnectRedstone() { return true; }
    @Override public int isProvidingStrongPower() { return emitting ? 15 : 0; }
    @Override public int isProvidingWeakPower() { return emitting ? 15 : 0; }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(5, 5, 13, 11, 11, 16);
        bch.addBox(7, 7, 10, 9, 9, 13);
    }

    @Override public float getCableConnectionLength(AECableType cable) { return 4; }
    @Override public IPartModel getStaticModels() { return emitting ? ON : OFF; }

    @Override
    public void writeToStream(FriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(emitting);
    }

    @Override
    public boolean readFromStream(FriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean was = emitting;
        emitting = data.readBoolean();
        return changed || was != emitting;
    }

    @Override
    public void readFromNBT(CompoundTag tag) {
        super.readFromNBT(tag);
        try { mode = Mode.valueOf(tag.getString("mode")); } catch (IllegalArgumentException ignored) { mode = Mode.STUCK; }
        emitting = tag.getBoolean("emitting");
    }

    @Override
    public void writeToNBT(CompoundTag tag) {
        super.writeToNBT(tag);
        tag.putString("mode", mode.name());
        tag.putBoolean("emitting", emitting);
    }
}
