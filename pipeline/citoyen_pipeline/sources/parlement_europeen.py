"""European Parliament open data API v2 (https://data.europarl.europa.eu/api/v2, CC BY 4.0, no key,
500 requests per 5 minutes).

Structure verified on 2026-10-21 with tools/probe_sources.py:
- /meps/{id}: data[0].{familyName, givenName, bday, hasMembership[]}; a mandate is a membership with
  membershipClassification "def/ep-entities/EU_INSTITUTION", role "def/ep-roles/MEMBER" and
  organization "org/ep-{term}", dated by memberDuring.{startDate, endDate}.
- /meetings?year=Y: data[].activity_id (plenary sittings start with "MTG-PL-").
- /meetings/{id}/decisions: data[] of type "Vote" with activity_id, activity_date, activity_label.fr,
  decision_method, and the roll-call lists had_voter_favor / had_voter_against / had_voter_abstention
  ("person/{id}"). Those ELI event URIs resolve on data.europarl.europa.eu (HTTP 200).
"""

from __future__ import annotations

import json
from datetime import UTC, datetime

from citoyen_pipeline.fetch import Cache
from citoyen_pipeline.model import Body, Identity, Mandate, PersonFacts, Source, SourceExtract, Speech, Vote
from citoyen_pipeline.sources.common import as_list, parse_date, text

API = "https://data.europarl.europa.eu/api/v2"
SITE = "https://data.europarl.europa.eu/"
FORMAT = "format=application%2Fld%2Bjson"
LICENSE = "CC BY 4.0 (Parlement européen)"
PAGE = 500
# Roll-call list -> position name as published (suffix of the list property).
POSITIONS = {"had_voter_favor": "favor", "had_voter_against": "against", "had_voter_abstention": "abstention"}


def mep_url(mep_id: str) -> str:
    return f"{API}/meps/{mep_id}?{FORMAT}"


def meetings_url(year: int, offset: int = 0) -> str:
    return f"{API}/meetings?year={year}&{FORMAT}&offset={offset}&limit={PAGE}"


def speeches_url(mep_id: str, offset: int = 0) -> str:
    return f"{API}/speeches?person-id={mep_id}&{FORMAT}&offset={offset}&limit={PAGE}"


def decisions_url(meeting: str, offset: int = 0) -> str:
    return f"{API}/meetings/{meeting}/decisions?{FORMAT}&offset={offset}&limit={PAGE}"


def _load(cache: Cache, key: str) -> dict:
    body = cache.path(key).read_text(encoding="utf-8")
    # The API answers an empty body (no JSON) when a query has no result, e.g. a member without
    # plenary speeches (seen in CI on 2026-10-28): treated as an empty page.
    return json.loads(body) if body.strip() else {}


def _mandates(mep: dict) -> list[dict]:
    return [
        m
        for m in as_list(mep.get("hasMembership"))
        if m.get("membershipClassification") == "def/ep-entities/EU_INSTITUTION"
        and m.get("role") == "def/ep-roles/MEMBER"
        and str(m.get("organization", "")).startswith("org/ep-")
    ]


def _years(mandates: list[dict]) -> set[int]:
    today = datetime.now(UTC).date()
    years: set[int] = set()
    for mandate in mandates:
        period = mandate.get("memberDuring") or {}
        start = parse_date(period.get("startDate"))
        end = parse_date(period.get("endDate")) or today
        if start:
            years.update(range(start.year, min(end, today).year + 1))
    return years


def _pages(cache: Cache, prefix: str, url_for) -> list[dict]:
    items, offset = [], 0
    while True:
        key = f"{prefix}-{offset}.json"
        cache.fetch(key, url_for(offset))
        page = as_list(_load(cache, key).get("data"))
        items.extend(page)
        if len(page) < PAGE:
            return items
        offset += PAGE


def fetch(cache: Cache, identities: list[Identity]) -> None:
    years: set[int] = set()
    for identity in identities:
        if identity.pe_id:
            entry = cache.fetch(f"pe/mep-{identity.pe_id}.json", mep_url(identity.pe_id), refresh=True)
            mep = as_list(_load(cache, entry.key).get("data"))[0]
            years |= _years(_mandates(mep))
            # /speeches?person-id= : plenary interventions (verified 2026-10-21, ELI event ids).
            _pages(cache, f"pe/speeches-{identity.pe_id}", lambda o, m=identity.pe_id: speeches_url(m, o))
    for year in sorted(years):
        for meeting in _pages(cache, f"pe/meetings-{year}", lambda offset, y=year: meetings_url(y, offset)):
            meeting_id = text(meeting.get("activity_id")) or ""
            if meeting_id.startswith("MTG-PL-"):
                _pages(
                    cache, f"pe/decisions/{meeting_id}", lambda offset, m=meeting_id: decisions_url(m, offset)
                )


