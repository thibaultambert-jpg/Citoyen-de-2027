from datetime import date

import pytest

from citoyen_pipeline.identity import IdentityMismatch, check_identities, normalize_name
from citoyen_pipeline.model import Assembly, Identity, PersonFacts

IDENTITY = Identity("fictif-a", "Exemple", "Alpha", date(1970, 1, 1), an_id="PA000001")


def test_names_are_compared_without_accents_case_or_hyphens():
    assert normalize_name("  Éxemple-Dupont ") == normalize_name("exemple dupont")


def test_matching_identity_is_accepted():
    check_identities([IDENTITY], [PersonFacts(Assembly.AN, "PA000001", "EXEMPLE", date(1970, 1, 1))])


@pytest.mark.parametrize(
    "facts",
    [
        PersonFacts(Assembly.AN, "PA000001", "Autre", date(1970, 1, 1)),  # homonym check: name differs
        PersonFacts(Assembly.AN, "PA000001", "Exemple", date(1971, 1, 1)),  # birth date differs
        PersonFacts(Assembly.AN, "PA000001", "Exemple", None),  # birth date missing
        PersonFacts(Assembly.AN, "PA000002", "Exemple", date(1970, 1, 1)),  # id absent from source
    ],
)
def test_non_concordant_identity_fails_the_build(facts):
    with pytest.raises(IdentityMismatch):
        check_identities([IDENTITY], [facts])
