"""Normalised records written to the `core` pack. Field names mirror the pack SQLite columns."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date
from enum import StrEnum


class Facet(StrEnum):
    PROGRAM = "PROGRAM"
    MANDATES = "MANDATES"
    VOTES = "VOTES"
    SPEECHES = "SPEECHES"


class Level(StrEnum):
    AVAILABLE = "AVAILABLE"
    PARTIAL = "PARTIAL"
    NOT_APPLICABLE = "NOT_APPLICABLE"
    UNAVAILABLE = "UNAVAILABLE"


class Body(StrEnum):
    """Where a mandate is held or a vote cast. Recorded votes exist for AN, SENAT and PE only."""

    AN = "AN"
    SENAT = "SENAT"
    PE = "PE"
    GOVERNMENT = "GOVERNMENT"
    LOCAL = "LOCAL"


@dataclass(frozen=True)
class Source:
    """Provenance of a fact: shown behind the app's "Source" button (AGENTS.md §2.3)."""

    id: str
    name: str
    url: str
    retrieved_on: date
    license: str

    def __post_init__(self) -> None:
        if not self.url.startswith("https://"):
            raise ValueError(f"Source URL must be HTTPS: {self.url}")


@dataclass(frozen=True)
class Candidate:
    id: str
    display_name: str
    sort_name: str
    status: str
    status_date: date
    status_source_id: str


@dataclass(frozen=True)
class Identity:
    candidate_id: str
    family_name: str
    given_name: str
    birth_date: date
    an_id: str | None = None
    senat_id: str | None = None
    pe_id: str | None = None
    # Exact speaker name on vie-publique.fr. That source has no identifier: this alias is validated by
    # hand (no homonym), and the build report lists the titles found under it for review.
    vie_publique_name: str | None = None

    def ids(self) -> dict[Body, str]:
        pairs = {Body.AN: self.an_id, Body.SENAT: self.senat_id, Body.PE: self.pe_id}
        return {body: value for body, value in pairs.items() if value}


@dataclass(frozen=True)
class Mandate:
    id: str
    candidate_id: str
    body: Body
    label: str
    start_date: date
    end_date: date | None
    source_id: str


@dataclass(frozen=True)
class Vote:
    """A recorded vote, position kept exactly as published (AGENTS.md §7.3)."""

    id: str
    candidate_id: str
    body: Body
    ballot_id: str
    ballot_date: date
    ballot_title: str
    ballot_type: str | None
    position: str
    ballot_url: str
    source_id: str
    # AMENDMENT, ARTICLE, WHOLE_TEXT, MOTION or OTHER, from explicit title rules; None when unknown.
    ballot_subject: str | None = None


@dataclass(frozen=True)
class PersonFacts:
    """What an official source says about a person, used to check identity links."""

    body: Body
    source_person_id: str
    family_name: str
    birth_date: date | None


@dataclass(frozen=True)
class Speech:
    """A speech: plenary intervention (PE) or official statement (vie-publique.fr). Metadata and link only."""

    id: str
    candidate_id: str
    kind: str  # PLENARY_SPEECH or OFFICIAL_STATEMENT
    body: Body | None
    speech_date: date
    title: str
    detail: str | None
    url: str
    source_id: str


class TagMethod(StrEnum):
    """How a theme was attached (AGENTS.md §7.4): explicit keyword rule or human decision."""

    RULE = "RULE"
    MANUAL = "MANUAL"


class TagConfidence(StrEnum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


UNKNOWN_THEME = "inconnu"


@dataclass(frozen=True)
class Theme:
    id: str
    label: str


@dataclass(frozen=True)
class ThemeTag:
    theme_id: str
    method: TagMethod
    confidence: TagConfidence


class ProgramStatus(StrEnum):
    COMPLET = "COMPLET"
    PARTIEL = "PARTIEL"
    NON_PUBLIE = "NON_PUBLIE"


@dataclass(frozen=True)
class ProgramDocument:
    """A programme document listed by hand in inputs/programs/manifest.yaml."""

    id: str
    candidate_id: str
    title: str
    url: str
    format: str  # "pdf" or "html"
    published_on: date
    version: str
    sha256: str
    retrieved_on: date
    license: str


@dataclass(frozen=True)
class ProgramChunk:
    """A verbatim excerpt of a programme document (AGENTS.md §7.5), never rephrased."""

    id: str
    document_id: str
    candidate_id: str
    ordinal: int
    heading_path: tuple[str, ...]
    page: int | None
    anchor: str | None
    text: str
    token_count: int


@dataclass
class ProgramsPack:
    generated_on: date
    statuses: dict[str, ProgramStatus]
    documents: list[ProgramDocument]
    chunks: list[ProgramChunk]
    chunk_themes: dict[str, list[ThemeTag]]


@dataclass
class SourceExtract:
    """Output of one source adapter."""

    sources: list[Source] = field(default_factory=list)
    people: list[PersonFacts] = field(default_factory=list)
    mandates: list[Mandate] = field(default_factory=list)
    votes: list[Vote] = field(default_factory=list)
    speeches: list[Speech] = field(default_factory=list)
    # Human-review notes added to report.json (never shown in the app).
    review: dict[str, list[str]] = field(default_factory=dict)


@dataclass
class CorePack:
    generated_on: date
    inclusion_policy_date: date
    sources: list[Source]
    candidates: list[Candidate]
    coverage: dict[tuple[str, Facet], Level]
    mandates: list[Mandate]
    votes: list[Vote]
    speeches: list[Speech] = field(default_factory=list)
    themes: list[Theme] = field(default_factory=list)
    vote_themes: dict[str, list[ThemeTag]] = field(default_factory=dict)
    # Retrieval thresholds calibrated on eval/golden.jsonl (compact JSON), read by the app (AGENTS.md §8).
    rag_config: str | None = None
    # Electoral reserve period (AGENTS.md §10): campaign content hidden in the app when set (compact JSON).
    reserve: str | None = None


@dataclass(frozen=True)
class LegalText:
    id: str
    title: str
    position: int
    url: str
    source_name: str
    retrieved_on: date
    license: str


@dataclass(frozen=True)
class LegalArticle:
    """One article in force, verbatim: one chunk of the LEGAL corpus (AGENTS.md §8)."""

    id: str
    text_id: str
    ordinal: int
    number: str
    heading_path: tuple[str, ...]
    body: str
    in_force_since: date | None
    url: str
    # End of the version in force, when the source announces one (state ABROGE_DIFF).
    in_force_until: date | None = None


@dataclass
class LegalPack:
    generated_on: date
    texts: list[LegalText]
    articles: list[LegalArticle]