def extract(cache: Cache, identities: list[Identity]) -> SourceExtract:
    linked = {i.pe_id: i for i in identities if i.pe_id}
    result = SourceExtract()
    if not linked:
        return result

    years: set[int] = set()
    for mep_id, identity in linked.items():
        entry = cache.entry(f"pe/mep-{mep_id}.json")
        mep = as_list(_load(cache, entry.key).get("data"))[0]
        result.people.append(
            PersonFacts(
                body=Body.PE,
                source_person_id=mep_id,
                family_name=text(mep.get("familyName")) or "",
                birth_date=parse_date(mep.get("bday")),
            )
        )
        source = Source(
            id=f"pe-mep-{mep_id}",
            name="Parlement européen — fiche de député européen (API Open Data)",
            url=entry.url,
            retrieved_on=entry.retrieved_date,
            license=LICENSE,
        )
        mandates = _mandates(mep)
        if mandates:
            result.sources.append(source)
        for mandate in mandates:
            period = mandate.get("memberDuring") or {}
            start = parse_date(period.get("startDate"))
            if start is None:
                continue
            term = str(mandate["organization"]).removeprefix("org/ep-")
            result.mandates.append(
                Mandate(
                    id=f"pe-{text(mandate.get('identifier'))}",
                    candidate_id=identity.candidate_id,
                    body=Body.PE,
                    label=f"Mandat de député européen — {term}e législature",
                    start_date=start,
                    end_date=parse_date(period.get("endDate")),
                    source_id=source.id,
                )
            )
        years |= _years(mandates)
        result.speeches.extend(_speeches(cache, mep_id, identity, result))

    persons = {f"person/{mep_id}": identity for mep_id, identity in linked.items()}
    for year in sorted(years):
        for meeting in _cached_pages(cache, f"pe/meetings-{year}"):
            meeting_id = text(meeting.get("activity_id")) or ""
            if not meeting_id.startswith("MTG-PL-"):
                continue
            first = cache.entry(f"pe/decisions/{meeting_id}-0.json")
            source = Source(
                id=f"pe-decisions-{meeting_id}",
                name=f"Parlement européen — votes par appel nominal, séance {meeting_id}",
                url=first.url,
                retrieved_on=first.retrieved_date,
                license=LICENSE,
            )
            votes = [
                vote
                for decision in _cached_pages(cache, f"pe/decisions/{meeting_id}")
                for vote in _votes(decision, persons, source.id)
            ]
            if votes:
                result.sources.append(source)
                result.votes.extend(votes)
    return result


def _speeches(cache: Cache, mep_id: str, identity: Identity, result: SourceExtract) -> list[Speech]:
    prefix = f"pe/speeches-{mep_id}"
    if not cache.has(f"{prefix}-0.json"):
        return []
    first = cache.entry(f"{prefix}-0.json")
    source = Source(
        id=f"pe-speeches-{mep_id}",
        name="Parlement européen — interventions en séance plénière (API Open Data)",
        url=first.url,
        retrieved_on=first.retrieved_date,
        license=LICENSE,
    )
    speeches = []
    for item in _cached_pages(cache, prefix):
        event, day = text(item.get("id")), parse_date(item.get("activity_date"))
        if not (event and day):
            continue
        labels = item.get("activity_label") or {}
        speeches.append(
            Speech(
                id=f"pe-{text(item.get('activity_id')) or event}-{mep_id}",
                candidate_id=identity.candidate_id,
                kind="PLENARY_SPEECH",
                body=Body.PE,
                speech_date=day,
                title=text(labels.get("fr")) or text(labels.get("mul")) or event,
                detail=None,
                url=SITE + event,
                source_id=source.id,
            )
        )
    if speeches:
        result.sources.append(source)
    return speeches


def _cached_pages(cache: Cache, prefix: str) -> list[dict]:
    items, offset = [], 0
    while cache.has(f"{prefix}-{offset}.json"):
        page = as_list(_load(cache, f"{prefix}-{offset}.json").get("data"))
        items.extend(page)
        if len(page) < PAGE:
            break
        offset += PAGE
    return items


def _votes(decision: dict, persons: dict[str, Identity], source_id: str):
    if decision.get("type") != "Vote":
        return
    ballot_id = text(decision.get("activity_id"))
    ballot_date = parse_date(decision.get("activity_date"))
    event = text(decision.get("id"))
    if not (ballot_id and ballot_date and event):
        return
    labels = decision.get("activity_label") or {}
    title = text(labels.get("fr")) or text(labels.get("mul")) or ballot_id
    method = text(decision.get("decision_method"))
    for key, position in POSITIONS.items():
        for person in as_list(decision.get(key)):
            identity = persons.get(person)
            if identity is None:
                continue
            yield Vote(
                id=f"pe-{ballot_id}-{person.removeprefix('person/')}",
                candidate_id=identity.candidate_id,
                body=Body.PE,
                ballot_id=ballot_id,
                ballot_date=ballot_date,
                ballot_title=title,
                ballot_type=method.rsplit("/", 1)[-1] if method else None,
                position=position,
                ballot_url=SITE + event,
                source_id=source_id,
            )
