package io.github.genichimaruo.appliedsingulo.me;

import io.github.genichimaruo.appliedsingulo.registry.ModContent;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
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

    public AndroidMeInterfaceBlock(Properties properties) { super(properties); }


    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AndroidMeInterfaceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModContent.ANDROID_ME_INTERFACE_ENTITY.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<AndroidMeInterfaceBlockEntity>) AndroidMeInterfaceBlockEntity::serverTick;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof AndroidMeInterfaceBlockEntity be)
            be.onPlaced(player);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof AndroidMeInterfaceBlockEntity cardHost
                && cardHost.logic().useCard(player.getItemInHand(hand), player))
            return InteractionResult.sidedSuccess(level.isClientSide);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof AndroidMeInterfaceBlockEntity be) {
            if (!be.logic().mayUse(player)) {
                player.displayClientMessage(Component.translatable("message.applied_singulo.device.not_owner"), true);
            } else {
                if (be.queue().owner() == null) be.logic().setOwningPlayer(player);   // placed by a machine: first user claims it
                AndroidInterfaceMenu.open(player, appeng.menu.locator.MenuLocators.forBlockEntity(be));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof AndroidMeInterfaceBlockEntity be)
            for (var drop : be.logic().breakDown()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), drop);
        super.onRemove(state, level, pos, newState, moving);
    }
}
