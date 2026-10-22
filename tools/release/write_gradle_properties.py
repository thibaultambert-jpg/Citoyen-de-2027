"""Writes the release build properties into $GRADLE_USER_HOME/gradle.properties from environment
variables set by the release workflow (GitHub secrets and variables), so that no value appears on a
command line or in the repository. Fails when a value required for a public release is missing.

Usage: python3 -I tools/release/write_gradle_properties.py
"""

import os
import sys
from pathlib import Path

# Gradle property -> environment variable. Required: a release must be signed, must verify the
# packs it downloads and must carry its legal notice (docs/publication-checklist.md).
REQUIRED = {
    "citoyen.signing.storeFile": "APP_KEYSTORE_FILE",
    "citoyen.signing.storePassword": "APP_KEYSTORE_PASSWORD",
    "citoyen.signing.keyAlias": "APP_KEY_ALIAS",
    "citoyen.signing.keyPassword": "APP_KEY_PASSWORD",
    "citoyen.packs.publicKey": "CITOYEN_PACKS_PUBLIC_KEY",
    "citoyen.legal.publisher": "CITOYEN_LEGAL_PUBLISHER",
    "citoyen.legal.director": "CITOYEN_LEGAL_DIRECTOR",
    "citoyen.legal.contact": "CITOYEN_LEGAL_CONTACT",
    "citoyen.legal.hosting": "CITOYEN_LEGAL_HOSTING",
}
# Optional: without them the app keeps its defaults (assistant shown as unavailable, report button
# pointing to the repository issues, packs from the packs-current release of this repository).
OPTIONAL = {
    "citoyen.llm.url": "CITOYEN_LLM_URL",
    "citoyen.llm.sha256": "CITOYEN_LLM_SHA256",
    "citoyen.llm.sizeBytes": "CITOYEN_LLM_SIZE_BYTES",
    "citoyen.reportUrl": "CITOYEN_REPORT_URL",
    "citoyen.packs.baseUrl": "CITOYEN_PACKS_BASE_URL",
}


def escape(value: str) -> str:
    """java.util.Properties escaping for a value."""
    out = value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r")
    for char in "=:#!":
        out = out.replace(char, "\\" + char)
    out = out[:1].replace(" ", "\\ ") + out[1:]
    # ASCII only: gradle.properties is read as ISO-8859-1, other characters are \uXXXX escapes.
    return "".join(c if ord(c) < 0x80 else "".join(f"\\u{u:04x}" for u in _utf16(c)) for c in out)


def _utf16(char: str) -> list[int]:
    data = char.encode("utf-16-be")
    return [int.from_bytes(data[i : i + 2], "big") for i in range(0, len(data), 2)]


def main() -> int:
    missing = [env for env in REQUIRED.values() if not os.environ.get(env, "").strip()]
    if missing:
        print("Release refused, missing secrets or variables:", ", ".join(missing))
        return 1
    absent = [env for env in OPTIONAL.values() if not os.environ.get(env, "").strip()]
    if absent:
        print("Defaults kept for:", ", ".join(absent))
    lines = [
        f"{prop}={escape(os.environ[env].strip())}"
        for prop, env in {**REQUIRED, **OPTIONAL}.items()
        if os.environ.get(env, "").strip()
    ]
    home = Path(os.environ.get("GRADLE_USER_HOME") or Path.home() / ".gradle")
    home.mkdir(parents=True, exist_ok=True)
    target = home / "gradle.properties"
    with target.open("a", encoding="ascii") as stream:
        stream.write("\n".join(lines) + "\n")
    target.chmod(0o600)
    print(f"{len(lines)} release properties written to {target}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
