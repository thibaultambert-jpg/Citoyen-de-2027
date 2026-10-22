# Checklist de publication

À dérouler par l’humain avant chaque version publiée. Chaque case non cochée bloque la publication.
Rien ici n’est rempli par l’agent : les valeurs marquées `TODO(verify)` sont à fournir ou à confirmer.

## 1. Valeurs de build (propriétés Gradle, jamais dans le dépôt)

- [ ] `citoyen.legal.publisher`, `citoyen.legal.director`, `citoyen.legal.contact`,
      `citoyen.legal.hosting` : mentions légales (page « Mentions légales » ; vides → « Non renseigné »).
- [ ] `citoyen.reportUrl` : adresse du bouton « Signaler une erreur » (défaut : issues du dépôt GitHub).
- [ ] `citoyen.packs.baseUrl` et `citoyen.packs.publicKey` : hébergement des packs et clé publique
      Ed25519. La clé privée reste un secret de CI, jamais visible de l’agent.
- [ ] `citoyen.llm.url`, `citoyen.llm.sha256`, `citoyen.llm.sizeBytes` : fichier GGUF
      Ministral-3-3B-Instruct-2512 Q4_K_M (AGENTS.md §9). Sans valeurs, l’assistant reste indisponible.
- [ ] Modèle d’embeddings (`embed.gguf`) : choix final après benchmark sur le jeu golden ; même modèle
      et même quantification dans le pipeline et sur l’appareil.
- [ ] Clé de signature de l’application (keystore de publication) : aucune configuration de signature
      release n’existe encore dans `app/build.gradle.kts`. `TODO(verify)` : signature d’app Play ou APK direct.

## 2. Contrôles automatiques (CI)

- [ ] CI verte : build, lint (ktlint, Android lint), tests unitaires et Robolectric, pipeline (pytest, ruff).
- [ ] Évaluation RAG bloquante (`eval/golden.jsonl`, ≥ 150 questions) au vert sur le pack publié.
- [ ] `tools/release-checks/check_apk.py` : APK < 60 Mo hors modèle ; bibliothèques natives alignées
      sur 16 Ko (exigence Google Play).
- [ ] Tests de neutralité : liste de mots évaluatifs, ordre alphabétique, parité des gabarits.
- [ ] Tests d’accessibilité : libellés, cibles ≥ 48 dp, titres, police ×2, contraste AA des deux thèmes.
- [ ] Budgets : récupération RAG < 300 ms (`PerformanceBudgetTest`) ; requêtes Room indexées (`QueryBudgetTest`).
- [ ] `targetSdk` = dernière version stable au moment de la publication (actuellement API 36,
      `build-logic`) ; à revérifier.

## 3. Contrôles manuels sur appareil

- [ ] Parcours complet avec TalkBack (liste, fiche, onglets, votes, comparateur, question, Infos).
- [ ] Police système maximale et option « Taille du texte » de l’app : aucun texte coupé.
- [ ] Démarrage à froid < 2 s sur un appareil d’entrée de gamme (Android 10, minSdk 29), mesuré
      en release (Macrobenchmark ou `adb shell am start -W`).
- [ ] Requêtes Room < 50 ms sur appareil (fiche d’un candidat avec beaucoup de votes, release).
- [ ] Mode avion : l’app est utile dès le premier lancement avec le pack `core` embarqué.
- [ ] Assistant IA sur un appareil ≥ 6 Go de RAM : temps au premier token, jetons/s, pic de RAM
      (écran de benchmark debug) ; comparaison Q4_K_M / Q4_0 avant de figer la quantification.
- [ ] « Mode sans IA » sur un appareil sous le seuil : message clair.
- [ ] Aucune question dans `adb logcat`.

## 4. Données et contenus

- [ ] Règle d’inclusion (`docs/inclusion-policy.md`) validée et datée ; date présente dans le pack.
- [ ] `candidates.yaml` et `identities.yaml` relus (deux attributs concordants par identité).
- [ ] Droits de réutilisation de chaque programme vérifiés (`programs/manifest.yaml`).
- [ ] Liste officielle du Conseil constitutionnel intégrée dès sa publication (statut `OFFICIEL`).
- [ ] Calendrier officiel et « Comment voter » : uniquement depuis des sources officielles intégrées
      au pipeline (décret de convocation, service-public.fr), jamais écrits de mémoire.
- [ ] Période de réserve électorale : avis d’un juriste (par exemple art. L49 du Code électoral) ;
      `pipeline/inputs/reserve.json` mis à jour en conséquence, pack republié.

## 5. Store et conformité

- [ ] Distribution choisie (Play Store ou APK direct).
- [ ] Politique de confidentialité publiée à une URL stable (reprend la page « Vie privée » de l’app).
- [ ] Formulaire « Sécurité des données » Play : aucune donnée collectée ni partagée.
- [ ] `TODO(verify)` : règles Google Play applicables aux applications d’information électorale ou
      politique (déclarations éventuelles).
- [ ] `THIRD_PARTY.md` à jour ; licences Apache-2.0, MIT ou BSD seulement ; textes de licence inclus.
- [ ] Page « Historique des mises à jour » cohérente avec les packs publiés.
