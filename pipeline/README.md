# Pipeline de données

Python 3.12, hors build Gradle (AGENTS.md §5–7). Étapes : `fetch` (cache brut + sha256) → lecture des
sources → vérification des identités → couverture → `build` (SQLite) → manifeste → signature Ed25519.

```sh
make venv        # environnement local
make test lint   # tests hors réseau, ruff
make fetch       # télécharge les sources nécessaires (réseau)
make pack        # dist/core.sqlite, manifest.json, report.json (+ .sig si PACK_SIGNING_KEY)
make test-pack   # pack fictif signé par la clé de TEST, utilisé par les tests de l'app
```

Formats des sources vérifiés avec `tools/probe_sources.py` (exécuté en CI, workflow « Pipeline source
probe »). Remplir les entrées : `docs/pipeline-inputs.md`.
