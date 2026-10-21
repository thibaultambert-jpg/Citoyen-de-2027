from pathlib import Path

from citoyen_pipeline import testpack

COMMITTED = Path(__file__).resolve().parents[2] / "core" / "data" / "src" / "test" / "resources" / "packs"


def test_committed_test_pack_matches_the_pipeline(tmp_path):
    """The app's tests read this pack: any pack format change must regenerate it (`make test-pack`)."""
    testpack.build(tmp_path)
    for name in ("core.sqlite", "manifest.json", "manifest.json.sig", "test_public_key.txt"):
        assert (tmp_path / name).read_bytes() == (COMMITTED / name).read_bytes(), name
