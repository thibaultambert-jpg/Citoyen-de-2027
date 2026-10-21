# Reproducible pipeline entry points (AGENTS.md section 7). Run from /pipeline.
PYTHON ?= python3.12
VENV ?= .venv
PY = $(VENV)/bin/python

.PHONY: venv test lint fetch pack clean

venv:
	$(PYTHON) -m venv $(VENV)
	$(PY) -m pip install --quiet -e ".[dev]"

test:
	$(PY) -m pytest -q

lint:
	$(PY) -m ruff check .
	$(PY) -m ruff format --check .

# Downloads official sources into cache/ (network required; runs in CI).
fetch:
	$(PY) -m citoyen_pipeline fetch --cache cache

# Builds dist/ from inputs/ and cache/; signs when PACK_SIGNING_KEY is set.
pack:
	$(PY) -m citoyen_pipeline pack --inputs inputs --cache cache --out dist

clean:
	rm -rf dist cache
