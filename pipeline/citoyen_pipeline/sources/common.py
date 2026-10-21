"""Helpers shared by the source adapters."""

from __future__ import annotations

import hashlib
from datetime import date, datetime


def as_list(value) -> list:
    """Official XML-to-JSON exports give a dict for one element and a list for several."""
    if value is None:
        return []
    return value if isinstance(value, list) else [value]


def text(value) -> str | None:
    """Text of an element exported either as a plain string or as {"#text": ...}."""
    if isinstance(value, dict):
        value = value.get("#text")
    if value is None:
        return None
    value = str(value).strip()
    return value or None


def parse_date(value: str | None) -> date | None:
    """Accepts ISO dates, ISO date-times and the Senate `YYYY/MM/DD HH:MM:SS` format."""
    value = text(value)
    if not value:
        return None
    value = value.replace("/", "-")
    try:
        return date.fromisoformat(value[:10])
    except ValueError:
        return datetime.fromisoformat(value).date()


def short_hash(*parts: str) -> str:
    return hashlib.sha1("␟".join(parts).encode("utf-8")).hexdigest()[:12]
