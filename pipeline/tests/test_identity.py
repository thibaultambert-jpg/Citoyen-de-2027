from datetime import date

import pytest

from citoyen_pipeline.identity import IdentityMismatch, check_identities, normalize_name
from citoyen_pipeline.model import BirthRecord, Body, Identity, PersonFacts

IDENTITY = Identity("fictif-a", "Exemple", "Alpha", date(1970, 1, 1), an_id="PA000001")


def test_names_are_compared_without_accents_case_or_hyphens():
    assert normalize_name("  Éxemple-Dupont ") == normalize_name("exemple dupont")


def test_matching_identity_is_accepted():
    check_identities([IDENTITY], [PersonFacts(Body.AN, "PA000001", "EXEMPLE", date(1970, 1, 1))])


@pytest.mark.parametrize(
    "facts",
    [
        PersonFacts(Body.AN, "PA000001", "Autre", date(1970, 1, 1)),  # homonym check: name differs
        PersonFacts(Body.AN, "PA000001", "Exemple", date(1971, 1, 1)),  # birth date differs
        PersonFacts(Body.AN, "PA000001", "Exemple", None),  # birth date missing
        PersonFacts(Body.AN, "PA000002", "Exemple", date(1970, 1, 1)),  # id absent from source
    ],
)
def test_non_concordant_identity_fails_the_build(facts):
    with pytest.raises(IdentityMismatch):
        check_identities([IDENTITY], [facts])


def test_declared_name_may_be_one_part_of_a_compound_official_name():
    check_identities([IDENTITY], [PersonFacts(Body.AN, "PA000001", "Exemple-Dupont", date(1970, 1, 1))])
    with pytest.raises(IdentityMismatch):  # the birth date still has to match
        check_identities([IDENTITY], [PersonFacts(Body.AN, "PA000001", "Exemple-Dupont", date(1971, 1, 1))])
    with pytest.raises(IdentityMismatch):  # a part, not a prefix
        check_identities(
            [IDENTITY], [PersonFacts(Body.AN, "PA000001", "Exemplette-Dupont", date(1970, 1, 1))]
        )


MEP = Identity("fictif-a", "Exemple", "Alpha", date(1970, 1, 1), pe_id="1")
UNDATED_MEP = PersonFacts(Body.PE, "1", "Exemple", None, given_name="Alpha")


def test_birth_date_missing_from_the_body_may_be_confirmed_by_another_official_source():
    check_identities([MEP], [UNDATED_MEP], [BirthRecord(Body.PE, "EXEMPLE", "Alpha", date(1970, 1, 1))])


@pytest.mark.parametrize(
    "facts, record",
    [
        (UNDATED_MEP, None),  # no other source
        (UNDATED_MEP, BirthRecord(Body.PE, "Exemple", "Alpha", date(1971, 1, 1))),  # other birth date
        (UNDATED_MEP, BirthRecord(Body.AN, "Exemple", "Alpha", date(1970, 1, 1))),  # other body
        (UNDATED_MEP, BirthRecord(Body.PE, "Exemple", "Beta", date(1970, 1, 1))),  # other given name
        (  # the body's own given name differs
            PersonFacts(Body.PE, "1", "Exemple", None, given_name="Beta"),
            BirthRecord(Body.PE, "Exemple", "Alpha", date(1970, 1, 1)),
        ),
    ],
)
def test_birth_date_confirmation_needs_names_birth_date_and_body(facts, record):
    with pytest.raises(IdentityMismatch):
        check_identities([MEP], [facts], [record] if record else [])
