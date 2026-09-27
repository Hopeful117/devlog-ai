# Story 0152 — Exposer un Story Agent Context read-only versionné

## Statut

À revoir — document de Story uniquement, aucune implémentation incluse.

## Objectif

Exposer au Story Context Agent un contexte d’Engineering Story **read-only**,
Core-owned et explicitement versionné. Le consommateur doit recevoir une
projection déterministe du contexte canonique, avec son périmètre, sa fraîcheur,
ses références typées et ses identités vérifiables, sans pouvoir modifier les
données DevLog ni déclencher une seconde recherche ou sélection.

## Contexte

ADR-069 impose une construction canonique unique par requête conceptuelle : le
Core compose et autorise le contexte, puis chaque consommateur reçoit une
projection nommée et versionnée. Le Story Context Agent ne doit donc pas traiter
`EngineeringContext` ou `SelectedKnowledge` comme une nouvelle source
d’autorité, ni reconstruire un `AnalysisContext` ou appeler
`KnowledgeSelectionService` pour la même requête.

La projection doit rester compatible avec la migration existante et avec le
contrat Python `extra="forbid"`. Elle distingue `contextDigest` (contexte
canonique), `projectionDigest` (payload effectivement exposé) et, uniquement
pour compatibilité, `selectionDigest` (ancienne sélection). ADR-068 impose des
références AI typées, une séparation des namespaces de grounding et l’autorité
du Core sur la provenance et la validité des références.

## Périmètre

- Définir le contrat public/interne nommé `StoryContextAgentProjection v1`.
- Exposer ce contrat par le chemin applicatif Story Context Agent déjà existant,
  sans créer un moteur de retrieval, une base ou une source d’autorité parallèle.
- Produire la projection depuis **la même instance** de contexte canonique que
  celle utilisée pour la tâche SCA.
- Rendre explicites : scope projet/story/intent/files, révisions et fraîcheur,
  contexte autorisé, relations, preuves, provenance, trust, grounding typé,
  budgets, réduction, avertissements et versions de politique.
- Propager séparément `contextDigest`, `projectionDigest` et le cas échéant
  `selectionDigest` dans l’enveloppe de tâche/snapshot/prompt, avec validation
  avant soumission et au callback.
- Conserver temporairement `selectedKnowledge` comme enveloppe de compatibilité
  seulement si nécessaire ; son nom ne doit pas masquer le nouveau contrat.
- Documenter et tester le caractère non-broadening : une projection peut réduire,
  ordonner ou sérialiser, mais ne peut ajouter ou réautoriser une référence.

### Contrat wire normatif (v1)

