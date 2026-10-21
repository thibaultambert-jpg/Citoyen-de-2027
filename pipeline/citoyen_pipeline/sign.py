"""Pack manifest and Ed25519 signature (AGENTS.md §5).

`manifest.json` lists every pack with its sha256 and size; `manifest.json.sig` holds the base64
Ed25519 signature of the exact manifest bytes. The app embeds the public key and refuses any pack
whose manifest signature or file hash does not match.
"""

from __future__ import annotations

import base64
import hashlib
import json
import os
from pathlib import Path

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey

from citoyen_pipeline import PACK_SCHEMA_VERSION

KEY_ENV = "PACK_SIGNING_KEY"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build_manifest(packs: list[dict], generated_on: str) -> bytes:
    manifest = {
        "schema_version": PACK_SCHEMA_VERSION,
        "generated_on": generated_on,
        "embedding_model_id": None,
        "packs": sorted(packs, key=lambda p: p["id"]),
    }
    return (json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8")


def pack_entry(pack_id: str, version: str, file: Path, sources: list[dict], licenses: list[str]) -> dict:
    return {
        "id": pack_id,
        "version": version,
        "file": file.name,
        "sha256": sha256_file(file),
        "size": file.stat().st_size,
        "sources": sources,
        "licenses": sorted(set(licenses)),
    }


def private_key_from_env() -> Ed25519PrivateKey | None:
    """The key is a base64 raw 32-byte seed, provided as a CI secret only; never stored in the repository."""
    value = os.environ.get(KEY_ENV, "").strip()
    if not value:
        return None
    return Ed25519PrivateKey.from_private_bytes(base64.b64decode(value))


def sign(manifest: bytes, key: Ed25519PrivateKey) -> bytes:
    return base64.b64encode(key.sign(manifest)) + b"\n"


def public_key_b64(key: Ed25519PrivateKey) -> str:
    from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat

    return base64.b64encode(key.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)).decode("ascii")


def verify(manifest: bytes, signature_b64: bytes, public_key_b64_value: str) -> None:
    """Raises cryptography.exceptions.InvalidSignature when the signature does not match."""
    key = Ed25519PublicKey.from_public_bytes(base64.b64decode(public_key_b64_value))
    key.verify(base64.b64decode(signature_b64.strip()), manifest)
