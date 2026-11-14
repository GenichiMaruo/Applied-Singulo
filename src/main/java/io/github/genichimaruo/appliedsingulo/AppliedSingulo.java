package io.github.genichimaruo.appliedsingulo;

import io.github.genichimaruo.appliedsingulo.integration.AndroidOrderCommands;
import io.github.genichimaruo.appliedsingulo.order.OrphanedEscrows;
import io.github.genichimaruo.appliedsingulo.registry.ModContent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.common.MinecraftForge;

@Mod(AppliedSingulo.MOD_ID)
public final class AppliedSingulo {
    public static final String MOD_ID = "applied_singulo";
    public AppliedSingulo() {
        IEventBus modBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        ModContent.register(modBus);
        MinecraftForge.EVENT_BUS.addListener(AndroidOrderCommands::register);
        MinecraftForge.EVENT_BUS.addListener(OrphanedEscrows::onLevelTick);
        if (FMLEnvironment.dist.isClient()) io.github.genichimaruo.appliedsingulo.client.ClientSetup.init(modBus);
    }
}
