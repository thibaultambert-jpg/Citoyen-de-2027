"""Official statements published by vie-publique.fr (DILA): metadata and official link only, no full text
(AGENTS.md §6).

Structure verified on 2026-10-21 with tools/probe_sources.py: vp_discours.json is a JSON list (~240 MB)
of {id, titre, url, prononciation (date), intervenants: [{nom, qualite}], type_document, media, ...}.
Speakers carry no identifier: a statement is attached only through the hand-validated
`vie_publique_name` of identities.yaml, and the build report lists every `qualite` found under that
name so that a human can spot a homonym.
"""

from __future__ import annotations

import json

from citoyen_pipeline.fetch import Cache
from citoyen_pipeline.model import Identity, Source, SourceExtract, Speech
from citoyen_pipeline.sources.common import as_list, parse_date, text

URL = "https://echanges.dila.gouv.fr/OPENDATA/DISCOURS_PUBLICS/vp_discours.json"
# TODO(verify): reuse licence of the DILA "discours publics" dataset (metadata only are packed).
LICENSE = "Licence Ouverte (DILA)"


def fetch(cache: Cache) -> None:
    cache.fetch("vie-publique/discours.json", URL, refresh=True)


def extract(cache: Cache, identities: list[Identity]) -> SourceExtract:
    by_name = {i.vie_publique_name: i for i in identities if i.vie_publique_name}
    result = SourceExtract()
    if not by_name:
        return result
    entry = cache.entry("vie-publique/discours.json")
    source = Source(
        id="vie-publique-discours",
        name="vie-publique.fr (DILA) — discours publics : métadonnées",
        url=entry.url,
        retrieved_on=entry.retrieved_date,
        license=LICENSE,
    )
    qualities: dict[str, set[str]] = {name: set() for name in by_name}
    for item in json.loads(cache.path(entry.key).read_text(encoding="utf-8")):
        url, title, day = (
            text(item.get("url")),
            text(item.get("titre")),
            parse_date(item.get("prononciation")),
        )
        if not (url and url.startswith("https://") and title and day):
            continue
        for speaker in as_list(item.get("intervenants")):
            identity = by_name.get(text(speaker.get("nom")))
            if identity is None:
                continue
            qualities[identity.vie_publique_name].add(text(speaker.get("qualite")) or "(sans qualité)")
            detail = " — ".join(p for p in (text(item.get("type_document")), text(item.get("media"))) if p)
            result.speeches.append(
                Speech(
                    id=f"vp-{text(item.get('id'))}-{identity.candidate_id}",
                    candidate_id=identity.candidate_id,
                    kind="OFFICIAL_STATEMENT",
                    body=None,
                    speech_date=day,
                    title=title,
                    detail=detail or None,
                    url=url,
                    source_id=source.id,
                )
            )
    if result.speeches:
        result.sources.append(source)
    result.review = {f"vie_publique_name:{name}": sorted(found) for name, found in qualities.items()}
    return result
