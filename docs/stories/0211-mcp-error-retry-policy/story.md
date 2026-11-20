# Story 0211 - Politique normative des erreurs MCP et des retries

## Statut

IMPLEMENTED - AWAITING HUMAN REVIEW.

Cette Story definit et fige une politique. Son implementation a ete autorisee
par le proprietaire humain. Le commit, le push et l'acceptation finale de la
Story restent des gates humaines distinctes.

## Objectif

Definir la politique normative commune pour classifier les erreurs MCP,
determiner les retries autorises, encadrer le rejeu selon l'idempotence,
standardiser les diagnostics et preparer les Stories d'implementation.

Cette Story corrige la collision d'authentification transport/Core identifiee
dans le Repository Analysis. Les phases de retry backend plus larges restent
conditionnees par les contrats d'idempotence a formaliser.

## Artefacts gouvernants

- ADR-077 - MCP Actor Identity and Human Delegation Boundary
- ADR-078 - DevLog Internal Agent and External Agent Client Identity
- ADR-081 - MCP Error Classification, Retry and Idempotency Policy
- Story 0172 - MCP Runtime Observability and Instance Lifecycle
- Story 0174 - MCP Transport Bearer Renewal and Session Reconnection
- `docs/stories/0172-mcp-runtime-observability-and-instance-lifecycle/story-a-mcp-tool-call-observability-design-proposal.md`
- `docs/investigations/devlog-mcp-opencode-chatgpt-oauth-stability.md`
- `TOOLS.md`

## Taxonomie versionnee

La politique doit couvrir au minimum :

```text
CONFIGURATION_INVALID
TRANSPORT_UNAVAILABLE
TRANSPORT_AUTH_EXPIRED
TRANSPORT_AUTH_REJECTED
CORE_AUTHENTICATION_FAILED
CORE_AUTHORIZATION_FAILED
DELEGATION_FAILED
PROVIDER_FAILED
PROTOCOL_INVALID
PROCESS_FAILED
TIMEOUT
RATE_LIMITED
UNKNOWN
```

Chaque code doit avoir une definition, une couche proprietaire, une origine
attendue, une action utilisateur et un statut retryable ou terminal.

## Matrice normative de retry

La matrice doit specifier pour chaque categorie :

- retry autorise ou interdit ;
- nombre maximal de tentatives ;
- backoff et jitter ;
- cooldown ou circuit-breaker ;
- conditions d'idempotence ;
- comportement en cas de reponse ambigue ;
- code terminal expose.

Politique cible a confirmer dans les artefacts de la Story :

| Cas | Politique cible |
|---|---|
| Bearer expire explicitement identifie | Un renouvellement et un retry maximum |
| `401` Core non attribuable au bearer transport | Aucun retry |
| `403` ou delegation revoquee | Fail-closed |
| `400`, `404`, `409` | Aucun retry |
| Timeout ou `5xx` Keycloak | Retry borne avec backoff |
| Client Keycloak invalide | Echec immediat |
| Timeout ou `5xx` Core | Retry uniquement si operation idempotente |
| `429` | Retry conditionnel et borne selon `Retry-After` |
| Erreur protocole | Aucun retry |
| Resultat metier ambigu | Aucun rejeu sans garantie d'idempotence |

## Politique d'idempotence

Les operations MCP doivent etre classees comme :

- surement rejouables ;
- rejouables sous garantie explicite ;
- non rejouables.

Une reservation technique d'`operationId` ne constitue pas une garantie
d'idempotence metier.

Regle par defaut :

```text
resultat ambigu + absence de garantie d'idempotence
-> ne pas rejouer
```

## Politique de concurrence

La Story doit formaliser le comportement lorsque :

- plusieurs operations expirent simultanement ;
- un renouvellement single-flight est deja en cours ;
- une operation est en vol pendant le remplacement du processus ;
- un arret concurrent intervient pendant un retry ;
- une reponse appartient a une ancienne generation ;
- plusieurs transitions terminales sont en concurrence.

Les invariants de Story 0172 restent obligatoires : une seule transition
terminale, aucun evenement terminal duplique, aucun rejeu implicite et aucun
remplacement d'un processus non possede.

## Contrat de diagnostic

Les diagnostics destines a l'agent, au runtime, a `status/doctor` et aux
alertes doivent utiliser des metadonnees bornees :

- code ;
- couche responsable ;
- statut retryable ;
- tentative courante et maximum ;
- `instanceId` ;
- `operationId` ;
- correlation de session ;
- action recommandee.

Ils ne doivent jamais contenir de bearer, secret client, delegation token,
prompt, payload JSON-RPC, reponse provider ou contenu repository sensible.

## Perimetre

