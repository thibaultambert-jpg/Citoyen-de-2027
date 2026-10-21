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

UA = {
    "User-Agent": "citoyen-2027-pipeline-probe/0.1 (+https://github.com/thibaultambert-jpg/Citoyen-de-2027)"
}
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


def trim(value, depth: int = 0):
    """Copy of a JSON value with lists cut to 2 items and long strings shortened (structure only)."""
    if depth > 12:
        return "..."
    if isinstance(value, dict):
        return {k: trim(v, depth + 1) for k, v in value.items()}
    if isinstance(value, list):
        return [trim(v, depth + 1) for v in value[:2]] + (
            [f"... {len(value) - 2} more"] if len(value) > 2 else []
        )
    if isinstance(value, str) and len(value) > 50:
        return value[:50] + "..."
    return value


def dump(title: str, value) -> None:
    print(f"--- {title}")
    print(json.dumps(trim(value), ensure_ascii=False, indent=1)[:9000])


def zip_json(url: str) -> zipfile.ZipFile | None:
    status, _, body = get(url)
    print(f"zip {url} status={status} bytes={len(body)}")
    return zipfile.ZipFile(io.BytesIO(body)) if status == 200 else None


def as_list_probe(value):
    return value if isinstance(value, list) else ([] if value is None else [value])


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
            prefixes = sorted(
                {n.split("/")[0] + "/" + (n.split("/")[1] if n.count("/") > 1 else "") for n in names}
            )
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
    found = sorted(
        {
            urllib.parse.urljoin(page, m)
            for m in re.findall(r'href="([^"]+)"', body.decode("utf-8", "replace"))
        }
    )
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
    print(
        "title:",
        data.get("title"),
        "| license:",
        data.get("license"),
        "| last_update:",
        data.get("last_update"),
    )
    urls = []
    for res in data.get("resources", [])[:40]:
        print(
            f"  - {res.get('title')!r} format={res.get('format')} url={res.get('url')} modified={res.get('last_modified')}"  # noqa: E501
        )
        urls.append(res.get("url"))
    return urls


LEGI_TITLES = (
    "constitution du 4 octobre 1958",
    "declaration des droits de l'homme",
    "preambule de la constitution du 27 octobre 1946",
    "charte de l'environnement",
    "6 novembre 1962",
    "code electoral",
)


def _fold(text: str) -> str:
    import unicodedata

    decomposed = unicodedata.normalize("NFKD", text.casefold())
    return "".join(c for c in decomposed if not unicodedata.combining(c)).replace("\u2019", "'")


