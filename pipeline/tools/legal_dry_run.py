"""Builds the legal pack from the real LEGI base and prints what it contains (CI only, no signing).

Usage: python tools/legal_dry_run.py  (from /pipeline, network required)
"""

from __future__ import annotations

import json
import sys
from datetime import UTC, datetime
from pathlib import Path

from citoyen_pipeline import legal
from citoyen_pipeline.build import write_legal_pack
from citoyen_pipeline.fetch import Cache


def main() -> int:
    inputs = legal.load_inputs(Path("inputs/legal/texts.yaml"))
    cache = Cache(Path("cache"))
    legal.fetch(cache, inputs)
    print("state:", (cache.root / legal.EXTRACT_DIR / legal.STATE_FILE).read_text(encoding="utf-8"))
    today = datetime.now(UTC).date()
    pack, review = legal.assemble(cache, inputs, today)
    for text in pack.texts:
        articles = [a for a in pack.articles if a.text_id == text.id]
        print(f"\n== {text.id} | {text.title} | {len(articles)} articles | {text.url}")
        print("   source:", text.source_name)
        for article in articles[:3] + articles[-1:]:
            print(f"   - [{article.number}] {' > '.join(article.heading_path)} | {article.url}")
            print("     ", article.body[:300].replace("\n", " / "))
            if article.in_force_until:
                print("      until", article.in_force_until)
    print("\nreview:", json.dumps(review, indent=2, ensure_ascii=False)[:6000])
    version = write_legal_pack(pack, Path("dist/legal.sqlite"))
    print("legal pack", version, Path("dist/legal.sqlite").stat().st_size, "bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
