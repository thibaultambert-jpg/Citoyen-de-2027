"""Generates the Ed25519 key pair that signs published packs. Run it on a trusted machine:

    python tools/generate_signing_key.py

Store the private value as the GitHub Actions secret PACK_SIGNING_KEY (never in the repository) and
put the public value in gradle.properties as citoyen.packs.publicKey so the app can verify packs.
"""

import base64

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, NoEncryption, PrivateFormat, PublicFormat

key = Ed25519PrivateKey.generate()
private = key.private_bytes(Encoding.Raw, PrivateFormat.Raw, NoEncryption())
public = key.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)
print("PACK_SIGNING_KEY (secret, GitHub Actions only):", base64.b64encode(private).decode())
print("citoyen.packs.publicKey (gradle.properties):   ", base64.b64encode(public).decode())
