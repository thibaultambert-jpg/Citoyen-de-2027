# :llm

Local assistant (AGENTS.md §8–9): llama.cpp through JNI, in a separate `:llm` process.

- `src/main/cpp/llama.cpp`: git submodule, llama.cpp release b11400 (commit 0bb496d, MIT), built from
  source by the NDK (`-DGGML_BACKEND_DL=ON -DGGML_CPU_ALL_VARIANTS=ON`: one CPU library per instruction
  set, the best one loaded at run time; 16 KB pages). Clone with `git submodule update --init`.
- `src/main/cpp/citoyen_llm.cpp`: the bridge (chat template of the GGUF file, token count, greedy
  generation under a GBNF grammar with prompt-prefix reuse, stop, embeddings). No prompt or text is logged.
- `service/`: `LlmService` (":llm" process; unload after 2 min idle, on memory pressure; stop on
  severe thermal status) and `LlmClient` (app process).
- `model/`: download (WorkManager, Wi-Fi, HTTP Range, SHA-256), manual import, atomic activation.
- `prompts/answer_fr.txt`: system prompt (AGENTS.md §8), packaged as an asset, scanned by
  `:tools:neutrality-check`.

The model file is set at build time: `-Pcitoyen.llm.url=… -Pcitoyen.llm.sha256=… -Pcitoyen.llm.sizeBytes=…`.
Without them the app works in "Mode sans IA".

Bridge test on a Linux host (as in CI): build `src/main/cpp` with CMake, then
`./gradlew :llm:testDebugUnitTest -Pllm.hostLib=<libcitoyen_llm.so> -Pllm.testModel=<stories15M-q4_0.gguf>`.
