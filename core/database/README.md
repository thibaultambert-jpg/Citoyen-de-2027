# :core:database

Room database holding the activated data packs. Schema v1 (J1): `candidates` (status source
embedded), `candidate_coverage` (one row per facet), `data_packs` (version and data date).
Enumerations are stored as their published names; dates as ISO-8601 text. `CorePackDao.replaceCorePack`
swaps a whole pack in one transaction. Schemas are exported to `schemas/` (FTS5 arrives with J5–J6).
