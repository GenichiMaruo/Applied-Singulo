package io.github.genichimaruo.appliedsingulo.order;

import io.github.genichimaruo.singulo.android.work.WorkLocationCardItem;
import io.github.genichimaruo.singulo.api.android.AndroidOrders;
import io.github.genichimaruo.singulo.api.android.AndroidRecipe;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Adapter over Singulo's public Android API (0.19.0: AndroidOrders, AndroidEscrows, AndroidRecipe).
 * The Work Location Card helpers are the only Singulo internals read, so a device can be pointed at a console
 * by clicking it with a card that recorded that console.
 */
public final class SinguloLinks {
    private static long ceilDiv(long numerator, long denominator) {
        long quotient = Math.floorDiv(numerator, denominator);
        return numerator % denominator == 0 ? quotient : Math.addExact(quotient, 1);
    }
    private SinguloLinks() { }

    /** Null when {@code owner} may order from the console; otherwise why not (public snapshot API). */
    @Nullable
    public static String checkAccess(ServerLevel level, BlockPos console, UUID owner) {
        var snapshot = AndroidOrders.snapshot(level, console, owner);
        return switch (snapshot.state()) {
            case AVAILABLE -> snapshot.remainingReceiptCapacity() > 0 ? null : "console_receipt_ledger_full";
            case REJECTED -> "not_console_owner";
            case UNAVAILABLE -> "console_unavailable";
        };
    }

    /** A neighbouring Work Console that {@code actor} owns (detected through the public snapshot API). */
    @Nullable
    public static BlockPos ownedNeighbourConsole(ServerLevel level, BlockPos pos, UUID actor) {
        for (Direction d : Direction.values()) {
            var p = pos.relative(d);
            if (level.isLoaded(p) && AndroidOrders.snapshot(level, p, actor).state() == AndroidOrders.SnapshotState.AVAILABLE)
                return p.immutable();
        }
        return null;
    }

    /**
     * Whether {@code pos} is a Supply Port linked to a Work Console that {@code owner} owns, or such a console itself:
     * the only neighbours the ME interface shows its network's storage to.
     */
    public static boolean ownedWorkBlock(ServerLevel level, BlockPos pos, UUID owner) {
        if (!level.isLoaded(pos)) return false;
        var be = level.getBlockEntity(pos);
        BlockPos console;
        if (be instanceof io.github.genichimaruo.singulo.android.work.SupplyPortBlockEntity port) console = port.console();
        else if (be instanceof io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity) console = pos;
        else return false;
        return console != null && level.isLoaded(console)
            && AndroidOrders.snapshot(level, console, owner).state() == AndroidOrders.SnapshotState.AVAILABLE;
    }

    public static boolean isLocationCard(ItemStack stack) { return WorkLocationCardItem.isCard(stack); }

    /** The console recorded on a Work Location Card, or null (empty card or another dimension). */
    @Nullable
    public static BlockPos cardTarget(ItemStack card, Level level) {
        return WorkLocationCardItem.isCard(card) && WorkLocationCardItem.sameDimension(card, level)
            ? WorkLocationCardItem.pos(card) : null;
    }

    // ------------------------------------------------------------------ recipes

    /** One way to satisfy a recipe input: any of {@code options}, {@code count} items per craft. */
    public record RecipeInput(List<ItemStack> options, long count) {
        public RecipeInput { options = options.stream().map(s -> s.copyWithCount(1)).toList(); }
    }

    /**
     * A Singulo Android recipe expressed with plain items, for AE2 pattern export. {@code chained}: the inputs are the
     * raw materials of a whole crafting chain (intermediates are crafted inside the same Singulo order).
     */
    public record PatternRecipe(ItemStack output, int outputCount, List<RecipeInput> inputs, boolean chained) {
        public PatternRecipe { output = output.copyWithCount(1); inputs = List.copyOf(inputs); }
        public PatternRecipe(ItemStack output, int outputCount, List<RecipeInput> inputs) { this(output, outputCount, inputs, false); }
        @Override public ItemStack output() { return output.copy(); }
    }

    /** Exportable (escrow-eligible) recipes of the console's loaded Recipe Racks; empty when not owned or unavailable. */
    public static List<PatternRecipe> recipes(ServerLevel level, BlockPos console, UUID owner) {
        var snapshot = AndroidOrders.recipes(level, console, owner);
        if (snapshot.state() != AndroidOrders.SnapshotState.AVAILABLE) return List.of();
        return patternRecipes(snapshot.recipes());
    }

