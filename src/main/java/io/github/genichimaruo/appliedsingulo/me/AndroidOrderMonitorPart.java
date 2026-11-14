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
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.genichimaruo.appliedsingulo.AppliedSingulo;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Android Order Monitor: a cable panel that shows, like AE2's storage monitor, the current order of the Android ME
 * Interfaces on its ME network: product icon, progress and the number of orders. Red text when something is stuck.
 */
public final class AndroidOrderMonitorPart extends AEBasePart implements IGridTickable {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(AppliedSingulo.MOD_ID,
        "part/android_order_monitor");
    public static final List<ResourceLocation> MODELS = List.of(MODEL);
    private record Models(List<ResourceLocation> getModels) implements IPartModel { }
    private static final IPartModel STATIC = new Models(List.of(MODEL));

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
    @Override public IPartModel getStaticModels() { return STATIC; }
    @Override public boolean requireDynamicRender() { return true; }

    @Override
    public void renderDynamic(float partialTicks, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!isPowered()) return;
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        rotateToFace(pose, getSide());
        pose.translate(0, 0, 0.5 + 0.002);   // just in front of the screen face

        var s = shown;
        var font = Minecraft.getInstance().font;
        if (!s.topItem().isEmpty()) {
            var rl = ResourceLocation.tryParse(s.topItem());
            var stack = rl == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.get(rl).getDefaultInstance();
            pose.pushPose();
            pose.translate(0, 0.08, 0);
            pose.scale(0.3f, 0.3f, 0.001f);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, pose, buffers, getLevel(), 0);
            pose.popPose();
            text(pose, buffers, font, s.topClaimed() + " / " + s.topCount(), -0.17f, s.stuck() ? 0xFF6060 : 0xFFFFFF);
        } else {
            text(pose, buffers, font, Component.translatable("gui.applied_singulo.monitor.idle").getString(), 0.03f, 0xA0A8C0);
        }
        String orders = Component.translatable("gui.applied_singulo.monitor.orders", s.running(), s.batching()).getString();
        text(pose, buffers, font, orders, -0.29f, s.stuck() ? 0xFF6060 : 0x9FE8FF);
        pose.popPose();
    }

    private static void text(PoseStack pose, MultiBufferSource buffers, Font font, String s, float y, int color) {
        pose.pushPose();
        pose.translate(0, y, 0);
        float scale = 1 / 110f;
        int width = font.width(s);
        if (width * scale > 0.7f) scale = 0.7f / width;
        pose.scale(scale, -scale, scale);
        font.drawInBatch(s, -width / 2f, -font.lineHeight / 2f, color, false, pose.last().pose(), buffers,
            Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /** Rotate so that +Z points out of {@code side}, +Y is up on the screen and +X is to the viewer's right. */
    private static void rotateToFace(PoseStack pose, Direction side) {
        switch (side) {
            case NORTH -> pose.mulPose(Axis.YP.rotationDegrees(180));
            case EAST -> pose.mulPose(Axis.YP.rotationDegrees(90));
            case WEST -> pose.mulPose(Axis.YP.rotationDegrees(-90));
            case UP -> pose.mulPose(Axis.XP.rotationDegrees(-90));
            case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(90));
            default -> { }
        }
    }
}
