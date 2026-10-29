"""Adapters run on fictional files that copy the verified structure of each official source."""

import json
import zipfile
from dataclasses import replace
from datetime import date

import pytest

from citoyen_pipeline.fetch import Cache
from citoyen_pipeline.identity import IdentityMismatch, check_identities
from citoyen_pipeline.model import BirthRecord, Body, Identity
from citoyen_pipeline.sources import assemblee, rne, senat

ALPHA = Identity("fictif-a", "Exemple", "Alpha", date(1970, 1, 1), an_id="PA000001", senat_id="00001X")
OTHER = "PA999999"  # someone else in the same files: must never leak into the pack


def _zip(entries: dict[str, dict]) -> bytes:
    import io

    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        for name, content in entries.items():
            archive.writestr(name, json.dumps(content))
    return buffer.getvalue()


def _acteur(uid: str, nom: str, born: str, mandats) -> dict:
    return {
        "acteur": {
            "uid": {"@xsi:type": "IdActeur_type", "#text": uid},
            "etatCivil": {
                "ident": {"civ": "M.", "prenom": "Alpha", "nom": nom},
                "infoNaissance": {"dateNais": born},
            },
            "mandats": {"mandat": mandats},
        }
    }


def _scrutin(uid: str, numero: str, pours: list[str], contres) -> dict:
    return {
        "scrutin": {
            "uid": uid,
            "numero": numero,
            "legislature": "17",
            "dateScrutin": "2025-03-04",
            "titre": "Scrutin fictif",
            "typeVote": {"codeTypeVote": "SPO", "libelleTypeVote": "scrutin public ordinaire"},
            "ventilationVotes": {
                "organe": {
                    "groupes": {
                        "groupe": [
                            {
                                "vote": {
                                    "decompteNominatif": {
                                        "nonVotants": None,
                                        "pours": {"votant": [{"acteurRef": a} for a in pours]}
                                        if pours
                                        else None,
                                        "contres": contres,
                                        "abstentions": None,
                                    }
                                }
                            }
                        ]
                    }
                }
            },
        }
    }


@pytest.fixture
def cache(tmp_path) -> Cache:
    cache = Cache(tmp_path / "cache")
    mandat_an = {
        "uid": "PM000001",
        "typeOrgane": "ASSEMBLEE",
        "legislature": "17",
        "dateDebut": "2024-07-07",
        "dateFin": None,
        "election": {"lieu": {"departement": "Département fictif", "numCirco": "1"}},
    }
    autre_mandat = {"uid": "PM000002", "typeOrgane": "GP", "dateDebut": "2024-07-07"}
    gouvernement = {
        "uid": "PM000003",
        "typeOrgane": "GOUVERNEMENT",
        "dateDebut": "2020-01-01",
        "dateFin": "2021-01-01",
        "organes": {"organeRef": "PO000009"},
    }
    cache.put(
        "an/acteurs.json.zip",
        assemblee.ACTEURS_URL,
        _zip(
            {
                "json/acteur/PA000001.json": _acteur(
                    "PA000001", "Exemple", "1970-01-01", [mandat_an, autre_mandat, gouvernement]
                ),
                "json/organe/PO000009.json": {"organe": {"uid": "PO000009", "libelleAbrege": "FICTIF"}},
                f"json/acteur/{OTHER}.json": _acteur(OTHER, "Autre", "1960-01-01", mandat_an),
            }
        ),
        "2026-10-01",
    )
    for legislature in assemblee.SCRUTIN_LEGISLATURES:
        # A single voter is exported as a dict, several as a list: both shapes are covered.
        cache.put(
            f"an/scrutins-{legislature}.json.zip",
            assemblee.scrutins_url(legislature),
            _zip(
                {
                    f"json/VTANR5L{legislature}V1.json": _scrutin(
                        f"VTANR5L{legislature}V1", "1", ["PA000001", OTHER], None
                    ),
                    f"json/VTANR5L{legislature}V2.json": _scrutin(
                        f"VTANR5L{legislature}V2", "2", [], {"votant": {"acteurRef": "PA000001"}}
                    ),
                }
            ),
            "2026-10-01",
        )
    cache.put(
        "senat/general.json",
        senat.GENERAL_URL,
        json.dumps(
            {
                "results": [
                    {"Matricule": "00001X", "Nom_usuel": "Exemple", "Date_naissance": "1970/01/01 00:00:00"}
                ]
            }
        ).encode(),
        "2026-10-01",
    )
    cache.put(
        "senat/mandats.json",
        senat.MANDATES_URL,
        json.dumps(
            {
                "results": [
                    {
                        "Matricule": "00001X",
                        "Identifiant_mandat": "7",
                        "Date_de_debut_de_mandat": "2014/10/01 00:00:00",
                        "Date_de_fin_de_mandat": "2020/09/30 00:00:00",
                    },
                    {
                        "Matricule": "99999Z",
                        "Identifiant_mandat": "8",
                        "Date_de_debut_de_mandat": "2014/10/01 00:00:00",
                    },
                ]
            }
        ).encode(),
        "2026-10-01",
    )
    cache.put(
        "rne/conseiller-municipal-cm.csv",
        "https://example.invalid/elus-conseiller-municipal-cm.csv",
        (
            "﻿Code du département;Libellé du département;Code de la commune;Libellé de la commune;"
            "Nom de l'élu;Prénom de l'élu;Code sexe;Date de naissance;Date de début du mandat;"
            "Libellé de la fonction\n"
            "00;Département fictif;00000;Commune fictive;EXEMPLE;Alpha;M;1970-01-01;2026-03-15;Maire\n"
            # Same name, other birth date: a homonym, never attached.
            "00;Département fictif;00001;Autre commune;EXEMPLE;Alpha;M;1980-05-05;2026-03-15;\n"
        ).encode(),
        "2026-10-01",
    )
    return cache