    /**
     * Chained recipes first, then the single-step ones. AE2 tries the patterns of one item in list order, one unit at
     * a time, falling through only when a pattern cannot be fulfilled: with the raw materials in ME the whole chain is
     * one push (one Singulo order); otherwise AE2 uses the single-step patterns and intermediates already in ME.
     */
    public static List<PatternRecipe> patternRecipes(List<AndroidRecipe> all) {
        List<AndroidRecipe> eligible = all.stream().filter(SinguloLinks::escrowEligible).toList();
        List<PatternRecipe> chains = new ArrayList<>(), steps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (var recipe : eligible) {
            var chain = chain(recipe, eligible);
            if (chain != null && seen.add(chain.toString())) chains.add(chain);
            var step = convert(recipe);
            if (step != null && seen.add(step.toString())) steps.add(step);
        }
        chains.addAll(steps);
        return chains;
    }

    // ------------------------------------------------------------------ chains

    static final int MAX_CHAIN_DEPTH = 8;
    /** Largest multiplier used to make every raw material an exact integer (otherwise round up per craft). */
    static final long MAX_CHAIN_MULTIPLIER = 64;

    /** Exact non-negative fraction. */
    private record Frac(long num, long den) {
        static Frac of(long n, long d) { long g = gcd(n, d); return new Frac(n / g, d / g); }
        Frac times(long n) { return of(Math.multiplyExact(num, n), den); }
        Frac div(long d) { return of(num, Math.multiplyExact(den, d)); }
        Frac plus(Frac o) {
            return of(Math.addExact(Math.multiplyExact(num, o.den), Math.multiplyExact(o.num, den)), Math.multiplyExact(den, o.den));
        }
        static long gcd(long a, long b) { a = Math.abs(a); b = Math.abs(b); while (b != 0) { long t = a % b; a = b; b = t; } return Math.max(1, a); }
    }

    /**
     * {@code recipe} with every input that another eligible recipe makes expanded down to raw materials (inputs with
     * alternatives, or without a recipe, stay raw). Null when nothing is expanded (identical to the single step) or on
     * arithmetic overflow. Cycles are cut by never reusing a recipe already on the path.
     */
    @Nullable
    public static PatternRecipe chain(AndroidRecipe recipe, List<AndroidRecipe> eligible) {
        var top = convert(recipe);
        if (top == null) return null;
        java.util.LinkedHashMap<String, Frac> needs = new java.util.LinkedHashMap<>();
        java.util.Map<String, List<ItemStack>> leaves = new java.util.HashMap<>();
        boolean[] expanded = { false };
        long multiplier = 1;
        List<RecipeInput> inputs = new ArrayList<>();
        try {
            expand(recipe, Frac.of(1, 1), eligible, new ArrayList<>(), needs, leaves, expanded);
            if (!expanded[0] || needs.isEmpty()) return null;
            for (var f : needs.values()) multiplier = Math.multiplyExact(multiplier / Frac.gcd(multiplier, f.den()), f.den());
            if (multiplier > MAX_CHAIN_MULTIPLIER) multiplier = 1;
            for (var e : needs.entrySet()) {
                var f = e.getValue();
                long count = ceilDiv(Math.multiplyExact(f.num(), multiplier), f.den());
                if (count > 0) inputs.add(new RecipeInput(leaves.get(e.getKey()), count));
            }
        } catch (ArithmeticException overflow) {
            return null;
        }
        long out = (long) top.outputCount() * multiplier;
        if (out > Integer.MAX_VALUE || inputs.isEmpty()) return null;
        // A cycle (e.g. ingot <-> block) could end at the product itself; such a pattern would consume its own output.
        for (var in : inputs)
            for (var option : in.options())
                if (ItemStack.isSameItemSameTags(option, top.output())) return null;
        return new PatternRecipe(top.output(), (int) out, inputs, true);
    }

    private static void expand(AndroidRecipe recipe, Frac crafts, List<AndroidRecipe> eligible, List<AndroidRecipe> path,
                               java.util.Map<String, Frac> needs, java.util.Map<String, List<ItemStack>> leaves, boolean[] expanded) {
        path.add(recipe);
        for (var m : recipe.materials()) {
            if (m.count() <= 0) continue;
            Frac need = crafts.times(m.count());
            AndroidRecipe sub = m.ingredientJson().isEmpty() && path.size() < MAX_CHAIN_DEPTH ? producer(m.sample(), eligible, path) : null;
            if (sub != null) {
                expanded[0] = true;
                expand(sub, need.div(sub.outputs().get(0).count()), eligible, path, needs, leaves, expanded);
                continue;
            }
            List<ItemStack> options = new ArrayList<>();
            if (!m.ingredientJson().isEmpty()) for (var o : m.alternatives()) if (!o.isEmpty()) options.add(o.copyWithCount(1));
            if (options.isEmpty()) options.add(m.sample().copyWithCount(1));
            StringBuilder key = new StringBuilder();
            for (var o : options) key.append(materialKey(o)).append('|');
            leaves.putIfAbsent(key.toString(), options);
            needs.merge(key.toString(), need, Frac::plus);
        }
        path.removeLast();
    }