- Definir la taxonomie commune.
- Produire la matrice erreur/retry/backoff/idempotence/action.
- Reconciler les comportements de Stories 0172 et 0174.
- Distinguer les `401` transport, Core et delegation.
- Definir les erreurs terminales et transitoires.
- Formaliser les regles de concurrence et de transition terminale.
- Definir le contrat de diagnostic.
- Produire le decoupage des Stories d'implementation.
- Identifier la necessite et le perimetre d'un nouvel ADR.

## Hors perimetre

- Modifier le runtime MCP ou le serveur Java.
- Ajouter des retries dans le code.
- Modifier les contrats d'outils MCP.
- Modifier l'identite technique ou humaine.
- Renouveler une delegation humaine.
- Changer la propriete des credentials provider.
- Introduire une queue durable, un broker ou un circuit-breaker en production.
- Rejouer une operation ambigue.

## Criteres d'acceptation

1. Une taxonomie versionnee des erreurs MCP est definie.
2. Chaque categorie dispose d'une decision retryable ou terminale explicite.
3. Les `401` transport, Core et delegation sont distingues.
4. Les `403`, `400`, `404` et `409` ne sont pas retryes par defaut.
5. Les conditions de retry des timeouts, `5xx` et `429` sont bornees.
6. Les regles d'idempotence metier sont distinctes de l'identite technique des
   operations.
7. Les operations ambigues ne sont pas automatiquement rejouees sans garantie
   explicite.
8. Les regles de concurrence restent compatibles avec Story 0172.
9. La politique de Story 0174 est explicitement reconciliee avec la matrice.
10. Les erreurs provider, Core et MCP ne sont pas aplaties silencieusement en
    `MCP_REAUTH_REQUIRED`.
11. Le contrat de diagnostic exclut secrets et payloads sensibles.
12. Une matrice de scenarios couvre expiration, renouvellement refuse, Core
    `401`, Core `403`, delegation expiree, Keycloak timeout, Core `5xx`, `429`,
    erreur protocole, operation ambigue, appels concurrents et arret pendant
    retry.
13. Aucun code de production ni comportement runtime n'est modifie.
14. Les decisions restant ouvertes sont explicitement soumises a validation
    humaine.

## Scenarios de validation de la politique

Les scenarios sont provider-free et ne necessitent ni Keycloak reel ni
credentials reelles. Ils doivent verifier la decision attendue, pas executer le
retry en production :

- bearer expire ;
- renouvellement refuse ;
- Core `401` ;
- Core `403` ;
- delegation expiree ;
- Keycloak timeout ;
- backend `5xx` ;
- `429` avec et sans `Retry-After` ;
- erreur JSON-RPC/protocole ;
- resultat metier ambigu ;
- operations concurrentes ;
- arret pendant retry ;
- transition terminale concurrente.

## Decisions humaines requises

1. Creer ou non un nouvel ADR dedie a cette politique.
2. Autoriser ou non le retry des `429` dans la premiere implementation.
3. Definir les operations MCP officiellement idempotentes.
4. Decider si le statut `retryable` est visible par l'agent appelant.
5. Definir le budget maximal de retry par operation et par session.
6. Decider si les evenements d'observabilite restent best-effort ou necessitent
   une deduplication renforcee.
7. Decider le traitement d'un `401` Core lorsque l'origine transport ne peut pas
   etre prouvee.

## Stories d'implementation attendues

1. Retry et renouvellement du runtime MCP.
2. Politique des appels MCP vers Core.
3. Idempotence et securite du rejeu.
4. Diagnostics et experience operateur.
5. Validation de resilience.
6. Documentation et runbooks.

## Validation de cette Story

- Revue de coherence avec les Stories 0172 et 0174.
- Verification de la matrice contre le runtime et le serveur MCP actuels.
- Verification de la couverture des scenarios connus.
- Revue de securite des frontieres d'authentification et des diagnostics.
- Revue humaine des regles de retry et d'idempotence.
- `git diff --check`.

## Gouvernance

Cette Story a ete validee pour la planification mais ne constitue pas une
autorisation d'implementation. Le Repository Analysis et l'Implementation Plan
restent des gates distincts et devront etre approuves avant toute modification
du code.

## References

- [Epic - Politique de resilience MCP](../../epics/mcp-error-resilience-policy/epic.md)
- [Story 0172](../0172-mcp-runtime-observability-and-instance-lifecycle/story.md)
- [Story 0174](../0174-mcp-bearer-renewal/story.md)
- [ADR-077](../../decisions/ADR-077.md)
- [ADR-078](../../decisions/ADR-078.md)
- [ADR-081](../../decisions/ADR-081.md)
- [Investigation MCP/OpenCode/ChatGPT](../../investigations/devlog-mcp-opencode-chatgpt-oauth-stability.md)
