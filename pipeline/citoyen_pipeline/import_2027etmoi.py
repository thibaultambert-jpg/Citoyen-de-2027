"""Prepares the hand-maintained inputs from the open data of « 2027 et moi » (personal use).

Decision of 28/10/2026 (AGENTS.md §1 bis): instead of typing candidates.yaml, identities.yaml and
programs/manifest.yaml by hand, they are generated from the data/ folder of
https://github.com/2027etmoi/2027etmoi (Licence Ouverte 2.0, attribution: « 2027 et moi », with the
date of each file). These files only PREPARE the inputs: every fact shown by the app still comes from
the official sources the pipeline fetches, and every identity is checked against them (identifier,
family name and birth date, AGENTS.md §7.2). Nothing else is taken from them: no party, political
bloc, poll, assessment, biography, judicial record or photo (AGENTS.md §2, §10).

Layout of the files read (checked on 28/10/2026):
- data/candidats.json: {mise_a_jour, candidats: [{id, nom, statut: declare|renonce|pressenti,
  source: {titre, url, date}, verifie_le, ...}]}
- data/biographies/<id>.json: {naissance: {date: YYYY-MM-DD, ...}, ...}
- data/votes-an.json, data/votes-pe.json: {mandats: {<id>: [{source: {url}}]}}; the official page
  URLs carry the Assemblée nationale (PAnnn) and European Parliament (meps/fr/nnn) identifiers.
- data/programmes/<id>.json: {programme: {titre, url, etat: complet|partiel|aucun}, ressources}
"""

from __future__ import annotations

import json
import re
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import date
from pathlib import Path

from citoyen_pipeline.identity import normalize_name
from citoyen_pipeline.sources.common import parse_date

ATTRIBUTION = "2027 et moi (https://2027etmoi.fr), Licence Ouverte 2.0"
STATUSES = {"declare": "DECLARE", "renonce": "RETIRE"}
PROGRAM_STATUSES = {"complet": "COMPLET", "partiel": "PARTIEL"}
PARTICLES = {"de", "du", "des", "d'", "le", "la", "van", "von"}
AN_ID = re.compile(r"/deputes/(PA\d+)")
PE_ID = re.compile(r"/meps/[a-z]{2}/(\d+)")


@dataclass
class Imported:
    candidates: dict
    identities: dict
    programs: dict
    review: list[str] = field(default_factory=list)


def split_name(full: str) -> tuple[str, str]:
    """(given, family): the family name is the last word, with the particles before it."""
    words = full.split()
    start = len(words) - 1
    while start > 1 and words[start - 1].lower() in PARTICLES:
        start -= 1
    return " ".join(words[:start]), " ".join(words[start:])


def sort_name(given: str, family: str) -> str:
    words = family.split()
    while len(words) > 1 and words[0].islower():
        words = words[1:]
    return f"{' '.join(words)} {given}"


