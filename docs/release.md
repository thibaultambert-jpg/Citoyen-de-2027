# Publier une version (APK direct et packs de données)

Décision du 22/10/2026 : distribution par APK direct, sans store. Deux workflows GitHub Actions
publient, **uniquement quand l’éditeur les déclenche** :

| Workflow | Déclencheur | Résultat |
| --- | --- | --- |
| `Release APK` (`release-app.yml`) | pousser une étiquette `vX.Y.Z` | APK signé dans une release GitHub **brouillon**, à relire puis publier à la main |
| `Release data packs` (`release-packs.yml`) | lancement manuel (onglet Actions) | packs signés ; publiés sur la release `packs-current` seulement si « publier » est coché |

Les clés privées ne sont jamais dans le dépôt, ni dans un journal, ni visibles de l’agent.

## 1. Générer les clés (une fois, sur une machine de confiance)

### Clé de signature de l’application

```sh
keytool -genkeypair -v -keystore citoyen-release.jks -alias citoyen \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 citoyen-release.jks > citoyen-release.jks.b64   # macOS : base64 -i citoyen-release.jks
```

`keytool` est fourni avec le JDK. Le mot de passe du keystore et celui de la clé sont demandés.

**Sauvegarde hors ligne indispensable** (keystore + mots de passe, par exemple deux supports chiffrés) :
sans cette clé, aucune mise à jour ne pourra être installée par-dessus les installations existantes.

### Clé de signature des packs (Ed25519)

```sh
cd pipeline && make venv && .venv/bin/python tools/generate_signing_key.py
```

Le script affiche la valeur secrète (`PACK_SIGNING_KEY`) et la clé publique. Sauvegarde hors ligne
également : une nouvelle clé oblige à publier une nouvelle version de l’application.

## 2. Renseigner GitHub

Dépôt → Settings → Secrets and variables → Actions.

**Secrets** (chiffrés, jamais affichés) :

| Nom | Valeur |
| --- | --- |
| `APP_KEYSTORE_BASE64` | contenu de `citoyen-release.jks.b64` |
| `APP_KEYSTORE_PASSWORD` | mot de passe du keystore |
| `APP_KEY_ALIAS` | alias choisi (`citoyen` dans l’exemple) |
| `APP_KEY_PASSWORD` | mot de passe de la clé |
| `PACK_SIGNING_KEY` | valeur secrète affichée par `generate_signing_key.py` |

**Variables** (valeurs publiques, intégrées à l’application) :

| Nom | Obligatoire | Valeur |
| --- | --- | --- |
| `CITOYEN_PACKS_PUBLIC_KEY` | oui | clé publique affichée par `generate_signing_key.py` |
| `CITOYEN_LEGAL_PUBLISHER`, `CITOYEN_LEGAL_DIRECTOR`, `CITOYEN_LEGAL_CONTACT`, `CITOYEN_LEGAL_HOSTING` | oui | mentions légales |
| `CITOYEN_LLM_URL`, `CITOYEN_LLM_SHA256`, `CITOYEN_LLM_SIZE_BYTES` | non | fichier du modèle ; sans elles, assistant indisponible |
| `CITOYEN_REPORT_URL` | non | « Signaler une erreur » (défaut : issues du dépôt) |
| `CITOYEN_PACKS_BASE_URL` | non | défaut : release `packs-current` de ce dépôt |

Le workflow APK refuse de construire si une valeur obligatoire manque.

## 3. Publier des packs de données

1. Actions → `Release data packs` → Run workflow, **sans** cocher « publier » : essai à blanc.
2. Télécharger l’artefact `packs` et relire `report.json` (couverture par candidat, section `review`).
3. Relancer en cochant « publier ». Le workflow vérifie la signature avec `CITOYEN_PACKS_PUBLIC_KEY`,
   les tailles et empreintes, puis l’évaluation de la recherche (bloquante) ; il crée une archive
   `packs-<date>-r<n>` et met à jour `packs-current` (packs d’abord, manifeste en dernier).

Retour arrière : réenvoyer sur `packs-current` les fichiers d’une archive précédente, dans le même
ordre. L’application installe tout manifeste signé dont la version diffère de la sienne : elle revient
donc à l’archive à sa prochaine synchronisation.

Pendant l’envoi (quelques secondes), une application qui se synchronise peut lire un manifeste et des
packs de versions différentes : elle refuse l’ensemble (empreintes) et réessaie à la synchronisation
suivante. Rien d’invalide n’est jamais installé.

## 4. Publier une version de l’application

1. Dérouler `docs/publication-checklist.md`.
2. Incrémenter `versionCode` et `versionName` dans `app/build.gradle.kts`, fusionner.
3. `git tag v<versionName> && git push origin v<versionName>`.
4. Le workflow vérifie que l’étiquette correspond à `versionName`, lance les tests, construit et signe
   l’APK, applique les contrôles (taille < 60 Mo, alignement 16 Ko, `apksigner verify`) et crée une
   release **brouillon** avec l’APK, `SHA256SUMS.txt` et l’empreinte du certificat.
5. Relire le brouillon, puis le publier depuis l’interface GitHub.

L’empreinte du certificat ne change jamais d’une version à l’autre : la publier une fois pour toutes
(README, page du projet) permet à chacun de vérifier qu’un APK vient bien de l’éditeur.
