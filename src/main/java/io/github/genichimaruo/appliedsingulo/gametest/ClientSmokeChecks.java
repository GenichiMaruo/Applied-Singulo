package io.github.genichimaruo.appliedsingulo.gametest;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;

/** Optional development-only client smoke; excluded from distribution JARs. */
@Mod.EventBusSubscriber(modid = "applied_singulo", value = Dist.CLIENT)
public final class ClientSmokeChecks {
    private static boolean done;
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (done || !Boolean.getBoolean("applied_singulo.clientSmoke") || event.phase != TickEvent.Phase.END) return;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen) || mc.getOverlay() != null) return;
        done = true;
        try {
            Class.forName("io.github.genichimaruo.appliedsingulo.client.AndroidInterfaceScreen");
            appeng.client.gui.style.StyleManager.loadStyleDoc("/screens/applied_singulo/android_interface.json");
            int count = 0;
            for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
                if (!key.getNamespace().equals("applied_singulo")) continue;
                var model = mc.getItemRenderer().getModel(new net.minecraft.world.item.ItemStack(item), null, null, 0);
                if (model == mc.getModelManager().getMissingModel()) throw new IllegalStateException("Missing item model " + key);
                count++;
            }
            if (count < 4) throw new IllegalStateException("Expected addon item models");
            org.slf4j.LoggerFactory.getLogger("AppliedSinguloSmoke").info("APPLIED_CLIENT_SMOKE_PASS models={} style=loaded guiClass=loaded title=ready", count);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("AppliedSinguloSmoke").error("APPLIED_CLIENT_SMOKE_FAILED", e);
        } finally { mc.stop(); }
    }
}
