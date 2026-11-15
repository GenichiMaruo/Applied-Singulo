package io.github.genichimaruo.appliedsingulo.integration;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.genichimaruo.appliedsingulo.me.AndroidInterfaceHost;
import io.github.genichimaruo.singulo.api.android.AndroidOrders;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Player-authorized development path, not an AE2 crafting-material receiver. */
public final class AndroidOrderCommands {
    private AndroidOrderCommands() { }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("appliedsingulo")
            .then(Commands.literal("submit")
                .then(Commands.argument("console", BlockPosArgument.blockPos())
                    .then(Commands.argument("request", UuidArgument.uuid())
                        .then(Commands.argument("batches", IntegerArgumentType.integer(1, 4096))
                            .executes(AndroidOrderCommands::submit)))))
            .then(Commands.literal("status")
                .then(Commands.argument("console", BlockPosArgument.blockPos())
                    .then(Commands.argument("request", UuidArgument.uuid())
                        .executes(c -> action(c, "status")))))
            .then(Commands.literal("cancel")
                .then(Commands.argument("console", BlockPosArgument.blockPos())
                    .then(Commands.argument("request", UuidArgument.uuid())
                        .executes(c -> action(c, "cancel")))))
            .then(Commands.literal("acknowledge")
                .then(Commands.argument("console", BlockPosArgument.blockPos())
                    .then(Commands.argument("request", UuidArgument.uuid())
                        .executes(c -> action(c, "acknowledge")))))
            .then(Commands.literal("snapshot")
                .then(Commands.argument("console", BlockPosArgument.blockPos())
                    .executes(AndroidOrderCommands::snapshot)))
            .then(Commands.literal("interface")
                .then(Commands.argument("interface", BlockPosArgument.blockPos())
                    .then(Commands.literal("status").executes(c -> androidInterface(c, false)))
                    .then(Commands.literal("cancel").executes(c -> androidInterface(c, true)))))
            .then(Commands.literal("newid").executes(c -> {
                c.getSource().getPlayerOrException();
                c.getSource().sendSuccess(() -> Component.literal(UUID.randomUUID().toString()), false);
                return Command.SINGLE_SUCCESS;
            })));
    }

    private static BlockPos console(CommandContext<CommandSourceStack> context, ServerPlayer player)
            throws CommandSyntaxException {
        return nearby(context, player, "console");
    }

    private static BlockPos nearby(CommandContext<CommandSourceStack> context, ServerPlayer player, String argument)
            throws CommandSyntaxException {
        var pos = BlockPosArgument.getLoadedBlockPos(context, argument);
        // Keep this manual development surface local; it never loads a distant chunk.
        if (player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 64 * 64)
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                Component.literal("Target must be within 64 blocks")).create();
        return pos;
    }

    private static int submit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var pos = console(context, player);
        PatternOrderRequest request;
        try {
            request = PatternOrderRequest.decode(player.getMainHandItem(), player.serverLevel(),
                IntegerArgumentType.getInteger(context, "batches"));
        } catch (IllegalArgumentException invalid) {
            context.getSource().sendFailure(Component.literal(invalid.getMessage()));
            return 0;
        }
        var receipt = AndroidOrders.submit(player.serverLevel(), pos, player.getUUID(),
            UuidArgument.getUuid(context, "request"), request.target(), request.count(), List.of(), null);
        return show(context.getSource(), receipt);
    }

    private static int action(CommandContext<CommandSourceStack> context, String action)
            throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var pos = console(context, player);
        var id = UuidArgument.getUuid(context, "request");
        if (action.equals("acknowledge")) {
            boolean removed = AndroidOrders.acknowledge(player.serverLevel(), pos, player.getUUID(), id);
            if (removed) context.getSource().sendSuccess(() -> Component.literal(
                "Terminal receipt acknowledged. Never reuse this request UUID."), false);
            else context.getSource().sendFailure(Component.literal("Receipt not acknowledged"));
            return removed ? Command.SINGLE_SUCCESS : 0;
        }
        return show(context.getSource(), action.equals("cancel")
            ? AndroidOrders.cancel(player.serverLevel(), pos, player.getUUID(), id)
            : AndroidOrders.status(player.serverLevel(), pos, player.getUUID(), id));
    }

    /** Owner-only overview of every external receipt on a console (Singulo 0.18.0 snapshot API). */
    private static int snapshot(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var snap = AndroidOrders.snapshot(player.serverLevel(), console(context, player), player.getUUID());
        var source = context.getSource();
        if (snap.state() != AndroidOrders.SnapshotState.AVAILABLE) {
            source.sendFailure(Component.literal("Snapshot " + snap.state()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("active=" + snap.activeCount() + " history=" + snap.historyCount()
            + " capacity=" + snap.remainingReceiptCapacity() + "/" + snap.receiptCapacity()
            + " maxCount=" + snap.maxRequestCount()), false);
        for (var receipt : snap.receipts()) show(source, receipt);
        return Command.SINGLE_SUCCESS;
    }

    /** Android ME Interfaces at a position: the block, or every interface panel on a cable there. */
    private static java.util.List<AndroidInterfaceHost> interfacesAt(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
        var be = level.getBlockEntity(pos);
        if (be instanceof AndroidInterfaceHost host) return java.util.List.of(host);
        java.util.List<AndroidInterfaceHost> out = new java.util.ArrayList<>();
        if (be instanceof appeng.api.parts.IPartHost parts)
            for (var side : net.minecraft.core.Direction.values())
                if (parts.getPart(side) instanceof AndroidInterfaceHost host) out.add(host);
        return out;
    }

    private static int androidInterface(CommandContext<CommandSourceStack> context, boolean cancel) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var pos = nearby(context, player, "interface");
        var hosts = interfacesAt(player.serverLevel(), pos);
        if (hosts.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No Android ME Interface at " + pos.toShortString()));
            return 0;
        }
        for (var host : hosts) {
            if (!host.logic().mayUse(player)) {
                context.getSource().sendFailure(Component.translatable("message.applied_singulo.device.not_owner"));
                continue;
            }
            if (cancel) {
                int n = host.logic().cancelAll();
                context.getSource().sendSuccess(() -> Component.translatable("message.applied_singulo.device.cancelled", n), false);
            }
            for (var line : host.logic().describe()) context.getSource().sendSuccess(() -> line, false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int show(CommandSourceStack source, AndroidOrders.Receipt receipt) {
        var text = Component.literal(receipt.requestId() + " " + receipt.state()
            + " order=" + receipt.orderId() + " delivered=" + receipt.delivered() + "/" + receipt.requested()
            + (receipt.reason() == null ? "" : " reason=" + receipt.reason()));
        if (receipt.state() == AndroidOrders.State.REJECTED
                || receipt.state() == AndroidOrders.State.UNAVAILABLE
                || receipt.state() == AndroidOrders.State.UNKNOWN
                || receipt.state() == AndroidOrders.State.FAILED) {
            source.sendFailure(text);
            return 0;
        }
        source.sendSuccess(() -> text, false);
        return Command.SINGLE_SUCCESS;
    }
}
