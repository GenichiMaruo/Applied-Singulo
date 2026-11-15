package io.github.genichimaruo.appliedsingulo.gametest;

import java.util.Arrays;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;

/** Actual datapack recipes must survive schema changes, not merely compile as Java. */
public final class RecipeGameTests {
    @GameTest(template = "empty")
    public static void allCraftingRecipesLoadAndMatch(GameTestHelper h) {
        verify(h, "android_me_interface", "android_me_interface", 3, 3,
            "minecraft:iron_ingot", "ae2:engineering_processor", "minecraft:iron_ingot",
            "ae2:logic_processor", "ae2:interface", "ae2:logic_processor",
            "minecraft:iron_ingot", "singulo:android_supply_port", "minecraft:iron_ingot");
        verify(h, "android_me_interface_panel", "android_me_interface_panel", 1, 1, "applied_singulo:android_me_interface");
        verify(h, "android_me_interface_from_panel", "android_me_interface", 1, 1, "applied_singulo:android_me_interface_panel");
        verify(h, "android_order_monitor", "android_order_monitor", 3, 1,
            "ae2:storage_monitor", "ae2:logic_processor", "singulo:desk_monitor");
        verify(h, "android_status_emitter", "android_status_emitter", 3, 1,
            "ae2:level_emitter", "ae2:logic_processor", "singulo:ruin_lamp");
        h.succeed();
    }
    private static void verify(GameTestHelper h, String id, String output, int width, int height, String... items) {
        var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("applied_singulo", id));
        var holder = h.getLevel().recipeAccess().recipeMap().byKey(key);
        h.assertTrue(holder != null, "Recipe failed to load: " + id);
        h.assertTrue(holder.value() instanceof CraftingRecipe, "Not a crafting recipe: " + id);
        var recipe = (CraftingRecipe) holder.value();
        var input = CraftingInput.of(width, height, Arrays.stream(items)
            .map(s -> new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(s)))).toList());
        h.assertTrue(recipe.matches(input, h.getLevel()), "Recipe no longer matches: " + id);
        var result = recipe.assemble(input);
        h.assertTrue(result.is(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("applied_singulo", output)))
            && result.getCount() == 1, "Wrong output: " + id);
    }
}
