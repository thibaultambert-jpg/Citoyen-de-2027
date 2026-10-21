"""Assemblée nationale open data (data.assemblee-nationale.fr, Licence Ouverte).

Files and JSON structure verified on 2026-10-21 with tools/probe_sources.py:
- AMO30 "tous acteurs, tous mandats, tous organes (historique)": json/acteur/PA*.json with
  acteur.uid(#text), etatCivil.ident.{nom,prenom}, etatCivil.infoNaissance.dateNais,
  mandats.mandat[] (typeOrgane, legislature, dateDebut, dateFin, election.lieu).
  Government memberships are mandats with typeOrgane "GOUVERNEMENT" whose organe
  (json/organe/PO*.json) has libelle "Gouvernement" and libelleAbrege naming the government.
- Scrutins: json/VT*.json with scrutin.{uid, numero, legislature, dateScrutin, titre,
  typeVote.libelleTypeVote, ventilationVotes.organe.groupes.groupe[].vote.decompteNominatif}
  where decompteNominatif.{pours,contres,abstentions,nonVotants}.votant[] carry acteurRef.
"""

from __future__ import annotations

import json
import re
import zipfile
from collections.abc import Iterator

from citoyen_pipeline.fetch import Cache
from citoyen_pipeline.model import Body, Identity, Mandate, PersonFacts, Source, SourceExtract, Vote
from citoyen_pipeline.sources.common import as_list, parse_date, text

LICENSE = "Licence Ouverte (Etalab)"
REPOSITORY = "https://data.assemblee-nationale.fr/static/openData/repository"
ACTEURS_URL = (
    f"{REPOSITORY}/17/amo/tous_acteurs_mandats_organes_xi_legislature/"
    "AMO30_tous_acteurs_tous_mandats_tous_organes_historique.json.zip"
)
# Legislatures whose public votes are packed (current one first). Older ones: TODO(verify) archive URLs.
SCRUTIN_LEGISLATURES = (17, 16)

# decompteNominatif keys -> position name as published in the decompteVoix block of the same file.
POSITIONS = {"pours": "pour", "contres": "contre", "abstentions": "abstentions", "nonVotants": "nonVotants"}


def scrutins_url(legislature: int) -> str:
    return f"{REPOSITORY}/{legislature}/loi/scrutins/Scrutins.json.zip"


# Official page of a scrutin (pattern checked with the `urls` probe on 2026-10-21: HTTP 200).
BALLOT_PAGE = "https://www.assemblee-nationale.fr/dyn/{legislature}/scrutins/{numero}"


# Subject of a ballot from the start of its official title (AGENTS.md §10: amendment, article, whole text).
# Explicit, ordered rules; anything else stays "OTHER", never guessed.
SUBJECT_RULES = (
    (re.compile(r"^(l'|les )(sous-)?amendements?\b", re.IGNORECASE), "AMENDMENT"),
    (re.compile(r"^(l'|les )articles?\b", re.IGNORECASE), "ARTICLE"),
    (re.compile(r"^l'ensemble\b", re.IGNORECASE), "WHOLE_TEXT"),
    (re.compile(r"^(la|les) motions?\b", re.IGNORECASE), "MOTION"),
)


def ballot_subject(title: str) -> str:
    normalized = title.strip().replace("’", "'")
    return next((subject for pattern, subject in SUBJECT_RULES if pattern.search(normalized)), "OTHER")


def fetch(cache: Cache) -> None:
    cache.fetch("an/acteurs.json.zip", ACTEURS_URL)
    for legislature in SCRUTIN_LEGISLATURES:
        cache.fetch(f"an/scrutins-{legislature}.json.zip", scrutins_url(legislature))


def _json_entries(path, folder: str) -> Iterator[dict]:
    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            if f"/{folder}/" in f"/{name}" and name.endswith(".json"):
                yield json.loads(archive.read(name))


