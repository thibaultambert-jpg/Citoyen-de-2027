"""Identity checks (AGENTS.md §7.2): a hand-written link is accepted only when the official source
agrees on at least two attributes, the identifier itself plus family name and birth date.

Two cases are accepted without hand-written exceptions:
- a compound official family name ("A-B") of which the declared name is one part ("A"), the birth date
  still having to match;
- a body that does not publish the birth date: the given name must then match too, and another
  official source (RNE) must list a member of that body with the same names and birth date."""

from __future__ import annotations

import unicodedata
from collections.abc import Iterable

from citoyen_pipeline.model import BirthRecord, Identity, PersonFacts


class IdentityMismatch(ValueError):
    pass


def normalize_name(name: str) -> str:
    decomposed = unicodedata.normalize("NFD", name)
    stripped = "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")
    return " ".join(stripped.replace("’", "'").replace("-", " ").lower().split())


def same_family_name(official: str, declared: str) -> bool:
    """Equal names, or [declared] is one part of a compound [official] name ("A-B")."""
    declared = normalize_name(declared)
    if normalize_name(official) == declared:
        return True
    parts = [normalize_name(part) for part in official.split("-")]
    return len(parts) > 1 and declared in parts


def check_identities(
    identities: list[Identity], people: list[PersonFacts], birth_records: Iterable[BirthRecord] = ()
) -> None:
    """Raises if a linked identifier is missing from the source or does not match name and birth date."""
    problems = identity_problems(identities, people, birth_records)
    if problems:
        lines = [p for found in problems.values() for p in found]
        raise IdentityMismatch("Identity links rejected:\n" + "\n".join(f"- {p}" for p in lines))


def identity_problems(
    identities: list[Identity], people: list[PersonFacts], birth_records: Iterable[BirthRecord] = ()
) -> dict[str, list[str]]:
    """Candidate id -> reasons its identity link is rejected; empty when every link matches."""
    index = {(p.body, p.source_person_id): p for p in people}
    births = {
        (r.body, normalize_name(r.family_name), normalize_name(r.given_name), r.birth_date)
        for r in birth_records
    }
    problems = []
    for identity in identities:
        for body, source_id in identity.ids().items():
            facts = index.get((body, source_id))
            if facts is None:
                problems.append(f"{identity.candidate_id}: {body} id {source_id} not found in source")
                continue
            if not same_family_name(facts.family_name, identity.family_name):
                problems.append(
                    f"{identity.candidate_id}: {body} family name differs "
                    f"({facts.family_name!r} vs {identity.family_name!r})"
                )
            if facts.birth_date is None and _birth_confirmed_elsewhere(identity, facts, births):
                continue
            if facts.birth_date is None or facts.birth_date != identity.birth_date:
                problems.append(
                    f"{identity.candidate_id}: {body} birth date differs or is missing "
                    f"({facts.birth_date} vs {identity.birth_date})"
                )
    by_candidate: dict[str, list[str]] = {}
    for problem in problems:
        by_candidate.setdefault(problem.split(":", 1)[0], []).append(problem)
    return by_candidate


def _birth_confirmed_elsewhere(identity: Identity, facts: PersonFacts, births: set) -> bool:
    given = normalize_name(identity.given_name)
    return (
        facts.given_name is not None
        and normalize_name(facts.given_name) == given
        and (facts.body, normalize_name(identity.family_name), given, identity.birth_date) in births
    )
