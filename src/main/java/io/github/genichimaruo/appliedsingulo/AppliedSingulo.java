package io.github.genichimaruo.appliedsingulo;

import io.github.genichimaruo.appliedsingulo.integration.AndroidOrderCommands;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(AppliedSingulo.MOD_ID)
public final class AppliedSingulo {
    public static final String MOD_ID = "applied_singulo";
    public AppliedSingulo() {
        NeoForge.EVENT_BUS.addListener(AndroidOrderCommands::register);
    }
}
