"""Legal corpus (AGENTS.md §8 `LEGAL`) from the DILA LEGI dump: one article = one chunk, verbatim, in
the version in force on the build date.

Formats verified by the CI probe (pipeline/tools/probe_sources.py, `j5legi`): the global archive holds
one directory per text with `texte/version/<LEGITEXT>.xml` (title, nature), `texte/struct/<LEGITEXT>.xml`
(ordered LIEN_ART / LIEN_SECTION_TA links with `etat`, `debut`, `fin`), `section_ta/.../<LEGISCTA>.xml`
(TITRE_TA, STRUCTURE_TA) and `article/.../<LEGIARTI>.xml` (NUM, ETAT, DATE_DEBUT, BLOC_TEXTUEL/CONTENU).
Official pages: https://www.legifrance.gouv.fr/codes/article_lc/<LEGIARTI> for codes,
.../loda/article_lc/<LEGIARTI> for other texts (checked against Légifrance pages, see docs/jalons/J5.md).
"""

from __future__ import annotations

import re
import tarfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import date
from pathlib import Path

from citoyen_pipeline.fetch import Cache
from citoyen_pipeline.inputs import InputError, load_yaml
from citoyen_pipeline.model import LegalArticle, LegalPack, LegalText

LEGI_INDEX = "https://echanges.dila.gouv.fr/OPENDATA/LEGI/"
INDEX_KEY = "legi/index.html"
ARCHIVE_KEY = "legi/global.tar.gz"
EXTRACT_DIR = "legi/extract"
LICENSE = "Licence Ouverte (Etalab) — base LEGI, DILA"
LEGIFRANCE = "https://www.legifrance.gouv.fr"


@dataclass(frozen=True)
class LegalInput:
    id: str
    legi_id: str
    position: int
    # Section ids (LEGISCTA) to keep, with everything below them; empty keeps the whole text.
    sections: tuple[str, ...]


def load_inputs(path: Path) -> list[LegalInput]:
    if not path.exists():
        return []
    data = load_yaml(path, "legal_texts.schema.json")
    inputs = [
        LegalInput(item["id"], item["legi_id"], position, tuple(item.get("sections", [])))
        for position, item in enumerate(data["texts"], start=1)
    ]
    for field in ("id", "legi_id"):
        values = [getattr(i, field) for i in inputs]
        if len(values) != len(set(values)):
            raise InputError(f"{path}: duplicate {field}")
    return inputs


# --- Fetch ------------------------------------------------------------------------------------


def latest_global_archive(index_html: str) -> str:
    """Most recent `Freemium_legi_global_<date>-<time>.tar.gz` listed on the DILA index."""
    names = sorted(set(re.findall(r'href="(Freemium_legi_global_[0-9-]+\.tar\.gz)"', index_html)))
    if not names:
        raise RuntimeError(f"No global LEGI archive listed on {LEGI_INDEX}")
    return LEGI_INDEX + names[-1]


def fetch(cache: Cache, inputs: list[LegalInput]) -> None:
    if not inputs:
        return
    index = cache.fetch(INDEX_KEY, LEGI_INDEX, refresh=True)
    url = latest_global_archive(cache.path(index.key).read_text(encoding="utf-8", errors="replace"))
    cache.fetch_large(ARCHIVE_KEY, url)
    extract(cache.path(ARCHIVE_KEY), cache.root / EXTRACT_DIR, {i.legi_id for i in inputs})


def extract(archive: Path, target: Path, legi_ids: set[str]) -> None:
    """Copies the directory of each wanted text out of the archive (two passes: the directory of a
    text is only known from its `texte/struct/<LEGITEXT>.xml` member)."""
    directories: dict[str, str] = {}
    with tarfile.open(archive, "r:gz") as tar:
        for member in tar:
            match = re.search(r"^(.*)/texte/struct/(LEGITEXT\d+)\.xml$", member.name)
            if match and match.group(2) in legi_ids:
                directories[match.group(1) + "/"] = match.group(2)
    if missing := legi_ids - set(directories.values()):
        raise RuntimeError(f"Texts not found in the LEGI archive: {sorted(missing)}")
    with tarfile.open(archive, "r:gz") as tar:
        for member in tar:
            if not member.isfile() or not member.name.endswith(".xml"):
                continue
            for prefix, legi_id in directories.items():
                if member.name.startswith(prefix):
                    relative = Path(member.name[len(prefix) :])
                    if ".." in relative.parts:
                        raise RuntimeError(f"Unexpected path in archive: {member.name}")
                    out = target / legi_id / relative
                    out.parent.mkdir(parents=True, exist_ok=True)
                    out.write_bytes(tar.extractfile(member).read())


# --- Parsing ----------------------------------------------------------------------------------


def _date(value: str | None) -> date:
    return date.fromisoformat(value) if value else date.max


def in_force(link: ET.Element, on: date) -> bool:
    """A link applies on [on] when its period covers it; articles must also be `VIGUEUR`."""
    if not _date(link.get("debut")) <= on < _date(link.get("fin")):
        return False
    if link.tag == "LIEN_ART":
        return link.get("etat") == "VIGUEUR"
    return link.get("etat") not in ("ABROGE", "ANNULE", "PERIME")


