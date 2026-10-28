"""Sénat open data (data.senat.fr, dataset "Les Sénateurs", Licence Ouverte).

Structure verified on 2026-10-21 with tools/probe_sources.py:
- ODSEN_GENERAL.json: {"results": [{Matricule, Qualite, Nom_usuel, Prenom_usuel, Date_naissance, ...}]}
- ODSEN_ELUSEN.json: {"results": [{Matricule, Identifiant_mandat, Date_de_debut_de_mandat,
  Date_de_fin_de_mandat, Motif_debut_de_mandat, Motif_fin_de_mandat, ...}]} (dates "YYYY/MM/DD HH:MM:SS")
No open dataset of the Senate's individual public votes was found on data.senat.fr: Senate votes
are therefore reported as unavailable (coverage), never as absent.
"""

from __future__ import annotations

import json

from citoyen_pipeline.fetch import Cache
from citoyen_pipeline.model import Body, Identity, Mandate, PersonFacts, Source, SourceExtract
from citoyen_pipeline.sources.common import parse_date, text

LICENSE = "Licence Ouverte (Etalab)"
BASE = "https://data.senat.fr/data/senateurs"
GENERAL_URL = f"{BASE}/ODSEN_GENERAL.json"
MANDATES_URL = f"{BASE}/ODSEN_ELUSEN.json"


def fetch(cache: Cache) -> None:
    cache.fetch("senat/general.json", GENERAL_URL)
    cache.fetch("senat/mandats.json", MANDATES_URL)


def extract(cache: Cache, identities: list[Identity]) -> SourceExtract:
    linked = {i.senat_id: i for i in identities if i.senat_id}
    result = SourceExtract()
    if not linked:
        return result

    general = cache.entry("senat/general.json")
    for row in json.loads(cache.path(general.key).read_text(encoding="utf-8"))["results"]:
        matricule = text(row.get("Matricule"))
        if matricule in linked:
            result.people.append(
                PersonFacts(
                    body=Body.SENAT,
                    source_person_id=matricule,
                    family_name=text(row.get("Nom_usuel")) or "",
                    birth_date=parse_date(row.get("Date_naissance")),
                )
            )

    entry = cache.entry("senat/mandats.json")
    source = Source(
        id="senat-mandats",
        name="Sénat — mandats sénatoriaux (jeu « Les Sénateurs »)",
        url=entry.url,
        retrieved_on=entry.retrieved_date,
        license=LICENSE,
    )
    result.sources.append(source)
    for row in json.loads(cache.path(entry.key).read_text(encoding="utf-8"))["results"]:
        matricule = text(row.get("Matricule"))
        start = parse_date(row.get("Date_de_debut_de_mandat"))
        if matricule not in linked or start is None:
            continue
        result.mandates.append(
            Mandate(
                id=f"senat-{text(row.get('Identifiant_mandat'))}",
                candidate_id=linked[matricule].candidate_id,
                body=Body.SENAT,
                label="Mandat de sénateur",
                start_date=start,
                end_date=parse_date(row.get("Date_de_fin_de_mandat")),
                source_id=source.id,
            )
        )
    return result