def test_assemblee_keeps_only_linked_person_and_assembly_mandates(cache):
    extract = assemblee.extract(cache, [ALPHA])
    assert [m.id for m in extract.mandates] == ["an-PM000001", "an-PM000003"]
    mandate = extract.mandates[0]
    government = extract.mandates[1]
    assert government.body == Body.GOVERNMENT
    assert government.label == "Membre du Gouvernement (FICTIF)"
    assert mandate.body == Body.AN and mandate.end_date is None
    assert "17e législature" in mandate.label and "circonscription n° 1" in mandate.label
    check_identities([replace(ALPHA, senat_id=None)], extract.people)


def test_identity_check_uses_facts_from_every_source(cache):
    people = assemblee.extract(cache, [ALPHA]).people + senat.extract(cache, [ALPHA]).people
    check_identities([ALPHA], people)


def test_assemblee_votes_keep_published_positions_and_official_link(cache):
    votes = assemblee.extract(cache, [ALPHA]).votes
    assert {(v.ballot_id, v.position) for v in votes} == {
        ("VTANR5L17V1", "pour"),
        ("VTANR5L17V2", "contre"),
        ("VTANR5L16V1", "pour"),
        ("VTANR5L16V2", "contre"),
    }
    assert all(v.candidate_id == "fictif-a" for v in votes)
    assert votes[0].ballot_url.startswith("https://www.assemblee-nationale.fr/dyn/")


@pytest.mark.parametrize(
    ("title", "subject"),
    [
        ("l'amendement n° 15 de M. X à l'article premier", "AMENDMENT"),
        ("les amendements identiques n° 3 et 4", "AMENDMENT"),
        ("le sous-amendement n° 2", "OTHER"),
        ("l'article 22 (examen prioritaire) du projet de loi", "ARTICLE"),
        ("l’ensemble du projet de loi de finances", "WHOLE_TEXT"),
        ("la motion de censure déposée en application de l'article 49", "MOTION"),
        ("la déclaration du Gouvernement", "OTHER"),
    ],
)
def test_ballot_subject_rules(title, subject):
    assert assemblee.ballot_subject(title) == subject


def test_assemblee_rejects_identity_with_other_birth_date(cache):
    wrong = Identity("fictif-a", "Exemple", "Alpha", date(1971, 1, 1), an_id="PA000001")
    with pytest.raises(IdentityMismatch):
        check_identities([wrong], assemblee.extract(cache, [wrong]).people)


def test_no_link_means_no_data(cache):
    unlinked = Identity("fictif-a", "Exemple", "Alpha", date(1970, 1, 1))
    assert assemblee.extract(cache, [unlinked]).votes == []
    assert senat.extract(cache, [unlinked]).mandates == []


def test_senat_mandates_and_person_facts(cache):
    extract = senat.extract(cache, [ALPHA])
    assert [(m.id, m.start_date, m.end_date) for m in extract.mandates] == [
        ("senat-7", date(2014, 10, 1), date(2020, 9, 30))
    ]
    assert extract.votes == []
    check_identities([replace(ALPHA, an_id=None)], extract.people)


def test_rne_requires_name_and_birth_date(cache):
    extract = rne.extract(cache, [ALPHA])
    assert len(extract.mandates) == 1
    mandate = extract.mandates[0]
    assert mandate.body == Body.LOCAL
    assert mandate.label == "Mandat de conseiller municipal — Commune fictive (Maire)"
    assert [s.id for s in extract.sources] == ["rne-conseiller-municipal-cm"]


