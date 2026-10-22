import json

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from citoyen_pipeline import testpack
from citoyen_pipeline.cli import main
from citoyen_pipeline.sign import public_key_b64
from citoyen_pipeline.verify_dist import check, publishable_files


def _dist(tmp_path):
    testpack.build(tmp_path)
    return tmp_path, public_key_b64(testpack.test_key())


def test_signed_test_pack_is_publishable(tmp_path):
    dist, key = _dist(tmp_path)
    assert check(dist, key) == []
    assert publishable_files(dist)[-2:] == ["manifest.json.sig", "manifest.json"]
    assert main(["verify", "--dist", str(dist), "--public-key", key]) == 0
    assert (dist / "upload-order.txt").read_text().splitlines()[-1] == "manifest.json"


def test_other_public_key_is_refused(tmp_path):
    dist, _ = _dist(tmp_path)
    other = public_key_b64(Ed25519PrivateKey.generate())
    assert check(dist, other) == ["signature does not match the public key (InvalidSignature)"]
    assert main(["verify", "--dist", str(dist), "--public-key", other]) == 1


def test_missing_signature_or_key_is_refused(tmp_path):
    dist, key = _dist(tmp_path)
    assert check(dist, "") == [
        "no public key given: set the citoyen.packs.publicKey value the app is built with"
    ]
    (dist / "manifest.json.sig").unlink()
    assert check(dist, key) == ["manifest.json.sig is missing: PACK_SIGNING_KEY was not set"]


def test_altered_pack_is_refused(tmp_path):
    dist, key = _dist(tmp_path)
    first = json.loads((dist / "manifest.json").read_bytes())["packs"][0]["file"]
    with (dist / first).open("ab") as stream:
        stream.write(b"x")
    assert check(dist, key) == [
        f"{first}: size {(dist / first).stat().st_size} != {(dist / first).stat().st_size - 1}"
    ]
