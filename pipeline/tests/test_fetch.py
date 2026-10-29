import json
from datetime import UTC, datetime

from citoyen_pipeline import fetch as fetch_module
from citoyen_pipeline.fetch import Cache
from citoyen_pipeline.model import Identity
from citoyen_pipeline.sources import parlement_europeen as pe


def test_stable_files_survive_a_cache_restored_without_its_log(tmp_path, monkeypatch):
    calls = []
    monkeypatch.setattr(fetch_module, "_download", lambda url: calls.append(url) or b"{}")
    Cache(tmp_path).fetch("pe/a.json", "https://example.invalid/a", stable=True)
    Cache(tmp_path).fetch("pe/b.json", "https://example.invalid/b")
    (tmp_path / "fetch_log.json").unlink()  # a CI cache restores the files, not the shared log

    restored = Cache(tmp_path)
    restored.fetch("pe/a.json", "https://example.invalid/a", stable=True)
    restored.fetch("pe/b.json", "https://example.invalid/b", stable=True)
    assert calls == [
        "https://example.invalid/a",
        "https://example.invalid/b",
        "https://example.invalid/b",
    ]
    assert restored.has("pe/a.json")


def test_european_parliament_skips_the_rest_of_a_year_after_refused_sittings(tmp_path, monkeypatch):
    year = datetime.now(UTC).year - 1
    sittings = [f"MTG-PL-{year}-01-{day:02d}" for day in range(1, 10)]
    mep = {
        "data": [
            {
                "hasMembership": [
                    {
                        "membershipClassification": "def/ep-entities/EU_INSTITUTION",
                        "role": "def/ep-roles/MEMBER",
                        "organization": "org/ep-9",
                        "memberDuring": {"startDate": f"{year}-01-01", "endDate": f"{year}-12-31"},
                    }
                ]
            }
        ]
    }
    meetings = {"data": [{"activity_id": s} for s in sittings]}
    asked = []

    def download(url):
        asked.append(url)
        if "/decisions" in url:
            raise RuntimeError(f"Download failed after 3 attempts: {url}")
        if "/meetings?" in url:
            return json.dumps(meetings).encode()
        if "/speeches" in url:
            return b""
        return json.dumps(mep).encode()

    monkeypatch.setattr(fetch_module, "_download", download)
    pe.fetch(
        Cache(tmp_path), [Identity("fictif-a", "Exemple", "Alpha", datetime(1970, 1, 1).date(), pe_id="1")]
    )
    assert sum("/decisions" in url for url in asked) == pe.MAX_FAILED_SITTINGS
