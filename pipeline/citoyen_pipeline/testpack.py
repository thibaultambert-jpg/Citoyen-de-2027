"""Fictional, signed `core` and `programs` packs used by the app's tests as a cross-language contract.

The signing key is derived from a fixed public seed: it is a TEST key, worthless by design, and the
app only trusts it inside unit tests. Never use it to sign a published pack.
"""

from __future__ import annotations

import base64
from datetime import date
from pathlib import Path

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from citoyen_pipeline.build import write_core_pack, write_legal_pack, write_programs_pack
from citoyen_pipeline.coverage import compute_coverage
from citoyen_pipeline.inputs import load_candidates, load_identities, load_rag_config
from citoyen_pipeline.model import (
    Body,
    CorePack,
    LegalArticle,
    LegalPack,
    LegalText,
    Mandate,
    ProgramChunk,
    ProgramDocument,
    ProgramsPack,
    ProgramStatus,
    Source,
    Speech,
    SponsorshipCount,
    TagConfidence,
    TagMethod,
    ThemeTag,
    Vote,
)
from citoyen_pipeline.sign import build_manifest, pack_entry, public_key_b64, sign
from citoyen_pipeline.themes import load_taxonomy

TEST_SEED = bytes(range(32))
GENERATED_ON = date(2026, 1, 15)
VERSION = "2026-01-15+fixture"
PROGRAMS_VERSION = "2026-01-15+programs-fixture"
LEGAL_VERSION = "2026-01-15+legal-fixture"
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
    speeches = [
        Speech(
            "fictif-a-discours-1",
            "fictif-a",
            "OFFICIAL_STATEMENT",
            None,
            date(2025, 6, 1),
            "Déclaration fictive",
            "Interview — Média fictif",
            "https://example.invalid/discours-1",
            fake.id,
        )
    ]
    taxonomy = load_taxonomy(FIXTURES / "themes.yaml")
    programs = fictional_programs()
    pack = CorePack(
        GENERATED_ON,
        policy,
        sources + [fake],
        candidates,
        compute_coverage(candidates, identities, mandates, votes, speeches, programs.statuses),
        mandates,
        votes,
        speeches,
        themes=taxonomy.all_themes,
        vote_themes={"fictif-a-vote-1": [ThemeTag("fictif-01", TagMethod.RULE, TagConfidence.LOW)]},
        rag_config=load_rag_config(FIXTURES / "rag_config.json"),
        sponsorships=[
            SponsorshipCount(
                "fictif-a", 3, 2, (("Mandat fictif 1", 2), ("Mandat fictif 2", 1)), date(2026, 1, 10), fake.id
            )
        ],
    )
    out.mkdir(parents=True, exist_ok=True)
    pack_file = out / "core.sqlite"
    write_core_pack(pack, VERSION, pack_file)
    programs_file = out / "programs.sqlite"
    write_programs_pack(programs, taxonomy.ids(), programs_file, PROGRAMS_VERSION)
    legal_file = out / "legal.sqlite"
    write_legal_pack(fictional_legal(), legal_file, LEGAL_VERSION)
    manifest = build_manifest(
        [
            pack_entry("core", VERSION, pack_file, [], ["Licence fictive"]),
            pack_entry("programs", PROGRAMS_VERSION, programs_file, [], ["Conditions fictives"]),
            pack_entry("legal", LEGAL_VERSION, legal_file, [], ["Licence fictive"]),
        ],
        GENERATED_ON.isoformat(),
    )
    (out / "manifest.json").write_bytes(manifest)
    (out / "manifest.json.sig").write_bytes(sign(manifest, test_key()))
    (out / "test_public_key.txt").write_text(public_key_b64(test_key()) + "\n", encoding="ascii")
    return {"public_key": public_key_b64(test_key()), "seed_b64": base64.b64encode(TEST_SEED).decode()}


