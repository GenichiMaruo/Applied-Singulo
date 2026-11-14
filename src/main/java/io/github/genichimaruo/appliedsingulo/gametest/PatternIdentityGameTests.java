package io.github.genichimaruo.appliedsingulo.gametest;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import io.github.genichimaruo.appliedsingulo.me.AndroidRecipePattern;
import io.github.genichimaruo.appliedsingulo.order.SinguloLinks;
import io.github.genichimaruo.singulo.api.android.AndroidEscrows;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("applied_singulo")
@PrefixGameTestTemplate(false)
public final class PatternIdentityGameTests {
    private static CompoundTag data(String value) {
        var tag = new CompoundTag(); tag.putString("variant", value); return tag;
    }
    private static AndroidRecipePattern pattern(GameTestHelper h, ItemStack input) {
        return AndroidRecipePattern.of(new SinguloLinks.PatternRecipe(new ItemStack(Items.STONE), 1,
            List.of(new SinguloLinks.RecipeInput(List.of(input), 1))));
    }
    private static void verify(GameTestHelper h, ItemStack a, ItemStack b, AEKey ka, AEKey kb) {
        var pa = pattern(h, a); var pb = pattern(h, b);
        h.assertTrue(!(ka.equals(kb)), "Fixture variants must differ");
        h.assertTrue(!(pa.equals(pb)), "Different full input identities must produce different patterns");
        h.assertTrue(pa.equals(pattern(h, a.copy())), "Identical full key must deduplicate");
        h.assertTrue(pa.getInputs()[0].isValid(ka, h.getLevel()) && !pa.getInputs()[0].isValid(kb, h.getLevel()), "A accepts only A");
        h.assertTrue(pb.getInputs()[0].isValid(kb, h.getLevel()) && !pb.getInputs()[0].isValid(ka, h.getLevel()), "B accepts only B");
        var definition = pa.getDefinition().toStack().getOrCreateTag();
        var encoded = definition.getList("inputs", Tag.TAG_COMPOUND).getCompound(0)
            .getList("options", Tag.TAG_COMPOUND).getCompound(0);
        h.assertTrue(ka.equals(AEKey.fromTagGeneric(encoded)), "Definition must contain full reversible key, not hash or display text");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void itemComponentVariantsHaveDistinctFullDefinitions(GameTestHelper h) {
        var a = new ItemStack(Items.COBBLESTONE); var b = a.copy();
        // These strings have equal Java hash codes; full data must remain distinct.
        a.setTag(data("Aa")); b.setTag(data("BB"));
        verify(h, a, b, AEItemKey.of(a), AEItemKey.of(b));
    }
    @GameTest(template = "empty")
    public static void fluidComponentVariantsHaveDistinctFullDefinitions(GameTestHelper h) {
        var a = new FluidStack(Fluids.WATER, 1000); var b = a.copy();
        a.setTag(data("Aa")); b.setTag(data("BB"));
        verify(h, AndroidEscrows.fluidSample(a), AndroidEscrows.fluidSample(b), AEFluidKey.of(a), AEFluidKey.of(b));
    }

    private static io.github.genichimaruo.singulo.api.android.AndroidRecipe recipe(ItemStack output, ItemStack... inputs) {
        return new io.github.genichimaruo.singulo.api.android.AndroidRecipe("crafting", net.minecraft.core.BlockPos.ZERO,
            List.of(new io.github.genichimaruo.singulo.api.android.AndroidRecipe.Product(output, 1, false)),
            java.util.Arrays.stream(inputs).map(i -> new io.github.genichimaruo.singulo.api.android.AndroidRecipe.Material(
                i, 1, List.of(i), "", false, false, !AndroidEscrows.fluidOf(i).isEmpty())).toList(), false);
    }
    private static void verifyChain(GameTestHelper h, ItemStack a, ItemStack b) {
        var intermediate = recipe(new ItemStack(Items.STICK), a);
        var product = recipe(new ItemStack(Items.STONE), new ItemStack(Items.STICK), b);
        var chain = SinguloLinks.chain(product, List.of(intermediate, product));
        h.assertTrue(chain != null && chain.inputs().size() == 2, "Distinct full-key leaves must not merge by hash");
        h.assertTrue(chain.inputs().stream().allMatch(i -> i.count() == 1), "Each distinct leaf keeps its own quantity");
        var pattern = AndroidRecipePattern.of(chain);
        h.assertTrue(pattern != null && pattern.getInputs().length == 2, "Both leaves survive pattern conversion");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void chainKeepsItemComponentsSeparate(GameTestHelper h) {
        var a = new ItemStack(Items.COBBLESTONE); var b = a.copy();
        a.setTag(data("Aa")); b.setTag(data("BB"));
        verifyChain(h, a, b);
    }
    @GameTest(template = "empty")
    public static void chainKeepsFluidComponentsSeparate(GameTestHelper h) {
        var a = new FluidStack(Fluids.WATER, 1); var b = a.copy();
        a.setTag(data("Aa")); b.setTag(data("BB"));
        verifyChain(h, AndroidEscrows.fluidSample(a), AndroidEscrows.fluidSample(b));
    }
    @GameTest(template = "empty")
    public static void recipeEnumerationDoesNotDeduplicateDisplayStrings(GameTestHelper h) {
        var a = new ItemStack(Items.COBBLESTONE); var b = a.copy();
        a.setTag(data("Aa")); b.setTag(data("BB"));
        var recipes = SinguloLinks.patternRecipes(List.of(recipe(new ItemStack(Items.STONE), a), recipe(new ItemStack(Items.STONE), b)));
        h.assertTrue(recipes.size() == 2, "Recipes with different inputs survive enumeration");
        h.assertTrue(!AndroidRecipePattern.of(recipes.get(0)).equals(
            AndroidRecipePattern.of(recipes.get(1))), "Final full-key definitions remain distinct");
        h.succeed();
    }
}
