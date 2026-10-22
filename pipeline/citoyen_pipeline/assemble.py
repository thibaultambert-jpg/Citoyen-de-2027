"""Assembles the `core` and `programs` packs from the inputs and the cached sources."""

from __future__ import annotations

import hashlib
from collections.abc import Callable
from dataclasses import dataclass
from datetime import date
from pathlib import Path

from citoyen_pipeline import legal, programs
from citoyen_pipeline.coverage import compute_coverage
from citoyen_pipeline.fetch import Cache
from citoyen_pipeline.identity import check_identities
from citoyen_pipeline.inputs import load_candidates, load_identities, load_rag_config, load_reserve
from citoyen_pipeline.model import CorePack, Identity, ProgramsPack, SourceExtract, ThemeTag
from citoyen_pipeline.sources import assemblee, discours, parlement_europeen, rne, senat
from citoyen_pipeline.themes import load_overrides, load_taxonomy

PROGRAMS_MANIFEST = Path("programs") / "manifest.yaml"
LEGAL_TEXTS = Path("legal") / "texts.yaml"


@dataclass(frozen=True)
class Adapter:
    name: str
    needed: Callable[[list[Identity]], bool]
    fetch: Callable[[Cache, list[Identity]], None]
    extract: Callable[[Cache, list[Identity]], SourceExtract]


ADAPTERS = (
    Adapter(
        "assemblee", lambda ids: any(i.an_id for i in ids), lambda c, _: assemblee.fetch(c), assemblee.extract
    ),
    Adapter("senat", lambda ids: any(i.senat_id for i in ids), lambda c, _: senat.fetch(c), senat.extract),
    Adapter("rne", lambda ids: bool(ids), lambda c, _: rne.fetch(c), rne.extract),
    Adapter(
        "parlement_europeen",
        lambda ids: any(i.pe_id for i in ids),
        parlement_europeen.fetch,
        parlement_europeen.extract,
    ),
    Adapter(
        "discours",
        lambda ids: any(i.vie_publique_name for i in ids),
        lambda c, _: discours.fetch(c),
        discours.extract,
    ),
)


def load_inputs(inputs: Path):
    policy_date, candidates, status_sources = load_candidates(inputs / "candidates.yaml")
    identities = load_identities(inputs / "identities.yaml", {c.id for c in candidates})
    return policy_date, candidates, status_sources, identities


def fetch_all(inputs: Path, cache: Cache) -> list[str]:
    _, candidates, _, identities = load_inputs(inputs)
    used = [a for a in ADAPTERS if a.needed(identities)]
    for adapter in used:
        adapter.fetch(cache, identities)
    entries = programs.load_manifest(inputs / PROGRAMS_MANIFEST, {c.id for c in candidates})
    programs.fetch(cache, entries)
    legal_inputs = legal.load_inputs(inputs / LEGAL_TEXTS)
    legal.fetch(cache, legal_inputs)
    return [a.name for a in used] + (["programs"] if entries else []) + (["legi"] if legal_inputs else [])


def assemble(inputs: Path, cache: Cache, generated_on: date) -> CorePack:
    return assemble_with_review(inputs, cache, generated_on)[0]


def assemble_with_review(
    inputs: Path, cache: Cache, generated_on: date
) -> tuple[CorePack, dict[str, list[str]]]:
    core, _, review = assemble_packs(inputs, cache, generated_on)
    return core, review


def assemble_packs(
    inputs: Path, cache: Cache, generated_on: date
) -> tuple[CorePack, ProgramsPack, dict[str, list[str]]]:
    policy_date, candidates, status_sources, identities = load_inputs(inputs)
    taxonomy = load_taxonomy(inputs / "themes.yaml")
    overrides = load_overrides(inputs / "theme_overrides.yaml", taxonomy)
    entries = programs.load_manifest(inputs / PROGRAMS_MANIFEST, {c.id for c in candidates})
    programs_pack, programs_review = programs.assemble(
        cache, entries, [c.id for c in candidates], taxonomy, overrides, generated_on
    )
    combined = SourceExtract(sources=list(status_sources))
    for adapter in ADAPTERS:
        if adapter.needed(identities):
            part = adapter.extract(cache, identities)
            combined.sources += part.sources
            combined.people += part.people
            combined.mandates += part.mandates
            combined.votes += part.votes
            combined.speeches += part.speeches
            combined.review.update(part.review)
    # Every identifier written by hand must agree with its official source (AGENTS.md §7.2).
    check_identities(identities, combined.people)
    if unknown := set(overrides.ballots) - {v.ballot_id for v in combined.votes}:
        raise ValueError(f"theme_overrides.yaml: unknown ballot ids {sorted(unknown)}")
    vote_themes: dict[str, list[ThemeTag]] = {}
    for vote in combined.votes:
        manual = overrides.ballots.get(vote.ballot_id)
        vote_themes[vote.id] = overrides.manual(manual) if manual else taxonomy.tag(vote.ballot_title)
    pack = CorePack(
        generated_on=generated_on,
        inclusion_policy_date=policy_date,
        sources=_unique(combined.sources),
        candidates=candidates,
        coverage=compute_coverage(
            candidates,
            identities,
            combined.mandates,
            combined.votes,
            combined.speeches,
            programs_pack.statuses,
        ),
        mandates=combined.mandates,
        votes=combined.votes,
        speeches=combined.speeches,
        themes=taxonomy.all_themes,
        vote_themes=vote_themes,
        rag_config=load_rag_config(inputs / "rag_config.json"),
        reserve=load_reserve(inputs / "reserve.json"),
    )
    return pack, programs_pack, {**combined.review, **programs_review}


def inputs_digest(inputs: Path, cache: Cache) -> str:
    digest = hashlib.sha256()
    names = (
        "candidates.yaml",
        "identities.yaml",
        "themes.yaml",
        "theme_overrides.yaml",
        str(PROGRAMS_MANIFEST),
        "rag_config.json",
        "reserve.json",
    )
    for path in [*(inputs / name for name in names), cache.root / "fetch_log.json"]:
        if path.exists():
            digest.update(path.read_bytes())
    return digest.hexdigest()


def _unique(sources):
    seen = {}
    for source in sources:
        seen.setdefault(source.id, source)
    return list(seen.values())


def assemble_legal(inputs: Path, cache: Cache, generated_on: date):
    """The legal pack has its own content-based version: it does not change the core version."""
    return legal.assemble(cache, legal.load_inputs(inputs / LEGAL_TEXTS), generated_on)
