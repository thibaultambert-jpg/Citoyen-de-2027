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

import json
import re
import shutil
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
#
# The DILA publishes one global archive (`Freemium_legi_global_<stamp>.tar.gz`) then daily increments
# (`LEGI_<stamp>.tar.gz`, stamp = YYYYMMDD-HHMMSS). An increment holds the changed files under
# `<stamp>/legi/global/...` and two lists (verified by the CI probe `j5legi3`):
# `liste_suppression_legi.dat` (one file path per line, without `.xml`) and
# `liste_suppression_legi_dossier.dat` (one directory per line, followed by ` D`).

_STAMP = r"[0-9]{8}-[0-9]{6}"
_STRUCT = re.compile(r"^(.*)/texte/struct/(LEGITEXT\d+)\.xml$")
STATE_FILE = "state.json"


def latest_global_archive(index_html: str) -> str:
    """Most recent `Freemium_legi_global_<date>-<time>.tar.gz` listed on the DILA index."""
    names = sorted(set(re.findall(rf'href="(Freemium_legi_global_{_STAMP}\.tar\.gz)"', index_html)))
    if not names:
        raise RuntimeError(f"No global LEGI archive listed on {LEGI_INDEX}")
    return LEGI_INDEX + names[-1]


def increments_after(index_html: str, global_url: str) -> list[str]:
    """Increments published after the global archive, oldest first."""
    since = re.search(_STAMP, global_url.rsplit("/", 1)[-1]).group(0)
    names = sorted(set(re.findall(rf'href="(LEGI_({_STAMP})\.tar\.gz)"', index_html)), key=lambda n: n[1])
    return [LEGI_INDEX + name for name, stamp in names if stamp > since]


def stamp_date(url: str) -> date:
    stamp = re.search(_STAMP, url.rsplit("/", 1)[-1]).group(0)
    return date(int(stamp[:4]), int(stamp[4:6]), int(stamp[6:8]))


def fetch(cache: Cache, inputs: list[LegalInput]) -> None:
    if not inputs:
        return
    index = cache.fetch(INDEX_KEY, LEGI_INDEX, refresh=True)
    html = cache.path(index.key).read_text(encoding="utf-8", errors="replace")
    global_url = latest_global_archive(html)
    cache.fetch_large(ARCHIVE_KEY, global_url)
    target = cache.root / EXTRACT_DIR
    shutil.rmtree(target, ignore_errors=True)
    legi_ids = {i.legi_id for i in inputs}
    directories = extract(cache.path(ARCHIVE_KEY), target, legi_ids)
    last = global_url
    for url in increments_after(html, global_url):
        name = url.rsplit("/", 1)[-1]
        entry = cache.fetch_large(f"legi/increments/{name}", url)
        apply_increment(cache.path(entry.key), target, directories)
        last = url
    (target / STATE_FILE).write_text(
        json.dumps({"base": global_url, "last": last, "updated_on": stamp_date(last).isoformat()}, indent=2),
        encoding="utf-8",
    )


def _write(target: Path, legi_id: str, relative: str, data: bytes) -> None:
    path = Path(relative)
    if path.is_absolute() or ".." in path.parts:
        raise RuntimeError(f"Unexpected path in archive: {relative}")
    out = target / legi_id / path
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(data)


def extract(archive: Path, target: Path, legi_ids: set[str]) -> dict[str, str]:
    """Copies the directory of each wanted text out of the global archive (two passes: the directory
    of a text is only known from its `texte/struct/<LEGITEXT>.xml` member). Returns the directory
    prefix of each text."""
    directories: dict[str, str] = {}
    with tarfile.open(archive, "r:gz") as tar:
        for member in tar:
            match = _STRUCT.search(member.name)
            if match and match.group(2) in legi_ids:
                directories[match.group(2)] = match.group(1) + "/"
    if missing := legi_ids - set(directories):
        raise RuntimeError(f"Texts not found in the LEGI archive: {sorted(missing)}")
    with tarfile.open(archive, "r:gz") as tar:
        for member in tar:
            if member.isfile() and member.name.endswith(".xml"):
                for legi_id, prefix in directories.items():
                    if member.name.startswith(prefix):
                        _write(target, legi_id, member.name[len(prefix) :], tar.extractfile(member).read())
    return directories


def apply_increment(archive: Path, target: Path, directories: dict[str, str]) -> None:
    """Applies one daily increment to the extracted texts: deletions first, then new and changed
    files (a text moving to another directory is deleted there and written here)."""
    with tarfile.open(archive, "r:gz") as tar:
        members = [m for m in tar.getmembers() if m.isfile()]
        lists = {
            m.name.rsplit("/", 1)[-1]: tar.extractfile(m).read().decode("utf-8")
            for m in members
            if m.name.endswith(".dat")
        }
        for line in lists.get("liste_suppression_legi_dossier.dat", "").splitlines():
            folder = line.strip().removesuffix(" D").strip()
            for legi_id, prefix in directories.items():
                if folder and prefix.rstrip("/") == folder:
                    shutil.rmtree(target / legi_id, ignore_errors=True)
        for line in lists.get("liste_suppression_legi.dat", "").splitlines():
            path = line.strip()
            for legi_id, prefix in directories.items():
                if path.startswith(prefix):
                    (target / legi_id / (path[len(prefix) :] + ".xml")).unlink(missing_ok=True)
        files = {m.name.split("/", 1)[1]: m for m in members if m.name.endswith(".xml") and "/" in m.name}
        for name in files:
            match = _STRUCT.search(name)
            if match and match.group(2) in directories:
                directories[match.group(2)] = match.group(1) + "/"
        for name, member in files.items():
            for legi_id, prefix in directories.items():
                if name.startswith(prefix):
                    _write(target, legi_id, name[len(prefix) :], tar.extractfile(member).read())


