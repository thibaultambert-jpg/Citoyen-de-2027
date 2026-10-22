from datetime import date
from pathlib import Path

import pytest

from citoyen_pipeline.inputs import InputError, load_sponsorship_names
from citoyen_pipeline.sources.sponsorships import aggregate, parse

FIXTURES = Path(__file__).parent / "fixtures"

HEADER = "Civilité;Nom;Prénom;Mandat;Circonscription;Département;Candidat;Date de publication"
# Fictional rows in the verified 2022 layout (';', UTF-8 with BOM, DD/MM/YYYY).
ROWS = (
    (
        "M.",
        "ELU-UN",
        "P",
        "Mandat fictif 1",
        "Commune 1",
        "Département fictif 1",
        "FICTIF Candidat A",
        "01/02/2026",
    ),
    (
        "Mme",
        "ELUE-DEUX",
        "P",
        "Mandat fictif 1",
        "Commune 2",
        "Département fictif 2",
        "FICTIF Candidat A",
        "04/02/2026",
    ),
    (
        "M.",
        "ELU-TROIS",
        "P",
        "Mandat fictif 2",
        "",
        "Département fictif 2",
        "FICTIF Candidat A",
        "04/02/2026",
    ),
    (
        "Mme",
        "ELUE-QUATRE",
        "P",
        "Mandat fictif 1",
        "Commune 3",
        "Département fictif 1",
        "FICTIF Candidat Z",
        "08/02/2026",
    ),
)
CSV_2022 = ("\ufeff" + "\n".join([HEADER, *(";".join(r) for r in ROWS)]) + "\n").encode()


def test_parses_the_2022_layout():
    rows = parse(CSV_2022)
    assert len(rows) == 4
    assert rows[0].candidate == "FICTIF Candidat A"
    assert rows[0].mandate == "Mandat fictif 1"
    assert rows[0].department == "Département fictif 1"
    assert rows[0].published_on == date(2026, 2, 1)


def test_parses_the_2017_candidate_column():
    body = CSV_2022.replace(b";Candidat;", ";Candidat-e parrainé-e;".encode())
    assert {r.candidate for r in parse(body)} == {"FICTIF Candidat A", "FICTIF Candidat Z"}


def test_unknown_header_fails():
    with pytest.raises(ValueError, match="Unknown sponsorship file header"):
        parse("Nom;Prénom;Autre\nA;B;C\n".encode())


def test_counts_only_the_hand_written_names():
    counts, review = aggregate(
        parse(CSV_2022), {"fictif-a": "FICTIF Candidat A", "fictif-b": "FICTIF Candidat B"}, "src"
    )
    a, b = counts
    assert (a.candidate_id, a.total, a.departments) == ("fictif-a", 3, 2)
    assert a.by_mandate == (("Mandat fictif 1", 2), ("Mandat fictif 2", 1))
    # The date of the figures is the latest publication in the file, for every candidate.
    assert a.published_on == b.published_on == date(2026, 2, 8)
    assert (b.total, b.departments, b.by_mandate) == (0, 0, ())
    assert review == [
        "fictif-b: no row for the name 'FICTIF Candidat B' (check its exact spelling)",
        "published name not linked to a candidate: 'FICTIF Candidat Z'",
    ]


def test_names_are_compared_exactly():
    counts, _ = aggregate(parse(CSV_2022), {"fictif-a": "Fictif Candidat A"}, "src")
    assert counts[0].total == 0


def test_inputs_are_validated(tmp_path):
    dataset, names = load_sponsorship_names(
        FIXTURES / "sponsorships.yaml", {"fictif-a", "fictif-b", "fictif-c"}
    )
    assert dataset == "parrainages-fictifs"
    assert names == {"fictif-a": "FICTIF Candidat A", "fictif-b": "FICTIF Candidat B"}
    with pytest.raises(InputError, match="unknown candidate ids"):
        load_sponsorship_names(FIXTURES / "sponsorships.yaml", {"fictif-a"})
    empty = tmp_path / "sponsorships.yaml"
    empty.write_text('version: 1\ndataset: ""\nnames: {fictif-a: "X"}\n', encoding="utf-8")
    with pytest.raises(InputError, match="no dataset"):
        load_sponsorship_names(empty, {"fictif-a"})
    assert load_sponsorship_names(tmp_path / "absent.yaml", set()) == ("", {})


def test_committed_input_is_empty_until_the_dataset_exists():
    inputs = Path(__file__).resolve().parents[1] / "inputs" / "sponsorships.yaml"
    assert load_sponsorship_names(inputs, set()) == ("", {})
