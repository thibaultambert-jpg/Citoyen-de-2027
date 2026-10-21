"""Command line: `python -m citoyen_pipeline {fetch,pack,test-pack}`."""

from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import UTC, date, datetime
from pathlib import Path

from citoyen_pipeline import testpack
from citoyen_pipeline.assemble import assemble_legal, assemble_packs, fetch_all, inputs_digest
from citoyen_pipeline.build import pack_version, write_core_pack, write_legal_pack, write_programs_pack
from citoyen_pipeline.fetch import Cache
from citoyen_pipeline.sign import build_manifest, pack_entry, private_key_from_env, public_key_b64, sign


def _generated_on() -> date:
    # SOURCE_DATE_EPOCH makes the build date, hence the whole pack, reproducible.
    epoch = os.environ.get("SOURCE_DATE_EPOCH")
    moment = datetime.fromtimestamp(int(epoch), UTC) if epoch else datetime.now(UTC)
    return moment.date()


def cmd_fetch(args) -> int:
    used = fetch_all(Path(args.inputs), Cache(Path(args.cache), delay_seconds=args.delay))
    print("Fetched sources:", ", ".join(used) if used else "none (no identity link in inputs)")
    return 0


def cmd_pack(args) -> int:
    inputs, out, cache = Path(args.inputs), Path(args.out), Cache(Path(args.cache))
    pack, programs, review = assemble_packs(inputs, cache, _generated_on())
    if not pack.candidates and not args.allow_empty:
        print("inputs/candidates.yaml is empty: nothing to publish (use --allow-empty to build anyway).")
        return 2
    version = pack_version(pack, inputs_digest(inputs, cache))
    pack_file = out / "core.sqlite"
    write_core_pack(pack, version, pack_file)
    used = {m.source_id for m in pack.mandates} | {v.source_id for v in pack.votes}
    used |= {c.status_source_id for c in pack.candidates} | {s.source_id for s in pack.speeches}
    sources = [
        {"name": s.name, "url": s.url, "retrieved_on": s.retrieved_on.isoformat(), "license": s.license}
        for s in sorted(pack.sources, key=lambda s: s.id)
        if s.id in used
    ]
    programs_file = out / "programs.sqlite"
    programs_version = write_programs_pack(programs, {t.id for t in pack.themes}, programs_file)
    documents = [
        {"name": d.title, "url": d.url, "retrieved_on": d.retrieved_on.isoformat(), "license": d.license}
        for d in sorted(programs.documents, key=lambda d: d.id)
    ]
    entries = [
        pack_entry("core", version, pack_file, sources, [s["license"] for s in sources]),
        pack_entry("programs", programs_version, programs_file, documents, [d["license"] for d in documents]),
    ]
    legal_pack, legal_review = assemble_legal(inputs, cache, pack.generated_on)
    review = {**review, **legal_review}
    legal_version = None
    if legal_pack.texts:
        legal_file = out / "legal.sqlite"
        legal_version = write_legal_pack(legal_pack, legal_file)
        legal_sources = [
            {
                "name": t.source_name,
                "url": t.url,
                "retrieved_on": t.retrieved_on.isoformat(),
                "license": t.license,
            }
            for t in legal_pack.texts
        ]
        entries.append(
            pack_entry(
                "legal", legal_version, legal_file, legal_sources, [t.license for t in legal_pack.texts]
            )
        )
    manifest = build_manifest(entries, pack.generated_on.isoformat())
    (out / "manifest.json").write_bytes(manifest)
    report = {
        "version": version,
        "candidates": {
            c.id: {
                "mandates": sum(m.candidate_id == c.id for m in pack.mandates),
                "votes": sum(v.candidate_id == c.id for v in pack.votes),
                "speeches": sum(s.candidate_id == c.id for s in pack.speeches),
                "program_status": programs.statuses[c.id].value,
                "program_chunks": sum(ch.candidate_id == c.id for ch in programs.chunks),
                "coverage": {
                    f.value: lvl.value for (cid, f), lvl in sorted(pack.coverage.items()) if cid == c.id
                },
            }
            for c in pack.candidates
        },
        "programs_version": programs_version,
        "legal": {
            "version": legal_version,
            "articles": {t.id: sum(a.text_id == t.id for a in legal_pack.articles) for t in legal_pack.texts},
        },
        # Share of items per theme, to review the keyword rules (AGENTS.md §7.4).
        "themes": {
            "votes": _theme_counts(pack.vote_themes),
            "program_chunks": _theme_counts(programs.chunk_themes),
        },
        # To check by hand before publishing (e.g. homonyms behind a vie-publique.fr name).
        "review": review,
    }
    (out / "report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    key = private_key_from_env()
    if key is None:
        print("PACK_SIGNING_KEY is not set: manifest left UNSIGNED (the app will refuse it).")
    else:
        (out / "manifest.json.sig").write_bytes(sign(manifest, key))
        print("Signed with public key", public_key_b64(key))
    print(json.dumps(report, indent=2, ensure_ascii=False))
    return 0


def _theme_counts(tags) -> dict[str, int]:
    counts: dict[str, int] = {}
    for item_tags in tags.values():
        for tag in item_tags:
            counts[tag.theme_id] = counts.get(tag.theme_id, 0) + 1
    return dict(sorted(counts.items()))


def cmd_test_pack(args) -> int:
    info = testpack.build(Path(args.out))
    print("Fictional test pack written to", args.out, "| TEST public key:", info["public_key"])
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="citoyen_pipeline")
    sub = parser.add_subparsers(dest="command", required=True)
    fetch = sub.add_parser("fetch", help="download the official sources needed by the inputs")
    fetch.add_argument("--inputs", default="inputs")
    fetch.add_argument("--cache", default="cache")
    fetch.add_argument("--delay", type=float, default=0.7, help="seconds between requests (API quotas)")
    fetch.set_defaults(func=cmd_fetch)
    pack = sub.add_parser("pack", help="build, report and sign the core and programs packs")
    pack.add_argument("--inputs", default="inputs")
    pack.add_argument("--cache", default="cache")
    pack.add_argument("--out", default="dist")
    pack.add_argument("--allow-empty", action="store_true")
    pack.set_defaults(func=cmd_pack)
    test = sub.add_parser("test-pack", help="write the fictional, test-signed pack used by app tests")
    test.add_argument("--out", required=True)
    test.set_defaults(func=cmd_test_pack)
    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
