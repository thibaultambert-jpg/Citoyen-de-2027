# :rag

Retrieval without LLM (AGENTS.md §8): refusal gate, routing of structured questions, lexical BM25 index
and int8 vector index with RRF fusion, metadata filters, balanced retrieval across candidates,
abstention gate driven by `rag_config` (shipped in the `core` pack). Plain Kotlin (JVM): used by the app
and by the evaluation harness `:tools:rag-eval`.
