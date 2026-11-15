package io.github.genichimaruo.appliedsingulo.gametest;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import io.github.genichimaruo.appliedsingulo.me.AndroidInterfaceHost;
import io.github.genichimaruo.appliedsingulo.me.AndroidInterfaceLogic;
import io.github.genichimaruo.appliedsingulo.me.AndroidRecipePattern;
import io.github.genichimaruo.appliedsingulo.order.SinguloLinks;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Deterministic tests of async planning races, using real AE keys/patterns and controlled service futures. */
@GameTestHolder("applied_singulo")
@PrefixGameTestTemplate(false)
public final class StockPatternGameTests {
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            if (m.getName().equals("equals")) return p == a[0];
            if (m.getName().equals("toString")) return type.getSimpleName();
            return handler.invoke(p, m, a);
        });
    }
    private static AndroidRecipePattern pattern(int amount, net.minecraft.core.HolderLookup.Provider registries) {
        return AndroidRecipePattern.of(new SinguloLinks.PatternRecipe(new ItemStack(Items.STONE), amount,
            List.of(new SinguloLinks.RecipeInput(List.of(new ItemStack(Items.COBBLESTONE)), 1))), registries);
    }
    private static final class Fixture {
        final KeyCounter inventory = new KeyCounter();
        final AndroidInterfaceLogic logic;
        final ServerLevel level;
        CompletableFuture<ICraftingPlan> pending;
        int calculations, submissions;
        long lastAmount;
        boolean requesting;
        Fixture(GameTestHelper h) {
            level = h.getLevel();
            var crafting = proxy(ICraftingService.class, (p, m, a) -> switch (m.getName()) {
                case "isRequesting" -> requesting;
                case "getCraftingFor" -> List.of(pattern(1, level.registryAccess()));
                case "beginCraftingCalculation" -> {
                    calculations++; lastAmount = (long) a[3];
                    pending = new CompletableFuture<>(); yield pending;
                }
                case "submitJob" -> {
                    submissions++; requesting = true;
                    yield proxy(ICraftingSubmitResult.class, (q, n, b) -> n.getName().equals("successful") ? true : null);
                }
                default -> null;
            });
            var storage = proxy(IStorageService.class, (p, m, a) -> m.getName().equals("getCachedInventory") ? inventory : null);
            var grid = proxy(IGrid.class, (p, m, a) -> switch (m.getName()) {
                case "getCraftingService" -> crafting;
                case "getStorageService" -> storage;
                default -> null;
            });
            var node = proxy(IManagedGridNode.class, (p, m, a) -> switch (m.getName()) {
                case "getGrid" -> grid;
                case "isActive" -> true;
                case "addService" -> p;
                default -> null;
            });
            var host = proxy(AndroidInterfaceHost.class, (p, m, a) -> switch (m.getName()) {
                case "hostLevel" -> level;
                case "hostPos" -> BlockPos.ZERO;
                default -> null;
            });
            logic = new AndroidInterfaceLogic(host, node);
        }
        void tick() { invoke("updateStock", new Class<?>[]{ServerLevel.class}, level); }
        void replace(List<AndroidRecipePattern> patterns) {
            invoke("replacePatterns", new Class<?>[]{BlockPos.class, List.class}, BlockPos.ZERO, patterns);
        }
        void invoke(String name, Class<?>[] parameters, Object... args) {
            try {
                var method = AndroidInterfaceLogic.class.getDeclaredMethod(name, parameters);
                method.setAccessible(true); method.invoke(logic, args);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
        void finishPlan() {
            pending.complete(proxy(ICraftingPlan.class, (p, m, a) -> switch (m.getName()) {
                case "simulation" -> false;
                case "patternTimes" -> Map.of(pattern(1, level.registryAccess()), 1L);
                default -> null;
            }));
        }
    }
    @GameTest(template = "empty")
    public static void patternRefreshComparesDefinitionsNotDisplayStrings(GameTestHelper h) {
        var f = new Fixture(h);
        var first = pattern(1, h.getLevel().registryAccess()); var changed = pattern(2, h.getLevel().registryAccess());
        h.assertTrue(first.getDefinition().toString().equals(changed.getDefinition().toString()), "Regression fixture shares display identity");
        f.replace(List.of(first));
        var original = f.logic.patterns();
        f.replace(List.of(pattern(1, h.getLevel().registryAccess())));
        h.assertTrue(original == f.logic.patterns(), "Equal definitions do not replace the provider snapshot");
        f.replace(List.of(changed));
        h.assertTrue(f.logic.patterns().getFirst().outputCount() == 2 && !f.logic.patterns().contains(first), "Same-size changed recipe replaces old definition");
        f.logic.destroy(); h.succeed();
    }
    @GameTest(template = "empty")
    public static void duplicateStockSlotsShareOneCalculation(GameTestHelper h) {
        var f = new Fixture(h); var key = AEItemKey.of(Items.STONE);
        f.logic.setStock(0, key, 16); f.logic.setStock(1, key, 32);
        f.tick(); h.assertTrue(f.calculations == 1 && f.lastAmount == 32, "One calculation for largest target");
        f.finishPlan(); f.tick();
        h.assertTrue(f.submissions == 1, "Exactly one craft submission");
        f.logic.destroy(); h.succeed();
    }
    @GameTest(template = "empty")
    public static void completedStockPlanDiscardedAfterExternalRefill(GameTestHelper h) {
        var f = new Fixture(h); var key = AEItemKey.of(Items.STONE);
        f.logic.setStock(0, key, 32); f.tick(); f.finishPlan();
        f.inventory.add(key, 32); f.tick();
        h.assertTrue(f.submissions == 0 && f.calculations == 1, "Refilled stock never submits stale plan");
        f.logic.destroy(); h.succeed();
    }
    @GameTest(template = "empty")
    public static void partialRefillRecalculatesOnlyRemainingDeficit(GameTestHelper h) {
        var f = new Fixture(h); var key = AEItemKey.of(Items.STONE);
        f.logic.setStock(0, key, 32); f.tick(); f.finishPlan();
        f.inventory.add(key, 10); f.tick();
        h.assertTrue(f.submissions == 0 && f.calculations == 2 && f.lastAmount == 22, "Recalculate only current deficit");
        var pending = f.pending; f.logic.setStock(0, null, 0);
        h.assertTrue(pending.isCancelled(), "Clearing slot cancels pending calculation");
        f.logic.destroy(); h.succeed();
    }
}
