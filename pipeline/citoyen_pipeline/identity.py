"""Identity checks (AGENTS.md §7.2): a hand-written link is accepted only when the official source
agrees on at least two attributes, the identifier itself plus family name and birth date."""

from __future__ import annotations

import unicodedata

from citoyen_pipeline.model import Identity, PersonFacts


class IdentityMismatch(ValueError):
    pass


def normalize_name(name: str) -> str:
    decomposed = unicodedata.normalize("NFD", name)
    stripped = "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")
    return " ".join(stripped.replace("’", "'").replace("-", " ").lower().split())


def check_identities(identities: list[Identity], people: list[PersonFacts]) -> None:
    """Raises if a linked identifier is missing from the source or does not match name and birth date."""
    problems = identity_problems(identities, people)
    if problems:
        lines = [p for found in problems.values() for p in found]
        raise IdentityMismatch("Identity links rejected:\n" + "\n".join(f"- {p}" for p in lines))


def identity_problems(identities: list[Identity], people: list[PersonFacts]) -> dict[str, list[str]]:
    """Candidate id -> reasons its identity link is rejected; empty when every link matches."""
    index = {(p.body, p.source_person_id): p for p in people}
    problems = []
    for identity in identities:
        for body, source_id in identity.ids().items():
            facts = index.get((body, source_id))
            if facts is None:
                problems.append(f"{identity.candidate_id}: {body} id {source_id} not found in source")
                continue
            if normalize_name(facts.family_name) != normalize_name(identity.family_name):
                problems.append(
                    f"{identity.candidate_id}: {body} family name differs "
                    f"({facts.family_name!r} vs {identity.family_name!r})"
                )
            if facts.birth_date is None or facts.birth_date != identity.birth_date:
                problems.append(
                    f"{identity.candidate_id}: {body} birth date differs or is missing "
                    f"({facts.birth_date} vs {identity.birth_date})"
                )
    by_candidate: dict[str, list[str]] = {}
    for problem in problems:
        by_candidate.setdefault(problem.split(":", 1)[0], []).append(problem)
    return by_candidate
