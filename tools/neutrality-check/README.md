# :tools:neutrality-check

Build-time guard (AGENTS.md §10): fails `./gradlew test` when an Android string resource or an LLM prompt contains a term from `config/neutrality/evaluative-terms-fr.txt`. Not shipped in the APK.
