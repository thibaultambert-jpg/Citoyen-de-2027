"""Parrainages (sponsorships) validated by the Conseil constitutionnel, published on data.gouv.fr.

Format verified on 2026-10-22 with tools/probe_sources.py on the 2022 dataset (organisation Conseil
constitutionnel, licence "other-pd"): one CSV resource per publication, cumulative, ';' separated,
UTF-8 with BOM, columns Civilité;Nom;Prénom;Mandat;Circonscription;Département;Candidat;Date de
publication (DD/MM/YYYY). The 2017 dataset named the candidate column "Candidat-e parrainé-e".
TODO(verify): the 2027 dataset (slug, columns) once published; the parser fails on an unknown header.

Rows are attached to a candidate only through the name written by hand in inputs/sponsorships.yaml,
compared exactly: never by fuzzy or automatic matching (AGENTS.md §7.2). Only counts are kept; the
names of the elected sponsors are not copied into the pack.
"""

from __future__ import annotations

import csv
import io
import json
from collections import Counter
from dataclasses import dataclass
from datetime import date, datetime

from citoyen_pipeline.fetch import Cache
from citoyen_pipeline.model import Source, SponsorshipCount

DATASET_API = "https://www.data.gouv.fr/api/1/datasets/{slug}/"
SOURCE_NAME = "Conseil constitutionnel, parrainages validés (data.gouv.fr)"
CANDIDATE_COLUMNS = ("Candidat", "Candidat-e parrainé-e")
REQUIRED_COLUMNS = ("Mandat", "Département", "Date de publication")
DATASET_KEY = "sponsorships/dataset.json"
FILE_KEY = "sponsorships/latest.csv"


@dataclass(frozen=True)
class Row:
    candidate: str
    mandate: str
    department: str
    published_on: date


def fetch(cache: Cache, dataset: str) -> None:
    """Downloads the most recent CSV resource of the dataset (files are cumulative)."""
    listing = cache.fetch(DATASET_KEY, DATASET_API.format(slug=dataset), refresh=True)
    data = json.loads(cache.path(listing.key).read_text(encoding="utf-8"))
    csvs = [r for r in data.get("resources", []) if (r.get("format") or "").lower() == "csv" and r.get("url")]
    if not csvs:
        raise ValueError(f"data.gouv.fr dataset {dataset}: no CSV resource")
    latest = max(csvs, key=lambda r: r.get("last_modified") or "")
    cache.fetch(FILE_KEY, latest["url"], refresh=True)


def parse(body: bytes) -> list[Row]:
    reader = csv.DictReader(io.StringIO(body.decode("utf-8-sig")), delimiter=";")
    header = [h.strip() for h in reader.fieldnames or []]
    candidate_column = next((c for c in CANDIDATE_COLUMNS if c in header), None)
    missing = [c for c in REQUIRED_COLUMNS if c not in header]
    if candidate_column is None or missing:
        raise ValueError(
            f"Unknown sponsorship file header {header}: check the format (tools/probe_sources.py)"
        )
    rows = []
    for raw in reader:
        item = {k.strip(): (v or "").strip() for k, v in raw.items() if k}
        rows.append(
            Row(
                candidate=item[candidate_column],
                mandate=item["Mandat"],
                department=item["Département"],
                published_on=datetime.strptime(item["Date de publication"], "%d/%m/%Y").date(),
            )
        )
    return rows


def aggregate(
    rows: list[Row], names: dict[str, str], source_id: str
) -> tuple[list[SponsorshipCount], list[str]]:
    """Counts per candidate of the hand-written names; returns the counts and review notes."""
    if not rows:
        return [], ["the sponsorship file has no row"]
    published_on = max(r.published_on for r in rows)
    by_name: dict[str, list[Row]] = {}
    for row in rows:
        by_name.setdefault(row.candidate, []).append(row)
    counts, review = [], []
    for candidate_id, name in sorted(names.items()):
        own = by_name.get(name, [])
        if not own:
            review.append(f"{candidate_id}: no row for the name {name!r} (check its exact spelling)")
        counts.append(
            SponsorshipCount(
                candidate_id=candidate_id,
                total=len(own),
                departments=len({r.department for r in own}),
                by_mandate=tuple(sorted(Counter(r.mandate for r in own).items())),
                published_on=published_on,
                source_id=source_id,
            )
        )
    mapped = set(names.values())
    review += [f"published name not linked to a candidate: {n!r}" for n in sorted(set(by_name) - mapped)]
    return counts, review


def extract(cache: Cache, names: dict[str, str]) -> tuple[list[SponsorshipCount], list[Source], list[str]]:
    if not cache.has(FILE_KEY):
        return [], [], ["sponsorship file not fetched"]
    entry = cache.entry(FILE_KEY)
    license_id = json.loads(cache.path(DATASET_KEY).read_text(encoding="utf-8")).get("license") or "?"
    source = Source(
        id="sponsorships",
        name=SOURCE_NAME,
        url=entry.url,
        retrieved_on=entry.retrieved_date,
        license=f"data.gouv.fr : {license_id}",
    )
    counts, review = aggregate(parse(cache.path(FILE_KEY).read_bytes()), names, source.id)
    return counts, [source], review
