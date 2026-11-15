package io.github.genichimaruo.appliedsingulo.gametest;

import io.github.genichimaruo.appliedsingulo.order.OrphanedEscrows;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;

/** Uses isolated files, never the running world's SavedData cache or escrow custody. */
public final class MigrationGameTests {
    @GameTest(template = "empty")
    public static void legacyEscrowImportPreservesBackupAndRejectsReplay(GameTestHelper h) {
        Path temp = null;
        try {
            temp = Files.createTempDirectory("applied-escrow-migration-");
            var entry = new CompoundTag();
            var owner = UUID.randomUUID();
            var request = UUID.randomUUID();
            entry.store("owner", UUIDUtil.CODEC, owner);
            entry.store("request", UUIDUtil.CODEC, request);
            entry.putLong("pos", 123L);
            var entries = new ListTag();
            entries.add(entry);
            var data = new CompoundTag();
            data.put("entries", entries);
            var root = new CompoundTag();
            root.put("data", data);
            Path legacy = temp.resolve("applied_singulo_orphaned_escrows.dat");
            NbtIo.writeCompressed(root, legacy);
            var original = Files.readAllBytes(legacy);
            var method = OrphanedEscrows.class.getDeclaredMethod("migrateOrCreate", Path.class, HolderLookup.Provider.class);
            method.setAccessible(true);
            var imported = (OrphanedEscrows) method.invoke(null, temp, h.getLevel().registryAccess());
            h.assertTrue(imported.size() == 1 && imported.isDirty(), "Legacy custody imported and scheduled for new-format save");
            var restored = imported.save(new CompoundTag(), h.getLevel().registryAccess()).getListOrEmpty("entries").getCompound(0).orElseThrow();
            h.assertTrue(restored.read("owner", UUIDUtil.CODEC).orElseThrow().equals(owner)
                && restored.read("request", UUIDUtil.CODEC).orElseThrow().equals(request)
                && restored.getLongOr("pos", -1L) == 123L, "Custody identities and position preserved");
            h.assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(legacy)), "Legacy backup remains byte-identical");
            Path current = Identifier.fromNamespaceAndPath("applied_singulo", "orphaned_escrows.dat").resolveAgainst(temp);
            Files.createDirectories(current.getParent());
            Files.write(current, new byte[] {1, 2, 3});
            boolean rejected = false;
            try { method.invoke(null, temp, h.getLevel().registryAccess()); }
            catch (java.lang.reflect.InvocationTargetException failure) { rejected = failure.getCause() instanceof IllegalStateException; }
            h.assertTrue(rejected, "Existing undecodable new save must never replay legacy custody");
            h.assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(legacy)), "Rejected import leaves backup unchanged");
            h.succeed();
        } catch (Exception failure) { throw new AssertionError("Isolated migration fixture failed", failure); }
        finally {
            if (temp != null) try (var files = Files.walk(temp)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file);
            } catch (java.io.IOException failure) { throw new AssertionError("Cannot clean isolated migration fixture", failure); }
        }
    }
}
