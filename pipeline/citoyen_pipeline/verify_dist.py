"""Pre-publication check of a built `dist/` directory, as the app will see it (AGENTS.md §5).

The manifest signature must match the public key embedded in the app, and every listed pack must be
present with the exact size and sha256 of the manifest. Run by the pack release workflow before any
upload, so a pack that the app would refuse is never published.
"""

from __future__ import annotations

import json
from pathlib import Path

from cryptography.exceptions import InvalidSignature

from citoyen_pipeline.sign import sha256_file, verify


def check(dist: Path, public_key: str) -> list[str]:
    """Returns the problems found; an empty list means the directory can be published."""
    manifest_file, signature_file = dist / "manifest.json", dist / "manifest.json.sig"
    if not manifest_file.is_file():
        return ["manifest.json is missing"]
    if not signature_file.is_file():
        return ["manifest.json.sig is missing: PACK_SIGNING_KEY was not set"]
    if not public_key.strip():
        return ["no public key given: set the citoyen.packs.publicKey value the app is built with"]
    manifest = manifest_file.read_bytes()
    try:
        verify(manifest, signature_file.read_bytes(), public_key.strip())
    except (InvalidSignature, ValueError) as error:
        return [f"signature does not match the public key ({type(error).__name__})"]
    problems = []
    for entry in json.loads(manifest)["packs"]:
        file = dist / entry["file"]
        if not file.is_file():
            problems.append(f"{entry['file']} is missing")
        elif file.stat().st_size != entry["size"]:
            problems.append(f"{entry['file']}: size {file.stat().st_size} != {entry['size']}")
        elif sha256_file(file) != entry["sha256"]:
            problems.append(f"{entry['file']}: sha256 does not match the manifest")
    return problems


def publishable_files(dist: Path) -> list[str]:
    """Packs first, then the signature and the manifest: the manifest is the last file replaced."""
    packs = [entry["file"] for entry in json.loads((dist / "manifest.json").read_bytes())["packs"]]
    return [*packs, "manifest.json.sig", "manifest.json"]
