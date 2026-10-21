"""Probe official open-data sources and print their real structure.

Runs in CI only (the development sandbox cannot reach these hosts). The output is used to write
source adapters against verified formats instead of guessed ones (AGENTS.md section 3).
Standard library only.
"""

from __future__ import annotations

import csv
import io
import json
import re
import sys
import urllib.parse
import urllib.request
import zipfile

UA = {"User-Agent": "citoyen-2027-pipeline-probe/0.1 (+https://github.com/thibaultambert-jpg/Citoyen-de-2027)"}
MAX_BYTES = 400 * 1024 * 1024


def get(url: str, limit: int = MAX_BYTES) -> tuple[int, str, bytes]:
    req = urllib.request.Request(url, headers=UA)
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            return resp.status, resp.headers.get("Content-Type", ""), resp.read(limit)
    except Exception as exc:  # noqa: BLE001 - probe reports every failure
        return -1, repr(exc), b""


def shape(value, depth: int = 0, max_depth: int = 5) -> str:
    """Compact type tree of a JSON value."""
    pad = "  " * depth
    if depth > max_depth:
        return pad + "..."
    if isinstance(value, dict):
        lines = []
        for key, sub in list(value.items())[:40]:
            if isinstance(sub, (dict, list)):
                lines.append(f"{pad}{key}: {type(sub).__name__}")
                lines.append(shape(sub, depth + 1, max_depth))
            else:
                lines.append(f"{pad}{key}: {type(sub).__name__} = {str(sub)[:60]!r}")
        return "\n".join(lines)
    if isinstance(value, list):
        if not value:
            return pad + "[] (empty)"
        return f"{pad}[{len(value)} items] first:\n" + shape(value[0], depth + 1, max_depth)
    return pad + f"{type(value).__name__} = {str(value)[:60]!r}"


def describe(name: str, url: str) -> None:
    print(f"\n===== {name}\nURL {url}")
    status, ctype, body = get(url)
    print(f"status={status} type={ctype} bytes={len(body)}")
    if status != 200:
        return
    print(f"head={body[:16]!r}")
    if body[:2] == b"PK":
        try:
            zf = zipfile.ZipFile(io.BytesIO(body))
        except zipfile.BadZipFile as exc:
            print(f"bad zip: {exc}; tail={body[-64:]!r}")
            return
        with zf:
            names = zf.namelist()
            print(f"zip entries={len(names)} sample={names[:8]}")
            jsons = [n for n in names if n.endswith(".json")]
            prefixes = sorted({n.split("/")[0] + "/" + (n.split("/")[1] if n.count("/") > 1 else "") for n in names})
            print(f"prefixes={prefixes[:10]}")
            picks = []
            for prefix in ("acteur", "mandat", "organe", "scrutin", "deport"):
                picks += [n for n in jsons if prefix in n.lower()][:1]
            for entry in (picks or jsons)[:4]:
                print(f"--- entry {entry}")
                print(shape(json.loads(zf.read(entry)))[:6000])
            csvs = [n for n in names if n.endswith(".csv")]
            for entry in csvs[:1]:
                describe_csv(entry, zf.read(entry))
        return
    if "json" in ctype or body[:1] in (b"{", b"["):
        try:
            print(shape(json.loads(body))[:8000])
        except ValueError as exc:
            print(f"json error {exc}")
        return
    if "csv" in ctype or url.endswith(".csv"):
        describe_csv(url, body)
        return
    print(body[:800].decode("utf-8", "replace"))


def describe_csv(name: str, body: bytes) -> None:
    for encoding in ("utf-8-sig", "latin-1"):
        try:
            text = body.decode(encoding)
            break
        except UnicodeDecodeError:
            continue
    sample = text[:20000]
    dialect = csv.Sniffer().sniff(sample.splitlines()[0]) if sample else csv.excel
    rows = list(csv.reader(io.StringIO(sample), dialect))
    print(f"csv {name} encoding={encoding} delimiter={dialect.delimiter!r} lines~{text.count(chr(10))}")
    for row in rows[:3]:
        print("  ", row)


