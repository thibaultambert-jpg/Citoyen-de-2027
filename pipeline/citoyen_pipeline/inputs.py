"""Loading and validation of the hand-maintained inputs (candidates.yaml, identities.yaml)."""

from __future__ import annotations

import json
from datetime import date
from pathlib import Path

import yaml
from jsonschema import Draft202012Validator, FormatChecker

from citoyen_pipeline.model import Candidate, Identity, Source

SCHEMAS = Path(__file__).resolve().parent.parent / "schemas"


class InputError(ValueError):
    pass


def load_yaml(path: Path, schema_name: str) -> dict:
    # BaseLoader keeps every scalar as a string: no implicit date/bool conversion before validation.
    data = yaml.load(path.read_text(encoding="utf-8"), Loader=yaml.BaseLoader)
    if isinstance(data, dict) and "version" in data:
        data["version"] = int(data["version"])
    schema = json.loads((SCHEMAS / schema_name).read_text(encoding="utf-8"))
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    errors = sorted(validator.iter_errors(data), key=lambda e: list(e.absolute_path))
    if errors:
        details = "\n".join(f"- {'/'.join(map(str, e.absolute_path))}: {e.message}" for e in errors)
        raise InputError(f"{path} is invalid:\n{details}")
    return data


def load_candidates(path: Path) -> tuple[date, list[Candidate], list[Source]]:
    data = load_yaml(path, "candidates.schema.json")
    candidates, sources = [], []
    for item in data["candidates"]:
        src = item["status_source"]
        source = Source(
            id=f"status-{item['id']}",
            name=src["name"],
            url=src["url"],
            retrieved_on=date.fromisoformat(src["retrieved_on"]),
            license=src["license"],
        )
        sources.append(source)
        candidates.append(
            Candidate(
                id=item["id"],
                display_name=item["display_name"],
                sort_name=item["sort_name"],
                status=item["status"],
                status_date=date.fromisoformat(item["status_date"]),
                status_source_id=source.id,
            )
        )
    ids = [c.id for c in candidates]
    if len(ids) != len(set(ids)):
        raise InputError(f"{path}: duplicate candidate ids")
    return date.fromisoformat(data["inclusion_policy_date"]), candidates, sources


def load_identities(path: Path, candidate_ids: set[str]) -> list[Identity]:
    data = load_yaml(path, "identities.schema.json")
    identities = []
    for item in data["identities"]:
        if item["candidate_id"] not in candidate_ids:
            raise InputError(f"{path}: unknown candidate_id {item['candidate_id']}")
        identities.append(
            Identity(
                candidate_id=item["candidate_id"],
                family_name=item["family_name"],
                given_name=item["given_name"],
                birth_date=date.fromisoformat(item["birth_date"]),
                an_id=item.get("an_id"),
                senat_id=item.get("senat_id"),
                pe_id=item.get("pe_id"),
                vie_publique_name=item.get("vie_publique_name"),
            )
        )
    linked = [i.candidate_id for i in identities]
    if len(linked) != len(set(linked)):
        raise InputError(f"{path}: a candidate has several identity entries")
    return identities


def load_rag_config(path: Path) -> str:
    """Validated retrieval thresholds, returned as compact JSON for the core pack meta."""
    data = json.loads(path.read_text(encoding="utf-8"))
    schema = json.loads((SCHEMAS / "rag_config.schema.json").read_text(encoding="utf-8"))
    errors = list(Draft202012Validator(schema).iter_errors(data))
    if errors:
        raise InputError(f"{path} is invalid: {[e.message for e in errors]}")
    return json.dumps(data, sort_keys=True, separators=(",", ":"))


def load_sponsorship_names(path: Path, candidate_ids: set[str]) -> tuple[str, dict[str, str]]:
    """Dataset slug and hand-checked published names (sponsorships.yaml); absent file: no data."""
    if not path.exists():
        return "", {}
    data = load_yaml(path, "sponsorships.schema.json")
    if unknown := set(data["names"]) - candidate_ids:
        raise InputError(f"{path}: unknown candidate ids {sorted(unknown)}")
    if data["names"] and not data["dataset"]:
        raise InputError(f"{path}: names are given but no dataset")
    return data["dataset"], dict(data["names"])


__all__ = [
    "InputError",
    "load_candidates",
    "load_identities",
    "load_rag_config",
    "load_sponsorship_names",
    "load_yaml",
]