def probe_legi() -> None:
    """Structure of the DILA LEGI dump: archive names, paths, and the texts of the legal corpus."""
    import tarfile
    import time

    base = "https://echanges.dila.gouv.fr/OPENDATA/LEGI/"
    archives = links(base, r"\.tar\.gz$")
    print("archive count:", len(archives), "| last 5:", archives[-5:])
    globals_ = [u for u in archives if "global" in u.lower()]
    print("global archives:", globals_)
    if not globals_:
        return
    url = globals_[-1]
    path = "/tmp/legi_global.tar.gz"
    started = time.time()
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=600) as resp, open(path, "wb") as out:
        print("headers:", dict(resp.headers))
        while chunk := resp.read(1 << 20):
            out.write(chunk)
    import os

    print(f"downloaded {os.path.getsize(path)} bytes in {time.time() - started:.0f}s")
    found: dict[str, list[tuple[str, str]]] = {t: [] for t in LEGI_TITLES}
    first, prefixes, total = [], {}, 0
    with tarfile.open(path, "r:gz") as tar:
        for member in tar:
            total += 1
            if len(first) < 25:
                first.append(member.name)
            parts = member.name.split("/")
            key = "/".join(parts[:5])
            prefixes[key] = prefixes.get(key, 0) + 1
            if member.isfile() and "/texte/version/" in member.name:
                xml = tar.extractfile(member).read().decode("utf-8", "replace")
                title = re.search(r"<TITREFULL>(.*?)</TITREFULL>", xml, re.S)
                folded = _fold(title.group(1)) if title else ""
                for wanted in LEGI_TITLES:
                    if folded.startswith(wanted) or (wanted == "6 novembre 1962" and wanted in folded[:80]):
                        if len(found[wanted]) < 15:
                            found[wanted].append((member.name, title.group(1)[:160]))
    print("member count:", total)
    print("first members:")
    for name in first:
        print("  ", name)
    print("path prefixes (5 levels), top 40:")
    for key, count in sorted(prefixes.items(), key=lambda kv: -kv[1])[:40]:
        print(f"   {count:8d} {key}")
    for wanted, hits in found.items():
        print(f"\n== titles starting with {wanted!r}:")
        for name, title in hits:
            print("  ", name, "|", title)
    # Samples for the first hit of each wanted title: version, struct and two articles.
    text_dirs = {}
    for hits in found.values():
        for name, _ in hits[:2]:
            text_dirs[name.split("/texte/version/")[0]] = name
    with tarfile.open(path, "r:gz") as tar:
        articles: dict[str, int] = {}
        shown: dict[str, int] = {}
        for member in tar:
            for text_dir in text_dirs:
                if member.name.startswith(text_dir + "/") and member.isfile():
                    kind = member.name[len(text_dir) + 1 :].split("/")[0]
                    if kind == "article":
                        articles[text_dir] = articles.get(text_dir, 0) + 1
                    key = f"{text_dir}|{kind}"
                    limit = 2 if kind in ("article", "texte") else 1
                    if shown.get(key, 0) < limit:
                        shown[key] = shown.get(key, 0) + 1
                        body = tar.extractfile(member).read().decode("utf-8", "replace")
                        print(f"\n----- {member.name} ({len(body)} chars)\n{body[:3500]}")
        print("\narticle files per text:", articles)
    # Official page patterns, checked with identifiers found above (never assumed).
    for text_dir in text_dirs:
        text_id = text_dir.rstrip("/").split("/")[-1]
        for kind in ("loda/id", "codes/texte_lc", "jorf/id"):
            status, ctype, _ = get(f"https://www.legifrance.gouv.fr/{kind}/{text_id}", 200_000)
            print("legifrance", kind, text_id, status, ctype)


def probe_legi_details() -> None:
    """Second LEGI pass: texts of the constitutional block, the 1962 law and nested code sections."""
    import tarfile

    base = "https://echanges.dila.gouv.fr/OPENDATA/LEGI/"
    url = [u for u in links(base, r"\.tar\.gz$") if "global" in u.lower()][-1]
    path = "/tmp/legi_global.tar.gz"
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=600) as resp, open(path, "wb") as out:
        while chunk := resp.read(1 << 20):
            out.write(chunk)
    contains = (
        "1789",
        "27 octobre 1946",
        "charte de l'environnement",
        "2005-205",
        "droits de l'homme et du citoyen",
    )
    found: dict[str, list[str]] = {c: [] for c in contains}
    wanted_dirs = {"JORFTEXT000000684037", "LEGITEXT000006071193"}
    wanted_sections = {"LEGISCTA000006085760"}
    shown: dict[str, int] = {}
    with tarfile.open(path, "r:gz") as tar:
        for member in tar:
            if not member.isfile():
                continue
            name = member.name
            if "/texte/version/" in name:
                xml = tar.extractfile(member).read().decode("utf-8", "replace")
                title = re.search(r"<TITREFULL>(.*?)</TITREFULL>", xml, re.S)
                nature = re.search(r"<NATURE>(.*?)</NATURE>", xml)
                etat = re.search(r"<ETAT>(.*?)</ETAT>", xml)
                folded = _fold(title.group(1)) if title else ""
                for wanted in contains:
                    if wanted in folded[:120] and len(found[wanted]) < 15:
                        found[wanted].append(
                            f"{name} | {nature.group(1) if nature else ''} | {etat.group(1) if etat else ''}"
                            f" | {title.group(1)[:140]}"
                        )
            for marker in wanted_dirs | wanted_sections:
                if marker in name:
                    kind = (
                        "section" if marker in wanted_sections else name.split(marker + "/")[-1].split("/")[0]
                    )
                    key = f"{marker}|{kind}"
                    limit = 3 if kind == "article" else 1
                    if shown.get(key, 0) < limit and (
                        marker in wanted_sections or "/" in name.split(marker)[-1]
                    ):
                        shown[key] = shown.get(key, 0) + 1
                        body = tar.extractfile(member).read().decode("utf-8", "replace")
                        print(f"\n----- {name} ({len(body)} chars)\n{body[:4000]}")
    for wanted, hits in found.items():
        print(f"\n== titles containing {wanted!r}:")
        for hit in hits:
            print("  ", hit)


