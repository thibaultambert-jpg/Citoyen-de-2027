from datetime import date

import pytest

from citoyen_pipeline.inputs import InputError, load_candidates, load_identities


def test_fixture_candidates_load(fixtures):
    policy_date, candidates, sources = load_candidates(fixtures / "candidates.yaml")
    assert policy_date == date(2026, 1, 1)
    assert [c.id for c in candidates] == ["fictif-a", "fictif-b", "fictif-c"]
    assert {s.id for s in sources} == {c.status_source_id for c in candidates}


def test_repository_inputs_are_valid_and_contain_no_data_yet():
    from pathlib import Path

    inputs = Path(__file__).parent.parent / "inputs"
    _, candidates, _ = load_candidates(inputs / "candidates.yaml")
    assert load_identities(inputs / "identities.yaml", {c.id for c in candidates}) == []


def test_invalid_candidate_is_rejected(tmp_path):
    path = tmp_path / "candidates.yaml"
    path.write_text(
        "version: 1\ninclusion_policy_date: 2026-01-01\ncandidates:\n"
        "  - {id: Bad Id, display_name: X, sort_name: X, status: FAVORI, status_date: 2026-13-01,"
        " status_source: {name: s, url: 'http://example.invalid', retrieved_on: 2026-01-01, license: l}}\n",
        encoding="utf-8",
    )
    with pytest.raises(InputError) as error:
        load_candidates(path)
    message = str(error.value)
    assert "id" in message and "status" in message and "url" in message


def test_identity_for_unknown_candidate_is_rejected(fixtures):
    with pytest.raises(InputError):
        load_identities(fixtures / "identities.yaml", {"fictif-b"})


def test_rag_config_is_validated_and_compacted(tmp_path, fixtures):
    import json

    import pytest

    from citoyen_pipeline.inputs import InputError, load_rag_config

    compact = load_rag_config(fixtures / "rag_config.json")
    assert json.loads(compact)["min_matched_terms"] >= 1 and " " not in compact
    bad = tmp_path / "rag_config.json"
    bad.write_text('{"top_k": 0, "pool_size": 30, "rrf_k": 60, "min_matched_terms": 1, "min_coverage": 2}')
    with pytest.raises(InputError):
        load_rag_config(bad)


def test_reserve_flag_is_validated_and_compacted(tmp_path, fixtures):
    import json

    import pytest

    from citoyen_pipeline.inputs import InputError, load_reserve

    reserve = json.loads(load_reserve(fixtures / "reserve.json"))
    assert reserve == {"hide_campaign_content": False, "since": None}
    bad = tmp_path / "reserve.json"
    bad.write_text('{"hide_campaign_content": "oui"}')
    with pytest.raises(InputError):
        load_reserve(bad)
