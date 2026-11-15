package io.github.genichimaruo.appliedsingulo.me;

import appeng.menu.guisync.PacketWritable;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Everything the status screen shows, built on the server and synchronised as one value (AE2 {@code @GuiSync}).
 * Items are sent by registry id (records of ItemStack would compare by identity and resend every tick).
 */
public record InterfaceStatus(Link link, int priority, String console, String consoleProblem,
                              int patterns, int chainPatterns, List<Job> jobs, int running, int batching,
                              int held, int heldMax, int outputs, int outputsMax, boolean outputBlocked,
                              String problem, List<Push> recent, List<Stack> waiting,
                              List<Stock> stock) implements PacketWritable {

    /** ME network state of the host's main node. */
    public enum Link { ONLINE, NO_CHANNEL, OFFLINE }

    /** One Singulo escrow request (open batch or submitted). */
    public record Job(String item, long count, String state, boolean submitted, long delivered, long claimed,
                      int pushes, String reason) { }

    /** AE2 pushes since load, per product and kind (chain = raw materials to product in one order). */
    public record Push(String item, boolean chain, int count) { }

    /** Products or refunds waiting to be inserted into ME. */
    public record Stack(String item, long count) { }

    /** A keep-in-stock slot: item ("" = empty), target, amount stored in ME, state code. */
    public record Stock(String item, long target, long stored, String state) {
        public static final Stock EMPTY = new Stock("", 0, 0, "");
    }

    public static final InterfaceStatus EMPTY = new InterfaceStatus(Link.OFFLINE, 0, "", "", 0, 0, List.of(), 0, 0,
        0, 0, 0, 0, false, "", List.of(), List.of(), List.of());

    public InterfaceStatus {
        jobs = List.copyOf(jobs);
        recent = List.copyOf(recent);
        waiting = List.copyOf(waiting);
        stock = List.copyOf(stock);
    }

    public InterfaceStatus(FriendlyByteBuf buf) {
        this(buf.readEnum(Link.class), buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
            readJobs(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
            buf.readVarInt(), buf.readBoolean(), buf.readUtf(), readPushes(buf), readStacks(buf), readStock(buf));
    }

    @Override
    public void writeToPacket(FriendlyByteBuf buf) {
        buf.writeEnum(link);
        buf.writeVarInt(priority);
        buf.writeUtf(console);
        buf.writeUtf(consoleProblem);
        buf.writeVarInt(patterns);
        buf.writeVarInt(chainPatterns);
        buf.writeVarInt(jobs.size());
        for (var j : jobs) {
            buf.writeUtf(j.item);
            buf.writeVarLong(j.count);
            buf.writeUtf(j.state);
            buf.writeBoolean(j.submitted);
            buf.writeVarLong(j.delivered);
            buf.writeVarLong(j.claimed);
            buf.writeVarInt(j.pushes);
            buf.writeUtf(j.reason);
        }
        buf.writeVarInt(running);
        buf.writeVarInt(batching);
        buf.writeVarInt(held);
        buf.writeVarInt(heldMax);
        buf.writeVarInt(outputs);
        buf.writeVarInt(outputsMax);
        buf.writeBoolean(outputBlocked);
        buf.writeUtf(problem);
        buf.writeVarInt(recent.size());
        for (var p : recent) {
            buf.writeUtf(p.item);
            buf.writeBoolean(p.chain);
            buf.writeVarInt(p.count);
        }
        buf.writeVarInt(waiting.size());
        for (var w : waiting) {
            buf.writeUtf(w.item);
            buf.writeVarLong(w.count);
        }
        buf.writeVarInt(stock.size());
        for (var k : stock) {
            buf.writeUtf(k.item);
            buf.writeVarLong(k.target);
            buf.writeVarLong(k.stored);
            buf.writeUtf(k.state);
        }
    }

    private static List<Job> readJobs(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Job> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            out.add(new Job(buf.readUtf(), buf.readVarLong(), buf.readUtf(), buf.readBoolean(), buf.readVarLong(),
                buf.readVarLong(), buf.readVarInt(), buf.readUtf()));
        return out;
    }

    private static List<Stock> readStock(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Stock> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(new Stock(buf.readUtf(), buf.readVarLong(), buf.readVarLong(), buf.readUtf()));
        return out;
    }

    private static List<Stack> readStacks(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Stack> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(new Stack(buf.readUtf(), buf.readVarLong()));
        return out;
    }

    private static List<Push> readPushes(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Push> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(new Push(buf.readUtf(), buf.readBoolean(), buf.readVarInt()));
        return out;
    }
}
