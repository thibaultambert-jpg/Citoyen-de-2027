import json
from datetime import date

from citoyen_pipeline.import_2027etmoi import import_data, senate_index, sort_name, split_name

TODAY = date(2026, 10, 28)


def _write(root, name, payload):
    path = root / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")


def _source(day="2026-05-03"):
    return {"titre": "Média fictif", "url": "https://example.invalid/annonce", "date": day}


def make_data(root):
    """Fictional data in the layout of 2027etmoi/2027etmoi data/ (checked on 28/10/2026)."""
    candidates = [
        {
            "id": "alpha",
            "nom": "Alpha Exemple",
            "statut": "declare",
            "source": _source(),
            "verifie_le": "2026-10-20",
        },
        {
            "id": "beta",
            "nom": "Beta de Fictif",
            "statut": "renonce",
            "source": _source(),
            "verifie_le": "2026-10-20",
        },
        {
            "id": "gamma",
            "nom": "Gamma Test",
            "statut": "pressenti",
            "source": _source(),
            "verifie_le": "2026-10-20",
        },
    ]
    _write(root, "candidats.json", {"mise_a_jour": "2026-10-28", "candidats": candidates})
    _write(root, "biographies/alpha.json", {"naissance": {"date": "1970-01-01"}})
    _write(root, "biographies/beta.json", {"naissance": {"date": "1971-02-02"}})
    an_page = {"source": {"url": "https://www.assemblee-nationale.fr/dyn/deputes/PA000001"}}
    pe_page = {"source": {"url": "https://www.europarl.europa.eu/meps/fr/123456"}}
    _write(root, "votes-an.json", {"mandats": {"alpha": [an_page, an_page]}})
    _write(root, "votes-pe.json", {"mandats": {"alpha": [pe_page]}})
    programme = {
        "titre": "Programme fictif",
        "url": "https://example.invalid/programme.pdf",
        "etat": "complet",
    }
    _write(
        root,
        "programmes/alpha.json",
        {
            "mise_a_jour": "2026-09-22",
            "programme": programme,
            "ressources": [{"url": programme["url"], "date": "2026-01-10"}],
        },
    )
    _write(root, "programmes/beta.json", {"programme": {"url": "https://example.invalid/b", "etat": "aucun"}})


def test_names_are_split_with_particles():
    assert split_name("Alpha Exemple") == ("Alpha", "Exemple")
    assert split_name("Beta de Fictif") == ("Beta", "de Fictif")
    assert split_name("Gamma Le Test") == ("Gamma", "Le Test")
    assert sort_name("Beta", "de Fictif") == "Fictif Beta"
    assert sort_name("Gamma", "Le Test") == "Le Test Gamma"


def test_declared_and_withdrawn_candidacies_are_imported(tmp_path):
    make_data(tmp_path)
    result = import_data(tmp_path, TODAY, check_program=lambda doc: "0" * 64)
    candidates = result.candidates["candidates"]
    assert [(c["id"], c["status"]) for c in candidates] == [("alpha", "DECLARE"), ("beta", "RETIRE")]
    alpha = candidates[0]
    assert alpha["status_date"] == "2026-05-03"
    assert alpha["status_source"]["url"] == "https://example.invalid/annonce"
    assert "2027 et moi" in alpha["status_source"]["name"]
    assert "gamma: status 'pressenti' not imported (not a declared candidacy)" in result.review


def test_identities_carry_official_ids_and_birth_date(tmp_path):
    make_data(tmp_path)
    senate = senate_index(
        [{"Matricule": "99001X", "Nom_usuel": "de FICTIF", "Date_naissance": "1971/02/02 00:00:00"}]
    )
    result = import_data(tmp_path, TODAY, senate=senate)
    alpha, beta = result.identities["identities"]
    assert (alpha["an_id"], alpha["pe_id"], alpha["birth_date"]) == ("PA000001", "123456", "1970-01-01")
    assert "senat_id" not in alpha
    assert (beta["family_name"], beta["given_name"], beta["senat_id"]) == ("de Fictif", "Beta", "99001X")


def test_programmes_are_kept_only_when_checked(tmp_path):
    make_data(tmp_path)
    kept = import_data(tmp_path, TODAY, check_program=lambda doc: "a" * 64).programs["programs"]
    assert len(kept) == 1
    doc = kept[0]["documents"][0]
    assert (kept[0]["status"], doc["format"], doc["published_on"], doc["sha256"]) == (
        "COMPLET",
        "pdf",
        "2026-01-10",
        "a" * 64,
    )
    left_out = import_data(tmp_path, TODAY, check_program=lambda doc: None)
    assert left_out.programs["programs"] == []
    assert any("programme https://example.invalid/programme.pdf left out" in r for r in left_out.review)
