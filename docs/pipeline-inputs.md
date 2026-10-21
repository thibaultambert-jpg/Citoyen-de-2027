# Remplir les entrées du pipeline

Le pipeline (`/pipeline`, Python 3.12) ne publie que ce qu’un humain a renseigné et validé dans deux
fichiers. Aucun nom ni identifiant n’y est jamais ajouté de mémoire ou par génération automatique
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
