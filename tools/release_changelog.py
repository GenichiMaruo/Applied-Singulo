"""Select release notes without mutating the exact tagged build source."""
import re
import subprocess
import sys
from pathlib import Path

RECOVERY_COMMIT = "de27b71587820d47df827c52488406d036109c03"


def select(version: str, commit: str, source: Path, publisher: Path) -> Path:
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise ValueError("Invalid release version")
    name = Path("docs/curseforge") / f"changelog-{version}.md"
    if (source / name).is_file():
        return source / name
    if version == "0.2.0" and commit == RECOVERY_COMMIT and (publisher / name).is_file():
        return publisher / name
    raise ValueError(f"Missing tagged changelog: {name}")


if __name__ == "__main__":
    version = sys.argv[1]
    source = Path("release-source")
    commit = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    print(select(version, commit, source, Path(".")).as_posix())
