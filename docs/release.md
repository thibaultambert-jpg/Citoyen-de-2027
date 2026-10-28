# Tester l’application et ses données (usage personnel)

Décisions du 28/10/2026 : usage personnel, pas de publication. L’APK de test de la CI est la version
installée.

## Installer l’APK

GitHub → onglet **Actions** → choisir une exécution verte, en bas de page section *Artifacts* :

- workflow **Real data** → `citoyen-2027-real-data-apk` : APK contenant déjà les données réelles
  (recommandé pour la première installation) ;
- workflow **Build** → `citoyen-2027-debug-apk` : APK du dernier code, données fictives tant
  qu’aucun pack réel n’est installé.

Dézipper, copier `app-debug.apk` sur le téléphone (Android 10 ou plus récent, 64 bits) et l’ouvrir ;
autoriser l’installation depuis cette source quand Android le demande. Les APK sont signés avec la
clé de débogage fixe du dépôt (`app/debug.keystore`, non secrète) : chaque nouvel APK s’installe
par-dessus le précédent en gardant les données.

## Données réelles et mises à jour

Le workflow **Real data** tourne chaque nuit (et à chaque modification du pipeline) :

1. prépare la liste des candidats, leurs identités et leurs programmes à partir des données
   ouvertes de « 2027 et moi » (Licence Ouverte 2.0) ;
2. construit les packs depuis les **sources officielles** ; une identité qu’elles ne confirment pas
   (nom, date de naissance) est écartée avec ses données, une source en panne est laissée de côté ;
   tout est listé dans `report.json` (artefact `data-report`) ;
3. signe les packs avec ta clé (secret `PACK_SIGNING_KEY`), les vérifie avec la clé publique
   (variable `CITOYEN_PACKS_PUBLIC_KEY`) et les publie sur la release `packs-current` ;
4. produit l’APK de test qui les contient.

L’application installe les nouveaux packs d’elle-même : au lancement et une fois par jour en Wi-Fi,
ou à la demande (Infos → Données et mises à jour → Rechercher une mise à jour).

La planification de nuit ne s’exécute que depuis la branche par défaut (`main`) du dépôt : elle
démarre une fois la pull request fusionnée.

## Clé des packs

Générée une fois sur ta machine (le 28/10/2026). Commandes, sans `|`, dans Git Bash :

```sh
openssl genpkey -algorithm ed25519 -out citoyen-packs.pem
openssl pkey -in citoyen-packs.pem -outform DER -out secret.der
openssl pkey -in citoyen-packs.pem -pubout -outform DER -out public.der
tail -c 32 secret.der > secret.bin
tail -c 32 public.der > public.bin
base64 secret.bin    # -> secret PACK_SIGNING_KEY
base64 public.bin    # -> variable CITOYEN_PACKS_PUBLIC_KEY
```

Le workflow vérifie à chaque exécution que les deux correspondent, sans jamais afficher la clé. En
cas de changement de clé : mettre à jour les deux réglages GitHub, puis réinstaller l’APK une fois.
