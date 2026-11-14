package io.github.genichimaruo.appliedsingulo.registry;

import appeng.api.AECapabilities;
import appeng.api.parts.PartModels;
import appeng.api.parts.RegisterPartCapabilitiesEvent;
import appeng.items.parts.PartItem;
import appeng.menu.implementations.MenuTypeBuilder;
import io.github.genichimaruo.appliedsingulo.AppliedSingulo;
import io.github.genichimaruo.appliedsingulo.me.AndroidInterfaceHost;
import io.github.genichimaruo.appliedsingulo.me.AndroidInterfaceMenu;
import io.github.genichimaruo.appliedsingulo.me.AndroidInterfacePart;
import io.github.genichimaruo.appliedsingulo.me.AndroidMeInterfaceBlock;
import io.github.genichimaruo.appliedsingulo.me.AndroidMeInterfaceBlockEntity;
import io.github.genichimaruo.appliedsingulo.me.AndroidOrderMonitorPart;
import io.github.genichimaruo.appliedsingulo.me.AndroidStatusEmitterPart;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModContent {
    private ModContent() { }

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AppliedSingulo.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AppliedSingulo.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AppliedSingulo.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, AppliedSingulo.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AppliedSingulo.MOD_ID);

    private static BlockBehaviour.Properties machine() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5F, 6F).sound(SoundType.METAL);
    }

    public static final DeferredBlock<AndroidMeInterfaceBlock> ANDROID_ME_INTERFACE = BLOCKS.register(
        "android_me_interface", () -> new AndroidMeInterfaceBlock(machine()));
    public static final DeferredItem<BlockItem> ANDROID_ME_INTERFACE_ITEM =
        ITEMS.registerSimpleBlockItem(ANDROID_ME_INTERFACE, new Item.Properties());

    /** The interface as an AE2 cable panel (part). */
    public static final DeferredItem<PartItem<AndroidInterfacePart>> ANDROID_ME_INTERFACE_PANEL = ITEMS.register(
        "android_me_interface_panel", () -> new PartItem<>(new Item.Properties(), AndroidInterfacePart.class, AndroidInterfacePart::new));

    /** Redstone signal from the order state of the network's Android ME Interfaces. */
    public static final DeferredItem<PartItem<AndroidStatusEmitterPart>> ANDROID_STATUS_EMITTER = ITEMS.register(
        "android_status_emitter", () -> new PartItem<>(new Item.Properties(), AndroidStatusEmitterPart.class, AndroidStatusEmitterPart::new));

    /** Shows the network's current Android order on a cable panel. */
    public static final DeferredItem<PartItem<AndroidOrderMonitorPart>> ANDROID_ORDER_MONITOR = ITEMS.register(
        "android_order_monitor", () -> new PartItem<>(new Item.Properties(), AndroidOrderMonitorPart.class, AndroidOrderMonitorPart::new));

    /** Hidden definition item of auto-generated AE2 patterns; never obtainable in survival. */
    public static final DeferredItem<Item> ANDROID_RECIPE_PATTERN =
        ITEMS.registerSimpleItem("android_recipe_pattern", new Item.Properties().stacksTo(1));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AndroidMeInterfaceBlockEntity>> ANDROID_ME_INTERFACE_ENTITY =
        BLOCK_ENTITIES.register("android_me_interface", () -> BlockEntityType.Builder
            .of(AndroidMeInterfaceBlockEntity::new, ANDROID_ME_INTERFACE.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<AndroidInterfaceMenu>> ANDROID_INTERFACE_MENU = MENUS.register(
        "android_interface", () -> MenuTypeBuilder.create(AndroidInterfaceMenu::new, AndroidInterfaceHost.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(AppliedSingulo.MOD_ID, "android_interface")));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.applied_singulo"))
            .icon(() -> ANDROID_ME_INTERFACE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ANDROID_ME_INTERFACE_ITEM.get());
                output.accept(ANDROID_ME_INTERFACE_PANEL.get());
                output.accept(ANDROID_STATUS_EMITTER.get());
                output.accept(ANDROID_ORDER_MONITOR.get());
            })
            .build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
        PartModels.registerModels(AndroidInterfacePart.MODELS);
        PartModels.registerModels(AndroidStatusEmitterPart.MODELS);
        PartModels.registerModels(AndroidOrderMonitorPart.MODELS);
        modBus.addListener(ModContent::registerCapabilities);
        modBus.addListener(ModContent::registerPartCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, ANDROID_ME_INTERFACE_ENTITY.get(),
            (me, context) -> me);
        // The ME network as Singulo storage (only towards the owner's Supply Ports / Work Console).
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ANDROID_ME_INTERFACE_ENTITY.get(),
            AndroidMeInterfaceBlockEntity::storageView);
    }

    private static void registerPartCapabilities(RegisterPartCapabilitiesEvent event) {
        event.register(Capabilities.ItemHandler.BLOCK, (part, side) -> part.storageView(), AndroidInterfacePart.class);
    }
}
