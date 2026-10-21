# :core:network

HTTPS-only download of signed data packs (and, from J7, model files): `PackHttpClient` with resume
(HTTP Range) and size checks. No secret, no identifier, no user data is ever sent (AGENTS.md §2.4).
