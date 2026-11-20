# Story 0214 - Idempotence et securite du rejeu MCP

## Statut

IMPLEMENTED - awaiting human acceptance.

Cette Story materialise l'etape suivante de l'epic MCP Error Resilience Policy.
L'implementation a ete explicitement autorisee apres validation du perimetre et
des decisions de contrat. L'acceptance humaine finale reste a effectuer.

## Objectif

Definir et implementer, dans une Story ulterieure autorisee, une politique
explicite d'idempotence et de securite du rejeu pour les operations MCP vers
Core.

La politique doit distinguer l'identite technique d'une operation, la cle
d'idempotence metier, les operations surement rejouables et les mutations dont
le resultat devient ambigu apres un timeout ou une perte de connexion.

## Artefacts gouvernants

- Epic `mcp-error-resilience-policy`.
- ADR-077 - MCP Actor Identity and Human Delegation Boundary.
- ADR-078 - DevLog Internal Agent and External Agent Client Identity.
- ADR-081 - MCP Error Classification, Retry and Idempotency Policy.
- Story 0172 - MCP Runtime Observability and Instance Lifecycle.
- Story 0211 - Politique normative des erreurs MCP et des retries.
- Story 0212 - Retry et renouvellement du runtime MCP.
- Story 0213 - Politique des appels MCP vers Core.

## Probleme

Le runtime conserve un `operationId` local pour suivre une operation et
maintient une allowlist d'operations rejouables. Cet identifiant ne fournit
cependant pas une garantie d'idempotence metier apres redemarrage, perte de
connexion ou execution cote serveur suivie d'une reponse perdue.

Le contrat `devlog_story_agent` accepte deja une cle `Idempotency-Key` et Core
la hash, la scope et la compare au digest de soumission. D'autres mutations MCP
n'ont pas encore de contrat equivalent. La politique de rejeu n'est donc pas
uniforme et ne permet pas de repondre de facon deterministe a un timeout apres
effet metier possible.

## Classification initiale

### Operations surement rejouables

- lectures Core et ressources MCP sans effet metier ;
- appels Companion en lecture seule ;
- consultation de statuts, snapshots et resultats ;
- ingestion d'observabilite, sous sa cle de deduplication existante.

### Operations rejouables avec garantie explicite

- `devlog_story_agent` lorsqu'une `Idempotency-Key` valide est liee a la
  requete canonique ;
- follow-up Story Agent apres confirmation d'un contrat de cle approprie ;
- `sync_project_history` uniquement si la garantie de dedoublonnage Core est
  explicitement rattachee au contrat MCP.

### Operations non rejouables par defaut

- `register_project` ;
- creation de source ;
- toute mutation sans deduplication autoritaire cote serveur ;
- toute operation dont l'execution precedente est ambigue.

Cette classification est une hypothese de travail a confirmer pendant le
Repository Analysis et par validation humaine. Le nom d'un tool ne constitue
pas une preuve d'idempotence.

## Perimetre IN

- Inventorier toutes les operations MCP et Core candidates au rejeu.
- Distinguer `operationId`, identifiant de requete MCP et cle d'idempotence
  metier.
- Definir la source d'autorite de la deduplication pour chaque operation.
- Definir la canonicalisation de la requete liee a une cle.
- Definir le conflit produit par une meme cle utilisee pour une requete
  differente.
- Definir les scopes minimaux : operation, projet, principal et delegation.
- Definir le traitement d'un timeout avant effet, apres effet possible et apres
  resultat deja persiste.
- Definir la retention et le nettoyage des cles et resultats dedoublonnes.
- Garantir que les cles et payloads sensibles ne figurent pas dans les logs,
  evenements ou diagnostics.
- Ajouter les tests de concurrence, rejeu, conflit et resultat ambigu.
- Documenter la recuperation manuelle lorsqu'une mutation ne peut pas etre
  rejouee surement.

## Perimetre OUT

- Ajouter un retry illimite ou generique.
- Rejouer automatiquement une mutation sans contrat Core autoritaire.
- Introduire une queue, un broker ou un circuit breaker durable.
- Modifier l'identite technique, humaine ou la delegation.
- Renouveler une delegation humaine.
- Modifier les credentials provider.
- Modifier le runtime de renouvellement du bearer sans contrat avec Story 0212.
- Traiter `operationId` local comme une cle d'idempotence durable.
- Declarer `register_project` idempotent sans decision et contrat Core explicites.

