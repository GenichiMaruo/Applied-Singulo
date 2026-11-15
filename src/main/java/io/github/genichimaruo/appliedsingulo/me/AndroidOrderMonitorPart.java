package io.github.genichimaruo.appliedsingulo.me;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.util.AECableType;
import appeng.parts.AEBasePart;
import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * Android Order Monitor: a cable panel that shows, like AE2's storage monitor, the current order of the Android ME
 * Interfaces on its ME network: product icon, progress and the number of orders. Red text when something is stuck.
 */
public final class AndroidOrderMonitorPart extends AEBasePart implements IGridTickable {
    private NetworkOrders shown = NetworkOrders.NONE;

    public AndroidOrderMonitorPart(IPartItem<?> item) {
        super(item);
        getMainNode().setIdlePowerUsage(0.5).addService(IGridTickable.class, this);
    }

    public NetworkOrders shown() { return shown; }

    public void update() {
        var next = getMainNode().isActive() ? NetworkOrders.of(getMainNode().getGrid()) : NetworkOrders.NONE;
        if (!next.equals(shown)) {
            shown = next;
            if (getHost() != null) getHost().markForUpdate();
        }
    }

    @Override public TickingRequest getTickingRequest(IGridNode node) { return new TickingRequest(10, 10, false); }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        update();
        return TickRateModulation.SAME;
    }

    // ------------------------------------------------------------------ sync

    @Override
    public void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeVarInt(shown.interfaces());
        data.writeVarInt(shown.running());
        data.writeVarInt(shown.batching());
        data.writeBoolean(shown.stuck());
        data.writeUtf(shown.topItem());
        data.writeVarLong(shown.topClaimed());
        data.writeVarLong(shown.topCount());
    }

    @Override
    public boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        var next = new NetworkOrders(data.readVarInt(), data.readVarInt(), data.readVarInt(), data.readBoolean(),
            data.readUtf(), data.readVarLong(), data.readVarLong());
        changed |= !next.equals(shown);
        shown = next;
        return changed;
    }

    // ------------------------------------------------------------------ shape and rendering

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(2, 2, 14, 14, 14, 16);
        bch.addBox(4, 4, 13, 12, 12, 14);
    }

    @Override public float getCableConnectionLength(AECableType cable) { return 3; }
}
