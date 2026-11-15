package io.github.genichimaruo.appliedsingulo.client;

import appeng.api.stacks.AEItemKey;
import appeng.client.api.AEKeyRenderState;
import appeng.client.api.renderer.parts.PartDynamicRenderState;
import appeng.client.api.renderer.parts.PartRenderer;
import appeng.client.render.BlockEntityRenderHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.genichimaruo.appliedsingulo.me.AndroidOrderMonitorPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;

/** Client-only render extraction: never retains a live part or world in submitted state. */
public final class AndroidOrderMonitorRenderer implements PartRenderer<AndroidOrderMonitorPart, AndroidOrderMonitorRenderer.State> {
    public static final class State extends PartDynamicRenderState {
        final AEKeyRenderState item = new AEKeyRenderState();
        boolean powered;
        FormattedCharSequence progress;
        FormattedCharSequence orders;
        int progressWidth;
        int ordersWidth;
        int progressColor;
        int ordersColor;
    }
    @Override public State createState() { return new State(); }
    @Override public Class<State> stateClass() { return State.class; }
    @Override public void extract(AndroidOrderMonitorPart part, State state, float partialTicks) {
        state.side = part.getSide();
        state.powered = part.isPowered();
        state.item.clear();
        if (!state.powered) return;
        var shown = part.shown();
        var id = Identifier.tryParse(shown.topItem());
        var item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item != null) state.item.extract(AEItemKey.of(item), part.getLevel(), (int)part.getHost().getBlockEntity().getBlockPos().asLong());
        state.progress = (state.item.isEmpty() ? Component.translatable("gui.applied_singulo.monitor.idle")
            : Component.literal(shown.topClaimed() + " / " + shown.topCount())).getVisualOrderText();
        state.orders = Component.translatable("gui.applied_singulo.monitor.orders", shown.running(), shown.batching()).getVisualOrderText();
        state.progressWidth = Minecraft.getInstance().font.width(state.progress);
        state.ordersWidth = Minecraft.getInstance().font.width(state.orders);
        state.progressColor = state.item.isEmpty() ? 0xFFA0A8C0 : shown.stuck() ? 0xFFFF6060 : 0xFFFFFFFF;
        state.ordersColor = shown.stuck() ? 0xFFFF6060 : 0xFF9FE8FF;
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector nodes, CameraRenderState camera) {
        if (!state.powered) return;
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        switch (state.side) {
            case NORTH -> pose.mulPose(Axis.YP.rotationDegrees(180));
            case EAST -> pose.mulPose(Axis.YP.rotationDegrees(90));
            case WEST -> pose.mulPose(Axis.YP.rotationDegrees(-90));
            case UP -> pose.mulPose(Axis.XP.rotationDegrees(-90));
            case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(90));
            default -> { }
        }
        pose.translate(0, 0, 0.502);
        if (!state.item.isEmpty()) {
            pose.pushPose();
            pose.translate(0, 0.08, 0);
            BlockEntityRenderHelper.submitRenderItem2d(pose, state.item, 0.3f, nodes);
            pose.popPose();
        }
        text(pose, nodes, state.progress, state.progressWidth, state.item.isEmpty() ? 0.03f : -0.17f, state.progressColor);
        text(pose, nodes, state.orders, state.ordersWidth, -0.29f, state.ordersColor);
        pose.popPose();
    }
    private static void text(PoseStack pose, SubmitNodeCollector nodes, FormattedCharSequence text, int width, float y, int color) {
        pose.pushPose();
        pose.translate(0, y, 0.001);
        float scale = Math.min(1 / 110f, width == 0 ? 1 / 110f : 0.7f / width);
        pose.scale(scale, -scale, scale);
        nodes.submitText(pose, -width / 2f, -Minecraft.getInstance().font.lineHeight / 2f, text, false,
            Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT, color, 0, 0);
        pose.popPose();
    }
}