_BLOCK_TAGS = {"p", "div", "tr", "li", "table", "h1", "h2", "h3", "h4", "h5", "h6"}


def content_text(element: ET.Element | None) -> str:
    """Text of a CONTENU element: paragraphs and line breaks become new lines; spaces inside a line
    are normalised. Nothing else is changed."""
    if element is None:
        return ""
    parts: list[str] = []

    def walk(node: ET.Element) -> None:
        if node.tag in _BLOCK_TAGS:
            parts.append("\n")
        if node.text:
            parts.append(node.text)
        for child in node:
            if child.tag == "br":
                parts.append("\n")
            else:
                walk(child)
            if child.tail:
                parts.append(child.tail)
        if node.tag in _BLOCK_TAGS:
            parts.append("\n")
        elif node.tag in ("td", "th"):
            parts.append(" ")

    walk(element)
    lines = (re.sub(r"[ \t\r\f\v ]+", " ", line).strip() for line in "".join(parts).split("\n"))
    return "\n".join(line for line in lines if line)


def _index(root: Path) -> dict[str, Path]:
    return {path.stem: path for path in root.rglob("*.xml")}


def parse_text(
    root: Path, spec: LegalInput, on: date, retrieved_on: date
) -> tuple[LegalText, list[LegalArticle], list[str]]:
    files = _index(root)
    version = ET.parse(root / "texte" / "version" / f"{spec.legi_id}.xml").getroot()
    title = re.sub(r"\s+", " ", version.findtext(".//TITRE") or "").strip()
    nature = version.findtext(".//NATURE") or ""
    kind = "codes" if nature == "CODE" else "loda"
    text_url = (
        f"{LEGIFRANCE}/codes/texte_lc/{spec.legi_id}"
        if kind == "codes"
        else f"{LEGIFRANCE}/loda/id/{spec.legi_id}"
    )
    struct = ET.parse(root / "texte" / "struct" / f"{spec.legi_id}.xml").getroot().find("STRUCT")
    articles: list[LegalArticle] = []
    notes: list[str] = []
    seen: set[str] = set()
    found_sections: set[str] = set()

    def walk(links, path: tuple[str, ...], included: bool) -> None:
        for link in links:
            if not in_force(link, on):
                continue
            if link.tag == "LIEN_ART" and included:
                article_id = link.get("id")
                if article_id in seen:
                    continue
                seen.add(article_id)
                article = _article(files, article_id, spec.id, len(articles) + 1, path, kind)
                if article is None:
                    notes.append(f"{article_id}: empty or missing article file")
                else:
                    articles.append(article)
            elif link.tag == "LIEN_SECTION_TA":
                section_id = link.get("id")
                if section_id in spec.sections:
                    found_sections.add(section_id)
                section_file = files.get(section_id)
                if section_file is None:
                    notes.append(f"{section_id}: missing section file")
                    continue
                section = ET.parse(section_file).getroot()
                heading = re.sub(r"\s+", " ", section.findtext("TITRE_TA") or link.text or "").strip()
                structure = section.find("STRUCTURE_TA")
                walk(
                    list(structure) if structure is not None else [],
                    (*path, heading),
                    included or section_id in spec.sections,
                )

    walk(list(struct if struct is not None else []), (), not spec.sections)
    if missing := set(spec.sections) - found_sections:
        raise InputError(f"{spec.id}: sections not in force or not found: {sorted(missing)}")
    text = LegalText(
        id=spec.id,
        title=title,
        position=spec.position,
        url=text_url,
        source_name=f"{title} — base LEGI (DILA)",
        retrieved_on=retrieved_on,
        license=LICENSE,
    )
    return text, articles, notes


def _article(
    files: dict[str, Path], article_id: str, text_id: str, ordinal: int, path: tuple[str, ...], kind: str
) -> LegalArticle | None:
    file = files.get(article_id)
    if file is None:
        return None
    root = ET.parse(file).getroot()
    body = content_text(root.find("BLOC_TEXTUEL/CONTENU"))
    if not body:
        return None
    start = root.findtext(".//META_ARTICLE/DATE_DEBUT")
    return LegalArticle(
        id=article_id,
        text_id=text_id,
        ordinal=ordinal,
        number=(root.findtext(".//META_ARTICLE/NUM") or "").strip(),
        heading_path=path,
        body=body,
        in_force_since=date.fromisoformat(start) if start and not start.startswith("2999") else None,
        url=f"{LEGIFRANCE}/{kind}/article_lc/{article_id}",
    )


def assemble(
    cache: Cache, inputs: list[LegalInput], generated_on: date
) -> tuple[LegalPack, dict[str, list[str]]]:
    texts, articles, review = [], [], {}
    if inputs:
        retrieved_on = cache.entry(ARCHIVE_KEY).retrieved_date
        for spec in inputs:
            text, text_articles, notes = parse_text(
                cache.root / EXTRACT_DIR / spec.legi_id, spec, generated_on, retrieved_on
            )
            if not text_articles:
                raise InputError(f"{spec.id}: no article in force found")
            texts.append(text)
            articles += text_articles
            if notes:
                review[f"legal {spec.id}: skipped links"] = notes
    return LegalPack(generated_on, texts, articles), review
