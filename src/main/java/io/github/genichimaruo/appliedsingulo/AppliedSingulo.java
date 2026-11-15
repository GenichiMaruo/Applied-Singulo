package io.github.genichimaruo.appliedsingulo;

import io.github.genichimaruo.appliedsingulo.integration.AndroidOrderCommands;
import io.github.genichimaruo.appliedsingulo.order.OrphanedEscrows;
import io.github.genichimaruo.appliedsingulo.registry.ModContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod(AppliedSingulo.MOD_ID)
public final class AppliedSingulo {
    public static final String MOD_ID = "applied_singulo";
    public AppliedSingulo(IEventBus modBus) {
        ModContent.register(modBus);
        if (Boolean.getBoolean("applied_singulo.gametest")) io.github.genichimaruo.appliedsingulo.gametest.PortGameTests.init(modBus);
        NeoForge.EVENT_BUS.addListener(AndroidOrderCommands::register);
        NeoForge.EVENT_BUS.addListener(OrphanedEscrows::onLevelTick);
        if (FMLEnvironment.getDist().isClient()) io.github.genichimaruo.appliedsingulo.client.ClientSetup.init(modBus);
    }
}