def test_rne_mep_file_only_confirms_birth_dates_of_linked_meps(cache):
    cache.put(
        rne.MEP_KEY,
        "https://example.invalid/elus-representants-parlement-europeen-rpe.csv",
        (
            "\ufeffNom de l'élu;Prénom de l'élu;Code sexe;Date de naissance;Date de début du mandat\n"
            "EXEMPLE;Alpha;M;1970-01-01;2024-07-16\n"
            "EXEMPLE;Alpha;M;1980-05-05;2024-07-16\n"
        ).encode(),
        "2026-10-01",
    )
    assert rne.extract(cache, [ALPHA]).birth_records == []  # no pe_id: nothing to confirm
    extract = rne.extract(cache, [replace(ALPHA, pe_id="1")])
    assert extract.birth_records == [BirthRecord(Body.PE, "Exemple", "Alpha", date(1970, 1, 1))]
    assert len(extract.mandates) == 1  # the MEP file gives no mandate


def test_parlement_europeen_mandates_and_roll_call_votes(tmp_path):
    from citoyen_pipeline.sources import parlement_europeen as pe

    cache = Cache(tmp_path / "pe")
    identity = Identity("fictif-a", "Exemple", "Alpha", date(1970, 1, 1), pe_id="1")
    mep = {
        "data": [
            {
                "identifier": "1",
                "familyName": "Exemple",
                "givenName": "Alpha",
                "bday": "1970-01-01",
                "hasMembership": [
                    {
                        "identifier": "1-f-1",
                        "memberDuring": {"startDate": "2024-07-16"},
                        "organization": "org/ep-10",
                        "role": "def/ep-roles/MEMBER",
                        "membershipClassification": "def/ep-entities/EU_INSTITUTION",
                    },
                    {
                        "identifier": "1-f-2",
                        "memberDuring": {"startDate": "2024-07-16"},
                        "organization": "org/9",
                        "role": "def/ep-roles/MEMBER",
                        "membershipClassification": "def/ep-entities/COMMITTEE_PARLIAMENTARY_STANDING",
                    },
                ],
            }
        ]
    }
    cache.put("pe/mep-1.json", pe.mep_url("1"), json.dumps(mep).encode(), "2026-10-01")
    for year in range(2024, date.today().year + 1):
        meetings = {"data": [{"activity_id": f"MTG-PL-{year}-01-20"}]} if year == 2025 else {"data": []}
        cache.put(
            f"pe/meetings-{year}-0.json", pe.meetings_url(year), json.dumps(meetings).encode(), "2026-10-01"
        )
    decisions = {
        "data": [
            {
                "id": "eli/dl/event/MTG-PL-2025-01-20-DEC-1",
                "type": "Vote",
                "activity_id": "MTG-PL-2025-01-20-DEC-1",
                "activity_date": "2025-01-20",
                "activity_label": {"fr": "Décision fictive"},
                "decision_method": "def/ep-decision-methods/VOTE_ELECTRONIC_ROLLCALL",
                "had_voter_favor": ["person/1", "person/2"],
                "had_voter_against": ["person/3"],
            },
            {"id": "x", "type": "Activity", "activity_id": "MTG-PL-2025-01-20-OTHER"},
        ]
    }
    cache.put(
        "pe/decisions/MTG-PL-2025-01-20-0.json",
        pe.decisions_url("MTG-PL-2025-01-20"),
        json.dumps(decisions).encode(),
        "2026-10-01",
    )

    extract = pe.extract(cache, [identity])
    assert [(m.id, m.label) for m in extract.mandates] == [
        ("pe-1-f-1", "Mandat de député européen — 10e législature")
    ]
    assert [(v.ballot_id, v.position, v.ballot_type) for v in extract.votes] == [
        ("MTG-PL-2025-01-20-DEC-1", "favor", "VOTE_ELECTRONIC_ROLLCALL")
    ]
    assert (
        extract.votes[0].ballot_url == "https://data.europarl.europa.eu/eli/dl/event/MTG-PL-2025-01-20-DEC-1"
    )
    check_identities([identity], extract.people)


def test_assemble_end_to_end_with_identity_check(cache, fixtures):
    from citoyen_pipeline.assemble import assemble
    from citoyen_pipeline.model import Facet, Level

    pack = assemble(fixtures, cache, date(2026, 10, 1))
    assert {c.id for c in pack.candidates} == {"fictif-a", "fictif-b", "fictif-c"}
    assert sorted(m.body.value for m in pack.mandates) == ["AN", "GOVERNMENT", "LOCAL"]
    assert {v.candidate_id for v in pack.votes} == {"fictif-a"}
    assert pack.coverage[("fictif-a", Facet.VOTES)] == Level.AVAILABLE
    assert pack.coverage[("fictif-b", Facet.VOTES)] == Level.NOT_APPLICABLE
    # Themes: the fixture taxonomy plus "inconnu"; every vote is tagged (no rule matches here).
    assert len(pack.themes) == 16
    assert {tuple(t.theme_id for t in tags) for tags in pack.vote_themes.values()} == {("inconnu",)}
    assert set(pack.vote_themes) == {v.id for v in pack.votes}
    assert pack.coverage[("fictif-a", Facet.PROGRAM)] == Level.UNAVAILABLE