def _read(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def _first_id(pattern: re.Pattern, mandates: list[dict]) -> str | None:
    found = {m.group(1) for item in mandates if (m := pattern.search(item.get("source", {}).get("url", "")))}
    return found.pop() if len(found) == 1 else None


def _iso(value: str | None) -> str | None:
    return value if value and re.fullmatch(r"\d{4}-\d{2}-\d{2}", value) else None


def senate_index(general: list[dict]) -> dict[tuple[str, date], list[str]]:
    """(normalised family name, birth date) -> Senate matricules, from ODSEN_GENERAL.json."""
    index: dict[tuple[str, date], list[str]] = {}
    for row in general:
        born = parse_date(row.get("Date_naissance"))
        if born and row.get("Nom_usuel") and row.get("Matricule"):
            index.setdefault((normalize_name(row["Nom_usuel"]), born), []).append(row["Matricule"])
    return index


def import_data(
    data: Path,
    today: date,
    senate: dict[tuple[str, date], list[str]] | None = None,
    check_program: Callable[[dict], str | None] | None = None,
) -> Imported:
    """[check_program] returns the sha256 of a usable programme document, or None to leave it out."""
    listing = _read(data / "candidats.json")
    an = _read(data / "votes-an.json")["mandats"] if (data / "votes-an.json").exists() else {}
    pe = _read(data / "votes-pe.json")["mandats"] if (data / "votes-pe.json").exists() else {}
    review: list[str] = []
    candidates, identities, programs = [], [], []
    for item in listing["candidats"]:
        cid, status = item["id"], STATUSES.get(item["statut"])
        if status is None:
            review.append(f"{cid}: status {item['statut']!r} not imported (not a declared candidacy)")
            continue
        source = item["source"]
        status_date = _iso(source.get("date")) or _iso(item.get("verifie_le"))
        if not (status_date and source.get("url", "").startswith("https://")):
            review.append(f"{cid}: no dated https source, not imported")
            continue
        given, family = split_name(item["nom"])
        if len(item["nom"].split()) > 2:
            review.append(f"{cid}: name {item['nom']!r} split as given {given!r} / family {family!r}")
        candidates.append(
            {
                "id": cid,
                "display_name": item["nom"],
                "sort_name": sort_name(given, family),
                "status": status,
                "status_date": status_date,
                "status_source": {
                    "name": f"{source['titre']} (relevé par {ATTRIBUTION})",
                    "url": source["url"],
                    "retrieved_on": _iso(item.get("verifie_le")) or listing["mise_a_jour"],
                    "license": "Article de presse : lien vers la source uniquement",
                },
            }
        )
        identity = _identity(
            data, cid, given, family, an.get(cid, []), pe.get(cid, []), senate, today, review
        )
        if identity:
            identities.append(identity)
        program = _program(data, cid, today, check_program, review)
        if program:
            programs.append(program)
    return Imported(
        candidates={"version": 1, "inclusion_policy_date": today.isoformat(), "candidates": candidates},
        identities={"version": 1, "identities": identities},
        programs={"version": 1, "programs": programs},
        review=review,
    )


def _identity(data, cid, given, family, an_mandates, pe_mandates, senate, today, review) -> dict | None:
    bio = data / "biographies" / f"{cid}.json"
    born = _iso(_read(bio).get("naissance", {}).get("date")) if bio.exists() else None
    if born is None:
        review.append(f"{cid}: no birth date, no identity (no mandate or vote can be attached)")
        return None
    identity = {
        "candidate_id": cid,
        "family_name": family,
        "given_name": given,
        "birth_date": born,
        "validated_by": f"import {ATTRIBUTION}, checked by the pipeline against official sources",
        "validated_on": today.isoformat(),
    }
    if an_id := _first_id(AN_ID, an_mandates):
        identity["an_id"] = an_id
    if pe_id := _first_id(PE_ID, pe_mandates):
        identity["pe_id"] = pe_id
    if senate is not None:
        matches = senate.get((normalize_name(family), date.fromisoformat(born)), [])
        if len(matches) == 1:
            identity["senat_id"] = matches[0]
        elif len(matches) > 1:
            review.append(f"{cid}: several Senate records share name and birth date, none linked")
    return identity


def _program(data, cid, today, check_program, review) -> dict | None:
    path = data / "programmes" / f"{cid}.json"
    if not path.exists():
        return None
    payload = _read(path)
    main = payload.get("programme") or {}
    status = PROGRAM_STATUSES.get(main.get("etat"))
    url = main.get("url") or ""
    if status is None or not url.startswith("https://"):
        return None
    published = next((r.get("date") for r in payload.get("ressources", []) if r.get("url") == url), None)
    published_on = _iso(published) or _iso(payload.get("mise_a_jour")) or today.isoformat()
    doc = {
        "id": f"{cid}-programme",
        "title": main.get("titre") or "Programme",
        "url": url,
        "format": "pdf" if url.lower().split("?")[0].endswith(".pdf") else "html",
        "published_on": published_on,
        "version": f"relevé du {payload.get('mise_a_jour') or today.isoformat()} ({ATTRIBUTION})",
        "reuse_rights": {"terms": "Usage personnel (lien vers la source)", "validated_on": today.isoformat()},
    }
    sha256 = check_program(doc) if check_program else None
    if sha256 is None:
        review.append(f"{cid}: programme {url} left out (not downloadable or no extractable text)")
        return None
    return {"candidate_id": cid, "status": status, "documents": [{**doc, "sha256": sha256}]}
