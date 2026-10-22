# Installer Citoyen 2027 sur Android

L’application n’est pas sur un store : elle s’installe à partir d’un fichier APK publié sur la page
des versions du projet, <https://github.com/thibaultambert-jpg/Citoyen-de-2027/releases>.

## Appareils compatibles

- Android 10 ou plus récent, processeur 64 bits ARM (la quasi-totalité des téléphones récents).
- L’assistant IA (facultatif) demande au moins 6 Go de mémoire vive et 4 Go d’espace libre ; sans
  cela, l’application fonctionne en « Mode sans IA » (recherche et extraits des sources).

## Installer

1. Sur la page des versions, ouvrir la dernière version publiée et télécharger le fichier
   `citoyen-2027-<version>.apk`.
2. Ouvrir le fichier téléchargé. Android demande d’autoriser l’application qui l’ouvre (navigateur ou
   gestionnaire de fichiers) à installer des applications inconnues : l’autoriser pour cette fois,
   puis désactiver l’autorisation après l’installation si on le souhaite. Les intitulés exacts
   varient selon le fabricant du téléphone.
3. Confirmer l’installation. Google Play Protect peut afficher un avertissement pour une application
   qui ne vient pas du Play Store ; la vérification ci-dessous permet de s’assurer de son origine.

Au premier lancement, l’application contient déjà les données de base et fonctionne hors ligne.

## Vérifier l’APK (facultatif, recommandé)

Chaque version publie deux empreintes :

- **l’empreinte SHA-256 du fichier APK** (fichier `SHA256SUMS.txt`) : sur un ordinateur,
  `sha256sum citoyen-2027-<version>.apk` (Linux), `shasum -a 256 …` (macOS) ou
  `Get-FileHash … -Algorithm SHA256` (Windows) doit afficher la même valeur ;
- **l’empreinte du certificat de signature** (fichier `certificate.txt`) : elle est identique pour
  toutes les versions. Avec les outils Android : `apksigner verify --print-certs citoyen-2027-<version>.apk`.

Si une empreinte diffère, ne pas installer le fichier et le signaler.

## Mettre à jour

L’application ne se met pas à jour toute seule (pas de store). Pour une nouvelle version, télécharger
et ouvrir le nouvel APK : il s’installe par-dessus l’ancien, en conservant les données et le modèle
d’IA téléchargés. Android refuse l’installation si le fichier n’est pas signé par la même clé : c’est
une protection, ne pas contourner ce refus.

Les **données** (candidats, mandats, votes, programmes, textes) se mettent à jour indépendamment, dans
l’application (Infos → Données et mises à jour → Rechercher une mise à jour), sans réinstaller l’APK.

## Vie privée

Aucune question ni donnée personnelle ne quitte le téléphone ; pas de compte, pas de publicité, pas de
mesure d’audience. Le réseau ne sert qu’à télécharger les packs de données et, si on l’active, le
modèle d’IA.

## Désinstaller

Comme toute application Android : appui long sur l’icône → Désinstaller. Les données de l’application
et le modèle d’IA sont supprimés avec elle.