## Invariants

1. Un `operationId` local ne garantit jamais l'idempotence apres redemarrage.
2. Une operation n'est rejouable que si son contrat metier ou une cle de
   deduplication autoritaire le garantit.
3. Une meme cle avec une requete differente produit un conflit deterministe.
4. Une mutation au resultat ambigu n'est pas rejouee par defaut.
5. Une cle est scopee au minimum par operation, projet et principal autorise.
6. La delegation n'est jamais elargie ou reconstruite par un rejeu.
7. Les cles brutes, tokens, payloads et reponses provider sont absents des
   diagnostics et evenements.
8. Les retries restent gouvernes par une autorite unique et un budget borne.
9. L'observabilite reste dedoublonnable sans devenir une mutation metier.
10. Les invariants de generation, ownership et transition terminale de Story
    0172 restent applicables.

## Criteres d'acceptation proposes

### AC-1 - Classification complete

Chaque operation MCP vers Core est classee comme surement rejouable,
rejouable sous garantie explicite ou non rejouable par defaut.

### AC-2 - Separation des identifiants

La documentation et les tests distinguent clairement identifiant de requete,
`operationId` technique et `Idempotency-Key` metier.

### AC-3 - Rejeu autoritaire

Une requete identique avec une cle valide retourne le resultat original ou un
statut de suivi equivalent sans creer un second effet metier.

### AC-4 - Conflit de cle

Une cle deja utilisee pour une requete differente produit un conflit borne,
non-divulgant et deterministe.

### AC-5 - Resultat ambigu

Un timeout ou une perte de connexion apres une mutation potentielle ne
declenche aucun rejeu sans garantie explicite de deduplication.

### AC-6 - Scope et delegation

Une cle ne peut pas etre reutilisee hors du projet, principal, operation ou
contexte de delegation autorise par le contrat.

### AC-7 - Secret safety

Les cles, payloads, tokens et details backend ne figurent ni dans les logs, ni
dans les evenements, ni dans les reponses d'erreur MCP.

### AC-8 - Concurrence

Deux soumissions concurrentes avec la meme cle ne creent qu'un seul effet
metier et retournent des resultats compatibles.

### AC-9 - Tests de contrat

Les tests couvrent les lectures, mutations, `devlog_story_agent`, follow-ups,
observabilite, `429`, timeout avant/apres effet, conflit de cle, concurrence et
reprise manuelle.

## Decisions humaines requises

1. Confirmer la liste des operations officiellement idempotentes.
2. Decider si le serveur MCP genere une cle pour certaines operations ou si la
   cle est toujours fournie par l'appelant.
3. Decider si `register_project` reste non rejouable dans cette version.
4. Confirmer la garantie Core de dedoublonnage pour `sync_project_history`.
5. Definir la retention des cles et resultats dedoublonnes.
6. Definir la reponse d'une requete identique encore `IN_PROGRESS`.
7. Decider si la politique `429` est implementee dans cette Story ou dans une
   Story separee.

Les anciennes soumissions portant une cle sans scope, creees avant la migration
V88, sont traitees comme non rejouables. Elles doivent etre verifiees via le
statut metier puis resoumises avec une nouvelle cle scopee ; elles ne sont pas
automatiquement migrees, car leur contexte d'autorisation historique ne peut
pas etre prouve.

## Workflow et gates

1. Repository Analysis.
2. Implementation Plan.
3. Validation humaine des decisions et autorisation explicite d'implementation.
4. Implementation minimale et tests cibles.
5. Revue independante et verification complete.
6. Acceptance humaine de la Story.

L'implementation de cette Story n'autorise ni commit, ni push, ni merge, ni
acceptance finale. L'acceptance humaine reste une gate separee.

## References

- [Epic - Politique de resilience MCP](../../epics/mcp-error-resilience-policy/epic.md)
- [Story 0211](../0211-mcp-error-retry-policy/story.md)
- [Story 0212](../0212-mcp-runtime-retry-renewal/story.md)
- [Story 0213](../0213-mcp-core-call-policy/story.md)
- [ADR-077](../../decisions/ADR-077.md)
- [ADR-078](../../decisions/ADR-078.md)
- [ADR-081](../../decisions/ADR-081.md)
