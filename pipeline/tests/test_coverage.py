from datetime import date

from citoyen_pipeline.coverage import compute_coverage
from citoyen_pipeline.model import Assembly, Candidate, Facet, Identity, Level, Mandate, Vote

A = Candidate("fictif-a", "Candidat A", "A", "DECLARE", date(2026, 1, 1), "s")
B = Candidate("fictif-b", "Candidat B", "B", "DECLARE", date(2026, 1, 1), "s")


def vote(assembly):
    return Vote(f"v-{assembly}", "fictif-a", assembly, "1", date(2025, 1, 1), "t", None, "pour", "https://x.invalid", "s")


def test_candidate_without_parliamentary_link_has_votes_not_applicable():
    coverage = compute_coverage([B], [], [], [])
    assert coverage[("fictif-b", Facet.VOTES)] == Level.NOT_APPLICABLE
    assert coverage[("fictif-b", Facet.MANDATES)] == Level.UNAVAILABLE
    assert coverage[("fictif-b", Facet.PROGRAM)] == Level.UNAVAILABLE


def test_votes_partial_when_only_some_linked_assemblies_have_votes():
    identity = Identity("fictif-a", "Exemple", "Alpha", date(1970, 1, 1), an_id="PA1", pe_id="9")
    mandate = Mandate("m1", "fictif-a", Assembly.AN, "Député", date(2024, 1, 1), None, "s")
    coverage = compute_coverage([A], [identity], [mandate], [vote(Assembly.AN)])
    assert coverage[("fictif-a", Facet.VOTES)] == Level.PARTIAL
    assert coverage[("fictif-a", Facet.MANDATES)] == Level.AVAILABLE
    full = compute_coverage([A], [identity], [mandate], [vote(Assembly.AN), vote(Assembly.PE)])
    assert full[("fictif-a", Facet.VOTES)] == Level.AVAILABLE


def test_every_candidate_has_every_facet():
    coverage = compute_coverage([A, B], [], [], [])
    assert {(c, f) for c in ("fictif-a", "fictif-b") for f in Facet} == set(coverage)
