"""Writes the `core` and `programs` packs as SQLite files. Rows are inserted in a fixed order so
that identical inputs give identical files (reproducible builds, AGENTS.md §4)."""

from __future__ import annotations

import hashlib
import json
import sqlite3
from pathlib import Path

from citoyen_pipeline import PACK_SCHEMA_VERSION
from citoyen_pipeline.model import CorePack, ProgramsPack, ThemeTag

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
CREATE TABLE speeches (
    id TEXT PRIMARY KEY NOT NULL,
    candidate_id TEXT NOT NULL REFERENCES candidates(id),
    kind TEXT NOT NULL,
    body TEXT,
    speech_date TEXT NOT NULL,
    title TEXT NOT NULL,
    detail TEXT,
    url TEXT NOT NULL,
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
CREATE TABLE themes (id TEXT PRIMARY KEY NOT NULL, label TEXT NOT NULL);
CREATE TABLE vote_themes (
    vote_id TEXT NOT NULL REFERENCES votes(id),
    theme_id TEXT NOT NULL REFERENCES themes(id),
    method TEXT NOT NULL,
    confidence TEXT NOT NULL,
    PRIMARY KEY (vote_id, theme_id)
);
"""

PROGRAMS_SCHEMA = """
CREATE TABLE meta (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL);
CREATE TABLE program_status (candidate_id TEXT PRIMARY KEY NOT NULL, status TEXT NOT NULL);
CREATE TABLE program_documents (
    id TEXT PRIMARY KEY NOT NULL,
    candidate_id TEXT NOT NULL,
    title TEXT NOT NULL,
    url TEXT NOT NULL,
    format TEXT NOT NULL,
    published_on TEXT NOT NULL,
    version TEXT NOT NULL,
    sha256 TEXT NOT NULL,
    retrieved_on TEXT NOT NULL,
    license TEXT NOT NULL
);
CREATE TABLE program_chunks (
    id TEXT PRIMARY KEY NOT NULL,
    document_id TEXT NOT NULL REFERENCES program_documents(id),
    candidate_id TEXT NOT NULL,
    ordinal INTEGER NOT NULL,
    heading_path TEXT NOT NULL,
    page INTEGER,
    anchor TEXT,
    text TEXT NOT NULL,
    token_count INTEGER NOT NULL
);
CREATE TABLE chunk_themes (
    chunk_id TEXT NOT NULL REFERENCES program_chunks(id),
    theme_id TEXT NOT NULL,
    method TEXT NOT NULL,
    confidence TEXT NOT NULL,
    PRIMARY KEY (chunk_id, theme_id)
);
"""
# Heading paths are stored as one text, headings separated by a line feed (headings never contain
# one: whitespace is normalised at extraction).
HEADING_SEPARATOR = "\n"


def pack_version(pack: CorePack, inputs_digest: str) -> str:
    return f"{pack.generated_on.isoformat()}+{inputs_digest[:12]}"


def write_core_pack(pack: CorePack, version: str, path: Path) -> None:
    referenced = (
        {c.status_source_id for c in pack.candidates}
        | {m.source_id for m in pack.mandates}
        | {v.source_id for v in pack.votes}
        | {s.source_id for s in pack.speeches}
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
        db.executemany(
            "INSERT INTO speeches VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            [
                (
                    s.id,
                    s.candidate_id,
                    s.kind,
                    s.body.value if s.body else None,
                    s.speech_date.isoformat(),
                    s.title,
                    s.detail,
                    s.url,
                    s.source_id,
                )
                for s in sorted(pack.speeches, key=lambda s: s.id)
            ],
        )
        db.executemany(
            "INSERT INTO themes VALUES (?, ?)",
            [(t.id, t.label) for t in sorted(pack.themes, key=lambda t: t.id)],
        )
        vote_ids = {v.id for v in pack.votes}
        theme_ids = {t.id for t in pack.themes}
        db.executemany(
            "INSERT INTO vote_themes VALUES (?, ?, ?, ?)", _tag_rows(pack.vote_themes, vote_ids, theme_ids)
        )
        db.commit()
        db.execute("VACUUM")
    finally:
        db.close()


def _tag_rows(tags: dict[str, list[ThemeTag]], item_ids: set[str], theme_ids: set[str]) -> list[tuple]:
    rows = []
    for item_id, item_tags in sorted(tags.items()):
        if item_id not in item_ids:
            raise ValueError(f"Theme tags for an unknown item: {item_id}")
        for tag in sorted(item_tags, key=lambda t: t.theme_id):
            if tag.theme_id not in theme_ids:
                raise ValueError(f"Unknown theme {tag.theme_id} on {item_id}")
            rows.append((item_id, tag.theme_id, tag.method.value, tag.confidence.value))
    return rows


def write_programs_pack(
    pack: ProgramsPack, theme_ids: set[str], path: Path, version: str | None = None
) -> str:
    """Writes the pack and returns its version: by default a digest of its rows, so that the version
    (hence a download by the app) only changes when the programme content changes."""
    candidates = set(pack.statuses)
    documents = {d.id for d in pack.documents}
    if stray := {d.candidate_id for d in pack.documents} - candidates:
        raise ValueError(f"Programme documents of unknown candidates: {sorted(stray)}")
    if stray := {c.document_id for c in pack.chunks} - documents:
        raise ValueError(f"Chunks of unknown documents: {sorted(stray)}")

    tables = {
        "program_status": [(cid, status.value) for cid, status in sorted(pack.statuses.items())],
        "program_documents": [
            (
                d.id,
                d.candidate_id,
                d.title,
                d.url,
                d.format,
                d.published_on.isoformat(),
                d.version,
                d.sha256,
                d.retrieved_on.isoformat(),
                d.license,
            )
            for d in sorted(pack.documents, key=lambda d: d.id)
        ],
        "program_chunks": [
            (
                c.id,
                c.document_id,
                c.candidate_id,
                c.ordinal,
                HEADING_SEPARATOR.join(c.heading_path),
                c.page,
                c.anchor,
                c.text,
                c.token_count,
            )
            for c in sorted(pack.chunks, key=lambda c: c.id)
        ],
        "chunk_themes": _tag_rows(pack.chunk_themes, {c.id for c in pack.chunks}, theme_ids),
    }
    if version is None:
        digest = hashlib.sha256(json.dumps([PACK_SCHEMA_VERSION, tables], ensure_ascii=False).encode())
        version = f"programs-{digest.hexdigest()[:12]}"

    path.parent.mkdir(parents=True, exist_ok=True)
    path.unlink(missing_ok=True)
    db = sqlite3.connect(path)
    try:
        db.executescript(PROGRAMS_SCHEMA)
        meta = {
            "schema_version": str(PACK_SCHEMA_VERSION),
            "pack_id": "programs",
            "version": version,
            "generated_on": pack.generated_on.isoformat(),
        }
        db.executemany("INSERT INTO meta VALUES (?, ?)", sorted(meta.items()))
        for table, rows in tables.items():
            if rows:
                placeholders = ", ".join("?" * len(rows[0]))
                db.executemany(f"INSERT INTO {table} VALUES ({placeholders})", rows)
        db.commit()
        db.execute("VACUUM")
    finally:
        db.close()
    return version
