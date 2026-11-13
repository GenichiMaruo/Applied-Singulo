package io.github.genichimaruo.appliedsingulo.gametest;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import io.github.genichimaruo.appliedsingulo.integration.PatternOrderRequest;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("applied_singulo")
@PrefixGameTestTemplate(false)
public final class PatternRequestGameTests {
    private static ItemStack pattern(long output) {
        return PatternDetailsHelper.encodeProcessingPattern(
            List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1)),
            List.of(new GenericStack(AEItemKey.of(Items.STONE), output)));
    }
    private static void reject(GameTestHelper h, Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        h.assertTrue(rejected, "Unsupported request must be rejected");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void readsPatternWithoutConsumingIt(GameTestHelper h) {
        var pattern = pattern(2);
        var original = pattern.copy();
        var request = PatternOrderRequest.decode(pattern, h.getLevel(), 3);
        h.assertTrue(request.target().is(Items.STONE) && request.count() == 6, "Output count must include batches");
        h.assertTrue(ItemStack.matches(pattern, original), "Pattern must not change");
        var copy = request.target(); copy.shrink(1);
        h.assertTrue(!request.target().isEmpty(), "Request target must be defensive");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void rejectsUnencodedItem(GameTestHelper h) {
        reject(h, () -> PatternOrderRequest.decode(new ItemStack(Items.STONE), h.getLevel(), 1));
    }
    @GameTest(template = "empty")
    public static void rejectsOutputOverLimit(GameTestHelper h) {
        reject(h, () -> PatternOrderRequest.decode(pattern(2), h.getLevel(), 4096));
    }
    @GameTest(template = "empty")
    public static void rejectsMultipleOutputs(GameTestHelper h) {
        var pattern = PatternDetailsHelper.encodeProcessingPattern(
            List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1)),
            List.of(new GenericStack(AEItemKey.of(Items.STONE), 1), new GenericStack(AEItemKey.of(Items.DIRT), 1)));
        reject(h, () -> PatternOrderRequest.decode(pattern, h.getLevel(), 1));
    }
    @GameTest(template = "empty")
    public static void rejectsFluidInputs(GameTestHelper h) {
        var pattern = PatternDetailsHelper.encodeProcessingPattern(
            List.of(new GenericStack(AEFluidKey.of(Fluids.WATER), 1000)),
            List.of(new GenericStack(AEItemKey.of(Items.STONE), 1)));
        reject(h, () -> PatternOrderRequest.decode(pattern, h.getLevel(), 1));
    }
    @GameTest(template = "empty")
    public static void rejectsFluidOutput(GameTestHelper h) {
        var pattern = PatternDetailsHelper.encodeProcessingPattern(
            List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1)),
            List.of(new GenericStack(AEFluidKey.of(Fluids.WATER), 1000)));
        reject(h, () -> PatternOrderRequest.decode(pattern, h.getLevel(), 1));
    }
    @GameTest(template = "empty")
    public static void missingConsoleIsUnavailableNotCompleted(GameTestHelper h) {
        var receipt = io.github.genichimaruo.singulo.api.android.AndroidOrders.status(
            h.getLevel(), h.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1)),
            java.util.UUID.randomUUID(), java.util.UUID.randomUUID());
        h.assertTrue(receipt.state() == io.github.genichimaruo.singulo.api.android.AndroidOrders.State.UNAVAILABLE,
            "Absent console must not look completed");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void commandsAreRegistered(GameTestHelper h) {
        var root = h.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("appliedsingulo");
        h.assertTrue(root != null, "Development commands must be registered");
        for (var command : List.of("newid", "submit", "status", "cancel", "acknowledge"))
            h.assertTrue(root.getChild(command) != null, "Missing command: " + command);
        h.succeed();
    }
}
