# Mesures sur téléphone (module `:benchmarks`)

Les budgets de AGENTS.md §4 dépendent du téléphone : la CI ne fait que compiler ces mesures. Elles
se lancent à la main, sur un téléphone réel branché en USB (débogage USB activé), de préférence
d’entrée de gamme (Android 10, 64 bits) :

```sh
./gradlew :benchmarks:connectedBenchmarkAndroidTest
```

Le build `benchmark` de l’application reprend le code et les réglages de la version publiée (données
du pack embarqué, sans fixture), signé avec la clé de débogage locale ; il n’est jamais distribué.
Pendant la mesure, laisser le téléphone branché, écran allumé, sans autre application ouverte.

| Mesure | Classe | Budget (AGENTS.md §4) | Métrique à lire |
| --- | --- | --- | --- |
| Démarrage à froid, sans compilation (pire cas) | `StartupBenchmark.coldStartNotCompiled` | < 2 s | `timeToInitialDisplayMs` (médiane) |
| Démarrage à froid, compilé | `StartupBenchmark.coldStartFullyCompiled` | < 2 s | `timeToInitialDisplayMs` (médiane) |
| Votes d’un candidat (Room) | `ScreenBenchmark.candidateSheetVotes` | < 50 ms | `citoyen.votes…Ms` (médiane) |
| Question : routage et recherche (sans IA) | `ScreenBenchmark.searchInSources` | < 300 ms | `citoyen.ask…Ms` (médiane) |

Les deux dernières mesures lisent des sections de trace écrites par l’application (`PerfTrace`,
`:core:data`) ; elles ne contiennent aucun texte de question. Sans candidat dans les données
installées, la mesure des votes est ignorée (rien à mesurer).

Résultats : dans la console, et en JSON dans
`benchmarks/build/outputs/connected_android_test_additional_output/`. Les reporter dans la checklist
de publication avec le modèle du téléphone et la version d’Android.

L’assistant IA a son propre banc d’essai, dans l’application (écran de débogage du J7) : temps au
premier jeton, jetons par seconde, pic de mémoire.
