import json
import sqlite3
from pathlib import Path

from citoyen_pipeline import testpack
from citoyen_pipeline.sign import public_key_b64, sha256_file, verify

COMMITTED = Path(__file__).resolve().parents[2] / "core" / "data" / "src" / "test" / "resources" / "packs"


def _dump(path: Path) -> list[str]:
    db = sqlite3.connect(path)
    try:
        return list(db.iterdump())
    finally:
        db.close()


def test_committed_test_pack_matches_the_pipeline(tmp_path):
    """The app's tests read this pack: a pack format change must regenerate it (`make test-pack`).

    Contents are compared row by row: the SQLite file header embeds the SQLite library version, so
    bytes legitimately differ between machines.
    """
    testpack.build(tmp_path)
    manifest = (COMMITTED / "manifest.json").read_bytes()
    entries = json.loads(manifest)["packs"]
    regenerated = json.loads((tmp_path / "manifest.json").read_bytes())["packs"]
    assert [e["file"] for e in entries] == ["core.sqlite", "legal.sqlite", "programs.sqlite"]
    for entry, fresh in zip(entries, regenerated, strict=True):
        file = entry["file"]
        assert _dump(COMMITTED / file) == _dump(tmp_path / file)
        assert entry["sha256"] == sha256_file(COMMITTED / file)
        assert entry["size"] == (COMMITTED / file).stat().st_size
        for key in ("sha256", "size"):
            entry.pop(key)
            fresh.pop(key)
        assert entry == fresh
    verify(manifest, (COMMITTED / "manifest.json.sig").read_bytes(), public_key_b64(testpack.test_key()))
    assert (COMMITTED / "test_public_key.txt").read_text().strip() == public_key_b64(testpack.test_key())
