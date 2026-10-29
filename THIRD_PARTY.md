# Dépendances tierces

Règle (AGENTS.md §3) : Apache-2.0, MIT ou BSD uniquement pour ce qui est embarqué dans l’APK ;
chaque ajout est justifié ici. Versions : `gradle/libs.versions.toml`.

## Embarquées dans l’application

| Dépendance | Licence | Justification |
| --- | --- | --- |
| Kotlin stdlib (JetBrains) | Apache-2.0 | Langage. |
| kotlinx.coroutines | Apache-2.0 | Coroutines / Flow (MVVM unidirectionnel). |
| kotlinx.serialization (json) | Apache-2.0 | Routes typées de Navigation Compose ; plus tard manifestes de packs. |
| AndroidX Core, Activity Compose | Apache-2.0 | Base Android, edge-to-edge. |
| Jetpack Compose (BOM), Material 3, Material 3 adaptive navigation suite | Apache-2.0 | UI déclarative, navigation adaptative (barre / rail). |
| AndroidX Lifecycle (runtime-compose, viewmodel-compose) | Apache-2.0 | ViewModel et collecte de Flow sensible au cycle de vie. |
| AndroidX Navigation Compose | Apache-2.0 | Navigation entre écrans. |
| Dagger Hilt, AndroidX Hilt Navigation Compose | Apache-2.0 | Injection de dépendances. |
| Material Symbols (google/material-design-icons), 8 icônes en vecteurs XML | Apache-2.0 | Icônes de navigation, copiées dans `core/designsystem/src/main/res/drawable` (pas de dépendance à `material-icons-extended`). |

| Roboto Flex (Type Network), `core/designsystem/src/main/res/font/roboto_flex.ttf` | SIL OFL 1.1 | Police variable des titres et du texte, embarquée pour le hors ligne. Licence hors règle Apache/MIT/BSD : acceptée par l’utilisateur (usage personnel, §1 bis). Texte de licence : `core/designsystem/ROBOTO_FLEX_OFL.txt`. |
| AndroidX Room (runtime, ktx) | Apache-2.0 | Base locale SQLite des packs (J1). |
| AndroidX DataStore (preferences) | Apache-2.0 | Préférences locales (ordre des candidats). |

| AndroidX WorkManager, AndroidX Hilt Work | Apache-2.0 | Synchronisation des packs en arrière-plan (Wi-Fi, reprise). |
| Google Tink (tink-android) | Apache-2.0 | Vérification Ed25519 des manifestes de packs (java.security n’a Ed25519 qu’à partir de l’API 33). |

## Outils de build et de test (non distribués)

| Outil | Licence | Remarque |
| --- | --- | --- |
| Gradle, Android Gradle Plugin, KSP, Room compiler | Apache-2.0 | Build. |
| Spotless | Apache-2.0 | Formatage. |
| ktlint | MIT | Règles de style Kotlin. |
| JUnit 5 (Jupiter, Platform) | **EPL-2.0** | Exigé par AGENTS.md §4 ; uniquement en `testImplementation`, jamais dans l’APK. À confirmer par l’humain. |
| JUnit 4 + JUnit Vintage engine | **EPL-1.0 / EPL-2.0** | Requis par Robolectric (runner JUnit 4), tests uniquement. |
| Robolectric | MIT | Tests JVM nécessitant le framework Android (Room, Compose UI). |
| AndroidX Benchmark (macro-junit4), AndroidX Test (ext junit, runner), UiAutomator | Apache-2.0 | Module `:benchmarks` (mesures sur téléphone des budgets de AGENTS.md §4), jamais dans l’APK distribué. Versions de nowinandroid. |
| AndroidX Test core, Compose UI test (junit4, manifest) | Apache-2.0 | Tests ; `ui-test-manifest` en `debugImplementation` des modules testés uniquement. |
| Turbine | Apache-2.0 | Tests de Flow. |
| kotlinx-coroutines-test | Apache-2.0 | Tests de coroutines. |

## Assistant local (`:llm`)

| Dépendance | Licence | Justification |
| --- | --- | --- |
| llama.cpp et ggml, release b11400 (commit `0bb496dbd3af0add77ff82c406a915b41e839d56`), sous-module `llm/src/main/cpp/llama.cpp` | MIT | Inférence locale du modèle GGUF (AGENTS.md §9), compilée depuis les sources (NDK/CMake), sans AAR tiers. Une bibliothèque CPU par jeu d’instructions, choisie à l’exécution. KleidiAI et OpenSSL désactivés. |
| Modèle Ministral-3-3B-Instruct-2512, GGUF Q4_K_M | Apache-2.0 | Téléchargé à la demande (non embarqué) ; URL et SHA-256 fournis par l’humain (`TODO(verify)`). |
| Modèle de test `stories15M-q4_0.gguf` (ggml-org/models) | `TODO(verify)` | CI uniquement (test du pont natif), jamais embarqué ni publié ; nom et SHA-256 repris de `tests/CMakeLists.txt` de llama.cpp b11400. |

## Pipeline de données (`/pipeline`, hors APK)

| Dépendance | Licence | Justification |
| --- | --- | --- |
| PyYAML | MIT | Lecture des entrées `candidates.yaml` / `identities.yaml`. |
| jsonschema | MIT | Validation des entrées (JSON Schema). |
| cryptography | Apache-2.0 OU BSD-3-Clause | Signature Ed25519 des manifestes. |
| pypdf 6.19.0 | BSD-3-Clause | Extraction du texte et des signets des programmes au format PDF (pipeline uniquement). |
| pytest, ruff | MIT | Tests et lint. |

Données réutilisées (dans les packs, pas dans le code) : Assemblée nationale et Sénat (Licence Ouverte),
Répertoire national des élus (Licence Ouverte v2.0), Parlement européen (CC BY 4.0). Chaque fait garde sa
source, sa date de récupération et sa licence.
Textes juridiques : base LEGI de la DILA (Licence Ouverte), lien vers Légifrance pour chaque article.
Programmes des candidats : hors open data, intégrés seulement après validation humaine de leurs conditions de
réutilisation (`pipeline/inputs/programs/manifest.yaml`), conditions affichées derrière le bouton « Source ».

## Exclusions explicites

Aucune bibliothèque GPL/AGPL, aucune donnée ODbL embarquée, aucun SDK d’analytics, de publicité ou de suivi.
