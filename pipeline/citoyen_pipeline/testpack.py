"""Fictional, signed `core` pack used by the app's tests as a cross-language contract.

The signing key is derived from a fixed public seed: it is a TEST key, worthless by design, and the
app only trusts it inside unit tests. Never use it to sign a published pack.
"""

from __future__ import annotations

import base64
from datetime import date
from pathlib import Path

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from citoyen_pipeline.build import write_core_pack
from citoyen_pipeline.coverage import compute_coverage
from citoyen_pipeline.inputs import load_candidates, load_identities
from citoyen_pipeline.model import Body, CorePack, Mandate, Source, Vote
from citoyen_pipeline.sign import build_manifest, pack_entry, public_key_b64, sign

TEST_SEED = bytes(range(32))
GENERATED_ON = date(2026, 1, 15)
VERSION = "2026-01-15+fixture"
FIXTURES = Path(__file__).resolve().parent.parent / "tests" / "fixtures"


def test_key() -> Ed25519PrivateKey:
    return Ed25519PrivateKey.from_private_bytes(TEST_SEED)


def build(out: Path) -> dict[str, str]:
    policy, candidates, sources = load_candidates(FIXTURES / "candidates.yaml")
    identities = load_identities(FIXTURES / "identities.yaml", {c.id for c in candidates})
    fake = Source(
        "fictif-source",
        "Source fictive",
        "https://example.invalid/source",
        date(2026, 1, 1),
        "Licence fictive",
    )
    mandates = [
        Mandate("fictif-a-mandat-1", "fictif-a", Body.AN, "Mandat fictif", date(2024, 7, 1), None, fake.id)
    ]
    votes = [
        Vote(
            "fictif-a-vote-1",
            "fictif-a",
            Body.AN,
            "scrutin-fictif-1",
            date(2025, 3, 4),
            "Scrutin fictif n° 1",
            "Type fictif",
            "pour",
            "https://example.invalid/scrutin-1",
            fake.id,
            ballot_subject="AMENDMENT",
        )
    ]
    pack = CorePack(
        GENERATED_ON,
        policy,
        sources + [fake],
        candidates,
        compute_coverage(candidates, identities, mandates, votes),
        mandates,
        votes,
    )
    out.mkdir(parents=True, exist_ok=True)
    pack_file = out / "core.sqlite"
    write_core_pack(pack, VERSION, pack_file)
    manifest = build_manifest(
        [pack_entry("core", VERSION, pack_file, [], ["Licence fictive"])], GENERATED_ON.isoformat()
    )
    (out / "manifest.json").write_bytes(manifest)
    (out / "manifest.json.sig").write_bytes(sign(manifest, test_key()))
    (out / "test_public_key.txt").write_text(public_key_b64(test_key()) + "\n", encoding="ascii")
    return {"public_key": public_key_b64(test_key()), "seed_b64": base64.b64encode(TEST_SEED).decode()}
