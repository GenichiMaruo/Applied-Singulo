package io.github.genichimaruo.appliedsingulo.registry;

import appeng.capabilities.Capabilities;
import appeng.api.parts.PartModels;
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
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public final class ModContent {
    private ModContent() { }

    private static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, AppliedSingulo.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, AppliedSingulo.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AppliedSingulo.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, AppliedSingulo.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AppliedSingulo.MOD_ID);

    private static BlockBehaviour.Properties machine() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5F, 6F).sound(SoundType.METAL);
    }

    public static final RegistryObject<AndroidMeInterfaceBlock> ANDROID_ME_INTERFACE = BLOCKS.register(
        "android_me_interface", () -> new AndroidMeInterfaceBlock(machine()));
    public static final RegistryObject<BlockItem> ANDROID_ME_INTERFACE_ITEM =
        ITEMS.register("android_me_interface", () -> new BlockItem(ANDROID_ME_INTERFACE.get(), new Item.Properties()));

    /** The interface as an AE2 cable panel (part). */
    public static final RegistryObject<PartItem<AndroidInterfacePart>> ANDROID_ME_INTERFACE_PANEL = ITEMS.register(
        "android_me_interface_panel", () -> new PartItem<>(new Item.Properties(), AndroidInterfacePart.class, AndroidInterfacePart::new));

    /** Redstone signal from the order state of the network's Android ME Interfaces. */
    public static final RegistryObject<PartItem<AndroidStatusEmitterPart>> ANDROID_STATUS_EMITTER = ITEMS.register(
        "android_status_emitter", () -> new PartItem<>(new Item.Properties(), AndroidStatusEmitterPart.class, AndroidStatusEmitterPart::new));

    /** Shows the network's current Android order on a cable panel. */
    public static final RegistryObject<PartItem<AndroidOrderMonitorPart>> ANDROID_ORDER_MONITOR = ITEMS.register(
        "android_order_monitor", () -> new PartItem<>(new Item.Properties(), AndroidOrderMonitorPart.class, AndroidOrderMonitorPart::new));

    /** Hidden definition item of auto-generated AE2 patterns; never obtainable in survival. */
    public static final RegistryObject<Item> ANDROID_RECIPE_PATTERN =
        ITEMS.register("android_recipe_pattern", () -> new Item(new Item.Properties().stacksTo(1)));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<AndroidMeInterfaceBlockEntity>> ANDROID_ME_INTERFACE_ENTITY =
        BLOCK_ENTITIES.register("android_me_interface", () -> BlockEntityType.Builder
            .of(AndroidMeInterfaceBlockEntity::new, ANDROID_ME_INTERFACE.get()).build(null));

    public static final RegistryObject<MenuType<AndroidInterfaceMenu>> ANDROID_INTERFACE_MENU = MENUS.register(
        "android_interface", AndroidInterfaceMenu::createType);

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () ->
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
    }
}
