"""Download cache (AGENTS.md §7.1 `fetch`): raw files are kept with their sha256 and retrieval date so
that every fact of a pack can be traced back to the exact file it came from."""

from __future__ import annotations

import hashlib
import json
import time
import urllib.request
from dataclasses import asdict, dataclass
from datetime import UTC, date, datetime
from pathlib import Path

USER_AGENT = "citoyen-2027-pipeline/0.1 (+https://github.com/thibaultambert-jpg/Citoyen-de-2027)"


@dataclass(frozen=True)
class Fetched:
    key: str
    url: str
    file: str
    sha256: str
    size: int
    retrieved_on: str

    @property
    def retrieved_date(self) -> date:
        return date.fromisoformat(self.retrieved_on)


class Cache:
    """Files live in `root/<key>`; `root/fetch_log.json` records where and when each came from."""

    def __init__(self, root: Path, delay_seconds: float = 0.0) -> None:
        self.root = root
        self.delay_seconds = delay_seconds
        self.root.mkdir(parents=True, exist_ok=True)
        self._log_path = root / "fetch_log.json"
        self._log: dict[str, Fetched] = {}
        if self._log_path.exists():
            raw = json.loads(self._log_path.read_text(encoding="utf-8"))
            self._log = {k: Fetched(**v) for k, v in raw.items()}

    def path(self, key: str) -> Path:
        return self.root / key

    def entry(self, key: str) -> Fetched:
        if key not in self._log:
            raise FileNotFoundError(f"{key} is not in the fetch cache: run `make fetch` first")
        return self._log[key]

    def has(self, key: str) -> bool:
        return key in self._log and self.path(key).exists()

    def fetch(self, key: str, url: str, refresh: bool = False) -> Fetched:
        if not url.startswith("https://"):
            raise ValueError(f"Only HTTPS sources are fetched: {url}")
        if self.has(key) and not refresh:
            return self._log[key]
        target = self.path(key)
        target.parent.mkdir(parents=True, exist_ok=True)
        body = _download(url)
        target.write_bytes(body)
        entry = Fetched(
            key=key,
            url=url,
            file=key,
            sha256=hashlib.sha256(body).hexdigest(),
            size=len(body),
            retrieved_on=datetime.now(UTC).date().isoformat(),
        )
        self.record(entry)
        if self.delay_seconds:
            time.sleep(self.delay_seconds)
        return entry

    def fetch_large(self, key: str, url: str) -> Fetched:
        """Like [fetch], streamed to disk: for archives of several hundred megabytes."""
        if not url.startswith("https://"):
            raise ValueError(f"Only HTTPS sources are fetched: {url}")
        if self.has(key) and self._log[key].url == url:
            return self._log[key]
        target = self.path(key)
        # A sidecar record lets a CI cache restore large archives without the shared fetch log.
        sidecar = target.with_name(target.name + ".fetched.json")
        if target.exists() and sidecar.exists():
            entry = Fetched(**json.loads(sidecar.read_text(encoding="utf-8")))
            if entry.url == url and entry.size == target.stat().st_size:
                self.record(entry)
                return entry
        target.parent.mkdir(parents=True, exist_ok=True)
        partial = target.with_name(target.name + ".part")
        digest, size = hashlib.sha256(), 0
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(request, timeout=600) as response, partial.open("wb") as out:
            while chunk := response.read(1 << 20):
                digest.update(chunk)
                size += len(chunk)
                out.write(chunk)
        partial.replace(target)
        entry = Fetched(key, url, key, digest.hexdigest(), size, datetime.now(UTC).date().isoformat())
        sidecar.write_text(json.dumps(asdict(entry)), encoding="utf-8")
        self.record(entry)
        return entry

    def record(self, entry: Fetched) -> None:
        self._log[entry.key] = entry
        self._log_path.write_text(
            json.dumps({k: asdict(v) for k, v in sorted(self._log.items())}, indent=2, ensure_ascii=False),
            encoding="utf-8",
        )

    def put(self, key: str, url: str, body: bytes, retrieved_on: str) -> Fetched:
        """Stores content obtained elsewhere (tests, manual imports) with the same bookkeeping."""
        target = self.path(key)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(body)
        entry = Fetched(key, url, key, hashlib.sha256(body).hexdigest(), len(body), retrieved_on)
        self.record(entry)
        return entry


def _download(url: str, attempts: int = 3) -> bytes:
    last_error: Exception | None = None
    for attempt in range(attempts):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=300) as response:
                return response.read()
        except Exception as error:  # noqa: BLE001 - retried, then re-raised
            last_error = error
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"Download failed after {attempts} attempts: {url}") from last_error
