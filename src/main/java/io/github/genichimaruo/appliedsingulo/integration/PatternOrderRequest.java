package io.github.genichimaruo.appliedsingulo.integration;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Reads the requested output only; never removes AE2 materials or the pattern. */
public record PatternOrderRequest(ItemStack target, long count) {
    public static final long MAX_COUNT = 4096;

    public PatternOrderRequest {
        target = target.copyWithCount(1);
        if (target.isEmpty() || count <= 0 || count > MAX_COUNT)
            throw new IllegalArgumentException("Output count must be between 1 and 4096");
    }

    @Override public ItemStack target() { return target.copy(); }

    public static PatternOrderRequest decode(ItemStack pattern, Level level, int batches) {
        if (batches <= 0 || batches > MAX_COUNT)
            throw new IllegalArgumentException("Batch count must be between 1 and 4096");
        if (pattern.isEmpty() || !PatternDetailsHelper.isEncodedPattern(pattern))
            throw new IllegalArgumentException("Hold an AE2 encoded pattern in your main hand");
        var details = PatternDetailsHelper.decodePattern(pattern, level);
        if (details == null)
            throw new IllegalArgumentException("Only patterns with one output are supported");
        return fromDetails(details, batches);
    }

    /** Shared validation for held patterns and Pattern Provider pushes. */
    public static PatternOrderRequest fromDetails(IPatternDetails details, int batches) {
        if (batches <= 0 || batches > MAX_COUNT)
            throw new IllegalArgumentException("Batch count must be between 1 and 4096");
        if (details.getOutputs().length != 1)
            throw new IllegalArgumentException("Only patterns with one output are supported");
        for (var input : details.getInputs()) {
            for (var candidate : input.getPossibleInputs()) {
                if (!(candidate.what() instanceof AEItemKey))
                    throw new IllegalArgumentException("Only item inputs are supported");
            }
        }
        var output = details.getOutputs()[0];
        if (!(output.what() instanceof AEItemKey item) || output.amount() <= 0)
            throw new IllegalArgumentException("Only positive item outputs are supported");
        if (output.amount() > MAX_COUNT / batches)
            throw new IllegalArgumentException("Requested output exceeds 4096 items");
        return new PatternOrderRequest(item.toStack(), output.amount() * batches);
    }
}
