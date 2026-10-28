import sqlite3
from datetime import date

import pytest
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from citoyen_pipeline import PACK_SCHEMA_VERSION
from citoyen_pipeline.build import write_core_pack
from citoyen_pipeline.coverage import compute_coverage
from citoyen_pipeline.inputs import load_candidates
from citoyen_pipeline.model import CorePack, TagConfidence, TagMethod, ThemeTag
from citoyen_pipeline.sign import build_manifest, pack_entry, public_key_b64, sha256_file, sign, verify
from citoyen_pipeline.themes import load_taxonomy


def make_pack(fixtures) -> CorePack:
    policy, candidates, sources = load_candidates(fixtures / "candidates.yaml")
    themes = load_taxonomy(fixtures / "themes.yaml").all_themes
    return CorePack(
        date(2026, 10, 1),
        policy,
        sources,
        candidates,
        compute_coverage(candidates, [], [], []),
        [],
        [],
        themes=themes,
    )


def test_tags_of_unknown_votes_or_themes_are_refused(fixtures, tmp_path):
    pack = make_pack(fixtures)
    pack.vote_themes = {"absent": [ThemeTag("fictif-01", TagMethod.RULE, TagConfidence.LOW)]}
    with pytest.raises(ValueError, match="unknown item"):
        write_core_pack(pack, "v", tmp_path / "core.sqlite")


def test_pack_contains_meta_candidates_and_coverage(fixtures, tmp_path):
    path = tmp_path / "core.sqlite"
    write_core_pack(make_pack(fixtures), "2026-10-01+test", path)
    db = sqlite3.connect(path)
    meta = dict(db.execute("SELECT key, value FROM meta"))
    assert meta["schema_version"] == str(PACK_SCHEMA_VERSION)
    assert meta["pack_id"] == "core"
    assert db.execute("SELECT COUNT(*) FROM candidates").fetchone()[0] == 3
    assert db.execute("SELECT COUNT(*) FROM candidate_coverage").fetchone()[0] == 12
    assert db.execute("SELECT COUNT(*) FROM sources").fetchone()[0] == 3
    assert db.execute("SELECT COUNT(*) FROM themes").fetchone()[0] == 16


def test_build_is_reproducible(fixtures, tmp_path):
    first, second = tmp_path / "1.sqlite", tmp_path / "2.sqlite"
    write_core_pack(make_pack(fixtures), "v", first)
    write_core_pack(make_pack(fixtures), "v", second)
    assert sha256_file(first) == sha256_file(second)


def test_manifest_signature_round_trip_and_tamper_detection(fixtures, tmp_path):
    path = tmp_path / "core.sqlite"
    write_core_pack(make_pack(fixtures), "v", path)
    key = Ed25519PrivateKey.generate()
    manifest = build_manifest([pack_entry("core", "v", path, [], ["Licence fictive"])], "2026-10-01")
    signature = sign(manifest, key)
    verify(manifest, signature, public_key_b64(key))
    with pytest.raises(InvalidSignature):
        verify(manifest.replace(b'"v"', b'"w"'), signature, public_key_b64(key))
