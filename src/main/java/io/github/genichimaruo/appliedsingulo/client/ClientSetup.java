package io.github.genichimaruo.appliedsingulo.client;

import appeng.client.InitScreens;
import io.github.genichimaruo.appliedsingulo.registry.ModContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only registrations (loaded only on the physical client). */
public final class ClientSetup {
    private ClientSetup() { }

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::registerScreens);
        modBus.addListener(ClientSetup::registerPartRenderers);
    }

    private static void registerPartRenderers(appeng.client.api.renderer.parts.RegisterPartRendererEvent event) {
        event.register(io.github.genichimaruo.appliedsingulo.me.AndroidOrderMonitorPart.class, new AndroidOrderMonitorRenderer());
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        // AE2 screen styles are resolved in AE2's namespace: assets/ae2/screens/applied_singulo/android_interface.json
        InitScreens.register(event, ModContent.ANDROID_INTERFACE_MENU.get(), AndroidInterfaceScreen::new,
            "/screens/applied_singulo/android_interface.json");
    }
}