    /** The first eligible recipe (rack order) whose product is {@code item} and that is not already on the path. */
    @Nullable
    private static AndroidRecipe producer(ItemStack item, List<AndroidRecipe> eligible, List<AndroidRecipe> path) {
        for (var r : eligible)
            if (!path.contains(r) && ItemStack.isSameItemSameTags(r.outputs().get(0).sample(), item)) return r;
        return null;
    }

    /** Mode name of Singulo's ordinary crafting recipes (RecipeData.MODE_CRAFTING). */
    public static final String MODE_CRAFTING = "crafting";

    /**
     * Whether Singulo's private crafting escrow can run the recipe: one ordinary crafting step with item
     * inputs only. Processing, procedures, fuel, catalysts and liquids are rejected by AndroidEscrows (they
     * would need shared machines or stock), so they are not offered to AE2.
     */
    public static boolean escrowEligible(AndroidRecipe recipe) {
        if (!(MODE_CRAFTING.equals(recipe.mode()) || MODE_PROCESSING.equals(recipe.mode())) || recipe.fuelFill()
                || recipe.outputs().isEmpty() || recipe.materials().isEmpty())
            return false;
        if (recipe.outputs().get(0).liquid()) return false;   // AE2 asks for the main product; it must be an item
        for (var p : recipe.outputs()) if (p.liquid() && !isFluid(p.sample())) return false;   // no chemicals
        for (var m : recipe.materials())
            if (m.fuel() || m.catalyst() || m.liquid() && !isFluid(m.sample())) return false;
        return true;
    }

    /** Mode name of Singulo's machine (processing) recipes. */
    public static final String MODE_PROCESSING = "processing";

    /** Whether {@code sample} is a Singulo fluid sample (amount in mB), as opposed to an item or a chemical. */
    public static boolean isFluid(ItemStack sample) {
        return !io.github.genichimaruo.singulo.api.android.AndroidEscrows.fluidOf(sample).isEmpty();
    }

    /** Stable identity of a material option: the fluid for fluid samples, otherwise item and components. */
    static String materialKey(ItemStack o) {
        var fluid = io.github.genichimaruo.singulo.api.android.AndroidEscrows.fluidOf(o);
        if (!fluid.isEmpty()) return "fluid:" + net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid.getFluid())
            + (!fluid.hasTag() ? "" : "#" + fluid.getTag().hashCode());
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(o.getItem())
            + (!o.hasTag() ? "" : "#" + o.getTag().hashCode());
    }

    /** Whether the console has an escrow-eligible recipe whose product is {@code target}. */
    public static boolean hasEscrowRecipe(ServerLevel level, BlockPos console, UUID owner, ItemStack target) {
        var snapshot = AndroidOrders.recipes(level, console, owner);
        return snapshot.state() == AndroidOrders.SnapshotState.AVAILABLE && snapshot.recipes().stream().anyMatch(r ->
            escrowEligible(r) && ItemStack.isSameItemSameTags(r.outputs().get(0).sample(), target));
    }

    /**
     * An escrow-eligible recipe as an AE2 pattern recipe (main product an item, at least one item input), or
     * null. Alternatives become AE2 input options.
     */
    @Nullable
    public static PatternRecipe convert(AndroidRecipe recipe) {
        if (!escrowEligible(recipe)) return null;
        var main = recipe.outputs().get(0);
        var output = main.sample();
        if (main.liquid() || output.isEmpty() || main.count() <= 0 || main.count() > Integer.MAX_VALUE) return null;
        List<RecipeInput> inputs = new ArrayList<>();
        for (var material : recipe.materials()) {
            if (material.count() <= 0) continue;
            List<ItemStack> options = new ArrayList<>();
            if (!material.ingredientJson().isEmpty())
                for (var option : material.alternatives()) if (!option.isEmpty()) options.add(option);
            if (options.isEmpty()) options.add(material.sample());
            inputs.add(new RecipeInput(options, material.count()));
        }
        if (inputs.isEmpty()) return null;
        return new PatternRecipe(output, (int) main.count(), inputs);
    }
}
