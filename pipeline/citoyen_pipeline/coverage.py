"""Coverage badge per candidate and facet (AGENTS.md §10), computed from what the pack contains."""

from __future__ import annotations

from citoyen_pipeline.model import Candidate, Facet, Identity, Level, Mandate, ProgramStatus, Speech, Vote


def compute_coverage(
    candidates: list[Candidate],
    identities: list[Identity],
    mandates: list[Mandate],
    votes: list[Vote],
    speeches: list[Speech] | None = None,
    program_statuses: dict[str, ProgramStatus] | None = None,
) -> dict[tuple[str, Facet], Level]:
    by_candidate = {i.candidate_id: i for i in identities}
    coverage: dict[tuple[str, Facet], Level] = {}
    for candidate in candidates:
        identity = by_candidate.get(candidate.id)
        linked = set(identity.ids()) if identity else set()
        has_mandates = any(m.candidate_id == candidate.id for m in mandates)
        voted_in = {v.body for v in votes if v.candidate_id == candidate.id}

        # Programme status comes from inputs/programs/manifest.yaml; no entry means unavailable.
        # Speeches cover PE interventions and official statements only (AN and Senate debates are
        # not integrated yet).
        status = (program_statuses or {}).get(candidate.id, ProgramStatus.NON_PUBLIE)
        coverage[(candidate.id, Facet.PROGRAM)] = {
            ProgramStatus.COMPLET: Level.AVAILABLE,
            ProgramStatus.PARTIEL: Level.PARTIAL,
        }.get(status, Level.UNAVAILABLE)
        has_speeches = any(s.candidate_id == candidate.id for s in speeches or [])
        coverage[(candidate.id, Facet.SPEECHES)] = Level.PARTIAL if has_speeches else Level.UNAVAILABLE
        # A mandate search only happens through validated identity links; no link means "unavailable",
        # never "no mandate".
        coverage[(candidate.id, Facet.MANDATES)] = Level.AVAILABLE if has_mandates else Level.UNAVAILABLE
        if not linked:
            # No parliamentary identifier: recorded votes do not apply to this candidate.
            coverage[(candidate.id, Facet.VOTES)] = Level.NOT_APPLICABLE
        elif voted_in >= linked:
            coverage[(candidate.id, Facet.VOTES)] = Level.AVAILABLE
        elif voted_in:
            coverage[(candidate.id, Facet.VOTES)] = Level.PARTIAL
        else:
            coverage[(candidate.id, Facet.VOTES)] = Level.UNAVAILABLE
    return coverage