def fictional_programs() -> ProgramsPack:
    """Candidat A: complete programme (PDF, pages); B: partial (web page, anchors); C: none."""

    def document(doc_id: str, candidate_id: str, fmt: str) -> ProgramDocument:
        return ProgramDocument(
            id=doc_id,
            candidate_id=candidate_id,
            title=f"Programme fictif ({candidate_id})",
            url=f"https://example.invalid/{doc_id}",
            format=fmt,
            published_on=date(2026, 1, 5),
            version="1",
            sha256="0" * 64,
            retrieved_on=date(2026, 1, 6),
            license="Conditions fictives",
        )

    chunks = [
        ProgramChunk(
            "fictif-a-programme-0001",
            "fictif-a-programme",
            "fictif-a",
            1,
            ("Partie fictive 1", "Section fictive"),
            1,
            None,
            "Mesure fictive n° 1 : texte fictif sur le motfictif01.",
            12,
        ),
        ProgramChunk(
            "fictif-a-programme-0002",
            "fictif-a-programme",
            "fictif-a",
            2,
            ("Partie fictive 2",),
            3,
            None,
            "Mesure fictive n° 2.\n\nParagraphe fictif sans thème reconnu.",
            12,
        ),
        ProgramChunk(
            "fictif-b-programme-0001",
            "fictif-b-programme",
            "fictif-b",
            1,
            (),
            None,
            "section-fictive",
            "Mesure fictive de B sur le motfictif01 et le motfictif02.",
            11,
        ),
    ]
    rule = TagMethod.RULE
    return ProgramsPack(
        generated_on=GENERATED_ON,
        statuses={
            "fictif-a": ProgramStatus.COMPLET,
            "fictif-b": ProgramStatus.PARTIEL,
            "fictif-c": ProgramStatus.NON_PUBLIE,
        },
        documents=[
            document("fictif-a-programme", "fictif-a", "pdf"),
            document("fictif-b-programme", "fictif-b", "html"),
        ],
        chunks=chunks,
        chunk_themes={
            "fictif-a-programme-0001": [ThemeTag("fictif-01", rule, TagConfidence.LOW)],
            "fictif-a-programme-0002": [ThemeTag("inconnu", rule, TagConfidence.LOW)],
            "fictif-b-programme-0001": [
                ThemeTag("fictif-01", rule, TagConfidence.LOW),
                ThemeTag("fictif-02", TagMethod.MANUAL, TagConfidence.HIGH),
            ],
        },
    )


def fictional_legal() -> LegalPack:
    """Two fictional texts: a preamble without number, numbered articles under headings."""

    def text(suffix: str, position: int) -> LegalText:
        return LegalText(
            id=f"fictif-texte-{suffix}",
            title=f"Texte fictif {suffix.upper()}",
            position=position,
            url=f"https://example.invalid/textes/{suffix}",
            source_name=f"Texte fictif {suffix}",
            retrieved_on=date(2026, 1, 1),
            license="Licence fictive",
        )

    def article(text_id, ordinal, number, headings, body, start, url, until=None) -> LegalArticle:
        return LegalArticle(
            f"{text_id}-art-{ordinal}", text_id, ordinal, number, headings, body, start, url, until
        )

    a, b = "fictif-texte-a", "fictif-texte-b"
    return LegalPack(
        GENERATED_ON,
        [text("a", 1), text("b", 2)],
        [
            article(
                a,
                1,
                "",
                ("Préambule fictif",),
                "Paragraphe fictif d’introduction.",
                None,
                "https://example.invalid/textes/a/preambule",
            ),
            article(
                a,
                2,
                "1",
                ("Titre fictif I", "Chapitre fictif 1"),
                "L’élection fictive a lieu selon une règle fictive.",
                date(2020, 1, 1),
                "https://example.invalid/textes/a/1",
                until=date(2030, 1, 1),
            ),
            article(
                b,
                1,
                "L. 1",
                ("Livre fictif",),
                "Ligne fictive.\nSeconde ligne fictive.",
                date(2022, 1, 1),
                "https://example.invalid/textes/b/l1",
            ),
        ],
    )