def probe_legi_increments() -> None:
    """Layout of the daily LEGI increments published after the global archive."""
    import tarfile

    base = "https://echanges.dila.gouv.fr/OPENDATA/LEGI/"
    increments = [u for u in links(base, r"LEGI_[0-9-]+\.tar\.gz$") if "global" not in u]
    print("increment count:", len(increments), "| first:", increments[:2], "| last:", increments[-3:])
    for url in increments[-2:]:
        status, _, body = get(url, 200 * 1024 * 1024)
        print(f"\n===== {url} status={status} size={len(body)}")
        with tarfile.open(fileobj=io.BytesIO(body), mode="r:gz") as tar:
            names = [m.name for m in tar.getmembers()]
            print("members:", len(names))
            for name in names[:15]:
                print("  ", name)
            prefixes: dict[str, int] = {}
            for name in names:
                key = "/".join(name.split("/")[:4])
                prefixes[key] = prefixes.get(key, 0) + 1
            print("prefixes (4 levels):", sorted(prefixes.items(), key=lambda kv: -kv[1])[:15])
            for member in tar.getmembers():
                if member.isfile() and not member.name.endswith(".xml"):
                    data = tar.extractfile(member).read()
                    print(
                        f"\n----- {member.name} ({len(data)} bytes)\n{data[:1500].decode('utf-8', 'replace')}"
                    )


