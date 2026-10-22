# Remplir les entrées du pipeline

Le pipeline (`/pipeline`, Python 3.12) ne publie que ce qu’un humain a renseigné et validé dans les
fichiers d’entrée. Aucun nom ni identifiant n’y est jamais ajouté de mémoire ou par génération automatique
(AGENTS.md §2.6, §7.2).

## `pipeline/inputs/candidates.yaml`

Un candidat entre dans la liste selon la règle d’inclusion (`docs/inclusion-policy.md`).

```yaml
version: 1
inclusion_policy_date: 2026-10-21      # date de la version validée de la règle d’inclusion
candidates:
  - id: prenom-nom                     # identifiant stable : minuscules, chiffres, tirets
    display_name: Prénom Nom           # nom affiché
    sort_name: Nom Prénom              # clé de tri alphabétique
    status: DECLARE                    # DECLARE, RETIRE ou OFFICIEL
    status_date: 2026-09-01            # date de l’annonce, du retrait ou de la liste officielle
    status_source:                     # source du statut, affichée derrière le bouton « Source »
      name: Nom de la source
      url: https://…                   # HTTPS obligatoire
      retrieved_on: 2026-09-02
      license: Licence ou conditions de réutilisation
```

## `pipeline/inputs/identities.yaml`

Rattache un candidat à ses identifiants officiels. Le pipeline vérifie chaque identifiant dans la source
officielle : **nom de famille et date de naissance doivent correspondre**, sinon la construction échoue.
Les mandats locaux du Répertoire national des élus sont rattachés par nom, prénom et date de naissance.

```yaml
version: 1
identities:
  - candidate_id: prenom-nom
    family_name: Nom
    given_name: Prénom
    birth_date: 1970-01-01
    validated_by: initiales de la personne qui a vérifié
    validated_on: 2026-10-21
    an_id: PA000000        # optionnel — Assemblée nationale : uid « PA… » de l’acteur
    senat_id: 00000X       # optionnel — Sénat : « Matricule »
    pe_id: "0000"          # optionnel — Parlement européen : « identifier » de la fiche député
```

Où trouver les identifiants : fiche du député sur data.assemblee-nationale.fr (uid `PA…`), jeu « Les
Sénateurs » sur data.senat.fr (`Matricule`), API du Parlement européen (`/meps`, champ `identifier`).

## `pipeline/inputs/programs/manifest.yaml`

Les programmes ne sont pas des données ouvertes : chaque document est ajouté à la main depuis le site
officiel du candidat, avec l’empreinte exacte du fichier et ses conditions de réutilisation validées.
Un candidat absent de ce fichier apparaît avec « Programme non disponible dans les sources utilisées ».

```yaml
version: 1
programs:
  - candidate_id: prenom-nom
    status: COMPLET                    # COMPLET ou PARTIEL (programme publié en partie)
    documents:
      - id: prenom-nom-programme       # minuscules, chiffres, tirets ; unique
        title: Titre du document tel que publié
        url: https://…                 # adresse officielle du fichier (PDF) ou de la page (HTML)
        format: pdf                    # pdf ou html
        sha256: 0123…                  # empreinte du fichier validé (sha256sum fichier.pdf)
        published_on: 2027-01-10       # date indiquée par le document ou sa page
        version: "1"
        reuse_rights:
          terms: Conditions de réutilisation, telles qu’affichées dans l’app
          validated_on: 2027-01-11     # date de la validation humaine
```

Si le fichier publié change (empreinte différente), la construction échoue : relire la nouvelle version,
puis mettre à jour `sha256`, `version` et `published_on`. Le texte est extrait tel quel (PDF : texte et
signets ; HTML : titres et paragraphes), découpé en extraits de 150 à 300 unités (mots et ponctuation) par
paragraphe ou mesure, jamais reformulé. Un PDF numérisé sans couche texte est refusé (pas d’OCR). Les
extraits contenant des consignes adressées à une IA sont listés dans `report.json` pour relecture.

## `pipeline/inputs/themes.yaml` et `theme_overrides.yaml`

`themes.yaml` fixe la liste des thèmes (15 à 20, plus « Thème non déterminé ») et leurs règles de
mots-clés (expressions régulières sur le texte en minuscules et sans accents). **Brouillon à relire** :
libellés neutres, règles explicites ; le rapport `report.json` donne le nombre de votes et d’extraits par
thème pour ajuster les règles. Aucun thème n’est attribué par une IA.

