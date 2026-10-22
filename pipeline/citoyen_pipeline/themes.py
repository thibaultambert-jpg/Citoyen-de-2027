"""Theme taxonomy and tagging (AGENTS.md §7.4): explicit keyword rules or human decisions, never an
LLM. Every tag records its method and confidence; an item matched by no rule is tagged "inconnu"."""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass
from pathlib import Path

from citoyen_pipeline.inputs import InputError, load_yaml
from citoyen_pipeline.model import UNKNOWN_THEME, TagConfidence, TagMethod, Theme, ThemeTag

EVALUATIVE_TERMS = Path(__file__).resolve().parents[2] / "config" / "neutrality" / "evaluative-terms-fr.txt"


def normalize(text: str) -> str:
    """Lower case without diacritics, so that rules are written once for "sante" and "santé"."""
    decomposed = unicodedata.normalize("NFKD", text.casefold())
    return "".join(c for c in decomposed if not unicodedata.combining(c))


@dataclass(frozen=True)
class Taxonomy:
    themes: list[Theme]
    unknown: Theme
    rules: dict[str, list[re.Pattern[str]]]

    @property
    def all_themes(self) -> list[Theme]:
        return [*self.themes, self.unknown]

    def ids(self) -> set[str]:
        return {t.id for t in self.all_themes}

    def tag(self, text: str) -> list[ThemeTag]:
        """Rule tags of [text]: one per theme matched, confidence MEDIUM from two distinct patterns."""
        normalized = normalize(text)
        tags = []
        for theme in self.themes:
            hits = sum(1 for pattern in self.rules[theme.id] if pattern.search(normalized))
            if hits:
                confidence = TagConfidence.MEDIUM if hits >= 2 else TagConfidence.LOW
                tags.append(ThemeTag(theme.id, TagMethod.RULE, confidence))
        return tags or [ThemeTag(UNKNOWN_THEME, TagMethod.RULE, TagConfidence.LOW)]


def load_taxonomy(path: Path) -> Taxonomy:
    data = load_yaml(path, "themes.schema.json")
    themes, rules = [], {}
    for item in data["themes"]:
        theme = Theme(item["id"], item["label"])
        try:
            rules[theme.id] = [re.compile(rf"\b(?:{p})\b") for p in item["patterns"]]
        except re.error as error:
            raise InputError(f"{path}: invalid pattern for {theme.id}: {error}") from error
        themes.append(theme)
    ids = [t.id for t in themes]
    if len(ids) != len(set(ids)):
        raise InputError(f"{path}: duplicate theme ids")
    taxonomy = Taxonomy(themes, Theme(UNKNOWN_THEME, data["unknown_label"]), rules)
    check_neutral_labels(taxonomy, path)
    return taxonomy


def check_neutral_labels(taxonomy: Taxonomy, path: Path) -> None:
    """Theme labels are shown in the app: same evaluative-term list as the app texts (§10)."""
    terms = [
        normalize(line.strip())
        for line in EVALUATIVE_TERMS.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.startswith("#")
    ]
    for theme in taxonomy.all_themes:
        label = normalize(theme.label)
        for term in terms:
            if re.search(rf"\b{re.escape(term)}\b", label):
                raise InputError(f"{path}: label of {theme.id} contains the evaluative term {term!r}")


@dataclass(frozen=True)
class Overrides:
    ballots: dict[str, list[str]]
    chunks: dict[str, list[str]]

    def manual(self, theme_ids: list[str]) -> list[ThemeTag]:
        return [ThemeTag(t, TagMethod.MANUAL, TagConfidence.HIGH) for t in theme_ids]


def load_overrides(path: Path, taxonomy: Taxonomy) -> Overrides:
    if not path.exists():
        return Overrides({}, {})
    data = load_yaml(path, "theme_overrides.schema.json")
    ballots, chunks = {}, {}
    for item in data["overrides"]:
        unknown = set(item["themes"]) - taxonomy.ids()
        if unknown:
            raise InputError(f"{path}: unknown themes {sorted(unknown)} for {item['id']}")
        target = ballots if item["target"] == "ballot" else chunks
        if item["id"] in target:
            raise InputError(f"{path}: {item['id']} is overridden twice")
        target[item["id"]] = list(item["themes"])
    return Overrides(ballots, chunks)
