# Story 0187 - MCP Activity: Agent Session Activity View

## Status

DESIGN APPROVED — AWAITING IMPLEMENTATION AUTHORIZATION

---

## Problem

Story 0186 enrichit chaque événement MCP avec `agentSessionId` et `agentProvider`.
Le DevLog Companion affiche maintenant un badge de provider sur chaque opération
et un tooltip montrant l'identifiant de session brut.

L'utilisateur ne peut toujours pas répondre à des questions de session :

- Quelles sessions d'agents ont été observées sur ce projet ?
- Quel provider est associé à chaque session ?
- Quand a-t-on observé la première et la dernière activité MCP ?
- Combien d'opérations cette session a-t-elle exécutées ? Combien ont échoué ?
- Quelles sont les opérations d'une session donnée, dans l'ordre chronologique ?
- Quelle proportion de l'activité ne peut pas être attribuée à une session ?

La vue "All Operations" donne une liste de toutes les invocations MCP, mais ne
structure pas l'activité par session. Pour comprendre ce qu'un agent a fait
pendant une session de travail, l'utilisateur doit filtrer visuellement chaque
ligne par UUID — ce qui est impraticable.

---

## Objectif fonctionnel

Ajouter une vue "Sessions" dans le DevLog Companion qui présente l'activité MCP
agrégée par session d'agent observée, avec accès rapide aux opérations de chaque
session.

La vue "All Operations" existante est conservée. La vue Sessions est une seconde
perspective sur les mêmes données.

---

## Governing Artifacts