def links(page: str, pattern: str) -> list[str]:
    status, _, body = get(page, 5 * 1024 * 1024)
    print(f"\n===== links on {page} status={status}")
    found = sorted({urllib.parse.urljoin(page, m) for m in re.findall(r'href="([^"]+)"', body.decode("utf-8", "replace"))})
    selected = [u for u in found if re.search(pattern, u)]
    for u in selected[:40]:
        print("  ", u)
    return selected


def datagouv(slug: str) -> list[str]:
    status, _, body = get(f"https://www.data.gouv.fr/api/1/datasets/{slug}/", 10 * 1024 * 1024)
    print(f"\n===== data.gouv dataset {slug} status={status}")
    if status != 200:
        return []
    data = json.loads(body)
    print("title:", data.get("title"), "| license:", data.get("license"), "| last_update:", data.get("last_update"))
    urls = []
    for res in data.get("resources", [])[:40]:
        print(f"  - {res.get('title')!r} format={res.get('format')} url={res.get('url')} modified={res.get('last_modified')}")
        urls.append(res.get("url"))
    return urls


def main() -> int:
    target = sys.argv[1] if len(sys.argv) > 1 else "all"
    if target == "an2":
        describe("AN AMO10", "https://data.assemblee-nationale.fr/static/openData/repository/17/amo/deputes_actifs_mandats_actifs_organes/AMO10_deputes_actifs_mandats_actifs_organes.json.zip")
        for url in links("https://data.assemblee-nationale.fr/travaux-parlementaires/votes", r"\.zip$")[:2]:
            describe("AN votes", url)
        links("https://data.assemblee-nationale.fr/", r"(acteurs|votes|scrutin|historique)")
    if target == "senat2":
        for name in ("ODSEN_GENERAL", "ODSEN_ELUSEN", "ODSEN_ELUEUR"):
            describe(f"Senat {name}", f"https://data.senat.fr/data/senateurs/{name}.json")
        links("https://data.senat.fr/", r"(scrutin|vote)")
        links("https://data.senat.fr/la-base-comptes-rendus/", r"(scrutin|vote|zip)")
    if target == "pe2":
        base = "https://data.europarl.europa.eu/api/v2"
        fmt = "format=application%2Fld%2Bjson"
        describe("PE mep detail", f"{base}/meps/1?{fmt}")
        describe("PE current meps", f"{base}/meps/show-current?{fmt}&offset=0&limit=1")
        describe("PE meetings 2025", f"{base}/meetings?year=2025&{fmt}&offset=0&limit=2")
        status, _, body = get(f"{base}/meetings?year=2025&{fmt}&offset=0&limit=1")
        if status == 200:
            meeting = json.loads(body)["data"][0]["activity_id"]
            describe("PE decisions", f"{base}/meetings/{meeting}/decisions?{fmt}&offset=0&limit=1")
            describe("PE vote-results", f"{base}/meetings/{meeting}/vote-results?{fmt}&offset=0&limit=1")
    if target in ("all", "rne"):
        for url in [u for u in datagouv("repertoire-national-des-elus-1") if u and u.endswith(".csv")][:2]:
            describe("RNE resource", url)
    if target in ("all", "an"):
        for page in (
            "https://data.assemblee-nationale.fr/acteurs/deputes-en-exercice",
            "https://data.assemblee-nationale.fr/travaux-parlementaires/votes",
        ):
            for url in links(page, r"\.(json|csv)\.zip$")[:2]:
                describe("AN file", url)
    if target in ("all", "senat"):
        datagouv("les-senateurs")
        for url in links("https://data.senat.fr/les-senateurs/", r"\.(csv|zip|json)$")[:3]:
            describe("Senat file", url)
    if target in ("all", "pe"):
        base = "https://data.europarl.europa.eu/api/v2"
        describe("PE meps", f"{base}/meps?format=application%2Fld%2Bjson&offset=0&limit=2")
        describe("PE meetings", f"{base}/meetings?format=application%2Fld%2Bjson&offset=0&limit=2")
    return 0


if __name__ == "__main__":
    sys.exit(main())
