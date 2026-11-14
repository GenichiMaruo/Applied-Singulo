package io.github.genichimaruo.appliedsingulo.me;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import io.github.genichimaruo.appliedsingulo.order.SinguloLinks;
import io.github.genichimaruo.appliedsingulo.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * A virtual AE2 pattern generated from a Singulo Recipe Rack entry. No encoded pattern item exists in the
 * world; the definition key is a hidden item carrying the recipe description, so equal recipes from several
 * interfaces compare equal and AE2 treats them as the same pattern offered by several providers.
 */
public final class AndroidRecipePattern implements IPatternDetails {
    private final AEItemKey definition;
    private final ItemStack output;
    private final int outputCount;
    private final IInput[] inputs;
    private final List<GenericStack> outputs;
    private final boolean chained;

    private AndroidRecipePattern(AEItemKey definition, ItemStack output, int outputCount, IInput[] inputs, boolean chained) {
        this.chained = chained;
        this.definition = definition;
        this.output = output.copyWithCount(1);
        this.outputCount = outputCount;
        this.inputs = inputs;
        this.outputs = List.of(new GenericStack(AEItemKey.of(output), outputCount));
    }

    /** Null when the recipe cannot be represented exactly (output with data components, zero amounts). */
    @Nullable
    public static AndroidRecipePattern of(SinguloLinks.PatternRecipe recipe) {
        var outputKey = AEItemKey.of(recipe.output());
        if (outputKey == null || outputKey.hasComponents() || recipe.outputCount() <= 0) return null;
        var description = new CompoundTag();
        description.putString("output", outputKey.getId().toString());
        description.putInt("outputCount", recipe.outputCount());
        if (recipe.chained()) description.putBoolean("chain", true);
        var inputList = new ListTag();
        List<IInput> inputs = new ArrayList<>();
        for (var in : recipe.inputs()) {
            List<AEKey> keys = new ArrayList<>();
            var options = new ListTag();
            for (var option : in.options()) {
                var fluid = io.github.genichimaruo.singulo.api.android.AndroidEscrows.fluidOf(option);
                AEKey key = fluid.isEmpty() ? AEItemKey.of(option) : appeng.api.stacks.AEFluidKey.of(fluid);
                if (key == null || keys.contains(key)) continue;
                keys.add(key);
                options.add(StringTag.valueOf(key.getId() + (key.hasComponents() ? "#" + key.hashCode() : "")));
            }
            if (keys.isEmpty() || in.count() <= 0) return null;
            var entry = new CompoundTag();
            entry.put("options", options);
            entry.putLong("count", in.count());
            inputList.add(entry);
            inputs.add(new Input(keys, in.count()));
        }
        if (inputs.isEmpty()) return null;
        description.put("inputs", inputList);
        var stack = new ItemStack(ModContent.ANDROID_RECIPE_PATTERN.get());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(description));
        stack.set(DataComponents.CUSTOM_NAME, recipe.output().getHoverName().copy());
        return new AndroidRecipePattern(AEItemKey.of(stack), recipe.output(), recipe.outputCount(), inputs.toArray(IInput[]::new), recipe.chained());
    }

    public ItemStack output() { return output.copy(); }
    public int outputCount() { return outputCount; }
    /** Whether this is a raw-materials-to-product chain (one push = the whole chain). */
    public boolean chained() { return chained; }

    @Override public AEItemKey getDefinition() { return definition; }
    @Override public IInput[] getInputs() { return inputs; }
    @Override public List<GenericStack> getOutputs() { return outputs; }

    @Override public boolean equals(Object o) { return o instanceof AndroidRecipePattern p && p.definition.equals(definition); }
    @Override public int hashCode() { return definition.hashCode(); }
    @Override public String toString() { return "AndroidRecipePattern[" + BuiltInRegistries.ITEM.getKey(output.getItem()) + " x" + outputCount + "]"; }

    private record Input(List<AEKey> keys, long count) implements IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return keys.stream().map(k -> new GenericStack(k, 1)).toArray(GenericStack[]::new);
        }
        @Override public long getMultiplier() { return count; }
        @Override public boolean isValid(AEKey input, Level level) { return keys.contains(input); }
        @Nullable @Override public AEKey getRemainingKey(AEKey template) { return null; }
    }

    /** Distinct output items, for status display. */
    public static Set<String> outputs(List<AndroidRecipePattern> patterns) {
        return patterns.stream().map(p -> p.output.getHoverName().getString()).collect(Collectors.toCollection(java.util.TreeSet::new));
    }
}