- Story 0185 — MCP Activity: Operation Lifecycle View (PR #181)
- Story 0186 — MCP Activity: Agent Session Correlation (PR #182)
- ADR-006 — AI Proposal and Knowledge Promotion Workflow
- ADR-067 — DevLog Engineering Story Context Agent

---

## État actuel du repository (post PR #182)

- Table `mcp_tool_events` : colonnes `agent_session_id` et `agent_provider`
  présentes (migration V78), nullable.
- Projection `findOperationsByProject` : expose `agentSessionId` et `agentProvider`
  par opération via `McpOperationView`.
- API `GET /api/v1/projects/{projectId}/mcp-operations` : retourne
  `agentSessionId` et `agentProvider` dans chaque opération.
- Angular : badge provider CC/OC/CD/? sur chaque ligne d'opération.
- Pas de projection ni d'endpoint au niveau session.
- Les événements pré-V78 ont `agent_session_id = NULL`.
- OpenCode 1.18.11 n'expose pas de session ID — ses opérations ont
  `agentSessionId = NULL`.

---

## Sources de données et fiabilité

| Source | Fiabilité | Disponibilité |
|--------|-----------|---------------|
| `CLAUDE_CODE_SESSION_ID` (Claude Code) | Haute — UUID stable par session | Confirmée sur cette machine |
| `AI_AGENT` (Claude Code) | Haute — provider + version | Confirmée |
| `CLAUDE_CODE_SESSION_ID` (Claude Desktop) | Non vérifiée | Supposée si même convention env |
| Session ID (OpenCode 1.18.11) | Absente | Pas d'env var exposée |
| Historique pré-V78 | Absente | NULL pour toutes les colonnes |

**Conséquence :** La vue Sessions ne peut présenter que les sessions Claude Code
(et éventuellement Claude Desktop). Les opérations OpenCode et pré-V78 restent
visibles dans "All Operations" mais sont exclues de la vue Sessions.

---

## Modèle de session observée

```java
public record AgentSessionView(
    String  agentSessionId,       // non-null dans cette projection (filtré WHERE IS NOT NULL)
    String  agentProvider,        // peut être null si provider manquant
    Instant firstObservedAt,      // MIN(occurred_at) sur opérations de la session
    Instant lastObservedAt,       // MAX(occurred_at) sur opérations de la session
    long    operationCount,
    long    respondedCount,
    long    failedCount,
    long    interruptedCount,
    long    activeOperationCount
) {}

public record AgentSessionPageResponse(
    List<AgentSessionView> content,
    long   totalSessions,
    int    page,
    int    size
) {}
```

**Pas de nouvelle table `agent_sessions`.** La projection est purement dérivée
de `mcp_tool_events`.

---

## Règles déterministes de projection

| Situation | Comportement |
|-----------|-------------|
| `agentSessionId` non null | Inclus dans la vue Sessions, agrégé par `agentSessionId` |
| `agentSessionId` null | **Exclu** de la vue Sessions. Visible dans All Operations. |
| Plusieurs `agentProvider` pour même session | `MIN(agent_provider)` — déterministe, en pratique impossible |
| Plusieurs instances pour même session | Regroupées — `agentSessionId` seul critère d'agrégation |
| Événements désordonnés | Sans impact — `MIN`/`MAX` commutatives |
| Opérations sans STARTED | Incluses — `firstObservedAt` = MIN de tout `occurred_at` |
| `activeOperationCount` | Opérations avec statut `ACTIVE` dans la projection |
| `lastObservedAt` | Dernière activité MCP observée — ≠ date de fin de session |
| Données contradictoires (provider différent pour même session) | `MIN(agent_provider)` |

**Règle critique : ne jamais regrouper les opérations avec `agentSessionId = NULL`
dans une session synthétique "anonymous".**

---

## SQL de la projection sessions

À ajouter dans `McpToolEventRepository` :

```sql
-- findSessionsByProject
WITH terminals AS (
    SELECT DISTINCT ON (instance_id, operation_id)
        instance_id, operation_id, status, occurred_at AS completed_at,
        agent_session_id, agent_provider
    FROM mcp_tool_events
    WHERE project_id = :projectId
      AND event_type IN ('MCP_TOOL_RESPONDED', 'MCP_TOOL_FAILED', 'MCP_TOOL_INTERRUPTED')
    ORDER BY instance_id, operation_id, occurred_at DESC, event_type ASC
),
starts AS (
    SELECT instance_id, operation_id, occurred_at AS started_at,
           agent_session_id, agent_provider
    FROM mcp_tool_events
    WHERE project_id = :projectId
      AND event_type = 'MCP_TOOL_STARTED'
),
op_level AS (
    SELECT
        COALESCE(s.agent_session_id, t.agent_session_id) AS agent_session_id,
        COALESCE(s.agent_provider,   t.agent_provider)   AS agent_provider,
        COALESCE(t.status, 'ACTIVE')                     AS status,
        COALESCE(s.started_at,  t.completed_at)          AS time_min,
        COALESCE(t.completed_at, s.started_at)           AS time_max
    FROM starts s
    FULL OUTER JOIN terminals t
        ON s.instance_id = t.instance_id AND s.operation_id = t.operation_id
    WHERE COALESCE(s.agent_session_id, t.agent_session_id) IS NOT NULL
)
SELECT
    agent_session_id,
    MIN(agent_provider)                                AS agent_provider,
    MIN(time_min)                                      AS first_observed_at,
    MAX(time_max)                                      AS last_observed_at,
    COUNT(*)                                           AS operation_count,
    COUNT(*) FILTER (WHERE status = 'RESPONDED')       AS responded_count,
    COUNT(*) FILTER (WHERE status = 'FAILED')          AS failed_count,
    COUNT(*) FILTER (WHERE status = 'INTERRUPTED')     AS interrupted_count,
    COUNT(*) FILTER (WHERE status = 'ACTIVE')          AS active_operation_count
FROM op_level
GROUP BY agent_session_id
ORDER BY MAX(time_max) DESC NULLS LAST
LIMIT :size OFFSET :offset

-- countSessionsByProject
SELECT COUNT(DISTINCT agent_session_id)
FROM mcp_tool_events
WHERE project_id = :projectId
  AND agent_session_id IS NOT NULL
```

---

## Contrats API

### Conventions REST observées dans le backend

- Base : `GET /api/v1/projects/{projectId}/...`
- Auth : `AuthenticatedPrincipalResolver` + `CoreProjectAuthorizationService`
- Réponse paginée : `{content, totalXxx, page, size}`
- Paramètres query : `page` (défaut 0), `size` (défaut 20, max 50)

### GET /api/v1/projects/{projectId}/agent-sessions

Paramètres : `page` (défaut 0), `size` (défaut 20, max 50).

Auth : permission `MCP_TOOL_EVENTS_READ`.

Réponse `200 OK` :
```json
{
  "content": [
    {
      "agentSessionId": "cc7d159d-a138-4b73-b14e-1e7279d013ab",
      "agentProvider": "claude-code_2-1-325_agent",
      "firstObservedAt": "2026-11-14T09:12:00Z",
      "lastObservedAt": "2026-11-14T10:45:33Z",
      "operationCount": 47,
      "respondedCount": 44,
      "failedCount": 2,
      "interruptedCount": 1,
      "activeOperationCount": 0
    }
  ],
  "totalSessions": 3,
  "page": 0,
  "size": 20
}
```

Tri : `lastObservedAt DESC NULLS LAST`.

### GET /api/v1/projects/{projectId}/agent-sessions/{sessionId}/operations

Paramètres : `page` (défaut 0), `size` (défaut 20, max 50).

Réutilise la projection `findOperationsByProject` filtrée par
`agent_session_id = :sessionId`. Retourne un `McpOperationPageResponse` identique
à l'endpoint existant.

Si aucune opération pour ce `sessionId` dans ce `projectId`, retourne `200`
avec `content: []` et `totalOperations: 0`.

`sessionId` est l'UUID de la session. UUID safe pour les path segments.

---

## Expérience Angular

### Architecture

**Nouveaux fichiers :**
- `agent-session.models.ts` : `AgentSession`, `AgentSessionPage`
- `agent-session.service.ts` : `AgentSessionService.byProject(id, page, size)`
- `agent-session-activity-section.ts`
- `agent-session-activity-section.html`
- `agent-session-activity-section.scss`
- `agent-session-activity-section.spec.ts`

**Fichiers modifiés :**
- `project-workspace-section-page.html` : switch sous-vue `[All Operations] | [Sessions]`
- `project-workspace-section-page.ts` : import du nouveau composant

### Sous-vues dans le panneau Activity

```
[ All Operations ] [ Sessions ]
```

Signal `selectedView: 'operations' | 'sessions'` dans le composant parent.
Pas de route dédiée. Le changement de sous-vue ne détruit pas les données déjà
chargées dans la vue inactive.

### Vue Sessions (liste)

Composant `AgentSessionActivitySection` :
- Polling `timer(0, intervalMs)` identique au composant opérations
- Observable réactif + `AsyncPipe`
- `takeUntil(destroyed$)` dans `ngOnDestroy`

Chaque ligne de session :
- Badge provider (CC/OC/CD/?) — même logique `providerBadge()` que Story 0186
- 8 premiers caractères du session ID + tooltip = UUID complet
- Compteur opérations total
- Compteur échecs (rouge si > 0)
- Plage `firstObservedAt` → `lastObservedAt`, ou badge "active" si
  `activeOperationCount > 0`
- Indicateur de navigation vers le détail

### Détail de session

Clic sur une ligne → liste chronologique des opérations de la session.
Réutilise le rendu d'opération existant, appelant l'endpoint
`agent-sessions/{sessionId}/operations` (voir D5).

### État vide

Quand toutes les opérations ont `agentSessionId = NULL` (pré-V78 ou OpenCode pur) :
message "No identified agent sessions — operations are visible in All Operations."

---

## Cas limites

| Cas | Comportement attendu |
|-----|---------------------|
| Toutes les opérations ont `agentSessionId = NULL` | Vue Sessions vide, message explicatif |
| Session avec seulement des opérations ACTIVE | `activeOperationCount > 0`, badge "active" |
| `agentProvider = NULL` | Badge `?`, pas d'erreur |
| UUID contenant des tirets dans l'URL | Pas d'encodage requis |
| Pagination sessions | `totalSessions / size` pages, même logique que opérations |
| Même `agentSessionId` sur plusieurs projets | Isolation par `project_id` — sessions distinctes |
| `lastObservedAt` très ancien | Affiché normalement — pas de purge |
| Sous-session CC (`CLAUDE_CODE_CHILD_SESSION=1`) | Comptabilisée dans la session parente (D1) |
| Données contradictoires provider/session | `MIN(agent_provider)` déterministe |

---

## Critères d'acceptation

**AC1** — `GET /api/v1/projects/{projectId}/agent-sessions` retourne une liste
paginée des sessions identifiées, triée par `lastObservedAt DESC`.

**AC2** — Chaque entrée de session contient : `agentSessionId`, `agentProvider`,
`firstObservedAt`, `lastObservedAt`, `operationCount`, `respondedCount`,
`failedCount`, `interruptedCount`, `activeOperationCount`.

**AC3** — Les opérations avec `agentSessionId = NULL` n'apparaissent pas dans
la liste des sessions.

**AC4** — `GET /api/v1/projects/{projectId}/agent-sessions/{sessionId}/operations`
retourne uniquement les opérations appartenant à cette session dans ce projet.

**AC5** — La vue Sessions Angular affiche les sessions identifiées avec badge
provider, session ID abrégé, compteurs, et plage horaire.

**AC6** — Le détail d'une session affiche la liste chronologique de ses opérations
en réutilisant le rendu existant.

**AC7** — Quand aucune session identifiée n'existe, la vue Sessions affiche un
état vide explicatif.

**AC8** — Les sessions d'un projet A ne sont pas visibles dans le projet B.

**AC9** — La vue "All Operations" existante est conservée et accessible depuis la
même interface.

**AC10** — Les tests Testcontainers couvrent la projection SQL de sessions pour :
agrégation correcte, isolation par projet, exclusion NULL, multi-instances,
`firstObservedAt`/`lastObservedAt`.

**AC11** — Les tests Angular couvrent : affichage de session, état vide, badge
provider, session ID abrégé.

---

## Tests unitaires et Testcontainers

### Backend Testcontainers — `AgentSessionPostgresIntegrationTest`

- `singleSessionYieldsOneRow` — session avec plusieurs opérations → 1 ligne agrégée
- `nullSessionExcluded` — opérations sans session → absent de la liste
- `countersAreAccurate` — mix RESPONDED/FAILED/INTERRUPTED/ACTIVE → compteurs corrects
- `projectIsolationPreventsSessionCrossVisibility`
- `multipleInstancesSameSession` — 2 instanceIds, même sessionId → 1 ligne
- `firstAndLastObservedAtCorrect` — vérifier MIN/MAX sur `occurred_at`
- `sessionOperationsEndpointFiltersCorrectly`

### Backend unitaires — `AgentSessionServiceTest`

- `listByProjectClampsPageSize`
- `listByProjectMapsAllFields`
- `listByProjectFiltersNullSessions`

### Tests Angular — `agent-session-activity-section.spec.ts`

- `showsSessionList` — sessions affichées avec badge et compteurs
- `showsEmptyState` — message quand liste vide
- `providerBadgeCC`, `providerBadgeOC`, `providerBadgeCD`, `providerBadgeUnknown`
- `sessionIdAbbreviated` — 8 premiers chars affichés
- `opensDetailOnClick`

---

## Risques et dépendances

| Risque | Probabilité | Impact | Mitigation |
|--------|-------------|--------|-----------|
| Provider contradictoire pour même sessionId | Très faible | Faible | MIN() déterministe |
| Sub-session CC comptée dans session parente | Moyen | Moyen | D1 — acceptable en V1, documenter |
| Performance SQL à grand volume (CTE imbriquée) | Moyen | Moyen | Index `(project_id, agent_session_id)` si nécessaire (D4) |
| OpenCode sans session ID → vue Sessions vide | Certain (actuel) | Faible | État vide explicatif |
| Claude Desktop non vérifié | Certain (non testé) | Faible | Test en conditions réelles si accessible |

---

## Plan d'implémentation incrémental

### Étape 1 — Backend

1. Ajouter `findSessionsByProject` et `countSessionsByProject` dans
   `McpToolEventRepository` avec `AgentSessionProjection` interface
2. Créer `AgentSessionView` et `AgentSessionPageResponse` records
3. Créer `AgentSessionService` (clamping page/size, mapping projection → view)
4. Créer `AgentSessionController` avec :
   - `GET /api/v1/projects/{projectId}/agent-sessions`
   - `GET /api/v1/projects/{projectId}/agent-sessions/{sessionId}/operations`
5. Tests Testcontainers `AgentSessionPostgresIntegrationTest` (7 tests)
6. Tests unitaires `AgentSessionServiceTest` (3 tests)

### Étape 2 — Angular

1. `agent-session.models.ts`
2. `agent-session.service.ts`
3. `agent-session-activity-section.ts/.html/.scss`
4. Intégration dans `project-workspace-section-page`
5. Tests spec `agent-session-activity-section.spec.ts`
6. Lint + format check

### Étape 3 — Vérification

```bash
./backend/mvnw -pl backend -am clean verify -B
cd frontend && npx ng test --watch=false --no-progress
cd frontend && npm run lint && npm run format:check
```

---

## Décisions — VALIDÉES

**D1** ✓ — Sous-sessions CC groupées avec la session parente en V1. Différenciation
déférée à une Story future.

**D2** ✓ — Détail de session : expansion in-place dans le panneau Activity.
Pas de route dédiée.

**D3** ✓ — Les opérations `agentSessionId = NULL` ne sont pas affichées comme
groupe "Unidentified" dans la vue Sessions. Elles restent visibles dans All Operations.

**D4** ✓ — Index `(project_id, agent_session_id)` ajouté via migration V79.

**D5** ✓ — Endpoint dédié `agent-sessions/{sessionId}/operations` pour le détail
de session.

---

## Hors périmètre

- Agent Presence Window
- Avatar 3D
- Chat avec les agents
- Notifications proactives
- Orchestrateur d'agents
- Nouveau runtime agentique
- Historique complet des conversations
- Collecte des prompts privés
- RAG
- Nouvelle infrastructure de messaging
- Système de surveillance permanente du poste
