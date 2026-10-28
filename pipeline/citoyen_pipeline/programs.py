"""Programme ingestion (AGENTS.md §6, §7.5): documents listed by hand, fetched, checked against the
validated sha256, extracted verbatim (headings, pages, anchors) and cut into chunks.

Text is never rephrased: the only change is whitespace normalisation (line breaks and repeated
spaces become one space; paragraphs are kept). Hyphenation at line ends is left as published.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import date
from html.parser import HTMLParser
from pathlib import Path

from pypdf import PdfReader

from citoyen_pipeline.fetch import Cache
from citoyen_pipeline.inputs import InputError, load_yaml
from citoyen_pipeline.model import ProgramChunk, ProgramDocument, ProgramsPack, ProgramStatus, ThemeTag
from citoyen_pipeline.themes import Overrides, Taxonomy, normalize

MIN_TOKENS = 150
MAX_TOKENS = 300
# TODO(verify): tokens are approximated as words and punctuation marks. J6 recalibrates the chunk
# bounds with the tokenizer of the embedding model chosen on the golden set.
_TOKEN = re.compile(r"\w+|[^\w\s]")
_SENTENCE_END = re.compile(r"(?<=[.!?…;:])\s+")
# Instructions aimed at a language model inside a programme text (AGENTS.md §8.11): reported for
# human review, never silently dropped.
_SUSPICIOUS = re.compile(
    r"\b(ignore[rsz]?|oublie[rsz]?|disregard)\b.{0,60}\b(instructions?|consignes?|regles?)\b"
    r"|\bprompt\b|\bsystem\s*:|\btu es (un|une) (assistant|ia|intelligence)",
)


def count_tokens(text: str) -> int:
    return len(_TOKEN.findall(text))


@dataclass(frozen=True)
class ProgramEntry:
    candidate_id: str
    status: ProgramStatus
    documents: list[dict]


def load_manifest(path: Path, candidate_ids: set[str]) -> list[ProgramEntry]:
    if not path.exists():
        return []
    data = load_yaml(path, "programs.schema.json")
    entries, seen_docs = [], set()
    for item in data["programs"]:
        if item["candidate_id"] not in candidate_ids:
            raise InputError(f"{path}: unknown candidate_id {item['candidate_id']}")
        for doc in item["documents"]:
            if doc["id"] in seen_docs:
                raise InputError(f"{path}: duplicate document id {doc['id']}")
            seen_docs.add(doc["id"])
        entries.append(ProgramEntry(item["candidate_id"], ProgramStatus(item["status"]), item["documents"]))
    if len({e.candidate_id for e in entries}) != len(entries):
        raise InputError(f"{path}: a candidate has several programme entries")
    return entries


def cache_key(doc: dict) -> str:
    return f"programs/{doc['id']}.{doc['format']}"


def fetch(cache: Cache, entries: list[ProgramEntry]) -> None:
    for entry in entries:
        for doc in entry.documents:
            key = cache_key(doc)
            stale = cache.has(key) and cache.entry(key).sha256 != doc["sha256"]
            cache.fetch(key, doc["url"], refresh=stale)


# --- Extraction -------------------------------------------------------------------------------


@dataclass(frozen=True)
class Block:
    """A paragraph with its position in the document."""

    heading_path: tuple[str, ...]
    page: int | None
    anchor: str | None
    text: str


def _clean(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


_BULLET = re.compile(r"^\s*([•▪◦‣●■–—-]|\d+[.)]\s)")
_PARAGRAPH_END = re.compile(r"[.!?…:;»\"]\s*$")


def _paragraphs(page_text: str) -> list[str]:
    """Groups the lines of a page into paragraphs: a paragraph ends on a line ending with closing
    punctuation, before a bullet, or at a blank line. The text layer is read in content order
    (pypdf's default mode), which keeps multi-column layouts in reading order."""
    paragraphs, current = [], []
    for line in page_text.splitlines():
        if not line.strip():
            paragraphs.append(current)
            current = []
            continue
        if current and _BULLET.match(line):
            paragraphs.append(current)
            current = []
        current.append(line)
        if _PARAGRAPH_END.search(line):
            paragraphs.append(current)
            current = []
    paragraphs.append(current)
    return [cleaned for lines in paragraphs if (cleaned := _clean(" ".join(lines)))]


def extract_pdf(path: Path) -> list[Block]:
    """Paragraphs per page; headings from the PDF outline (bookmarks) when the file has one.

    An outline entry applies from the top of the page it points to: the text layer gives no
    position, so content above a heading on the same page is attached to that heading.
    """
    reader = PdfReader(path)
    starts: dict[int, list[tuple[int, str]]] = {}

    def walk(items, level: int) -> None:
        for item in items:
            if isinstance(item, list):
                walk(item, level + 1)
                continue
            page = reader.get_destination_page_number(item)
            if page is not None and page >= 0:
                starts.setdefault(page, []).append((level, _clean(str(item.title))))

    walk(reader.outline, 0)
    blocks, stack = [], []
    for index, page in enumerate(reader.pages):
        for level, title in starts.get(index, []):
            del stack[level:]
            stack.append(title)
        for paragraph in _paragraphs(page.extract_text() or ""):
            blocks.append(Block(tuple(stack), index + 1, None, paragraph))
    return blocks


class _HtmlExtractor(HTMLParser):
    SKIPPED = {
        "head",
        "title",
        "script",
        "style",
        "nav",
        "header",
        "footer",
        "aside",
        "form",
        "noscript",
        "template",
        "svg",
    }
    HEADINGS = {f"h{i}": i for i in range(1, 7)}
    BLOCKS = {"p", "li", "blockquote", "dd", "dt", "td", "th", "figcaption", "pre"}

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.blocks: list[Block] = []
        self.headings: list[tuple[int, str]] = []
        self.anchor: str | None = None
        self.skip_depth = 0
        self.buffer: list[str] = []
        self.heading_level: int | None = None

    def handle_starttag(self, tag, attrs):
        if tag in self.SKIPPED:
            self.skip_depth += 1
            return
        if self.skip_depth:
            return
        element_id = dict(attrs).get("id")
        if tag in self.HEADINGS or tag in self.BLOCKS:
            self._flush()
            if element_id:
                self.anchor = element_id
            if tag in self.HEADINGS:
                self.heading_level = self.HEADINGS[tag]
        elif element_id and not self.buffer:
            self.anchor = element_id

    def handle_endtag(self, tag):
        if tag in self.SKIPPED:
            self.skip_depth = max(0, self.skip_depth - 1)
            return
        if not self.skip_depth and (tag in self.HEADINGS or tag in self.BLOCKS):
            self._flush()

    def handle_data(self, data):
        if not self.skip_depth:
            self.buffer.append(data)

    def _flush(self) -> None:
        text = _clean("".join(self.buffer))
        self.buffer = []
        if self.heading_level is not None:
            if text:
                self.headings = [(lvl, t) for lvl, t in self.headings if lvl < self.heading_level]
                self.headings.append((self.heading_level, text))
            self.heading_level = None
        elif text:
            self.blocks.append(Block(tuple(t for _, t in self.headings), None, self.anchor, text))

    def close(self):
        super().close()
        self._flush()


def extract_html(path: Path) -> list[Block]:
    parser = _HtmlExtractor()
    parser.feed(path.read_bytes().decode("utf-8", errors="strict"))
    parser.close()
    return parser.blocks


# --- Chunking ---------------------------------------------------------------------------------


def _pieces(paragraph: str) -> list[str]:
    """Splits a paragraph longer than MAX_TOKENS at sentence ends, then at word boundaries."""
    if count_tokens(paragraph) <= MAX_TOKENS:
        return [paragraph]
    pieces = []
    for sentence in _SENTENCE_END.split(paragraph):
        if count_tokens(sentence) <= MAX_TOKENS:
            pieces.append(sentence)
            continue
        words, current = sentence.split(" "), []
        for word in words:
            if current and count_tokens(" ".join([*current, word])) > MAX_TOKENS:
                pieces.append(" ".join(current))
                current = []
            current.append(word)
        if current:
            pieces.append(" ".join(current))
    return pieces


def chunk_blocks(document_id: str, candidate_id: str, blocks: list[Block]) -> list[ProgramChunk]:
    """Chunks of 150 to 300 tokens, one paragraph or measure at a time (AGENTS.md §7.5).

    A chunk never spans two headings or two pages; a new paragraph starts a new chunk once the
    current one reaches MIN_TOKENS. Short sections give chunks below MIN_TOKENS rather than mixing
    two sections.
    """
    chunks: list[ProgramChunk] = []
    current: list[str] = []  # paragraphs, each a list of pieces joined by spaces
    tokens = 0
    position: tuple | None = None
    anchor: str | None = None

    def flush() -> None:
        nonlocal current, tokens
        if current:
            text = "\n\n".join(current)
            heading_path, page = position
            chunks.append(
                ProgramChunk(
                    id=f"{document_id}-{len(chunks) + 1:04d}",
                    document_id=document_id,
                    candidate_id=candidate_id,
                    ordinal=len(chunks) + 1,
                    heading_path=heading_path,
                    page=page,
                    anchor=anchor,
                    text=text,
                    token_count=count_tokens(text),
                )
            )
        current, tokens = [], 0

    for block in blocks:
        block_position = (block.heading_path, block.page)
        if block_position != position or tokens >= MIN_TOKENS:
            flush()
        position = block_position
        new_paragraph = True
        for piece in _pieces(block.text):
            size = count_tokens(piece)
            if current and tokens + size > MAX_TOKENS:
                flush()
                new_paragraph = True
            if not current:
                anchor = block.anchor
            if new_paragraph:
                current.append(piece)
            else:
                current[-1] = f"{current[-1]} {piece}"
            tokens += size
            new_paragraph = False
    flush()
    return chunks


# --- Assembly ---------------------------------------------------------------------------------


def suspicious(chunk: ProgramChunk) -> bool:
    return bool(_SUSPICIOUS.search(normalize(chunk.text)))


def assemble(
    cache: Cache,
    entries: list[ProgramEntry],
    candidate_ids: list[str],
    taxonomy: Taxonomy,
    overrides: Overrides,
    generated_on: date,
) -> tuple[ProgramsPack, dict[str, list[str]]]:
    statuses = dict.fromkeys(candidate_ids, ProgramStatus.NON_PUBLIE)
    documents, chunks, review = [], [], {}
    for entry in entries:
        statuses[entry.candidate_id] = entry.status
        for doc in entry.documents:
            fetched = cache.entry(cache_key(doc))
            if fetched.sha256 != doc["sha256"]:
                raise InputError(
                    f"{doc['id']}: the published file changed since it was validated "
                    f"(sha256 {fetched.sha256}); review it and update inputs/programs/manifest.yaml"
                )
            path = cache.path(fetched.key)
            blocks = extract_pdf(path) if doc["format"] == "pdf" else extract_html(path)
            doc_chunks = chunk_blocks(doc["id"], entry.candidate_id, blocks)
            if not doc_chunks:
                raise InputError(f"{doc['id']}: no text extracted (scanned document? OCR is not supported)")
            documents.append(
                ProgramDocument(
                    id=doc["id"],
                    candidate_id=entry.candidate_id,
                    title=doc["title"],
                    url=doc["url"],
                    format=doc["format"],
                    published_on=date.fromisoformat(doc["published_on"]),
                    version=doc["version"],
                    sha256=doc["sha256"],
                    retrieved_on=fetched.retrieved_date,
                    license=doc["reuse_rights"]["terms"],
                )
            )
            chunks += doc_chunks
            flagged = [c.id for c in doc_chunks if suspicious(c)]
            if flagged:
                review[f"programme {doc['id']}: excerpts with instruction-like text"] = flagged
    unknown_overrides = set(overrides.chunks) - {c.id for c in chunks}
    if unknown_overrides:
        raise InputError(f"theme_overrides.yaml: unknown chunk ids {sorted(unknown_overrides)}")
    chunk_themes: dict[str, list[ThemeTag]] = {}
    for chunk in chunks:
        manual = overrides.chunks.get(chunk.id)
        chunk_themes[chunk.id] = (
            overrides.manual(manual) if manual else taxonomy.tag(" ".join([*chunk.heading_path, chunk.text]))
        )
    return ProgramsPack(generated_on, statuses, documents, chunks, chunk_themes), review