def test_vie_publique_statements_require_the_validated_name(tmp_path):
    from citoyen_pipeline.sources import discours

    cache = Cache(tmp_path / "vp")
    items = [
        {
            "id": "1",
            "titre": "Déclaration fictive",
            "url": "https://www.vie-publique.fr/discours/1-fictif",
            "prononciation": "2026-10-19",
            "intervenants": [{"nom": "Alpha Exemple", "qualite": "Qualité fictive"}],
            "type_document": "Interview",
            "media": "Média fictif",
        },
        {
            "id": "2",
            "titre": "Autre déclaration",
            "url": "https://www.vie-publique.fr/discours/2-fictif",
            "prononciation": "2026-10-20",
            "intervenants": [{"nom": "Alpha Exempl", "qualite": "Autre"}],  # near miss: never attached
        },
    ]
    cache.put("vie-publique/discours.json", discours.URL, json.dumps(items).encode(), "2026-10-21")

    without_alias = Identity("fictif-a", "Exemple", "Alpha", date(1970, 1, 1))
    assert discours.extract(cache, [without_alias]).speeches == []

    with_alias = replace(without_alias, vie_publique_name="Alpha Exemple")
    extract = discours.extract(cache, [with_alias])
    assert [(s.title, s.kind, s.detail) for s in extract.speeches] == [
        ("Déclaration fictive", "OFFICIAL_STATEMENT", "Interview — Média fictif")
    ]
    assert extract.review == {"vie_publique_name:Alpha Exemple": ["Qualité fictive"]}


def test_parlement_europeen_plenary_speeches(tmp_path):
    from citoyen_pipeline.sources import parlement_europeen as pe

    cache = Cache(tmp_path / "pe")
    identity = Identity("fictif-a", "Exemple", "Alpha", date(1970, 1, 1), pe_id="1")
    cache.put(
        "pe/mep-1.json",
        pe.mep_url("1"),
        json.dumps({"data": [{"familyName": "Exemple", "bday": "1970-01-01", "hasMembership": []}]}).encode(),
        "2026-10-01",
    )
    speeches = {
        "data": [
            {
                "id": "eli/dl/event/MTG-PL-2025-01-20-OTH-1",
                "activity_id": "MTG-PL-2025-01-20-OTH-1",
                "activity_date": "2025-01-20",
                "activity_label": {"fr": "Débat fictif (débat)"},
            }
        ]
    }
    cache.put("pe/speeches-1-0.json", pe.speeches_url("1"), json.dumps(speeches).encode(), "2026-10-01")

    extract = pe.extract(cache, [identity])
    assert [(s.title, s.body, s.url) for s in extract.speeches] == [
        (
            "Débat fictif (débat)",
            Body.PE,
            "https://data.europarl.europa.eu/eli/dl/event/MTG-PL-2025-01-20-OTH-1",
        )
    ]


def test_parlement_europeen_empty_answer_is_an_empty_page(tmp_path):
    """The API answers an empty body when a member has no speech (CI, 2026-10-28)."""
    from citoyen_pipeline.sources import parlement_europeen as pe

    cache = Cache(tmp_path / "pe")
    identity = Identity("fictif-a", "Exemple", "Alpha", date(1970, 1, 1), pe_id="1")
    cache.put(
        "pe/mep-1.json",
        pe.mep_url("1"),
        json.dumps({"data": [{"familyName": "Exemple", "bday": "1970-01-01", "hasMembership": []}]}).encode(),
        "2026-10-01",
    )
    cache.put("pe/speeches-1-0.json", pe.speeches_url("1"), b"", "2026-10-01")

    assert pe.extract(cache, [identity]).speeches == []


def test_lenient_build_leaves_out_a_failing_source(cache, fixtures, monkeypatch):
    """Personal-use builds keep the other sources when one cannot be read."""
    from citoyen_pipeline import assemble as assembly

    def broken(*_):
        raise RuntimeError("source unavailable")

    adapters = tuple(replace(a, extract=broken) if a.name == "assemblee" else a for a in assembly.ADAPTERS)
    monkeypatch.setattr(assembly, "ADAPTERS", adapters)
    with pytest.raises(RuntimeError):
        assembly.assemble_packs(fixtures, cache, date(2026, 10, 1))
    core, _, review = assembly.assemble_packs(fixtures, cache, date(2026, 10, 1), lenient_identities=True)
    assert review["sources left out"] == ["assemblee: RuntimeError: source unavailable"]
    assert "AN" not in {m.body.value for m in core.mandates}
    assert core.votes == []