L'exemple ci-dessous est normatif (les valeurs sont illustratives). `request`
est l'entrée canonique et `requestEcho` doit être une copie JSON exactement
égale, après la canonicalisation ADR-069 : aucune valeur, clé, liste ou
`storyId` ne peut être ajoutée, supprimée ou normalisée différemment dans
l'echo. `storyId` est toujours présent, y compris lorsqu'il vaut `null`.
Les références ont toutes la forme ADR-068 `type`/`ref`/`scope` (aucune forme
abrégée ou chaîne brute n'est valide).

```json
{
  "contractVersion": "story-context-agent-projection/v1",
  "projectionVersion": "sca/v1",
  "contextDigest": "<sha256 lowercase>",
  "projectionDigest": "<sha256 lowercase>",
  "request": {"projectSlug": "<slug>", "storyId": null, "intent": "<intent>", "files": []},
  "requestEcho": {"projectSlug": "<slug>", "storyId": null, "intent": "<intent>", "files": []},
  "scope": {
    "projectSlug": "<slug>",
    "storyId": null,
    "intent": "<intent>",
    "files": []
  },
  "freshness": {"sourceRevision": {"kind": "PROJECT_REVISION", "project": "<slug>", "revision": "<revision>"}, "state": "<state>"},
  "context": {"project": {}, "sections": [], "repositoryEvidence": [], "relations": []},
  "groundingCandidates": {"repositoryEvidence": [
    {"reference": {"type": "REPOSITORY_EVIDENCE", "ref": "<id>", "scope": "PROJECT_REVISION"}, "source": {}, "trust": "<tier>"}
  ]},
  "accounting": {"candidateCount": 0, "selectedCount": 0, "discardedCount": 0, "usedTokens": 0, "budget": 0, "truncated": false, "warnings": []},
  "policy": {"compositionVersion": "<version>", "projectionVersion": "sca/v1"},
  "compatibility": {
    "selectionDigest": "<optional legacy sha256>",
    "selectedKnowledge": {"contractVersion": "selected-knowledge-compat/v1", "contextDigest": "<same root contextDigest>", "projectionDigest": "<same root projectionDigest>", "value": {}}
  }
}
```

`compatibility` est absent si aucune compatibilité legacy n'est produite ; il
est alors impossible d'émettre `selectedKnowledge` seul. Dans cet objet,
`selectionDigest` est le seul digest de sélection et `selectedKnowledge` est
une enveloppe unique, dérivée du même snapshot, jamais une seconde forme de
projection. `contextDigest` et `projectionDigest` à la racine sont les seules
identités canoniques de la projection ; un digest dans `selectedKnowledge` ne
peut ni les remplacer ni les compléter. Si `compatibility` est présent,
`selectionDigest` et `selectedKnowledge` sont présents ensemble et le digest
correspond exactement à `value`; sinon l'objet est refusé. Les noms exacts des
DTO restent une décision d'implémentation, mais cette forme wire, les listes
déterministes et la sérialisation ADR-069 sont obligatoires.

#### Scope canonique des références

Pour `StoryContextAgentProjection v1`, toute référence de grounding exposée
par Core utilise exclusivement `scope=PROJECT_REVISION`. La révision est
`freshness.sourceRevision={kind: PROJECT_REVISION, project, revision}`,
`project` doit égaler `scope.projectSlug`, et `revision` doit être résoluble
dans le snapshot immuable. Core vérifie aussi l'ownership du projet, la
correspondance de la référence avec cette révision et le mapping de la
référence à la tâche/scope demandés. `PROJECT`, `STORY`, `TASK` ou tout autre
scope est refusé fail-closed en v1 ; leur introduction exige une décision ADR
et une nouvelle version de contrat. Les fixtures et critères ci-dessous
doivent donc tester un scope `PROJECT_REVISION` valide et chacun de ces cas
hors scope.

Les scalaires optionnels suivent une règle explicite : `storyId` est obligatoire
et nullable (présent avec `null` si absent de la requête) ; `selectionDigest`
et `selectedKnowledge` sont omissibles ensemble via `compatibility` ; les
autres scalaires optionnels sont omis lorsqu'ils ne sont pas fournis et ne sont
jamais envoyés à `null` (sauf si une version ultérieure les déclare
explicitement nullable). Une valeur `null` et une omission sont donc des
vecteurs distincts et doivent rester distincts dans les digests.

## Critères d’acceptation mesurables

1. Une requête SCA donnée (project, story, intent, files et snapshot identiques)
   produit exactement un `contextDigest` et une projection versionnée ; deux
   exécutions déterministes du même snapshot produisent le même
   `projectionDigest`.
2. Le payload contient `contractVersion`, `projectionVersion`, scope complet,
   fraîcheur/révision source, policy version, accounting, warnings et les deux
   digests ; le `selectionDigest` n'existe qu'en
   `compatibility.selectionDigest`, avec `compatibility.selectedKnowledge`,
   lorsqu'une sélection legacy a effectivement été produite ; sinon
   `compatibility` est absent.
3. `contextDigest` n’est jamais égalé ou remplacé par `selectionDigest`, et
   `projectionDigest` change lorsqu’une valeur, un ordre, une réduction ou un
   budget de projection change.
4. Un test de frontière vérifie que le chemin SCA appelle la construction
   canonique une fois et n’appelle ni `KnowledgeSelectionService` ni une
   retrieval/sélection concurrente pour cette requête.
5. Toute référence exposée est typée ADR-068 (`type`, `ref`, `scope`), avec
   `scope=PROJECT_REVISION`, révision résoluble, ownership projet et mapping
   tâche/scope vérifiés ; une référence inconnue, d’un autre namespace ou hors
   scope est refusée fail-closed.
6. La comparaison des références autorisées démontre
   `authorizedReferences(projection) ⊆ authorizedReferences(canonicalContext)` ;
   la projection ne peut ni réintroduire un élément écarté ni créer une
   autorisation depuis un résumé, une relation ou une section visible.
7. Les budgets et réductions sont bornés, observables et déterministes : le
   payload expose `selectedCount`, `discardedCount`, `usedTokens`, budget,
   `truncated` et warnings ; aucune suppression silencieuse n’est acceptée.
8. Le snapshot immuable et le prompt portent les mêmes scope, fraîcheur,
   `contextDigest` et `projectionDigest` avant soumission ; un callback dont
   l’identité, la version ou le grounding divergent est rejeté sans mutation du
   contexte enregistré.
9. Le contrat Python versionné est accepté par `extra="forbid"`, et les fixtures
   couvrent au minimum collection vide, guidance vide, story d’un autre projet,
   unicode, `storyId=null`, scalaires optionnels omis, null/omission distincts,
   ordre des listes, budget/troncature, références `PROJECT_REVISION` valides et
   scopes `PROJECT`/`STORY`/`TASK` invalides.
10. REST/MCP restent des adaptateurs read-only : aucun endpoint de la Story ne
    crée ou modifie de connaissance, de snapshot canonique ou de grounding ;
    les doublons/retries de tâche restent idempotents.

## Exigences de tests et contrôles de livraison

- Tests unitaires de builder/projection : ordre, contrat, digests, scope,
  fraîcheur, trust, provenance, relations, allow-list et accounting.
- Tests du use case : construction unique, absence de sélection concurrente,
  snapshot/prompt identiques, compatibilité `selectedKnowledge` explicite.
- Tests wire Java/Python et REST/MCP : désérialisation v1, `extra="forbid"`,
  champs obligatoires et erreurs de contrat.
- Tests de callback : digest/version/scope divergents, références invalides,
  retry, doublon terminal et absence de mutation du snapshot.
- Tests de non-régression des chemins existants des Stories 0145, 0146 et 0148 ;
  les contrats legacy ne changent pas implicitement.
- Fournir des vecteurs de digest pour listes/maps vides, null/omission, unicode,
  ordre et budgets, puis exécuter les tests ciblés et la compilation des modules
  concernés avant acceptation.


## Revisions normatives du contrat et controles Reviewer

ADR-068 est obligatoire: chaque reference expose exactement type, ref, scope; groundingCandidates.repositoryEvidence[] exige type=REPOSITORY_EVIDENCE et scope=PROJECT_REVISION, provenance/trust et allow-list Core. Toute reference inconnue, autre namespace ou hors scope est refusee fail-closed. Validation: authorizedReferences(projection) subset of authorizedReferences(canonicalContext), avec ownership projet, revision resolvable et mapping task/scope verifies. freshness.sourceRevision est obligatoire et vaut {kind: PROJECT_REVISION, project, revision}; project=scope.projectSlug et revision est resolvable. freshness.state obligatoire: FRESH|STALE|UNKNOWN|NOT_ESTABLISHED.

Canonicalisation ADR-069: UTF-8, JSON sans espaces, cles triees, nombres normalises, Unicode NFC, chemins slash sans point/double-point, listes deterministes; digests SHA-256 lowercase. scope/request sont obligatoires et request est un echo exact; collections vides = []/{}, jamais null. contextDigest et projectionDigest obligatoires; si presente, compatibility.selectionDigest contient le digest SHA-256 legacy de la selection. accounting counts, usedTokens, budget sont entiers >=0, usedTokens<=budget, truncated obligatoire. warnings est une liste obligatoire, vide autorisee, codes structures tries; guidance est optionnel absent si non fourni, jamais null. extra=forbid. Deserialisation ET validation et fixtures couvrent null/omission, vide, Unicode, ordre, guidance, revision, budget/troncature et references invalides.

Compatibilite legacy: `compatibility.selectionDigest` et `compatibility.selectedKnowledge` sont une enveloppe unique derivee du meme snapshot/contexte, jamais autorite; l'objet `compatibility` est absent pour consommateur v1-only. Il n'existe aucun `selectionDigest` racine. Inventaire: endpoints REST/MCP legacy, worker/queue, callback, deserialiseurs Python/JVM, fixtures, lecteurs snapshot, metriques/alertes, chacun avec proprietaire/version/etat.

La fenetre de bascule/retrait legacy est **TBD / non définie** et reste
soumise à confirmation explicite du propriétaire produit. L'implémentation de
la projection read-only et de ses contrôles peut être autorisée séparément,
sans constituer une autorisation de bascule ou de retrait legacy. Tant que la
confirmation produit n'est pas acquise, aucune date de bascule ni autorisation
de retrait n'existe. Sous cette condition seulement, la bascule serait
possible après 14 jours avec 100% v1-only, zéro erreurs de validation,
divergence digest ou grounding adapter, et
`legacy_reads/selected_knowledge_emitted` nuls pendant 7 jours. Le rollback
serait alors conditionné à la même confirmation: dual-read sans réécriture du
snapshot, erreur explicite après retrait, jamais de fallback de sélection.
Metriques: projection_requests_total{version}, legacy_reads_total,
selected_knowledge_emitted_total, contract_validation_failures_total{field},
callback_rejections_total{reason}, digest_mismatch_total,
duplicate_terminal_total. Regle selectedKnowledge: projection seulement,
selectionDigest verifie, aucun ajout/remplacement de digest.

Endpoints REST: GET /api/stories/{storyId}/context/agent, POST /api/stories/{storyId}/context/agent/tasks, POST /api/stories/{storyId}/context/agent/callback, GET /api/stories/{storyId}/context/agent/snapshots/{snapshotId}. Outils MCP: story_context_agent_get_projection, story_context_agent_submit_task, story_context_agent_callback, story_context_agent_get_snapshot. REST/MCP doivent appeler le meme StoryContextAgentProjectionService et service snapshot/callback; tests assertent meme projection, snapshot unique, scope, freshness et digests. Retry idempotent par (snapshotId,taskId,attemptId); doublon terminal 200 sans seconde ecriture; payload different 409 sans mutation.

Tests negatifs obligatoires: callback null->valeur, digest invente puis remplace, mutation post-submit du snapshot/contexte, version/scope/grounding divergents. Invariants 0149: fenetre (base,target], bornes/parentage invalides fail-closed, CHANGES/proofs tries et bornes, aucune reinjection apres compaction. Invariants 0150: briefing descriptif non causal, REPOSITORY_EVIDENCE/PROJECT_REVISION, snapshot execution, NOT_ESTABLISHED sans grounding, service REST/MCP partage sans KnowledgeSelectionService. Fixtures nommees: empty-collections, empty-guidance, guidance-absent, foreign-project, unicode-nfc, null-vs-omitted, story-id-null, optional-scalars-omitted, ordered-lists, budget-truncated, project-revision-valid-invalid, adr068-reference-valid-invalid, legacy-selected-knowledge et vecteurs digest.

## Hors périmètre

- Nouvelle collecte, retrieval, RAG, embeddings, vector store, mémoire ou
  orchestration multi-agent.
- Réécriture de `RepositoryContextEngine`, des collectors, des règles de trust
  ou de la composition canonique ADR-069.
- Fusion de tous les workflows `AnalysisContext`, suppression immédiate de
  `SelectedKnowledge` ou dépréciation implicite d’un endpoint legacy.
- Modification de la sémantique des budgets métier, des proposals, de la
  validation humaine ou de l’output non-trusted de l’IA.
- UI, migration de données non indispensable au snapshot, ou nouveau système de
  replay.

## Dépendances et références

- **ADR-069 — Canonical Engineering Context Boundary** : autorité pour la
  construction unique, les projections, les trois digests, la fraîcheur,
  l’immutabilité et les règles non-broadening.
- **ADR-068 — Typed AI-Facing References and Grounding Namespace Isolation** :
  autorité pour namespaces, références typées, provenance et allow-list.
- Story 0145 — *Canonicaliser la capacité EngineeringContext du Story Context
  Agent* : frontière Core et projection canonique introduites.
- Story 0146 — *Finaliser la migration canonique du Story Context Agent* :
  suppression des dépendances mortes et validation de la projection SCA.
- Story 0148 — *Mettre le Story Context Agent en état de preuve produit V1* :
  workflow REST/MCP, tâche, callback, snapshot immuable et lecture résultat.
- Story 0149 — *Deterministic changes projection* et Story 0150 —
  *Evidence-grounded Story Change Briefing* : projections read-only et
  références/digests réutilisables.

## Risques et mitigations

- **Rupture Python (`extra="forbid"`)** : versionner l’enveloppe, fournir des
  fixtures wire et maintenir une fenêtre de compatibilité explicite.
- **Confusion des digests** : champs séparés, test vectorisé et refus fail-closed
  de toute identité complétée ou réassignée après soumission.
- **Élargissement du grounding par la projection** : allow-list Core, références
  typées et test d’inclusion strict.
- **Dérive de fraîcheur ou de budget** : geler ces métadonnées dans le snapshot,
  les inclure dans les digests selon ADR-069 et exposer l’accounting.
- **Régression des consommateurs legacy** : migration additive, lecture des
  anciens snapshots et compatibility window datée avant tout retrait.
- **Payload trop volumineux** : réduction déterministe bornée, diagnostics de
  troncature et échec explicite si le minimum contractuel ne tient pas.

La Story est prête pour revue ; toute décision qui introduirait une nouvelle
autorité, un nouveau retrieval ou une modification d’ADR-069 doit être traitée
comme un follow-up séparé.
