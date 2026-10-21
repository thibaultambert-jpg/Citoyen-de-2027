"""Writes the `core` pack as a SQLite file. Rows are inserted in a fixed order so that identical
inputs give identical files (reproducible builds, AGENTS.md §4)."""

from __future__ import annotations

import sqlite3
from pathlib import Path

from citoyen_pipeline import PACK_SCHEMA_VERSION
from citoyen_pipeline.model import CorePack

SCHEMA = """
CREATE TABLE meta (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL);
CREATE TABLE sources (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    url TEXT NOT NULL,
    retrieved_on TEXT NOT NULL,
    license TEXT NOT NULL
);
CREATE TABLE candidates (
    id TEXT PRIMARY KEY NOT NULL,
    display_name TEXT NOT NULL,
    sort_name TEXT NOT NULL,
    status TEXT NOT NULL,
    status_date TEXT NOT NULL,
    status_source_id TEXT NOT NULL REFERENCES sources(id)
);
CREATE TABLE candidate_coverage (
    candidate_id TEXT NOT NULL REFERENCES candidates(id),
    facet TEXT NOT NULL,
    level TEXT NOT NULL,
    PRIMARY KEY (candidate_id, facet)
);
CREATE TABLE mandates (
    id TEXT PRIMARY KEY NOT NULL,
    candidate_id TEXT NOT NULL REFERENCES candidates(id),
    body TEXT NOT NULL,
    label TEXT NOT NULL,
    start_date TEXT NOT NULL,
    end_date TEXT,
    source_id TEXT NOT NULL REFERENCES sources(id)
);
CREATE TABLE votes (
    id TEXT PRIMARY KEY NOT NULL,
    candidate_id TEXT NOT NULL REFERENCES candidates(id),
    body TEXT NOT NULL,
    ballot_id TEXT NOT NULL,
    ballot_date TEXT NOT NULL,
    ballot_title TEXT NOT NULL,
    ballot_type TEXT,
    ballot_subject TEXT,
    position TEXT NOT NULL,
    ballot_url TEXT NOT NULL,
    source_id TEXT NOT NULL REFERENCES sources(id)
);
"""


def pack_version(pack: CorePack, inputs_digest: str) -> str:
    return f"{pack.generated_on.isoformat()}+{inputs_digest[:12]}"


def write_core_pack(pack: CorePack, version: str, path: Path) -> None:
    referenced = (
        {c.status_source_id for c in pack.candidates}
        | {m.source_id for m in pack.mandates}
        | {v.source_id for v in pack.votes}
    )
    known = {s.id for s in pack.sources}
    if missing := referenced - known:
        raise ValueError(f"Facts reference unknown sources: {sorted(missing)}")

    path.parent.mkdir(parents=True, exist_ok=True)
    path.unlink(missing_ok=True)
    db = sqlite3.connect(path)
    try:
        db.executescript(SCHEMA)
        meta = {
            "schema_version": str(PACK_SCHEMA_VERSION),
            "pack_id": "core",
            "version": version,
            "generated_on": pack.generated_on.isoformat(),
            "inclusion_policy_date": pack.inclusion_policy_date.isoformat(),
        }
        db.executemany("INSERT INTO meta VALUES (?, ?)", sorted(meta.items()))
        db.executemany(
            "INSERT INTO sources VALUES (?, ?, ?, ?, ?)",
            [
                (s.id, s.name, s.url, s.retrieved_on.isoformat(), s.license)
                for s in sorted(pack.sources, key=lambda s: s.id)
                if s.id in referenced
            ],
        )
        db.executemany(
            "INSERT INTO candidates VALUES (?, ?, ?, ?, ?, ?)",
            [
                (c.id, c.display_name, c.sort_name, c.status, c.status_date.isoformat(), c.status_source_id)
                for c in sorted(pack.candidates, key=lambda c: c.id)
            ],
        )
        db.executemany(
            "INSERT INTO candidate_coverage VALUES (?, ?, ?)",
            [(cid, facet.value, level.value) for (cid, facet), level in sorted(pack.coverage.items())],
        )
        db.executemany(
            "INSERT INTO mandates VALUES (?, ?, ?, ?, ?, ?, ?)",
            [
                (
                    m.id,
                    m.candidate_id,
                    m.body.value,
                    m.label,
                    m.start_date.isoformat(),
                    m.end_date.isoformat() if m.end_date else None,
                    m.source_id,
                )
                for m in sorted(pack.mandates, key=lambda m: m.id)
            ],
        )
        db.executemany(
            "INSERT INTO votes VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            [
                (
                    v.id,
                    v.candidate_id,
                    v.body.value,
                    v.ballot_id,
                    v.ballot_date.isoformat(),
                    v.ballot_title,
                    v.ballot_type,
                    v.ballot_subject,
                    v.position,
                    v.ballot_url,
                    v.source_id,
                )
                for v in sorted(pack.votes, key=lambda v: v.id)
            ],
        )
        db.commit()
        db.execute("VACUUM")
    finally:
        db.close()
