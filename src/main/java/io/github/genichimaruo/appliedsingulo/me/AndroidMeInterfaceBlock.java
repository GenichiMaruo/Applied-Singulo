package io.github.genichimaruo.appliedsingulo.me;

import com.mojang.serialization.MapCodec;
import io.github.genichimaruo.appliedsingulo.registry.ModContent;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Android ME Interface block: placement sets the owner (and an adjacent owned Work Console), a Work Location Card
 * points it at a console, using it opens the AE2-style status screen.
 */
public final class AndroidMeInterfaceBlock extends Block implements EntityBlock {
    public static final MapCodec<AndroidMeInterfaceBlock> CODEC = simpleCodec(AndroidMeInterfaceBlock::new);

    public AndroidMeInterfaceBlock(Properties properties) { super(properties); }

    @Override protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AndroidMeInterfaceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModContent.ANDROID_ME_INTERFACE_ENTITY.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<AndroidMeInterfaceBlockEntity>) AndroidMeInterfaceBlockEntity::serverTick;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player && level.getBlockEntity(pos) instanceof AndroidMeInterfaceBlockEntity be)
            be.onPlaced(player);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof AndroidMeInterfaceBlockEntity be && be.logic().useCard(stack, player))
            return InteractionResult.SUCCESS;
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AndroidMeInterfaceBlockEntity be) {
            if (!be.logic().mayUse(player)) {
                player.sendOverlayMessage(Component.translatable("message.applied_singulo.device.not_owner"));
            } else {
                if (be.queue().owner() == null) be.logic().setOwningPlayer(player);   // placed by a machine: first user claims it
                AndroidInterfaceMenu.open(player, appeng.menu.locator.MenuLocators.forBlockEntity(be));
            }
        }
        return InteractionResult.SUCCESS;
    }

}
