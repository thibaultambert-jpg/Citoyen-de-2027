from pathlib import Path

import pytest

from citoyen_pipeline.inputs import InputError
from citoyen_pipeline.model import TagConfidence, TagMethod, ThemeTag
from citoyen_pipeline.themes import load_overrides, load_taxonomy, normalize

INPUTS = Path(__file__).resolve().parents[1] / "inputs"


@pytest.fixture
def taxonomy(fixtures):
    return load_taxonomy(fixtures / "themes.yaml")


def test_normalize_removes_case_and_accents():
    assert normalize("Sécurité SOCIALE à l'Hôpital") == "securite sociale a l'hopital"


def test_rule_tags_are_accent_insensitive_and_record_their_confidence(taxonomy):
    assert taxonomy.tag("Un MOTFICTIF01 isolé") == [ThemeTag("fictif-01", TagMethod.RULE, TagConfidence.LOW)]
    assert taxonomy.tag("motfictif02 et autremot02s") == [
        ThemeTag("fictif-02", TagMethod.RULE, TagConfidence.MEDIUM)
    ]


def test_rules_match_whole_words_only(taxonomy):
    assert taxonomy.tag("premotfictif01") == [ThemeTag("inconnu", TagMethod.RULE, TagConfidence.LOW)]


def test_several_themes_can_apply(taxonomy):
    assert [t.theme_id for t in taxonomy.tag("motfictif01, motfictif03")] == ["fictif-01", "fictif-03"]


def test_published_taxonomy_is_valid_and_neutral():
    taxonomy = load_taxonomy(INPUTS / "themes.yaml")
    assert 15 <= len(taxonomy.themes) <= 20
    assert taxonomy.unknown.id == "inconnu"
    assert [t.theme_id for t in taxonomy.tag("Projet de loi relatif à l'hôpital et aux soins")] == ["sante"]


def test_evaluative_label_is_refused(tmp_path, fixtures):
    text = (fixtures / "themes.yaml").read_text(encoding="utf-8").replace("Thème fictif 01", "Meilleur thème")
    (tmp_path / "themes.yaml").write_text(text, encoding="utf-8")
    with pytest.raises(InputError, match="evaluative"):
        load_taxonomy(tmp_path / "themes.yaml")


def test_invalid_pattern_is_refused(tmp_path, fixtures):
    text = (fixtures / "themes.yaml").read_text(encoding="utf-8").replace("'motfictif01'", "'motfictif01('")
    (tmp_path / "themes.yaml").write_text(text, encoding="utf-8")
    with pytest.raises(InputError, match="invalid pattern"):
        load_taxonomy(tmp_path / "themes.yaml")


def test_overrides_are_manual_and_checked(tmp_path, taxonomy):
    path = tmp_path / "theme_overrides.yaml"
    path.write_text("version: 1\noverrides:\n  - {target: ballot, id: s-1, themes: [fictif-02]}\n")
    overrides = load_overrides(path, taxonomy)
    assert overrides.manual(overrides.ballots["s-1"]) == [
        ThemeTag("fictif-02", TagMethod.MANUAL, TagConfidence.HIGH)
    ]
    path.write_text("version: 1\noverrides:\n  - {target: chunk, id: c-1, themes: [absent]}\n")
    with pytest.raises(InputError, match="unknown themes"):
        load_overrides(path, taxonomy)


def test_missing_overrides_file_means_none(tmp_path, taxonomy):
    assert load_overrides(tmp_path / "absent.yaml", taxonomy).ballots == {}