def base_date(cache: Cache) -> date:
    """Date of the last LEGI update applied (global archive or increment)."""
    state = json.loads((cache.root / EXTRACT_DIR / STATE_FILE).read_text(encoding="utf-8"))
    return date.fromisoformat(state["updated_on"])


# --- Parsing ----------------------------------------------------------------------------------


def _date(value: str | None) -> date:
    return date.fromisoformat(value) if value else date.max


# States of a version that applies during its [debut, fin) period. Seen in LEGI: VIGUEUR; ABROGE_DIFF
# (applies until a known future date, e.g. article 3 of the law of 6 November 1962 until 2027-01-01);
# VIGUEUR_DIFF (applies from a future date). A link with an empty state (e.g. an amending article
# "a modifié les dispositions suivantes", dated 2999-01-01) or any other state is left out.
IN_FORCE_STATES = {"VIGUEUR", "ABROGE_DIFF", "VIGUEUR_DIFF"}
NO_END = date(2999, 1, 1)


def in_force(link: ET.Element, on: date) -> bool:
    """A link applies on [on] when its period covers it and its state is one of IN_FORCE_STATES."""
    return _date(link.get("debut")) <= on < _date(link.get("fin")) and link.get("etat") in IN_FORCE_STATES


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
    root: Path, spec: LegalInput, on: date, retrieved_on: date, base_updated_on: date
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
            if link.tag == "LIEN_ART" and included and _starts_later(link, on):
                notes.append(
                    f"{link.get('id')} (art. {link.get('num')}): version applying from {link.get('debut')}"
                )
            if not in_force(link, on):
                continue
            if link.tag == "LIEN_ART" and included:
                article_id = link.get("id")
                if article_id in seen:
                    continue
                seen.add(article_id)
                until = _date(link.get("fin"))
                article = _article(
                    files,
                    article_id,
                    spec.id,
                    len(articles) + 1,
                    path,
                    kind,
                    None if until >= NO_END else until,
                )
                if article is None:
                    notes.append(f"{article_id}: empty or missing article file")
                else:
                    articles.append(article)
            elif link.tag == "LIEN_SECTION_TA":
                section_id = link.get("id")
                # A section keeps its `cid` across versions (its `id` changes): selections use either.
                selected = {section_id, link.get("cid")} & set(spec.sections)
                found_sections.update(selected)
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
                    included or bool(selected),
                )

    walk(list(struct if struct is not None else []), (), not spec.sections)
    if missing := set(spec.sections) - found_sections:
        raise InputError(f"{spec.id}: sections not in force or not found: {sorted(missing)}")
    text = LegalText(
        id=spec.id,
        title=title,
        position=spec.position,
        url=text_url,
        source_name=f"{title} — base LEGI (DILA), mise à jour du {base_updated_on.strftime('%d/%m/%Y')}",
        retrieved_on=retrieved_on,
        license=LICENSE,
    )
    return text, articles, notes


def _starts_later(link: ET.Element, on: date) -> bool:
    start = _date(link.get("debut"))
    return link.get("etat") in IN_FORCE_STATES and on < start < NO_END


def _article(
    files: dict[str, Path],
    article_id: str,
    text_id: str,
    ordinal: int,
    path: tuple[str, ...],
    kind: str,
    in_force_until: date | None,
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
        in_force_until=in_force_until,
        url=f"{LEGIFRANCE}/{kind}/article_lc/{article_id}",
    )


def assemble(
    cache: Cache, inputs: list[LegalInput], generated_on: date
) -> tuple[LegalPack, dict[str, list[str]]]:
    texts, articles, review = [], [], {}
    if inputs:
        retrieved_on = cache.entry(ARCHIVE_KEY).retrieved_date
        state = cache.root / EXTRACT_DIR / STATE_FILE
        updated_on = base_date(cache) if state.exists() else retrieved_on
        for spec in inputs:
            text, text_articles, notes = parse_text(
                cache.root / EXTRACT_DIR / spec.legi_id, spec, generated_on, retrieved_on, updated_on
            )
            if not text_articles:
                raise InputError(f"{spec.id}: no article in force found")
            texts.append(text)
            articles += text_articles
            if notes:
                review[f"legal {spec.id}: skipped links and future versions"] = notes
    return LegalPack(generated_on, texts, articles), review
