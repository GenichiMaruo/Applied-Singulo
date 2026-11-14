package io.github.genichimaruo.appliedsingulo.gametest;

import appeng.capabilities.Capabilities;
import io.github.genichimaruo.appliedsingulo.order.AndroidOrderQueue;
import io.github.genichimaruo.appliedsingulo.order.OrderJob;
import io.github.genichimaruo.appliedsingulo.order.OrphanedEscrows;
import io.github.genichimaruo.appliedsingulo.order.SinguloLinks;
import io.github.genichimaruo.appliedsingulo.me.AndroidMeInterfaceBlockEntity;
import io.github.genichimaruo.appliedsingulo.me.InterfaceStatus;
import io.github.genichimaruo.appliedsingulo.me.AndroidRecipePattern;
import io.github.genichimaruo.appliedsingulo.registry.ModContent;
import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.android.work.RecipeData;
import io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity;
import io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity;
import io.github.genichimaruo.singulo.android.work.WorkLinkedBlockEntity;
import io.github.genichimaruo.singulo.android.work.WorkLocationCardItem;
import io.github.genichimaruo.singulo.api.android.AndroidEscrows;
import io.github.genichimaruo.singulo.api.android.AndroidRecipe;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("applied_singulo")
@PrefixGameTestTemplate(false)
public final class MeInterfaceGameTests {
    private static final BlockPos ME = new BlockPos(1, 1, 1);
    private static final BlockPos CONSOLE = new BlockPos(0, 1, 0), SUPPLY = new BlockPos(1, 1, 0), CHEST = new BlockPos(1, 1, 1),
        RACK = new BlockPos(4, 1, 0), TABLE = new BlockPos(4, 1, 3), REMOTE_ME = new BlockPos(6, 1, 6);

    private static List<ItemStack> cobble(int n) { return List.of(new ItemStack(Items.COBBLESTONE, n)); }

