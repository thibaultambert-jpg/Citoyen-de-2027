# Citoyen de 2027

Application Android native d’information **neutre** sur l’élection présidentielle française de 2027,
utilisable hors ligne. Les règles du projet sont dans [`AGENTS.md`](AGENTS.md) ; elles priment sur tout le reste.

## Construire

Prérequis : JDK 21, Android SDK (plateforme 37).

```sh
./gradlew spotlessCheck test lint :app:assembleDebug
```

`./gradlew test` exécute aussi le scan de neutralité (`:tools:neutrality-check`), qui échoue si un texte de
l’application ou un prompt contient un terme de `config/neutrality/evaluative-terms-fr.txt`.

## Installer et publier

- Tester l’application et publier des packs de données : [`docs/release.md`](docs/release.md).
- Mesurer les performances sur téléphone : [`docs/benchmarks.md`](docs/benchmarks.md).

## Modules

| Module | Rôle |
| --- | --- |
| `:app` | Activité, thème, navigation adaptative. |
| `:core:model` | Types du domaine (Kotlin pur). |
| `:core:testing` | Fixtures **fictives** (Candidat A, B, C). |
| `:core:designsystem` | Thème neutre, avatar à initiales, composants partagés. |
| `:core:ui` | Composants liés au modèle : badge de couverture, statut, date des données, bouton Source. |
| `:core:database`, `:core:data`, `:core:network` | Room/FTS5, dépôts et synchronisation, téléchargement des packs. |
| `:feature:*` | Écrans : candidats, fiche, comparer, question, institutions, infos. |
| `:rag`, `:llm` | Recherche dans les sources, LLM local. |
| `:tools:neutrality-check` | Garde-fou de neutralité exécuté au build. |
| `:benchmarks` | Mesures sur téléphone (démarrage, votes, recherche). |

Avancement par jalon : [`docs/jalons/`](docs/jalons/).
