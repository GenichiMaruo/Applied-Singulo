package io.github.genichimaruo.appliedsingulo.gametest;

import io.github.genichimaruo.appliedsingulo.registry.ModContent;
import io.github.genichimaruo.appliedsingulo.me.AndroidMeInterfaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("applied_singulo")
@PrefixGameTestTemplate(false)
public final class LegacyCompatibilityGameTests {
    @GameTest(template = "empty")
    public static void legacyResourcesAndMenuUseOwnNamespace(GameTestHelper h) {
        h.assertTrue(new ResourceLocation("applied_singulo", "android_interface").equals(
            BuiltInRegistries.MENU.getKey(ModContent.ANDROID_INTERFACE_MENU.get())), "Menu registered in wrong namespace");
        for (var recipe : java.util.List.of("android_me_interface", "android_me_interface_from_panel",
                "android_me_interface_panel", "android_order_monitor", "android_status_emitter"))
            h.assertTrue(h.getLevel().getRecipeManager().byKey(new ResourceLocation("applied_singulo", recipe)).isPresent(),
                "Legacy recipe was not decoded: " + recipe);
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void legacyCapabilitiesInvalidateAndRevive(GameTestHelper h) {
        var pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, ModContent.ANDROID_ME_INTERFACE.get());
        var be = (AndroidMeInterfaceBlockEntity) h.getBlockEntity(pos);
        var grid = be.getCapability(appeng.capabilities.Capabilities.IN_WORLD_GRID_NODE_HOST);
        var storage = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH);
        h.assertTrue(grid.isPresent() && storage.isPresent(), "Capabilities not exposed");
        be.invalidateCaps();
        h.assertTrue(!grid.isPresent() && !storage.isPresent(), "Old capabilities remain valid");
        be.reviveCaps();
        h.assertTrue(be.getCapability(appeng.capabilities.Capabilities.IN_WORLD_GRID_NODE_HOST).isPresent(), "Grid capability not revived");
        var revived = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElseThrow(() -> new IllegalStateException("Storage capability not revived"));
        h.assertTrue(revived.getSlots() == 0, "Unowned offline device exposed storage");
        h.succeed();
    }
}