`theme_overrides.yaml` enregistre une décision humaine pour un scrutin (`target: ballot`, identifiant
officiel du scrutin) ou un extrait (`target: chunk`, identifiant affiché dans `report.json`) ; elle remplace
les thèmes attribués par les règles et apparaît dans l’app comme « attribué après relecture humaine ».

```yaml
version: 1
overrides:
  - target: ballot
    id: identifiant-officiel-du-scrutin   # « uid » du scrutin dans la source
    themes: [sante]
```

## `pipeline/inputs/legal/texts.yaml`

Liste des textes juridiques de l’application (AGENTS.md §8), tirés de la base LEGI de la DILA (Licence
Ouverte) : archive globale puis toutes les mises à jour quotidiennes publiées depuis. Seuls les articles
en vigueur à la date de construction sont gardés, mot pour mot, avec leurs titres et le lien Légifrance de
l’article. L’ordre du fichier est l’ordre d’affichage.

```yaml
version: 1
texts:
  - id: constitution-1958            # identifiant dans l’app
    legi_id: LEGITEXT000006071194    # identifiant LEGI du texte
  - id: code-electoral
    legi_id: LEGITEXT000006070239
    sections:                        # facultatif : ne garder que ces sections (et leur contenu)
      - LEGISCTA000000000000         # « cid » de la section, stable d’une version à l’autre
```

Pour le Code électoral, les sections applicables à l’élection présidentielle sont à choisir par un
humain : l’identifiant `cid` d’une section figure dans les fichiers `section_ta` de LEGI (et dans
l’adresse Légifrance des sections). Une section absente ou plus en vigueur fait échouer la construction.
Le rapport `report.json` donne le nombre d’articles par texte et signale les versions à venir connues.

## `pipeline/inputs/sponsorships.yaml`

Parrainages validés publiés par le Conseil constitutionnel sur data.gouv.fr (au moins deux fois par
semaine pendant la collecte). Format vérifié sur le jeu 2022 (sonde du 22/10/2026) : un fichier CSV
cumulatif par publication, séparateur `;`, colonnes `Civilité;Nom;Prénom;Mandat;Circonscription;
Département;Candidat;Date de publication`. Le pipeline prend le CSV le plus récent du jeu.

```yaml
version: 1
dataset: ""   # slug data.gouv.fr du jeu 2027, dès sa publication
names:
  <id du candidat>: "NOM Prénom"   # copié tel quel depuis la colonne « Candidat » du fichier
```

- Le rattachement se fait **uniquement** par ces noms recopiés à la main et comparés à l’identique ;
  jamais d’appariement automatique.
- Le pack ne contient que des **décomptes** par candidat : total, nombre de départements ou
  collectivités distincts, répartition par mandat, date de la dernière publication incluse. Les
  noms des élus parrains ne sont pas copiés.
- `report.json` (section `review`) signale un nom introuvable dans le fichier et les noms publiés
  rattachés à aucun candidat.
- Tant que `dataset` est vide, aucune donnée de parrainage n’est produite. L’affichage dans
  l’application reste à faire (écran Infos ou fiche candidat).

## Publier

1. Générer la clé de signature une seule fois : `python pipeline/tools/generate_signing_key.py`.
   Valeur privée → secret GitHub Actions `PACK_SIGNING_KEY` ; valeur publique →
   `citoyen.packs.publicKey=…` dans `gradle.properties`.
2. Après relecture des deux fichiers, lancer le workflow **Publish data packs** (onglet Actions).
   Il télécharge les sources officielles, vérifie les identités, construit, signe et publie le pack
   dans la release `packs-current`, que l’app consulte chaque jour en Wi-Fi.

Sources utilisées en J2 : Assemblée nationale (acteurs, mandats, scrutins des 16e et 17e législatures),
Sénat (mandats sénatoriaux), Parlement européen (mandats, votes par appel nominal), Répertoire national des
élus (mandats locaux en cours). Aucun jeu ouvert des votes nominatifs du Sénat n’a été trouvé : ils sont
indiqués comme indisponibles, jamais comme absents.
