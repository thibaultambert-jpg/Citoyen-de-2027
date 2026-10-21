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
| Material Symbols (google/material-design-icons), 6 icônes en vecteurs XML | Apache-2.0 | Icônes de navigation, copiées dans `core/designsystem/src/main/res/drawable` (pas de dépendance à `material-icons-extended`). |

Déclarées dans le catalogue mais pas encore utilisées (J1–J2) : AndroidX Room, DataStore, WorkManager (Apache-2.0).

## Outils de build et de test (non distribués)

| Outil | Licence | Remarque |
| --- | --- | --- |
| Gradle, Android Gradle Plugin, KSP | Apache-2.0 | Build. |
| Spotless | Apache-2.0 | Formatage. |
| ktlint | MIT | Règles de style Kotlin. |
| JUnit 5 (Jupiter, Platform) | **EPL-2.0** | Exigé par AGENTS.md §4 ; uniquement en `testImplementation`, jamais dans l’APK. À confirmer par l’humain. |
| Turbine | Apache-2.0 | Tests de Flow. |
| kotlinx-coroutines-test | Apache-2.0 | Tests de coroutines. |

## Exclusions explicites

Aucune bibliothèque GPL/AGPL, aucune donnée ODbL embarquée, aucun SDK d’analytics, de publicité ou de suivi.
