package io.github.genichimaruo.appliedsingulo.gametest;

import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;

/** Test-only fixtures; cleanup runs on success, failure and timeout. */
final class LegacyTestPlayers {
    @SuppressWarnings("removal")
    static ServerPlayer create(GameTestHelper h) {
        var level = h.getLevel();
        var player = new ServerPlayer(level.getServer(), level,
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "addon-test"));
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        var channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
        try {
            onFinish(h, () -> {
                try {
                    if (level.getServer().getPlayerList().getPlayers().contains(player))
                        level.getServer().getPlayerList().remove(player);
                    player.discard();
                } finally { channel.finishAndReleaseAll(); }
            });
        } catch (RuntimeException e) { channel.finishAndReleaseAll(); throw e; }
        level.getServer().getPlayerList().placeNewPlayer(connection, player);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        var pos = h.absolutePos(net.minecraft.core.BlockPos.ZERO);
        player.teleportTo(level, pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        return player;
    }

    static void onFinish(GameTestHelper h, Runnable cleanup) {
        listen(h, new GameTestListener() {
            boolean closed;
            private void close() { if (!closed) { closed = true; cleanup.run(); } }
            public void testStructureLoaded(GameTestInfo test) { }
            public void testPassed(GameTestInfo test) { close(); }
            public void testFailed(GameTestInfo test) { close(); }
        });
    }

    @SuppressWarnings("unchecked")
    private static void listen(GameTestHelper h, GameTestListener listener) {
        try {
            var infoField = java.util.Arrays.stream(GameTestHelper.class.getDeclaredFields())
                .filter(f -> f.getType() == GameTestInfo.class).findFirst().orElseThrow();
            infoField.setAccessible(true);
            var info = (GameTestInfo) infoField.get(h);
            var listenersField = java.util.Arrays.stream(GameTestInfo.class.getDeclaredFields())
                .filter(f -> f.getGenericType().getTypeName().contains("GameTestListener")).findFirst().orElseThrow();
            listenersField.setAccessible(true);
            ((java.util.List<GameTestListener>) listenersField.get(info)).add(0, listener);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
}