    @GameTest(template = "empty")
    public static void queueBatchesRepeatedPushesIntoOneOrder(GameTestHelper h) {
        var queue = new AndroidOrderQueue(() -> { });
        queue.setOwner(UUID.randomUUID());
        var console = h.absolutePos(ME);
        for (int i = 0; i < 64; i++)
            h.assertTrue(queue.accept(new ItemStack(Items.STONE), 1, cobble(1), console, i), "push " + i);
        h.assertTrue(queue.jobs().size() == 1, "Same output must merge into one request, got " + queue.jobs().size());
        var job = queue.jobs().get(0);
        h.assertTrue(job.count() == 64 && job.pushes() == 64 && job.state() == OrderJob.State.QUEUED, "Merged count");
        h.assertTrue(job.heldMaterials().size() == 1 && job.heldMaterials().get(0).getCount() == 64,
            "Held materials are stacked, ready to offer: " + job.heldMaterials());
        h.assertTrue(queue.accept(new ItemStack(Items.SMOOTH_STONE), 1, cobble(1), console, 70), "Other output");
        h.assertTrue(queue.jobs().size() == 2, "Different outputs are separate requests");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void queueSplitsAtSinguloLimitsAndRejectsOversizedOffers(GameTestHelper h) {
        var queue = new AndroidOrderQueue(() -> { });
        queue.setOwner(UUID.randomUUID());
        var console = h.absolutePos(ME);
        h.assertTrue(queue.accept(new ItemStack(Items.STONE), 4000, cobble(1), console, 0), "first");
        h.assertTrue(queue.accept(new ItemStack(Items.STONE), 100, cobble(1), console, 1), "second");
        h.assertTrue(queue.jobs().size() == 2, "A request never exceeds 4096 items");
        List<ItemStack> tooMany = new ArrayList<>();
        for (int i = 0; i < AndroidOrderQueue.OFFER_STACKS + 1; i++) tooMany.add(new ItemStack(Items.DIRT, 64));
        var before = queue.heldContents().size();
        h.assertFalse(queue.accept(new ItemStack(Items.GLASS), 1, tooMany, console, 2), "Over Singulo's 128-stack offer limit");
        h.assertTrue(queue.heldContents().size() == before && "too_many_material_stacks".equals(queue.problem()), "Nothing taken");
        h.succeed();
    }

    private static AndroidRecipe.Material item(ItemStack sample, long count) {
        return new AndroidRecipe.Material(sample, count, List.of(sample), "", false, false, false);
    }

    @GameTest(template = "empty")
    public static void onlyEscrowEligibleRecipesBecomeAe2Patterns(GameTestHelper h) {
        var pos = h.absolutePos(ME);
        var stone = List.of(new AndroidRecipe.Product(new ItemStack(Items.STONE), 2, false));
        var crafting = new AndroidRecipe("crafting", pos, stone, List.of(item(new ItemStack(Items.COBBLESTONE), 3)), false);
        var recipe = SinguloLinks.convert(crafting);
        h.assertTrue(recipe != null && recipe.inputs().size() == 1 && recipe.inputs().get(0).count() == 3, "Converted: " + recipe);
        var pattern = AndroidRecipePattern.of(recipe);
        h.assertTrue(pattern != null && pattern.getInputs()[0].getMultiplier() == 3
            && pattern.getOutputs()[0].amount() == 2, "Pattern amounts");
        h.assertTrue(pattern.equals(AndroidRecipePattern.of(recipe)), "Same recipe, same pattern (dedup across interfaces)");

        var alt = new AndroidRecipe.Material(new ItemStack(Items.OAK_PLANKS), 2,
            List.of(new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.BIRCH_PLANKS)), "{\"tag\":\"minecraft:planks\"}", false, false, false);
        var withAlt = SinguloLinks.convert(new AndroidRecipe("crafting", pos, stone, List.of(alt), false));
        h.assertTrue(withAlt != null && withAlt.inputs().get(0).options().size() == 2, "Alternatives become AE2 options");

        // Machine (processing) recipes are exported now; their fluid inputs become AE2 fluid inputs (mB).
        var water = io.github.genichimaruo.singulo.api.android.AndroidEscrows.fluidSample(
            new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1));
        var machine = SinguloLinks.convert(new AndroidRecipe("processing", pos, stone, List.of(item(new ItemStack(Items.COBBLESTONE), 1),
            new AndroidRecipe.Material(water, 250, List.of(water), "", false, false, true)), false));
        h.assertTrue(machine != null && machine.inputs().size() == 2, "Machine recipe with a fluid exported: " + machine);
        var machinePattern = AndroidRecipePattern.of(machine);
        h.assertTrue(machinePattern != null && java.util.Arrays.stream(machinePattern.getInputs()).anyMatch(in ->
            in.getPossibleInputs()[0].what() instanceof appeng.api.stacks.AEFluidKey && in.getMultiplier() == 250), "Fluid input of 250 mB");
        h.assertTrue(SinguloLinks.convert(new AndroidRecipe("processing", pos, stone, List.of(item(new ItemStack(Items.COBBLESTONE), 1),
            new AndroidRecipe.Material(new ItemStack(Items.COAL), 1, List.of(), "", false, true, false)), false)) == null,
            "Machine recipes with fuel: not exported (fuel would come from shared stock)");
        h.assertTrue(SinguloLinks.convert(new AndroidRecipe("crafting", pos, stone, List.of(item(new ItemStack(Items.COBBLESTONE), 1),
            new AndroidRecipe.Material(new ItemStack(Items.SHEARS), 1, List.of(), "", true, false, false)), false)) == null,
            "Catalyst recipes would use shared stock: not exported");
        h.assertTrue(SinguloLinks.convert(new AndroidRecipe("crafting", pos, stone, List.of(item(new ItemStack(Items.COBBLESTONE), 1),
            new AndroidRecipe.Material(new ItemStack(Items.COAL), 1, List.of(), "", false, true, false)), false)) == null,
            "Fuel recipes: not exported");
        h.assertTrue(SinguloLinks.convert(new AndroidRecipe("crafting", pos, stone, List.of(), false)) == null, "No inputs: not exported");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void meInterfaceIsAGridNodeHostAndAdoptsOnlyAnOwnedConsole(GameTestHelper h) {
        var owner = UUID.randomUUID();
        h.setBlock(ME.south(), SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        ((WorkConsoleBlockEntity) h.getBlockEntity(ME.south())).setOwner(owner);
        h.setBlock(ME, ModContent.ANDROID_ME_INTERFACE.get());
        h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(ME)).getCapability(Capabilities.IN_WORLD_GRID_NODE_HOST).orElse(null) != null,
            "Cables must be able to connect");
        for (var side : Direction.values()) {
            var view = h.getLevel().getBlockEntity(h.absolutePos(ME)).getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
            h.assertTrue(view == null || view.getSlots() == 0,
                "Without an owner and an online ME network, no face may show or accept anything");
        }
        h.assertTrue(h.absolutePos(ME.south()).equals(SinguloLinks.ownedNeighbourConsole(h.getLevel(), h.absolutePos(ME), owner)),
            "The owner's adjacent console must be found");
        h.assertTrue(SinguloLinks.ownedNeighbourConsole(h.getLevel(), h.absolutePos(ME), UUID.randomUUID()) == null,
            "Someone else's console must not be adopted");
        h.succeed();
    }

    // ------------------------------------------------------------------ real Singulo workshop

    private static Item singulo(String name) { return BuiltInRegistries.ITEM.get(new ResourceLocation("singulo", name)); }

    private static ItemStack recipeCard(GameTestHelper h, List<ItemStack> grid, ItemStack output) {
        var card = new ItemStack(singulo("recipe_card"));
        new RecipeData(grid, output).write(card, h.getLevel().registryAccess());
        return card;
    }

    /** Console + Supply Port with a shared chest of 32 logs + Recipe Rack (planks, sticks) + crafting table + android. */
    private static WorkConsoleBlockEntity workshop(GameTestHelper h, boolean moving) {
        return workshop(h, moving, Blocks.CRAFTING_TABLE);
    }

    private static WorkConsoleBlockEntity workshop(GameTestHelper h, boolean moving, net.minecraft.world.level.block.Block tableBlock) {
        var level = h.getLevel();
        h.setBlock(CONSOLE, SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        h.setBlock(SUPPLY, SinguloBlocks.ANDROID_SUPPLY_PORT.get());
        h.setBlock(CHEST, Blocks.CHEST);
        h.setBlock(RACK, SinguloBlocks.ANDROID_RECIPE_RACK.get());
        h.setBlock(TABLE, tableBlock);
        ((ChestBlockEntity) h.getBlockEntity(CHEST)).setItem(0, new ItemStack(Items.OAK_LOG, 32));
        var player = h.makeMockServerPlayerInLevel();
        WorkConsoleBlockEntity console = (WorkConsoleBlockEntity) h.getBlockEntity(CONSOLE);
        console.setOwner(player.getUUID());
        var link = new ItemStack(singulo("work_location_card"));
        WorkLocationCardItem.record(link, level, h.absolutePos(CONSOLE), SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        for (var p : List.of(SUPPLY, RACK)) ((WorkLinkedBlockEntity) h.getBlockEntity(p)).useItem(player, link, InteractionHand.MAIN_HAND);
        var table = new ItemStack(singulo("work_location_card"));
        WorkLocationCardItem.record(table, level, h.absolutePos(TABLE), tableBlock);
        RecipeRackBlockEntity rack = (RecipeRackBlockEntity) h.getBlockEntity(RACK);
        List<ItemStack> planks = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        planks.set(0, new ItemStack(Items.OAK_LOG));
        List<ItemStack> sticks = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        sticks.set(0, new ItemStack(Items.OAK_PLANKS));
        sticks.set(3, new ItemStack(Items.OAK_PLANKS));
        rack.items().setStackInSlot(0, table);
        rack.items().setStackInSlot(1, recipeCard(h, planks, new ItemStack(Items.OAK_PLANKS, 4)));
        rack.items().setStackInSlot(2, recipeCard(h, sticks, new ItemStack(Items.STICK, 4)));
        List<ItemStack> fence = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        for (int i : new int[] { 3, 5, 6, 8 }) fence.set(i, new ItemStack(Items.OAK_PLANKS));
        for (int i : new int[] { 4, 7 }) fence.set(i, new ItemStack(Items.STICK));
        rack.items().setStackInSlot(3, recipeCard(h, fence, new ItemStack(Items.OAK_FENCE, 3)));
        if (player.getServer().getPlayerList().getPlayer(player.getUUID()) == player) player.getServer().getPlayerList().remove(player);
        player.discard();

        var android = SinguloEntities.BLADE_ANDROID.get().create(level);
        android.makeGuard(console.owner(), h.absolutePos(new BlockPos(2, 1, 4)), RuinAndroid.MODE_GUARD, 30);
        android.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 1, 2))));
        android.setNoAi(!moving);
        level.addFreshEntity(android);
        return console;
    }

    private static AndroidMeInterfaceBlockEntity remoteInterface(GameTestHelper h, WorkConsoleBlockEntity console) {
        h.setBlock(REMOTE_ME, ModContent.ANDROID_ME_INTERFACE.get());
        var me = (AndroidMeInterfaceBlockEntity) h.getBlockEntity(REMOTE_ME);
        me.queue().setOwner(console.owner());
        me.queue().setConsole(console.getBlockPos());   // what a Work Location Card click does
        return me;
    }

    private static int count(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    /**
     * End to end with a real android, without AE2 power: 4 batched pushes (1 log -> 4 planks each) become one
     * escrow request; the android crafts only from that request's logs, never from the shared chest, and the
     * 16 planks are claimed back by the remote interface, after which Singulo forgets the request.
     */
    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void remoteInterfaceCraftsFromPrivateEscrowAndClaimsByRequest(GameTestHelper h) {
        var console = workshop(h, true);
        var me = remoteInterface(h, console);
        UUID[] request = { null };
        boolean[] submitted = { false };
        h.startSequence()
            // Shared through the block entity's own ticking (no direct refresh call), as in a real world.
            .thenWaitUntil(() -> h.assertTrue(me.patterns().size() == 5,
                "Planks, sticks, fences and the stick/fence chains must be shared as AE2 patterns, got " + AndroidRecipePattern.outputs(me.patterns())))
            .thenExecute(() -> {
                long now = h.getLevel().getGameTime();
                for (int i = 0; i < 4; i++)
                    h.assertTrue(me.queue().accept(new ItemStack(Items.OAK_PLANKS), 4, List.of(new ItemStack(Items.OAK_LOG)),
                        console.getBlockPos(), now), "push " + i);
                h.assertTrue(me.queue().jobs().size() == 1 && me.queue().jobs().get(0).count() == 16, "Batched into one request");
                request[0] = me.queue().jobs().get(0).request;
            })
            .thenWaitUntil(() -> {
                if (!submitted[0] && !me.queue().jobs().isEmpty() && me.queue().jobs().get(0).submitted()) {
                    submitted[0] = true;
                    h.assertTrue(me.queue().heldContents().isEmpty(), "Materials must be relinquished exactly on acceptance");
                }
                h.assertTrue(submitted[0] || me.queue().jobs().isEmpty(), "Not submitted yet: " + me.queue().jobs());
                h.assertTrue(me.queue().jobs().isEmpty(), "Request still open: " + me.queue().jobs());
                h.assertTrue(count(me.queue().outputContents(), Items.OAK_PLANKS) == 16,
                    "All 16 planks claimed by request: " + me.queue().outputContents());
                h.assertTrue(((ChestBlockEntity) h.getBlockEntity(CHEST)).getItem(0).getCount() == 32,
                    "The shared chest must not be touched");
                h.assertTrue(AndroidEscrows.snapshot(h.getLevel(), console.owner(), request[0]).phase() == AndroidEscrows.Phase.UNKNOWN,
                    "Singulo custody must be acknowledged and released");
            })
            .thenSucceed();
    }

    /**
     * A large request crafted at an Android Workbench. Escrow orders cannot spill products into the bench's
     * buffer, so every trip must fit in the android's carry; otherwise the android loops fetching and returning
     * the materials forever (seen in game with AE2 bulk orders).
     */
    @GameTest(template = "empty", timeoutTicks = 12000)
    public static void largeEscrowRequestAtAndroidWorkbenchCompletes(GameTestHelper h) {
        var console = workshop(h, true, SinguloBlocks.ANDROID_WORKBENCH.get());
        var me = remoteInterface(h, console);
        long now = h.getLevel().getGameTime();
        List<ItemStack> logs = new ArrayList<>();
        for (int i = 0; i < 4; i++) logs.add(new ItemStack(Items.OAK_LOG, 64));
        h.assertTrue(me.queue().accept(new ItemStack(Items.OAK_PLANKS), 1024, logs, console.getBlockPos(), now - 200), "push");
        h.succeedWhen(() -> {
            h.assertTrue(me.queue().jobs().isEmpty(), "Request still open: " + me.queue().jobs());
            h.assertTrue(count(me.queue().outputContents(), Items.OAK_PLANKS) == 1024,
                "All 1024 planks claimed: " + count(me.queue().outputContents(), Items.OAK_PLANKS));
        });
    }

    /**
     * The in-game report: AE2 ordered 102 fences (34 crafts) at a Quantum Android Workbench. Planks and sticks fill
     * every carry slot, so a trip of all 34 crafts can never hold its fences; the trip must be split.
     */
    @GameTest(template = "empty", timeoutTicks = 12000)
    public static void fenceRequestAtQuantumWorkbenchCompletes(GameTestHelper h) {
        var console = workshop(h, true, SinguloBlocks.QUANTUM_ANDROID_WORKBENCH.get());
        var me = remoteInterface(h, console);
        long now = h.getLevel().getGameTime();
        var materials = List.of(new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.OAK_PLANKS, 8),
            new ItemStack(Items.STICK, 64), new ItemStack(Items.STICK, 4));
        h.assertTrue(me.queue().accept(new ItemStack(Items.OAK_FENCE), 102, materials, console.getBlockPos(), now - 200), "push");
        h.succeedWhen(() -> {
            h.assertTrue(me.queue().jobs().isEmpty(), "Request still open: " + me.queue().jobs());
            h.assertTrue(count(me.queue().outputContents(), Items.OAK_FENCE) == 102,
                "All 102 fences claimed: " + count(me.queue().outputContents(), Items.OAK_FENCE));
        });
    }

    /**
     * Two interfaces of one ME network touch the same Supply Port: Singulo must count the network's items once
     * (like QIO frequencies), take from it, and faces that do not touch the owner's work blocks show nothing.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void meNetworkNextToSupplyPortIsCountedOnce(GameTestHelper h) {
        var console = workshop(h, false);
        BlockPos me1 = SUPPLY.above(), me2 = SUPPLY.east(), drive = SUPPLY.above().east(), power = drive.above();
        h.setBlock(power, appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(drive, appeng.core.definitions.AEBlocks.DRIVE.block());
        h.setBlock(me1, ModContent.ANDROID_ME_INTERFACE.get());
        h.setBlock(me2, ModContent.ANDROID_ME_INTERFACE.get());
        var a = (AndroidMeInterfaceBlockEntity) h.getBlockEntity(me1);
        var b = (AndroidMeInterfaceBlockEntity) h.getBlockEntity(me2);
        a.queue().setOwner(console.owner());
        b.queue().setOwner(console.owner());
        var cells = h.getLevel().getBlockEntity(h.absolutePos(drive)).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        h.assertTrue(cells != null && cells.insertItem(0, appeng.core.definitions.AEItems.ITEM_CELL_1K.stack(), false).isEmpty(),
            "Storage cell into the drive");
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(a.mainNode().isActive() && b.mainNode().isActive()
                && a.mainNode().getGrid() == b.mainNode().getGrid(), "Both interfaces online on one grid"))
            .thenExecute(() -> {
                var storage = a.mainNode().getGrid().getStorageService().getInventory();
                h.assertTrue(storage.insert(appeng.api.stacks.AEItemKey.of(Items.COBBLESTONE), 100, appeng.api.config.Actionable.MODULATE,
                    appeng.api.networking.security.IActionSource.empty()) == 100, "100 cobblestone into ME");
            })
            .thenWaitUntil(() -> {
                long cobble = console.stock().stream().filter(c -> c.item.is(Items.COBBLESTONE)).mapToLong(c -> c.count).sum();
                h.assertTrue(cobble == 100, "The ME network must be counted once, got " + cobble);
                long logs = console.stock().stream().filter(c -> c.item.is(Items.OAK_LOG)).mapToLong(c -> c.count).sum();
                h.assertTrue(logs == 32, "The ordinary chest still counts: " + logs);
            })
            .thenExecute(() -> {
                var toDrive = a.storageView(Direction.EAST);   // faces the drive, not a Singulo work block
                h.assertTrue(toDrive.getSlots() == 0 && !toDrive.insertItem(0, new ItemStack(Items.DIRT), true).isEmpty(),
                    "A face that does not touch the owner's Supply Port / console must show and accept nothing");
                var toPort = a.storageView(Direction.DOWN);
                int slot = -1;
                for (int i = 0; i < toPort.getSlots(); i++) if (toPort.getStackInSlot(i).is(Items.COBBLESTONE)) slot = i;
                h.assertTrue(slot >= 0 && toPort.extractItem(slot, 10, false).getCount() == 10, "Androids can take from ME");
                h.assertTrue(toPort.insertItem(0, new ItemStack(Items.DIRT, 5), false).isEmpty(), "Returned items go into ME");
                b.queue().setOwner(UUID.randomUUID());
                h.assertTrue(b.storageView(Direction.WEST).getSlots() == 0, "Someone else's interface must not feed this console");
            })
            .thenSucceed();
    }

    private static AndroidRecipe crafting(ItemStack out, long n, AndroidRecipe.Material... in) {
        return new AndroidRecipe("crafting", BlockPos.ZERO, List.of(new AndroidRecipe.Product(out, n, false)), List.of(in), false);
    }

    /** Fences from logs: 4 planks + 2 sticks -> 3 fences; 2 planks -> 4 sticks; 1 log -> 4 planks  =>  5 logs -> 12 fences. */
    @GameTest(template = "empty")
    public static void chainedPatternsUseExactRawMaterialRatios(GameTestHelper h) {
        var planks = crafting(new ItemStack(Items.OAK_PLANKS), 4, item(new ItemStack(Items.OAK_LOG), 1));
        var sticks = crafting(new ItemStack(Items.STICK), 4, item(new ItemStack(Items.OAK_PLANKS), 2));
        var fence = crafting(new ItemStack(Items.OAK_FENCE), 3, item(new ItemStack(Items.OAK_PLANKS), 4), item(new ItemStack(Items.STICK), 2));
        var all = SinguloLinks.patternRecipes(List.of(planks, sticks, fence));
        h.assertTrue(all.size() == 5 && all.get(0).chained() && all.get(1).chained() && !all.get(2).chained(),
            "Two chains first, then three single steps: " + all);
        var fenceChain = all.stream().filter(r -> r.chained() && r.output().is(Items.OAK_FENCE)).findFirst().orElseThrow();
        h.assertTrue(fenceChain.outputCount() == 12 && fenceChain.inputs().size() == 1
            && fenceChain.inputs().get(0).options().get(0).is(Items.OAK_LOG) && fenceChain.inputs().get(0).count() == 5,
            "5 logs -> 12 fences: " + fenceChain);
        var stickChain = all.stream().filter(r -> r.chained() && r.output().is(Items.STICK)).findFirst().orElseThrow();
        h.assertTrue(stickChain.outputCount() == 8 && stickChain.inputs().get(0).count() == 1, "1 log -> 8 sticks: " + stickChain);
        h.assertFalse(AndroidRecipePattern.of(fenceChain).equals(AndroidRecipePattern.of(SinguloLinks.convert(fence))),
            "The chain and the single step are different AE2 patterns");

        // Cycles (ingot <-> block) must never produce a pattern that consumes its own output.
        var ingot = crafting(new ItemStack(Items.IRON_INGOT), 9, item(new ItemStack(Items.IRON_BLOCK), 1));
        var block = crafting(new ItemStack(Items.IRON_BLOCK), 1, item(new ItemStack(Items.IRON_INGOT), 9));
        for (var r : SinguloLinks.patternRecipes(List.of(ingot, block)))
            h.assertTrue(r.inputs().stream().noneMatch(in -> in.options().stream().anyMatch(o -> ItemStack.isSameItemSameTags(o, r.output()))),
                "Self-consuming pattern exported: " + r);
        h.succeed();
    }

    /** A chained push is one Singulo order that crafts planks, sticks and fences from the pushed logs. */
    @GameTest(template = "empty", timeoutTicks = 4800)
    public static void chainedPushCraftsWholeChainInOneOrder(GameTestHelper h) {
        var console = workshop(h, true);
        var me = remoteInterface(h, console);
        long now = h.getLevel().getGameTime();
        h.assertTrue(me.queue().accept(new ItemStack(Items.OAK_FENCE), 12, List.of(new ItemStack(Items.OAK_LOG, 5)),
            console.getBlockPos(), now - 200), "push");
        boolean[] oneOrder = { false };
        h.succeedWhen(() -> {
            if (!console.orders().isEmpty()) {
                h.assertTrue(console.orders().size() == 1, "The chain must be a single order, got " + console.orders().size());
                oneOrder[0] |= console.orders().get(0).steps.size() >= 3;   // planks, sticks and fences
            }
            h.assertTrue(oneOrder[0], "One order covering planks, sticks and fences expected: " + me.queue().jobs() + " orders=" + console.orders().stream().map(o -> o.steps.size()).toList());
            h.assertTrue(me.queue().jobs().isEmpty(), "Request still open: " + me.queue().jobs());
            h.assertTrue(count(me.queue().outputContents(), Items.OAK_FENCE) == 12, "12 fences: " + me.queue().outputContents());
            h.assertTrue(((ChestBlockEntity) h.getBlockEntity(CHEST)).getItem(0).getCount() == 32, "Shared logs untouched");
        });
    }

    /** AE2 itself picks the chain when the raw materials are in ME, and the single step when only intermediates are. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void ae2PrefersTheChainOnlyWhenRawMaterialsAreStored(GameTestHelper h) {
        var console = workshop(h, false);
        BlockPos mePos = new BlockPos(6, 1, 5), drive = mePos.above(), power = drive.above();
        h.setBlock(power, appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(drive, appeng.core.definitions.AEBlocks.DRIVE.block());
        h.setBlock(mePos, ModContent.ANDROID_ME_INTERFACE.get());
        var me = (AndroidMeInterfaceBlockEntity) h.getBlockEntity(mePos);
        me.queue().setOwner(console.owner());
        me.queue().setConsole(console.getBlockPos());
        var cells = h.getLevel().getBlockEntity(h.absolutePos(drive)).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        h.assertTrue(cells != null && cells.insertItem(0, appeng.core.definitions.AEItems.ITEM_CELL_1K.stack(), false).isEmpty(), "cell");
        var fence = appeng.api.stacks.AEItemKey.of(Items.OAK_FENCE);
        java.util.concurrent.Future<?>[] calc = { null };
        appeng.api.networking.crafting.ICraftingSimulationRequester requester =
            () -> appeng.api.networking.security.IActionSource.ofMachine(me);
        java.util.function.Consumer<java.util.Map<appeng.api.stacks.AEItemKey, Long>> store = items -> {
            var inv = me.mainNode().getGrid().getStorageService().getInventory();
            for (var e : items.entrySet())
                inv.insert(e.getKey(), e.getValue(), appeng.api.config.Actionable.MODULATE, appeng.api.networking.security.IActionSource.empty());
        };
        java.util.function.Supplier<appeng.api.networking.crafting.ICraftingPlan> plan = () -> {
            try { return (appeng.api.networking.crafting.ICraftingPlan) calc[0].get(); }
            catch (Exception e) { throw new IllegalStateException(e); }
        };
        java.util.function.Predicate<appeng.api.crafting.IPatternDetails> isChain = p ->
            p instanceof AndroidRecipePattern r && r.getDefinition().toStack().getOrCreateTag().getBoolean("chain");
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(me.mainNode().isActive() && me.patterns().size() == 5
                && !me.mainNode().getGrid().getCraftingService().getCraftables(k -> k.equals(fence)).isEmpty(),
                "Interface online with its patterns registered in AE2"))
            .thenExecute(() -> {
                store.accept(java.util.Map.of(appeng.api.stacks.AEItemKey.of(Items.OAK_LOG), 64L));
                calc[0] = me.mainNode().getGrid().getCraftingService().beginCraftingCalculation(h.getLevel(), requester, fence, 12,
                    appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
            })
            .thenWaitUntil(() -> h.assertTrue(calc[0].isDone(), "calculating"))
            .thenExecute(() -> {
                var p = plan.get();
                h.assertTrue(p.missingItems().isEmpty(), "Nothing missing with logs stored");
                h.assertTrue(p.patternTimes().size() == 1 && p.patternTimes().keySet().stream().allMatch(isChain),
                    "With logs in ME, AE2 must push the chain once: " + p.patternTimes());
                var inv = me.mainNode().getGrid().getStorageService().getInventory();
                inv.extract(appeng.api.stacks.AEItemKey.of(Items.OAK_LOG), 64, appeng.api.config.Actionable.MODULATE,
                    appeng.api.networking.security.IActionSource.empty());
                store.accept(java.util.Map.of(appeng.api.stacks.AEItemKey.of(Items.OAK_PLANKS), 16L,
                    appeng.api.stacks.AEItemKey.of(Items.STICK), 8L));
                calc[0] = me.mainNode().getGrid().getCraftingService().beginCraftingCalculation(h.getLevel(), requester, fence, 12,
                    appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
            })
            .thenWaitUntil(() -> h.assertTrue(calc[0].isDone(), "calculating"))
            .thenExecute(() -> {
                var p = plan.get();
                h.assertTrue(p.missingItems().isEmpty(), "Nothing missing with planks and sticks stored: " + p.missingItems());
                h.assertTrue(!p.patternTimes().isEmpty() && p.patternTimes().keySet().stream().noneMatch(isChain),
                    "Without logs, AE2 must fall back to the single-step fence pattern: " + p.patternTimes());
                // As in the reported world: logs AND intermediates stored. The chain has the higher priority.
                store.accept(java.util.Map.of(appeng.api.stacks.AEItemKey.of(Items.OAK_LOG), 64L));
                calc[0] = me.mainNode().getGrid().getCraftingService().beginCraftingCalculation(h.getLevel(), requester, fence, 12,
                    appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
            })
            .thenWaitUntil(() -> h.assertTrue(calc[0].isDone(), "calculating"))
            .thenExecute(() -> {
                var p = plan.get();
                h.assertTrue(p.patternTimes().size() == 1 && p.patternTimes().keySet().stream().allMatch(isChain),
                    "With logs and intermediates stored, AE2 must still push the chain once: " + p.patternTimes());
            })
            .thenSucceed();
    }

    /**
     * In-game report: an ordinary Singulo order whose items are in ME (seen through the interface) stalled after one
     * stack, "waiting for space". The ME view shows one slot per item type, so the android must be able to take more
     * than one stack from a single slot.
     */
    @GameTest(template = "empty", timeoutTicks = 4800)
    public static void singuloOrderTakesMoreThanAStackFromMe(GameTestHelper h) {
        var console = workshop(h, true);
        ((ChestBlockEntity) h.getBlockEntity(CHEST)).clearContent();
        BlockPos me1 = SUPPLY.above(), drive = me1.east(), power = drive.above();
        h.setBlock(power, appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(drive, appeng.core.definitions.AEBlocks.DRIVE.block());
        h.setBlock(me1, ModContent.ANDROID_ME_INTERFACE.get());
        var me = (AndroidMeInterfaceBlockEntity) h.getBlockEntity(me1);
        me.queue().setOwner(console.owner());
        var cells = h.getLevel().getBlockEntity(h.absolutePos(drive)).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        h.assertTrue(cells != null && cells.insertItem(0, appeng.core.definitions.AEItems.ITEM_CELL_1K.stack(), false).isEmpty(), "cell");
        UUID request = UUID.randomUUID();
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(me.mainNode().isActive(), "ME online"))
            .thenExecute(() -> {
                me.mainNode().getGrid().getStorageService().getInventory().insert(appeng.api.stacks.AEItemKey.of(Items.OAK_PLANKS), 200,
                    appeng.api.config.Actionable.MODULATE, appeng.api.networking.security.IActionSource.empty());
            })
            .thenWaitUntil(() -> h.assertTrue(console.stock().stream().filter(c -> c.item.is(Items.OAK_PLANKS)).mapToLong(c -> c.count).sum() == 200,
                "ME planks visible as console stock"))
            .thenExecute(() -> {
                var r = io.github.genichimaruo.singulo.api.android.AndroidOrders.submit(h.getLevel(), console.getBlockPos(), console.owner(),
                    request, new ItemStack(Items.STICK), 200, List.of(), null);   // 100 planks; a trip needs ~80 from one ME slot
                h.assertTrue(r.state() == io.github.genichimaruo.singulo.api.android.AndroidOrders.State.RUNNING, "Order: " + r.reason());
            })
            .thenWaitUntil(() -> {
                var r = io.github.genichimaruo.singulo.api.android.AndroidOrders.status(h.getLevel(), console.getBlockPos(), console.owner(), request);
                h.assertTrue(r.state() == io.github.genichimaruo.singulo.api.android.AndroidOrders.State.COMPLETED && r.delivered() == 200,
                    "Delivered " + r.delivered() + " state " + r.state() + " reason " + r.reason() + " status " + console.orders().stream().map(o -> o.status).toList());
            })
            .thenSucceed();
    }

    /**
     * The whole player path: an AE2 crafting job for 24 fences runs on a real crafting CPU, the CPU pushes the chain
     * pattern (logs only) to the interface, a real android crafts planks, sticks and fences in one Singulo order, and
     * the fences arrive in ME storage.
     */
    @GameTest(template = "empty", timeoutTicks = 6000)
    public static void ae2CraftingJobRunsTheChainAsOneOrder(GameTestHelper h) {
        var console = workshop(h, true);
        BlockPos mePos = new BlockPos(6, 1, 5), drive = mePos.above(), power = drive.above(), cpu = power.above();
        h.setBlock(cpu, appeng.core.definitions.AEBlocks.CRAFTING_STORAGE_1K.block());
        h.setBlock(power, appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(drive, appeng.core.definitions.AEBlocks.DRIVE.block());
        h.setBlock(mePos, ModContent.ANDROID_ME_INTERFACE.get());
        var me = (AndroidMeInterfaceBlockEntity) h.getBlockEntity(mePos);
        me.queue().setOwner(console.owner());
        me.queue().setConsole(console.getBlockPos());
        var cells = h.getLevel().getBlockEntity(h.absolutePos(drive)).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        h.assertTrue(cells != null && cells.insertItem(0, appeng.core.definitions.AEItems.ITEM_CELL_1K.stack(), false).isEmpty(), "cell");
        var fence = appeng.api.stacks.AEItemKey.of(Items.OAK_FENCE);
        var log = appeng.api.stacks.AEItemKey.of(Items.OAK_LOG);
        java.util.concurrent.Future<?>[] calc = { null };
        int[] ordersSeen = { 0 };
        int[] lastOrderId = { -1 };
        appeng.api.networking.crafting.ICraftingSimulationRequester requester =
            () -> appeng.api.networking.security.IActionSource.ofMachine(me);
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(me.mainNode().isActive() && me.patterns().size() == 5
                && !me.mainNode().getGrid().getCraftingService().getCpus().isEmpty()
                && !me.mainNode().getGrid().getCraftingService().getCraftables(k -> k.equals(fence)).isEmpty(),
                "Interface, patterns and crafting CPU online"))
            .thenExecute(() -> {
                me.mainNode().getGrid().getStorageService().getInventory().insert(log, 64,
                    appeng.api.config.Actionable.MODULATE, appeng.api.networking.security.IActionSource.empty());
                calc[0] = me.mainNode().getGrid().getCraftingService().beginCraftingCalculation(h.getLevel(), requester, fence, 24,
                    appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
            })
            .thenWaitUntil(() -> h.assertTrue(calc[0].isDone(), "calculating"))
            .thenExecute(() -> {
                appeng.api.networking.crafting.ICraftingPlan plan;
                try { plan = (appeng.api.networking.crafting.ICraftingPlan) calc[0].get(); }
                catch (Exception e) { throw new IllegalStateException(e); }
                h.assertTrue(plan.missingItems().isEmpty(), "Nothing missing");
                var result = me.mainNode().getGrid().getCraftingService().submitJob(plan, null, null, false,
                    appeng.api.networking.security.IActionSource.ofMachine(me));
                h.assertTrue(result.successful(), "Job submitted: " + result.errorCode());
            })
            .thenWaitUntil(() -> {
                for (var o : console.orders()) if (o.id != lastOrderId[0]) { lastOrderId[0] = o.id; ordersSeen[0]++; }
                long fences = me.mainNode().getGrid().getStorageService().getInventory().getAvailableStacks().get(fence);
                h.assertTrue(fences == 24, "Fences in ME: " + fences + ", interface jobs " + me.queue().jobs());
            })
            .thenExecute(() -> {
                long logs = me.mainNode().getGrid().getStorageService().getInventory().getAvailableStacks().get(log);
                h.assertTrue(logs == 54, "Exactly 10 logs used (5 logs -> 12 fences, twice), left " + logs);
                h.assertTrue(ordersSeen[0] == 1, "The whole job must be one Singulo order, saw " + ordersSeen[0]);
                h.assertTrue(me.queue().jobs().isEmpty(), "Interface done");
            })
            .thenSucceed();
    }

    /**
     * The interface as a cable panel facing the Supply Port: it joins the ME network through the cable, shares the
     * console's recipes, shows ME as Singulo storage only towards the block it faces, and its AE2 menu syncs a status.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void panelOnCableActsAsInterfaceAndOpensItsMenu(GameTestHelper h) {
        var console = workshop(h, false);
        ((ChestBlockEntity) h.getBlockEntity(CHEST)).clearContent();
        BlockPos bus = SUPPLY.above(), drive = bus.east(), power = drive.above();
        h.setBlock(power, appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(drive, appeng.core.definitions.AEBlocks.DRIVE.block());
        var player = h.makeMockServerPlayerInLevel();
        var level = h.getLevel();
        appeng.api.parts.PartHelper.setPart(level, h.absolutePos(bus), null, player,
            appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT));
        var panel = appeng.api.parts.PartHelper.setPart(level, h.absolutePos(bus), Direction.DOWN, player,
            ModContent.ANDROID_ME_INTERFACE_PANEL.get());
        h.assertTrue(panel != null, "Panel placed on the cable");
        panel.logic().queue().setOwner(console.owner());
        panel.logic().queue().setConsole(console.getBlockPos());
        var cells = level.getBlockEntity(h.absolutePos(drive)).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        h.assertTrue(cells != null && cells.insertItem(0, appeng.core.definitions.AEItems.ITEM_CELL_1K.stack(), false).isEmpty(), "cell");
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(panel.getMainNode().isActive() && panel.logic().patterns().size() == 5,
                "Panel online with the console's 3 recipes + 2 chains, got " + panel.logic().patterns().size()))
            .thenExecute(() -> panel.getMainNode().getGrid().getStorageService().getInventory().insert(
                appeng.api.stacks.AEItemKey.of(Items.COBBLESTONE), 40, appeng.api.config.Actionable.MODULATE,
                appeng.api.networking.security.IActionSource.empty()))
            .thenWaitUntil(() -> h.assertTrue(console.stock().stream().filter(c -> c.item.is(Items.COBBLESTONE))
                .mapToLong(c -> c.count).sum() == 40, "ME seen by the Supply Port through the panel"))
            .thenExecute(() -> {
                h.assertTrue(panel.logic().storageView(Direction.UP) == null, "Only the faced block may see ME");
                panel.setPriority(7);
                h.assertTrue(panel.getPriority() == 7, "Priority stored");
                // Opening sends a screen packet the GameTest mock player cannot receive; check the menu host and the synced value.
                var located = appeng.menu.locator.MenuLocators.forPart(panel)
                    .locate(player, io.github.genichimaruo.appliedsingulo.me.AndroidInterfaceHost.class);
                h.assertTrue(located == panel, "AE2 menu locator finds the panel as the menu host");
                var s = panel.logic().status();
                h.assertTrue(s.link() == InterfaceStatus.Link.ONLINE && s.priority() == 7 && s.patterns() == 3 && s.chainPatterns() == 2
                    && s.console().equals(console.getBlockPos().toShortString()), "Synced status: " + s);
                if (player.getServer().getPlayerList().getPlayer(player.getUUID()) == player) player.getServer().getPlayerList().remove(player);
                player.discard();
            })
            .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void interfaceStatusSurvivesTheNetwork(GameTestHelper h) {
        var status = new InterfaceStatus(InterfaceStatus.Link.NO_CHANNEL, -3, "1, 2, 3", "not_console_owner", 4, 2,
            List.of(new InterfaceStatus.Job("minecraft:oak_fence", 108, "RUNNING", true, 12, 9, 9, "")),
            1, 0, 5, 256, 2, 36, true, "hold_full", List.of(new InterfaceStatus.Push("minecraft:stick", true, 3)),
            List.of(new InterfaceStatus.Stack("minecraft:oak_fence", 72)),
            List.of(new InterfaceStatus.Stock("minecraft:stick", 64, 10, "crafting"), InterfaceStatus.Stock.EMPTY));
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        status.writeToPacket(buf);
        h.assertTrue(new InterfaceStatus(buf).equals(status) && buf.readableBytes() == 0, "Round trip");
        h.succeed();
    }

    /** The "waiting for ME" row of the menu: players can deposit (shift-click too) but not take; deposits reach ME. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void playersCanDepositIntoTheWaitingRowButNotTake(GameTestHelper h) {
        BlockPos mePos = new BlockPos(2, 1, 2), drive = mePos.above(), power = drive.above();
        h.setBlock(power, appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(drive, appeng.core.definitions.AEBlocks.DRIVE.block());
        h.setBlock(mePos, ModContent.ANDROID_ME_INTERFACE.get());
        var me = (AndroidMeInterfaceBlockEntity) h.getBlockEntity(mePos);
        var player = h.makeMockServerPlayerInLevel();
        me.queue().setOwner(player.getUUID());
        var cells = h.getLevel().getBlockEntity(h.absolutePos(drive)).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        h.assertTrue(cells != null && cells.insertItem(0, appeng.core.definitions.AEItems.ITEM_CELL_1K.stack(), false).isEmpty(), "cell");
        var menu = new io.github.genichimaruo.appliedsingulo.me.AndroidInterfaceMenu(1, player.getInventory(), me);
        var deposit = menu.getSlot(0);
        h.assertTrue(deposit.mayPlace(new ItemStack(Items.COBBLESTONE)) && !deposit.mayPickup(player), "Insert only");
        player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 20));
        int playerSlot = -1;
        for (int i = 0; i < menu.slots.size(); i++)
            if (menu.slots.get(i).container == player.getInventory() && menu.slots.get(i).getContainerSlot() == 0) playerSlot = i;
        menu.quickMoveStack(player, playerSlot);
        h.assertTrue(player.getInventory().getItem(0).isEmpty(), "Shift-click deposited the stack");
        h.assertTrue(me.queue().outputContents().stream().mapToInt(ItemStack::getCount).sum() == 20, "Deposit is in the buffer");
        h.succeedWhen(() -> {
            h.assertTrue(me.mainNode().isActive(), "ME online");
            long stored = me.mainNode().getGrid().getStorageService().getInventory().getAvailableStacks()
                .get(appeng.api.stacks.AEItemKey.of(Items.COBBLESTONE));
            h.assertTrue(stored == 20 && me.queue().outputContents().isEmpty(), "Deposit reached ME: " + stored);
            if (player.getServer().getPlayerList().getPlayer(player.getUUID()) == player) player.getServer().getPlayerList().remove(player);
            player.discard();
        });
    }

    /** Powered network with an interface block, a 1k cell drive and a cable bus for parts; returns the bus position. */
    private static BlockPos smallNetwork(GameTestHelper h, BlockPos mePos) {
        BlockPos drive = mePos.above(), power = drive.above(), bus = mePos.east();
        h.setBlock(power, appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(drive, appeng.core.definitions.AEBlocks.DRIVE.block());
        h.setBlock(mePos, ModContent.ANDROID_ME_INTERFACE.get());
        var cells = h.getLevel().getBlockEntity(h.absolutePos(drive)).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        h.assertTrue(cells != null && cells.insertItem(0, appeng.core.definitions.AEItems.ITEM_CELL_1K.stack(), false).isEmpty(), "cell");
        appeng.api.parts.PartHelper.setPart(h.getLevel(), h.absolutePos(bus), null, null,
            appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT));
        return bus;
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void statusEmitterFollowsTheNetworkOrders(GameTestHelper h) {
        BlockPos mePos = new BlockPos(2, 1, 2);
        var bus = smallNetwork(h, mePos);
        var me = (AndroidMeInterfaceBlockEntity) h.getBlockEntity(mePos);
        me.queue().setOwner(UUID.randomUUID());
        var emitter = appeng.api.parts.PartHelper.setPart(h.getLevel(), h.absolutePos(bus), Direction.UP, null,
            ModContent.ANDROID_STATUS_EMITTER.get());
        h.assertTrue(emitter != null && emitter.mode() == io.github.genichimaruo.appliedsingulo.me.AndroidStatusEmitterPart.Mode.STUCK, "Placed");
        h.startSequence()
            .thenWaitUntil(() -> {
                emitter.update();
                h.assertTrue(emitter.emitting() && emitter.isProvidingWeakPower() == 15, "No console set: stuck, emitting");
            })
            .thenExecute(() -> {
                me.queue().setConsole(h.absolutePos(new BlockPos(0, 1, 0)));
                emitter.update();
                h.assertFalse(emitter.emitting(), "Console set and no orders: not stuck");
            })
            .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void orderMonitorShowsTheCurrentOrder(GameTestHelper h) {
        BlockPos mePos = new BlockPos(2, 1, 2);
        var bus = smallNetwork(h, mePos);
        var me = (AndroidMeInterfaceBlockEntity) h.getBlockEntity(mePos);
        me.queue().setOwner(UUID.randomUUID());
        var monitor = appeng.api.parts.PartHelper.setPart(h.getLevel(), h.absolutePos(bus), Direction.UP, null,
            ModContent.ANDROID_ORDER_MONITOR.get());
        h.assertTrue(monitor != null, "Placed");
        h.assertTrue(me.queue().accept(new ItemStack(Items.OAK_PLANKS), 16, List.of(new ItemStack(Items.OAK_LOG, 4)),
            h.absolutePos(new BlockPos(0, 1, 0)), h.getLevel().getGameTime() + 100000), "push");   // stays batching
        h.succeedWhen(() -> {
            monitor.update();
            var shown = monitor.shown();
            h.assertTrue(shown.topItem().equals("minecraft:oak_planks") && shown.topCount() == 16 && shown.batching() == 1,
                "Monitor shows the batching order: " + shown);
        });
    }

    /** Keep in stock: with only logs in ME, the interface starts an AE2 job and the android makes planks until ME has 8. */
    @GameTest(template = "empty", timeoutTicks = 6000)
    public static void keepInStockOrdersFromTheAndroids(GameTestHelper h) {
        var console = workshop(h, true);
        BlockPos mePos = new BlockPos(6, 1, 5), drive = mePos.above(), power = drive.above(), cpu = power.above();
        h.setBlock(cpu, appeng.core.definitions.AEBlocks.CRAFTING_STORAGE_1K.block());
        h.setBlock(power, appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(drive, appeng.core.definitions.AEBlocks.DRIVE.block());
        h.setBlock(mePos, ModContent.ANDROID_ME_INTERFACE.get());
        var me = (AndroidMeInterfaceBlockEntity) h.getBlockEntity(mePos);
        me.queue().setOwner(console.owner());
        me.queue().setConsole(console.getBlockPos());
        var cells = h.getLevel().getBlockEntity(h.absolutePos(drive)).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        h.assertTrue(cells != null && cells.insertItem(0, appeng.core.definitions.AEItems.ITEM_CELL_1K.stack(), false).isEmpty(), "cell");
        var planks = appeng.api.stacks.AEItemKey.of(Items.OAK_PLANKS);
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(me.mainNode().isActive() && me.patterns().size() == 5
                && !me.mainNode().getGrid().getCraftingService().getCpus().isEmpty(), "Online"))
            .thenExecute(() -> {
                me.mainNode().getGrid().getStorageService().getInventory().insert(appeng.api.stacks.AEItemKey.of(Items.OAK_LOG), 16,
                    appeng.api.config.Actionable.MODULATE, appeng.api.networking.security.IActionSource.empty());
                me.logic().setStock(0, planks, 8);
            })
            .thenWaitUntil(() -> {
                long have = me.mainNode().getGrid().getStorageService().getInventory().getAvailableStacks().get(planks);
                h.assertTrue(have >= 8, "Planks kept in stock: " + have + " state " + me.logic().status().stock().get(0));
            })
            .thenExecute(() -> {
                var registries = h.getLevel().registryAccess();
                me.load(me.saveWithoutMetadata());
                h.assertTrue(planks.equals(me.logic().stockItem(0)) && me.logic().stockAmount(0) == 8, "Stock setting saved");
            })
            .thenSucceed();
    }

    /** Machine recipe through the interface queue: a real android smelts the batch in a (pre-fuelled) furnace. */
    @GameTest(template = "empty", timeoutTicks = 4800)
    public static void interfaceRunsMachineRecipesInPrivateCustody(GameTestHelper h) {
        var console = workshop(h, true);
        BlockPos furnacePos = new BlockPos(5, 1, 3);
        h.setBlock(furnacePos, Blocks.FURNACE);
        ((net.minecraft.world.level.block.entity.FurnaceBlockEntity) h.getBlockEntity(furnacePos)).setItem(1, new ItemStack(Items.COAL, 8));
        var card = new ItemStack(singulo("work_location_card"));
        WorkLocationCardItem.record(card, h.getLevel(), h.absolutePos(furnacePos), Blocks.FURNACE);
        RecipeRackBlockEntity rack = (RecipeRackBlockEntity) h.getBlockEntity(RACK);
        rack.items().setStackInSlot(RecipeRackBlockEntity.COLS, card);
        var recipe = new ItemStack(singulo("recipe_card"));
        List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        grid.set(0, new ItemStack(Items.RAW_IRON));
        RecipeData.processing(grid, new ItemStack(Items.IRON_INGOT), List.of(), ItemStack.EMPTY).write(recipe, h.getLevel().registryAccess());
        rack.items().setStackInSlot(RecipeRackBlockEntity.COLS + 1, recipe);
        var me = remoteInterface(h, console);
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(me.patterns().stream().anyMatch(p -> p.output().is(Items.IRON_INGOT)),
                "Furnace recipe shared as an AE2 pattern"))
            .thenExecute(() -> h.assertTrue(me.queue().accept(new ItemStack(Items.IRON_INGOT), 2, List.of(new ItemStack(Items.RAW_IRON, 2)),
                console.getBlockPos(), h.getLevel().getGameTime() - 200), "push"))
            .thenWaitUntil(() -> {
                h.assertTrue(me.queue().jobs().isEmpty(), "Open: " + me.queue().jobs());
                h.assertTrue(count(me.queue().outputContents(), Items.IRON_INGOT) == 2, "Ingots: " + me.queue().outputContents());
            })
            .thenSucceed();
    }

    /** AE2 fluid inputs become Singulo fluid samples, batch by amount, and claimed fluids go back into ME as fluids. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void fluidsTravelBetweenMeAndSinguloAsFluids(GameTestHelper h) {
        var waterKey = appeng.api.stacks.AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER);
        var counter = new appeng.api.stacks.KeyCounter();
        counter.add(waterKey, 1500);
        counter.add(appeng.api.stacks.AEItemKey.of(Items.COBBLESTONE), 2);
        var materials = io.github.genichimaruo.appliedsingulo.me.AndroidInterfaceLogic.materials(new appeng.api.stacks.KeyCounter[] { counter });
        h.assertTrue(materials != null && materials.stream().anyMatch(m ->
            io.github.genichimaruo.singulo.api.android.AndroidEscrows.fluidOf(m).getAmount() == 1500), "Fluid input as a 1500 mB sample");
        var queue = new AndroidOrderQueue(() -> { });
        queue.setOwner(UUID.randomUUID());
        var console = h.absolutePos(ME);
        h.assertTrue(queue.accept(new ItemStack(Items.STONE), 1, materials, console, 0), "first");
        h.assertTrue(queue.accept(new ItemStack(Items.STONE), 1, materials, console, 1), "second");
        long water = queue.heldContents().stream().mapToLong(m ->
            io.github.genichimaruo.singulo.api.android.AndroidEscrows.fluidOf(m).getAmount()).sum();
        h.assertTrue(water == 3000 && queue.heldContents().stream().filter(SinguloLinks::isFluid).count() == 1,
            "Batched fluids add up in one sample: " + water);

        BlockPos mePos = new BlockPos(2, 1, 2), drive = mePos.above(), power = drive.above();
        h.setBlock(power, appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(drive, appeng.core.definitions.AEBlocks.DRIVE.block());
        h.setBlock(mePos, ModContent.ANDROID_ME_INTERFACE.get());
        var me = (AndroidMeInterfaceBlockEntity) h.getBlockEntity(mePos);
        var cells = h.getLevel().getBlockEntity(h.absolutePos(drive)).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        h.assertTrue(cells != null && cells.insertItem(0, appeng.core.definitions.AEItems.FLUID_CELL_1K.stack(), false).isEmpty(), "fluid cell");
        h.succeedWhen(() -> {
            h.assertTrue(me.mainNode().isActive(), "ME online");
            // a claimed fluid refund, as the queue holds it, goes into ME as a fluid
            var tag = new net.minecraft.nbt.CompoundTag();
            var list = new net.minecraft.nbt.ListTag();
            list.add(io.github.genichimaruo.singulo.api.android.AndroidEscrows.fluidSample(
                new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 2000)).save(new net.minecraft.nbt.CompoundTag()));
            tag.put("fluidOutputs", list);
            if (me.mainNode().getGrid().getStorageService().getInventory().getAvailableStacks().get(waterKey) == 0)
                me.queue().load(tag);
            long stored = me.mainNode().getGrid().getStorageService().getInventory().getAvailableStacks().get(waterKey);
            h.assertTrue(stored == 2000, "Water in ME: " + stored);
        });
    }

    /** Breaking a device with a request in Singulo custody: cancelled, then its materials are dropped where it stood. */
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void brokenInterfaceRecoversEscrowedMaterialsAsDrops(GameTestHelper h) {
        var console = workshop(h, true);
        var me = remoteInterface(h, console);
        long now = h.getLevel().getGameTime();
        h.assertTrue(me.queue().accept(new ItemStack(Items.OAK_PLANKS), 4, List.of(new ItemStack(Items.OAK_LOG, 2)),
            console.getBlockPos(), now - 200), "push");
        h.runAfterDelay(2, () -> {
            h.assertTrue(me.queue().jobs().get(0).submitted(), "Submitted: " + me.queue().jobs());
            var request = me.queue().jobs().get(0).request;
            h.setBlock(REMOTE_ME, Blocks.AIR);
            var orphans = OrphanedEscrows.get(h.getLevel());
            h.assertTrue(orphans.size() >= 1, "Request handed to orphan recovery");
            h.succeedWhen(() -> {
                orphans.process(h.getLevel());
                var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(REMOTE_ME)).inflate(2));
                h.assertTrue(drops.stream().filter(e -> e.getItem().is(Items.OAK_LOG)).mapToInt(e -> e.getItem().getCount()).sum() == 2,
                    "Both escrowed logs must be dropped exactly once");
                h.assertTrue(AndroidEscrows.snapshot(h.getLevel(), console.owner(), request).phase() == AndroidEscrows.Phase.UNKNOWN,
                    "Recovered request must be acknowledged");
            });
        });
    }
}
