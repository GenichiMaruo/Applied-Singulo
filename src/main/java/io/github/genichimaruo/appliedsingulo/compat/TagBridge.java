package io.github.genichimaruo.appliedsingulo.compat;

import com.mojang.serialization.*;
import java.util.stream.Stream;
import net.minecraft.nbt.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.*;

/** Root-map bridge preserving the existing escrow schema across the ValueInput migration. */
public final class TagBridge {
    private TagBridge() {}
    public static final MapCodec<CompoundTag> ROOT = new MapCodec<>() {
        @Override public <T> Stream<T> keys(DynamicOps<T> ops) { return Stream.empty(); }
        @Override public <T> DataResult<CompoundTag> decode(DynamicOps<T> ops, MapLike<T> input) {
            return CompoundTag.CODEC.parse(ops, ops.createMap(input.entries()));
        }
        @Override public <T> RecordBuilder<T> encode(CompoundTag input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            input.forEach((key, value) -> prefix.add(key, NbtOps.INSTANCE.convertTo(ops, value)));
            return prefix;
        }
    };
    public static ValueInput input(CompoundTag tag, HolderLookup.Provider lookup) {
        return TagValueInput.create(ProblemReporter.DISCARDING, lookup, tag);
    }
    public static TagValueOutput output(HolderLookup.Provider lookup) {
        return TagValueOutput.createWithContext(ProblemReporter.DISCARDING, lookup);
    }
}
