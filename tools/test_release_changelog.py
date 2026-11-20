from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from release_changelog import RECOVERY_COMMIT, select


class ReleaseChangelogTests(unittest.TestCase):
    def setUp(self):
        tmp = TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        self.source = self.root / "release-source"
        self.publisher = self.root / "publisher"

    def notes(self, root, version):
        note = root / "docs/curseforge" / f"changelog-{version}.md"
        note.parent.mkdir(parents=True, exist_ok=True)
        note.write_text("reviewed notes")
        return note

    def test_tagged_notes_always_precede_fallback(self):
        note = self.notes(self.source, "0.2.0")
        self.notes(self.publisher, "0.2.0")
        self.assertEqual(select("0.2.0", RECOVERY_COMMIT, self.source, self.publisher), note)
        future = self.notes(self.source, "0.2.1")
        self.assertEqual(select("0.2.1", "a" * 40, self.source, self.publisher), future)

    def test_exact_recovery(self):
        note = self.notes(self.publisher, "0.2.0")
        self.assertEqual(select("0.2.0", RECOVERY_COMMIT, self.source, self.publisher), note)
        self.assertFalse((self.source / "docs").exists())

    def test_recovery_rejects_wrong_commit_future_version_or_missing_notes(self):
        self.notes(self.publisher, "0.2.0")
        self.notes(self.publisher, "0.2.1")
        for version, commit in (("0.2.0", "a" * 40), ("0.2.1", RECOVERY_COMMIT)):
            with self.subTest(version=version, commit=commit), self.assertRaises(ValueError):
                select(version, commit, self.source, self.publisher)
        (self.publisher / "docs/curseforge/changelog-0.2.0.md").unlink()
        with self.assertRaises(ValueError):
            select("0.2.0", RECOVERY_COMMIT, self.source, self.publisher)