def main() -> int:
    target = sys.argv[1] if len(sys.argv) > 1 else "all"
    if target == "j5legi3":
        probe_legi_increments()
    if target == "j5legi2":
        probe_legi_details()
    if target == "j5legi":
        probe_legi()
    if target == "j3gov":
        zf = zip_json(
            "https://data.assemblee-nationale.fr/static/openData/repository/17/amo/"
            "tous_acteurs_mandats_organes_xi_legislature/AMO30_tous_acteurs_tous_mandats_tous_organes_historique.json.zip"
        )
        if zf:
            names = zf.namelist()
            print("prefixes:", sorted({n.split("/")[1] for n in names if n.count("/") >= 2}))
            kinds, sample = {}, None
            for name in [n for n in names if "/acteur/" in n][:3000]:
                for m in as_list_probe(json.loads(zf.read(name))["acteur"]["mandats"]["mandat"]):
                    kinds[m.get("typeOrgane")] = kinds.get(m.get("typeOrgane"), 0) + 1
                    if m.get("typeOrgane") == "GOUVERNEMENT" and sample is None:
                        sample = m
            print("typeOrgane counts:", kinds)
            if sample:
                dump("GOUVERNEMENT mandat", sample)
                organe = sample.get("organes", {}).get("organeRef")
                if organe and f"json/organe/{organe}.json" in names:
                    dump("its organe", json.loads(zf.read(f"json/organe/{organe}.json")))
    if target == "j3speech":
        describe(
            "vie-publique discours",
            "https://echanges.dila.gouv.fr/OPENDATA/DISCOURS_PUBLICS/vp_discours.json",
        )
        base = "https://data.europarl.europa.eu/api/v2"
        fmt = "format=application%2Fld%2Bjson"
        describe("PE speeches", f"{base}/speeches?person-id=840&{fmt}&offset=0&limit=2")
        describe("PE speeches nofilter", f"{base}/speeches?{fmt}&offset=0&limit=1")
        links("https://data.assemblee-nationale.fr/travaux-parlementaires/debats", r"\.(zip|xml|json)$")
        links("https://data.assemblee-nationale.fr/", r"(debat|compte|syceron|cri)")
    if target == "pe4":
        base = "https://data.europarl.europa.eu/api/v2"
        fmt = "format=application%2Fld%2Bjson"
        status, _, body = get(f"{base}/meps/840?{fmt}")
        if status == 200:
            memberships = json.loads(body)["data"][0]["hasMembership"]
            kinds = sorted(
                {
                    (
                        str(m.get("membershipClassification")),
                        str(m.get("role")),
                        str(m.get("organization"))[:12],
                    )
                    for m in memberships
                }
            )
            print("membership kinds:", kinds)
            for m in memberships:
                if "COMMITTEE" not in str(m.get("membershipClassification")):
                    dump("non-committee membership", m)
                    break
    if target == "urls":
        for url in (
            "https://www.assemblee-nationale.fr/dyn/17/scrutins/2657",
            "https://data.assemblee-nationale.fr/static/openData/repository/17/loi/scrutins/Scrutins.json.zip",
            "https://data.europarl.europa.eu/eli/dl/doc/PV-10-2025-01-20-RCV-ITM-001",
            "https://data.europarl.europa.eu/eli/dl/event/MTG-PL-2025-01-20-DEC-171426",
            "https://www.europarl.europa.eu/meps/fr/840",
            "https://www.senat.fr/senateur/83008P.html",
        ):
            status, ctype, body = get(url, 4096)
            print(f"URLCHECK {status} {ctype} {url}")
    if target == "rne3":
        for url in [u for u in datagouv("repertoire-national-des-elus-1") if u and u.endswith(".csv")]:
            status, _, body = get(url, 64 * 1024)
            header = body.decode("utf-8-sig", "replace").splitlines()[0] if body else ""
            print(f"HEADER {url.rsplit('/', 1)[-1]} status={status}: {header}")
        datagouv("les-senateurs")
    if target == "an4":
        for page in (
            "https://data.assemblee-nationale.fr/acteurs/historique-des-deputes",
            "https://data.assemblee-nationale.fr/archives-16e/votes",
            "https://data.assemblee-nationale.fr/archives-16e/historique-des-deputes",
        ):
            links(page, r"\.zip$")
    if target == "an3":
        zf = zip_json(
            "https://data.assemblee-nationale.fr/static/openData/repository/17/amo/deputes_actifs_mandats_actifs_organes/AMO10_deputes_actifs_mandats_actifs_organes.json.zip"
        )
        if zf:
            acteur = json.loads(zf.read(next(n for n in zf.namelist() if "/acteur/" in n)))
            mandats = acteur["acteur"]["mandats"]["mandat"]
            mandats = mandats if isinstance(mandats, list) else [mandats]
            acteur["acteur"]["mandats"]["mandat"] = [
                m for m in mandats if m.get("typeOrgane") == "ASSEMBLEE"
            ][:1]
            dump("acteur (ASSEMBLEE mandate only)", acteur)
            print("typeOrgane values:", sorted({m.get("typeOrgane") for m in mandats}))
        zf = zip_json(
            "https://data.assemblee-nationale.fr/static/openData/repository/17/loi/scrutins/Scrutins.json.zip"
        )
        if zf:
            scrutin = json.loads(zf.read(sorted(zf.namelist())[-1]))
            groupes = scrutin["scrutin"]["ventilationVotes"]["organe"]["groupes"]["groupe"]
            dump("scrutin.ventilationVotes groupe[0]", groupes[0])
            print(
                "codeTypeVote values:",
                sorted(
                    {
                        json.loads(zf.read(n))["scrutin"]["typeVote"]["codeTypeVote"]
                        for n in zf.namelist()[:400]
                    }
                ),
            )
    if target == "pe3":
        base = "https://data.europarl.europa.eu/api/v2"
        fmt = "format=application%2Fld%2Bjson"
        status, _, body = get(f"{base}/meps/show-current?{fmt}&offset=0&limit=1")
        if status == 200:
            mep = json.loads(body)["data"][0]
            dump("current mep (list item)", mep)
            status, _, body = get(f"{base}/meps/{mep['identifier']}?{fmt}")
            if status == 200:
                dump("mep detail", json.loads(body))
    if target == "an2":
        describe(
            "AN AMO10",
            "https://data.assemblee-nationale.fr/static/openData/repository/17/amo/deputes_actifs_mandats_actifs_organes/AMO10_deputes_actifs_mandats_actifs_organes.json.zip",
        )
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
