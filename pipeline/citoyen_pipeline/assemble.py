"""Assembles the `core` and `programs` packs from the inputs and the cached sources."""

from __future__ import annotations

import hashlib
from collections.abc import Callable
from dataclasses import dataclass, replace
from datetime import date
from pathlib import Path

from citoyen_pipeline import legal, programs
from citoyen_pipeline.coverage import compute_coverage
from citoyen_pipeline.fetch import Cache
from citoyen_pipeline.identity import IdentityMismatch, identity_problems
from citoyen_pipeline.inputs import (
    load_candidates,
    load_identities,
    load_rag_config,
    load_sponsorship_names,
)
from citoyen_pipeline.model import CorePack, Identity, ProgramsPack, SourceExtract, ThemeTag
from citoyen_pipeline.sources import assemblee, discours, parlement_europeen, rne, senat, sponsorships
from citoyen_pipeline.themes import load_overrides, load_taxonomy

PROGRAMS_MANIFEST = Path("programs") / "manifest.yaml"
LEGAL_TEXTS = Path("legal") / "texts.yaml"
SPONSORSHIPS = "sponsorships.yaml"


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


# Identity field linking each adapter to a person (the RNE matches on name and birth date only).
ADAPTER_ID_FIELDS = {
    "assemblee": "an_id",
    "senat": "senat_id",
    "parlement_europeen": "pe_id",
    "discours": "vie_publique_name",
}


SLOW_ADAPTERS = ("parlement_europeen",)


def fetch_all(inputs: Path, cache: Cache, keep_going: bool = False) -> list[str]:
    """With [keep_going] (personal use), a source that cannot be downloaded is reported and left out."""
    _, candidates, _, identities = load_inputs(inputs)
    used = [a for a in ADAPTERS if a.needed(identities)]

    def fetch_adapters(adapters) -> None:
        for adapter in adapters:
            try:
                adapter.fetch(cache, identities)
            except Exception as error:  # noqa: BLE001 - reported, the other sources go on
                if not keep_going:
                    raise
                print(f"SOURCE LEFT OUT: {adapter.name}: {type(error).__name__}: {error}", flush=True)

    # The European Parliament API is by far the slowest source (thousands of requests, sometimes
    # stalled for hours): it comes last, so that a CI time limit only cuts some of its votes.
    fetch_adapters([a for a in used if a.name not in SLOW_ADAPTERS])
    entries = programs.load_manifest(inputs / PROGRAMS_MANIFEST, {c.id for c in candidates})
    programs.fetch(cache, entries)
    legal_inputs = legal.load_inputs(inputs / LEGAL_TEXTS)
    legal.fetch(cache, legal_inputs)
    dataset, _ = load_sponsorship_names(inputs / SPONSORSHIPS, {c.id for c in candidates})
    if dataset:
        sponsorships.fetch(cache, dataset)
    print("All sources but the European Parliament fetched.", flush=True)
    fetch_adapters([a for a in used if a.name in SLOW_ADAPTERS])
    return (
        [a.name for a in used]
        + (["programs"] if entries else [])
        + (["legi"] if legal_inputs else [])
        + (["sponsorships"] if dataset else [])
    )


def assemble(inputs: Path, cache: Cache, generated_on: date) -> CorePack:
    return assemble_with_review(inputs, cache, generated_on)[0]


def assemble_with_review(
    inputs: Path, cache: Cache, generated_on: date
) -> tuple[CorePack, dict[str, list[str]]]:
    core, _, review = assemble_packs(inputs, cache, generated_on)
    return core, review


def assemble_packs(
    inputs: Path, cache: Cache, generated_on: date, lenient_identities: bool = False
) -> tuple[CorePack, ProgramsPack, dict[str, list[str]]]:
    """With [lenient_identities] (imported inputs, personal use), a candidate whose identity link is
    not confirmed by the official sources loses that link and every fact attached through it,
    instead of stopping the build; the reasons go to report.json."""
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
            try:
                part = adapter.extract(cache, identities)
            except Exception as error:  # noqa: BLE001 - lenient builds only, see below
                if not lenient_identities:
                    raise
                # Source missing or unreadable: none of its facts, and its identifiers are not
                # checked against it (the candidates keep the facts of the other sources).
                combined.review.setdefault("sources left out", []).append(
                    f"{adapter.name}: {type(error).__name__}: {error}"
                )
                field = ADAPTER_ID_FIELDS.get(adapter.name)
                if field:
                    identities = [replace(i, **{field: None}) for i in identities]
                continue
            combined.sources += part.sources
            combined.people += part.people
            combined.birth_records += part.birth_records
            combined.mandates += part.mandates
            combined.votes += part.votes
            combined.speeches += part.speeches
            combined.review.update(part.review)
    combined.votes = _unique_votes(combined.votes, combined.review)
    # Every identifier written by hand must agree with its official source (AGENTS.md §7.2).
    if rejected := identity_problems(identities, combined.people, combined.birth_records):
        if not lenient_identities:
            lines = [p for found in rejected.values() for p in found]
            raise IdentityMismatch("Identity links rejected:\n" + "\n".join(f"- {p}" for p in lines))
        combined.review["identities rejected (facts not attached)"] = [
            p for found in rejected.values() for p in found
        ]
        identities = [i for i in identities if i.candidate_id not in rejected]
        combined.mandates = [m for m in combined.mandates if m.candidate_id not in rejected]
        combined.votes = [v for v in combined.votes if v.candidate_id not in rejected]
        combined.speeches = [s for s in combined.speeches if s.candidate_id not in rejected]
    if unknown := set(overrides.ballots) - {v.ballot_id for v in combined.votes}:
        raise ValueError(f"theme_overrides.yaml: unknown ballot ids {sorted(unknown)}")
    _, sponsorship_names = load_sponsorship_names(inputs / SPONSORSHIPS, {c.id for c in candidates})
    sponsorship_counts, sponsorship_sources, sponsorship_review = (
        sponsorships.extract(cache, sponsorship_names) if sponsorship_names else ([], [], [])
    )
    if sponsorship_review:
        combined.review["sponsorships"] = sponsorship_review
    vote_themes: dict[str, list[ThemeTag]] = {}
    for vote in combined.votes:
        manual = overrides.ballots.get(vote.ballot_id)
        vote_themes[vote.id] = overrides.manual(manual) if manual else taxonomy.tag(vote.ballot_title)
    pack = CorePack(
        generated_on=generated_on,
        inclusion_policy_date=policy_date,
        sources=_unique(combined.sources + sponsorship_sources),
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
        sponsorships=sponsorship_counts,
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
        SPONSORSHIPS,
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


def _unique_votes(votes: list, review: dict[str, list[str]]) -> list:
    """One vote per (ballot, person): a source may list the same ballot twice (re-published sittings).

    Identical repeats are merged; repeats that disagree on the position are left out and reported,
    never resolved by a guess (AGENTS.md §2.2).
    """
    first: dict[str, object] = {}
    conflicting: dict[str, set[str]] = {}
    for vote in votes:
        kept = first.setdefault(vote.id, vote)
        if kept.position != vote.position:
            conflicting.setdefault(vote.id, {kept.position}).add(vote.position)
    if conflicting:
        review["votes left out (contradictory positions for the same ballot)"] = [
            f"{vote_id}: {', '.join(sorted(positions))}" for vote_id, positions in sorted(conflicting.items())
        ]
    return [v for vote_id, v in first.items() if vote_id not in conflicting]
