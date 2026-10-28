# Tester l’application et publier les données (usage personnel)

Décisions du 28/10/2026 : usage personnel, pas de publication. L’APK de test de la CI est la version
installée.

## Installer l’APK de test

1. GitHub → onglet **Actions** → workflow **Build** → dernière exécution verte de la branche.
2. En bas de la page, section *Artifacts* : télécharger `citoyen-2027-debug-apk` (archive zip
   contenant `app-debug.apk`).
3. Copier `app-debug.apk` sur le téléphone (Android 10 ou plus récent, 64 bits) et l’ouvrir ; autoriser
   l’installation d’applications depuis cette source quand Android le demande.

L’APK est signé avec la clé de débogage fixe du dépôt (`app/debug.keystore`, non secrète) : chaque
nouvel APK s’installe par-dessus le précédent en gardant les données. Sans pack réel installé,
l’APK de test affiche les données fictives (Candidat A, B, C).

## Publier les packs de données réelles

### Clé des packs (une fois, sur ta machine)

```sh
cd pipeline && make venv && .venv/bin/python tools/generate_signing_key.py
```

Dépôt GitHub → Settings → Secrets and variables → Actions :

- secret `PACK_SIGNING_KEY` : la valeur secrète affichée ;
- variable `CITOYEN_PACKS_PUBLIC_KEY` : la clé publique affichée.

L’APK de test doit être construit avec cette clé publique pour accepter les packs : ajouter aussi la
ligne `citoyen.packs.publicKey=<clé publique>` dans `gradle.properties` (valeur publique, peut être
commitée).

### Construire et publier

1. Actions → `Release data packs` → Run workflow, **sans** cocher « publier » : essai à blanc ;
   télécharger l’artefact `packs` et relire `report.json`.
2. Relancer en cochant « publier » : le workflow vérifie la signature, les tailles et empreintes et
   l’évaluation de la recherche, crée une archive `packs-<date>-r<n>` et met à jour la release
   `packs-current` (packs d’abord, manifeste en dernier).
3. Dans l’app : Infos → Données et mises à jour → Rechercher une mise à jour.

Retour arrière : réenvoyer sur `packs-current` les fichiers d’une archive précédente, dans le même
ordre ; l’app installe tout manifeste signé dont la version diffère de la sienne.
