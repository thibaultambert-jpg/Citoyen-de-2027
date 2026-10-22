"""Répertoire national des élus (ministère de l'Intérieur, data.gouv.fr, Licence Ouverte v2.0).

CSV files (';' separated, UTF-8 with BOM) verified on 2026-10-21 with tools/probe_sources.py. They hold
current mandates only, without any identifier: a row is attached to a candidate only when family
name, given name AND birth date all match the hand-validated identity (AGENTS.md §7.2).
Parliamentary files (deputies, senators, MEPs) are skipped: AN, Sénat and PE data are authoritative.
"""

from __future__ import annotations

import csv
import io
import json

from citoyen_pipeline.fetch import Cache
from citoyen_pipeline.identity import normalize_name
from citoyen_pipeline.model import Body, Identity, Mandate, Source, SourceExtract
from citoyen_pipeline.sources.common import parse_date, short_hash

LICENSE = "Licence Ouverte v2.0 (Etalab)"
DATASET_API = "https://www.data.gouv.fr/api/1/datasets/repertoire-national-des-elus-1/"

# File-name marker -> mandate role, for the local mandates kept from the RNE.
ROLES = {
    "conseiller-municipal-cm": "conseiller municipal",
    "maire-mai": "maire",
    "conseiller-communautaire-epci": "conseiller communautaire",
    "conseiller-departemental-cd": "conseiller départemental",
    "conseiller-regional-cr": "conseiller régional",
    "conseiller-darrondissement-ca": "conseiller d'arrondissement",
    "membre-dune-assemblee-ma": "membre d'une assemblée de collectivité",
    "conseillers-des-francais-de-letranger-cons": "conseiller des Français de l'étranger",
    "assemblee-des-francais-de-letranger-afe": "membre de l'Assemblée des Français de l'étranger",
}
# Most specific place first. Header names are compared after whitespace normalisation.
PLACE_COLUMNS = (
    "Libellé de la commune",
    "Libellé du secteur",
    "Libellé de la section départementale",
    "Libellé de la collectivité à statut particulier",
    "Libellé du canton",
    "Libellé du département",
    "Libellé de la région",
    "Libellé de la circonscription consulaire",
    "Libellé la circonscription AFE",
    "Libellé la circ. AFE",
)


def fetch(cache: Cache) -> None:
    listing = cache.fetch("rne/dataset.json", DATASET_API, refresh=True)
    for resource in json.loads(cache.path(listing.key).read_text(encoding="utf-8"))["resources"]:
        url = resource.get("url") or ""
        marker = _marker(url)
        if marker:
            cache.fetch(f"rne/{marker}.csv", url, refresh=True)


def _marker(url: str) -> str | None:
    name = url.rsplit("/", 1)[-1]
    return next((m for m in ROLES if m in name), None)


def extract(cache: Cache, identities: list[Identity]) -> SourceExtract:
    result = SourceExtract()
    if not identities:
        return result
    by_person = {
        (normalize_name(i.family_name), normalize_name(i.given_name), i.birth_date): i for i in identities
    }
    for marker, role in ROLES.items():
        key = f"rne/{marker}.csv"
        if not cache.has(key):
            continue
        entry = cache.entry(key)
        source = Source(
            id=f"rne-{marker}",
            name=f"Répertoire national des élus — {role}",
            url=entry.url,
            retrieved_on=entry.retrieved_date,
            license=LICENSE,
        )
        used = False
        text = cache.path(key).read_bytes().decode("utf-8-sig")
        for row in csv.DictReader(io.StringIO(text), delimiter=";"):
            row = {" ".join(k.split()): (v or "").strip() for k, v in row.items() if k}
            identity = by_person.get(
                (
                    normalize_name(row.get("Nom de l'élu", "")),
                    normalize_name(row.get("Prénom de l'élu", "")),
                    parse_date(row.get("Date de naissance")),
                )
            )
            start = parse_date(row.get("Date de début du mandat"))
            if identity is None or start is None:
                continue
            used = True
            result.mandates.append(
                Mandate(
                    id=f"rne-{marker}-{short_hash(*row.values())}",
                    candidate_id=identity.candidate_id,
                    body=Body.LOCAL,
                    label=_label(role, row),
                    start_date=start,
                    end_date=None,
                    source_id=source.id,
                )
            )
        if used:
            result.sources.append(source)
    return result


def _label(role: str, row: dict[str, str]) -> str:
    place = next((row[c] for c in PLACE_COLUMNS if row.get(c)), None)
    label = f"Mandat de {role}" + (f" — {place}" if place else "")
    function = row.get("Libellé de la fonction")
    return f"{label} ({function})" if function else label