def extract(cache: Cache, identities: list[Identity]) -> SourceExtract:
    linked = {i.an_id: i for i in identities if i.an_id}
    result = SourceExtract()
    if not linked:
        return result

    acteurs = cache.entry("an/acteurs.json.zip")
    acteurs_source = Source(
        id="an-acteurs",
        name="Assemblée nationale — acteurs, mandats et organes (historique)",
        url=acteurs.url,
        retrieved_on=acteurs.retrieved_date,
        license=LICENSE,
    )
    result.sources.append(acteurs_source)
    organes = {}
    for record in _json_entries(cache.path(acteurs.key), "organe"):
        organe = record.get("organe", {})
        organes[text(organe.get("uid"))] = organe
    for record in _json_entries(cache.path(acteurs.key), "acteur"):
        acteur = record["acteur"]
        uid = text(acteur.get("uid"))
        if uid not in linked:
            continue
        civil = acteur.get("etatCivil", {})
        result.people.append(
            PersonFacts(
                body=Body.AN,
                source_person_id=uid,
                family_name=text(civil.get("ident", {}).get("nom")) or "",
                birth_date=parse_date(civil.get("infoNaissance", {}).get("dateNais")),
            )
        )
        for mandat in as_list(acteur.get("mandats", {}).get("mandat")):
            kind = mandat.get("typeOrgane")
            if kind not in ("ASSEMBLEE", "GOUVERNEMENT"):
                continue
            start = parse_date(mandat.get("dateDebut"))
            if start is None:
                continue
            result.mandates.append(
                Mandate(
                    id=f"an-{text(mandat.get('uid'))}",
                    candidate_id=linked[uid].candidate_id,
                    body=Body.AN if kind == "ASSEMBLEE" else Body.GOVERNMENT,
                    label=_mandate_label(mandat)
                    if kind == "ASSEMBLEE"
                    else _government_label(mandat, organes),
                    start_date=start,
                    end_date=parse_date(mandat.get("dateFin")),
                    source_id=acteurs_source.id,
                )
            )

    for legislature in SCRUTIN_LEGISLATURES:
        entry = cache.entry(f"an/scrutins-{legislature}.json.zip")
        source = Source(
            id=f"an-scrutins-{legislature}",
            name=f"Assemblée nationale — scrutins publics de la {legislature}e législature",
            url=entry.url,
            retrieved_on=entry.retrieved_date,
            license=LICENSE,
        )
        result.sources.append(source)
        for record in _json_entries(cache.path(entry.key), "json"):
            result.votes.extend(_votes(record["scrutin"], linked, source.id))
    return result


def _mandate_label(mandat: dict) -> str:
    legislature = text(mandat.get("legislature"))
    place = mandat.get("election", {}).get("lieu", {}) if isinstance(mandat.get("election"), dict) else {}
    parts = ["Mandat de député"]
    if legislature:
        parts.append(f"{legislature}e législature")
    departement, circo = text(place.get("departement")), text(place.get("numCirco"))
    if departement and circo:
        parts.append(f"{departement}, circonscription n° {circo}")
    return " — ".join(parts)


def _government_label(mandat: dict, organes: dict[str | None, dict]) -> str:
    organe = organes.get(text((mandat.get("organes") or {}).get("organeRef"))) or {}
    name = text(organe.get("libelleAbrege"))
    return f"Membre du Gouvernement ({name})" if name else "Membre du Gouvernement"


def _votes(scrutin: dict, linked: dict[str, Identity], source_id: str) -> Iterator[Vote]:
    uid = text(scrutin.get("uid"))
    numero = text(scrutin.get("numero"))
    legislature = text(scrutin.get("legislature"))
    ballot_date = parse_date(scrutin.get("dateScrutin"))
    if not (uid and numero and legislature and ballot_date):
        return
    groupes = scrutin.get("ventilationVotes", {}).get("organe", {}).get("groupes", {}).get("groupe")
    for groupe in as_list(groupes):
        nominatif = (groupe.get("vote") or {}).get("decompteNominatif") or {}
        for key, position in POSITIONS.items():
            for votant in as_list((nominatif.get(key) or {}).get("votant")):
                acteur = text(votant.get("acteurRef"))
                if acteur not in linked:
                    continue
                yield Vote(
                    id=f"an-{uid}-{acteur}",
                    candidate_id=linked[acteur].candidate_id,
                    body=Body.AN,
                    ballot_id=uid,
                    ballot_date=ballot_date,
                    ballot_title=text(scrutin.get("titre")) or "",
                    ballot_type=text((scrutin.get("typeVote") or {}).get("libelleTypeVote")),
                    ballot_subject=ballot_subject(text(scrutin.get("titre")) or ""),
                    position=position,
                    ballot_url=BALLOT_PAGE.format(legislature=legislature, numero=numero),
                    source_id=source_id,
                )
