# Story 0188 - MCP Activity: Agent Session Lifecycle

## Status

IMPLEMENTED — AWAITING HUMAN ACCEPTANCE

---

## Problème

Stories 0185–0187 ont livré une chaîne d'observabilité fonctionnelle :

- Chaque appel MCP est capturé avec son statut (RESPONDED/FAILED/INTERRUPTED/ACTIVE).
- Les sessions Claude Code sont identifiées via `CLAUDE_CODE_SESSION_ID`.
- La vue Sessions agrège l'activité par session avec compteurs et plage horaire.

Cependant, la vue Sessions ne peut pas répondre à des questions fondamentales :

- **Cette session est-elle encore en cours ?**
- **Cette session s'est-elle terminée normalement ?**
- **Depuis combien de temps aucune activité n'a été observée ?**

Le badge `active` de la Story 0187 (maintenant `N pending` après correction PR #184)
indique uniquement que des opérations MCP n'ont pas de terminal observé. Ce n'est
pas un signal de présence. Une session arrêtée depuis des heures peut afficher
`1 pending` si un événement terminal a été perdu.

L'utilisateur ne peut pas distinguer :
- une session d'agent active en train de travailler
- une session terminée normalement
- une session interrompue (crash, déconnexion)
- une session simplement inactive (agent en pause)

---

## Objectif fonctionnel

Ajouter à la vue Sessions un indicateur de cycle de vie fiable basé sur des
signaux réels plutôt que sur des inférences non fondées.

**Pas de promesse impossible** : certains états ne peuvent être déterminés que
par heuristique. Ces limites doivent être explicites dans l'UI.

---

## Governing Artifacts

- Story 0187 — MCP Activity: Agent Session Activity View (PR #183)
- ADR-006 — AI Proposal and Knowledge Promotion Workflow
- ADR-067 — DevLog Engineering Story Context Agent
- Stabilisation PR #184 — correction sémantique `active` → `N pending`

---

## Sources de signaux de cycle de vie

### Signaux disponibles

| Source | Signal | Fiabilité | Disponibilité |
|--------|--------|-----------|---------------|
| Claude Code hook `Stop` | Fin de session | **Haute** si configuré | Opt-in — hook `Stop` dans `~/.claude/settings.json` |
| Python runtime `STDIN_CLOSED` | Runtime stoppé proprement | **Haute** | Automatique à l'arrêt propre |
| Python runtime `MCP_PARENT_DISAPPEARED` | Processus parent disparu | **Moyenne** | Automatique si détecté |
| `lastObservedAt` + seuil temporel | Inactivité | **Heuristique** | Toujours disponible |
| `activeOperationCount` | Opérations sans terminal | **Faible** pour signifier présence | Toujours disponible |

### Signaux absents

| Provider | Raison |
|----------|--------|
| OpenCode ≤ 1.18.11 | Pas de `CLAUDE_CODE_SESSION_ID`, pas de hook analogue connu |
| Claude Desktop | Variable non vérifiée — supposée si même convention env, non confirmée |
| Pré-V78 | Pas de session ID |

**Le hook `Stop` Claude Code est la seule source fiable de signal de fin explicite.**
Il doit être configuré par l'utilisateur ou émis automatiquement par le runtime à
l'arrêt propre.

---

## Modèle de cycle de vie proposé

```
ACTIVE_TOOL      ← activeOperationCount > 0 AND lastObservedAt récent (< T_active)
IDLE             ← lastObservedAt < (now - T_idle) AND pas d'ENDED
ENDED            ← événement SESSION_LIFECYCLE_ENDED reçu
UNKNOWN          ← défaut — aucun signal suffisant
```

### Règles de transition

| État affiché | Condition | Source |
|-------------|-----------|--------|
| `ENDED` | Événement `SESSION_LIFECYCLE_ENDED` reçu | Signal explicite |
| `ACTIVE_TOOL` | `activeOperationCount > 0` **ET** `lastObservedAt > now - T_active` | Heuristique — outil en cours récent |
| `IDLE` | `lastObservedAt < now - T_idle` **ET** pas d'`ENDED` | Heuristique — inactivité |
| `UNKNOWN` | Aucune des conditions ci-dessus | Défaut honnête |

**T_idle** et **T_active** : constantes configurables. Valeurs proposées : T_idle = 10 min,
T_active = 2 min. À valider en D1.

**Invariant** : un état `ENDED` ne peut pas être écrasé par `ACTIVE_TOOL` ou `IDLE`.
Dès qu'un signal explicite de fin est reçu, l'état est définitif.

### Ce que ces états ne promettent pas

- `ACTIVE_TOOL` ne garantit pas qu'un agent est en train de répondre — seulement
  qu'une opération MCP a démarré et n'a pas encore de terminal observé récemment.
- `IDLE` ne garantit pas que la session est arrêtée — seulement qu'aucune activité
  n'a été observée depuis T_idle.
- `UNKNOWN` est l'état honnête par défaut.

---

## Contrats d'événements

### Nouveau endpoint — POST /api/v1/mcp/session-events

```json
{
  "projectSlug":    "my-project",
  "agentSessionId": "cc7d159d-a138-4b73-b14e-1e7279d013ab",
  "eventType":      "SESSION_LIFECYCLE_ENDED",
  "occurredAt":     "2026-11-16T12:00:00Z",
  "agentProvider":  "claude-code_2-1-325_agent"
}
```

Contraintes :
- `projectSlug` requis — résolu en `projectId` côté Java.
- `agentSessionId` requis — clé de corrélation.
- `eventType` pour V1 : uniquement `SESSION_LIFECYCLE_ENDED`.
- `occurredAt` optionnel — défaut `Instant.now()` si absent ou invalide.
- Idempotent : si `(projectId, agentSessionId, eventType)` déjà présent → `200` sans doublon.
- Auth : même mécanique que `/api/v1/mcp/tool-events` (token bearer MCP actor).

Réponse `200 OK` (corps vide).

### Émission côté Python runtime (arrêt propre)

Le runtime émet automatiquement `SESSION_LIFECYCLE_ENDED` si :
- `agentSessionId` est défini (env var `CLAUDE_CODE_SESSION_ID` présente)
- L'arrêt est propre (`STDIN_CLOSED` — fin normale de session)

Le runtime **n'émet pas** si l'arrêt est dû à un crash, `MCP_PARENT_DISAPPEARED`,
ou `TOKEN_RENEWAL_FAILED` — ces cas restent `UNKNOWN` ou `IDLE` par heuristique.

### Émission via hook Claude Code (opt-in)

L'utilisateur peut configurer dans `~/.claude/settings.json` :

```json
{
  "hooks": {
    "Stop": [
      {
        "matcher": "",
        "hooks": [
          {
            "type": "command",
            "command": "curl -s -X POST http://localhost:18080/api/v1/mcp/session-events -H 'Content-Type: application/json' -H \"Authorization: Bearer $(cat ~/.config/opencode/devlog-mcp-client-secret)\" -d \"{\\\"projectSlug\\\":\\\"MY_PROJECT\\\",\\\"agentSessionId\\\":\\\"$CLAUDE_CODE_SESSION_ID\\\",\\\"eventType\\\":\\\"SESSION_LIFECYCLE_ENDED\\\",\\\"agentProvider\\\":\\\"$AI_AGENT\\\"}\""
          }
        ]
      }
    ]
  }
}
```

Cette configuration est opt-in et documentée. Elle n'est pas injectée automatiquement.

**Note D4** : la disponibilité de `$CLAUDE_CODE_SESSION_ID` dans le contexte du hook
`Stop` doit être vérifiée en conditions réelles avant implémentation.

---

## Architecture proposée

### Extension incrémentale de l'existant

```
Existant :
  mcp_tool_events → McpToolEventRepository → AgentSessionService → GET /agent-sessions

Ajout Story 0188 :
  mcp_session_lifecycle_events (nouvelle table)
    └── McpSessionLifecycleEventRepository
        └── McpSessionLifecycleEventService (ingest)
            └── POST /api/v1/mcp/session-events

  AgentSessionService.listByProject(...)
    └── JOIN avec mcp_session_lifecycle_events (lookup par session)
        └── AgentSessionView enrichi avec lifecycleStatus

  Angular AgentSessionActivitySection
    └── lifecycleStatus → badge ENDED / ACTIVE_TOOL / IDLE / UNKNOWN
```

### Nouvelle table — mcp_session_lifecycle_events (migration V80)

```sql
CREATE TABLE mcp_session_lifecycle_events (
    id               UUID PRIMARY KEY,
    project_id       UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    agent_session_id VARCHAR(128) NOT NULL,
    event_type       VARCHAR(64)  NOT NULL,
    occurred_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    agent_provider   VARCHAR(128),
    CONSTRAINT uk_session_lifecycle_identity
        UNIQUE (project_id, agent_session_id, event_type)
);

CREATE INDEX ix_session_lifecycle_project_session
    ON mcp_session_lifecycle_events (project_id, agent_session_id);
```

La contrainte UNIQUE `(project_id, agent_session_id, event_type)` rend l'ingestion
idempotente. En V1, un seul `SESSION_LIFECYCLE_ENDED` par session par projet.

### Enrichissement de AgentSessionView

```java
public record AgentSessionView(
    String  agentSessionId,
    String  agentProvider,
    Instant firstObservedAt,
    Instant lastObservedAt,
    long    operationCount,
    long    respondedCount,
    long    failedCount,
    long    interruptedCount,
    long    activeOperationCount,
    String  lifecycleStatus          // "ENDED" | "ACTIVE_TOOL" | "IDLE" | "UNKNOWN"
) {}
```

Le calcul de `lifecycleStatus` est fait en Java (service layer), pas en SQL,
pour garder la logique lisible et testable unitairement.

```java
static String computeLifecycleStatus(
        boolean hasEndedEvent,
        long activeOperationCount,
        Instant lastObservedAt,
        Instant now,
        Duration idleThreshold,
        Duration activeToolThreshold) {

    if (hasEndedEvent) return "ENDED";
    if (lastObservedAt == null) return "UNKNOWN";
    Duration sinceLastActivity = Duration.between(lastObservedAt, now);
    if (activeOperationCount > 0
            && sinceLastActivity.compareTo(activeToolThreshold) <= 0) return "ACTIVE_TOOL";
    if (sinceLastActivity.compareTo(idleThreshold) > 0) return "IDLE";
    return "UNKNOWN";
}
```

---

## Expérience Angular

### Badge de cycle de vie

Remplace l'actuel badge `N pending` / timestamp par un badge à quatre états :

| State | Affichage | Tooltip |
|-------|-----------|---------|
| `ENDED` | `ended` | `Session ended — observed terminal signal` |
| `ACTIVE_TOOL` | `N pending` | `N operation(s) without observed terminal event` |
| `IDLE` | `idle` + lastObservedAt HH:mm | `No activity observed since X minutes` |
| `UNKNOWN` | `HH:mm:ss` (lastObservedAt) | `Session lifecycle unknown — last activity at HH:mm:ss` |

**L'état ACTIVE_TOOL garde le badge `N pending`** — identique à l'actuel (PR #184)
mais uniquement quand l'activité est récente (T_active).

### Pas de polling séparé

Le `lifecycleStatus` est calculé côté backend à chaque requête et retourné dans
`AgentSessionView`. Le polling existant (`timer(0, intervalMs)`) suffit à mettre
à jour les états heuristiques IDLE/ACTIVE_TOOL.

---

## Cas limites

| Cas | Comportement |
|-----|-------------|
| `SESSION_LIFECYCLE_ENDED` reçu mais aucune opération connue | Persisté dans la table, ignoré par la vue Sessions (source = `mcp_tool_events`) |
| Doublon `SESSION_LIFECYCLE_ENDED` | Idempotent — `ON CONFLICT DO NOTHING` |
| `occurred_at` antérieur à `firstObservedAt` | Accepté — événement lifecycle indépendant des tool events |
| T_idle écoulé mais ENDED non reçu | Affichage `IDLE` — honnête |
| Session OpenCode (pas d'`agentSessionId`) | Pas d'entrée — aucun impact |
| Hook `Stop` mal configuré | Silencieux — état reste `IDLE` ou `UNKNOWN` |
| Arrêt brutal (crash) sans STDIN_CLOSED | Pas d'émission — état `IDLE` après T_idle |

---

## Critères d'acceptation

**AC1** — `POST /api/v1/mcp/session-events` accepte un événement
`SESSION_LIFECYCLE_ENDED` valide et le persiste.

**AC2** — L'endpoint est idempotent : un doublon retourne `200` sans doublon.

**AC3** — `GET /api/v1/projects/{projectId}/agent-sessions` retourne un champ
`lifecycleStatus` pour chaque session.

**AC4** — Une session avec `SESSION_LIFECYCLE_ENDED` a `lifecycleStatus = "ENDED"`.

**AC5** — Une session dont `lastObservedAt` est antérieur de plus de T_idle sans
événement ENDED a `lifecycleStatus = "IDLE"`.

**AC6** — Une session avec `activeOperationCount > 0` et activité récente (< T_active)
a `lifecycleStatus = "ACTIVE_TOOL"`.

**AC7** — La vue Sessions Angular affiche le badge de cycle de vie approprié.

**AC8** — Le runtime Python émet `SESSION_LIFECYCLE_ENDED` à l'arrêt propre si
`CLAUDE_CODE_SESSION_ID` est défini.

**AC9** — La documentation du hook Claude Code `Stop` est disponible.

**AC10** — Les tests unitaires couvrent les 4 états de `computeLifecycleStatus`.

**AC11** — Les tests Testcontainers couvrent ingestion, idempotence et enrichissement
de la projection (5 scénarios).

---

## Tests

### Backend — `McpSessionLifecycleEventServiceTest` (unitaire)
- `ingestCreatesEvent`
- `ingestIsIdempotentForDuplicate`

### Backend — `AgentSessionLifecyclePostgresIntegrationTest` (Testcontainers)
- `endedEventSetsLifecycleStatusEnded`
- `idleThresholdSetsLifecycleStatusIdle`
- `activeToolWithRecentActivitySetsStatus`
- `missingEndedEventDefaultsToUnknown`
- `lifecycleIsolatedByProject`

### Backend — `AgentSessionServiceTest` (unitaire, extension)
- `computeLifecycleStatusEnded`
- `computeLifecycleStatusIdle`
- `computeLifecycleStatusActiveTool`
- `computeLifecycleStatusUnknown`

### Python runtime — `test_runtime.py` (extension)
- `test_lifecycle_ended_emitted_on_clean_stop_with_session_id`
- `test_lifecycle_ended_not_emitted_without_session_id`
- `test_lifecycle_ended_not_emitted_on_crash`

### Angular — `agent-session-activity-section.spec.ts` (extension)
- `showsEndedBadge`
- `showsActiveToolBadge`
- `showsIdleBadge`
- `showsUnknownAsTimestamp`

---

## Risques

| Risque | Probabilité | Impact | Mitigation |
|--------|-------------|--------|-----------|
| Hook `Stop` non configuré | Élevée | Moyen | `IDLE` honest fallback — UX acceptable sans hook |
| T_idle trop court → faux IDLE | Moyen | Faible | T_idle configurable — défaut 10 min |
| `$CLAUDE_CODE_SESSION_ID` absent dans hook `Stop` | Non vérifié | Élevé si vrai | Vérifier en conditions réelles avant implémentation (D4) |
| Latence du hook `Stop` (curl synchrone) | Moyen | Faible | Curl en arrière-plan avec `&` si nécessaire |
| OpenCode / Claude Desktop sans signal → `UNKNOWN` permanent | Certain | Faible | `UNKNOWN` est l'état honnête |

---

## Décisions humaines nécessaires avant implémentation

**D1 — Valider les seuils T_idle et T_active**
Valeurs proposées : T_idle = 10 min, T_active = 2 min.

**D2 — Nouvelle table vs extension de mcp_tool_events**
Nouvelle table `mcp_session_lifecycle_events` proposée (sémantique différente,
pas d'`operation_id`). Alternative : ajouter `SESSION_LIFECYCLE_ENDED` comme
`event_type` dans `mcp_tool_events` avec `operation_id = NULL`.
La nouvelle table est préférable pour la clarté du schéma.

**D3 — Émission automatique dans le runtime vs opt-in uniquement**
Proposition : émission automatique à l'arrêt propre (`STDIN_CLOSED`) si
`CLAUDE_CODE_SESSION_ID` défini. Alternative : opt-in via hook seulement.

**D4 — Vérifier disponibilité de `$CLAUDE_CODE_SESSION_ID` dans le hook `Stop`**
Non confirmé à ce jour. Bloquant pour le hook opt-in.

**D5 — Périmètre V1 : uniquement `SESSION_LIFECYCLE_ENDED` ?**
Confirmer si le schéma doit anticiper d'autres event types futurs ou rester minimal.

---

## Plan d'implémentation incrémental

### Étape 1 — Backend : ingestion

1. Migration V80 — table `mcp_session_lifecycle_events`
2. `McpSessionLifecycleEvent` entity
3. `McpSessionLifecycleEventRepository` avec `insertIfAbsent` + `hasEndedEvent`
4. `McpSessionLifecycleEventIngestRequest` record (validation)
5. `McpSessionLifecycleEventService.ingest()`
6. `McpSessionLifecycleEventController` → `POST /api/v1/mcp/session-events`
7. Tests unitaires + intégration (ingestion, idempotence)

### Étape 2 — Backend : enrichissement projection

1. Enrichir `AgentSessionService.listByProject()` avec lookup `hasEndedEvent`
2. Implémenter `computeLifecycleStatus()` (statique, testable)
3. Ajouter `lifecycleStatus` à `AgentSessionView`
4. Tests unitaires `computeLifecycleStatus` (4 cas)
5. Tests intégration Testcontainers (5 scénarios)

### Étape 3 — Python runtime

1. Méthode `_emit_session_lifecycle_event()` dans `ToolEventPusher`
2. Appel après `stop_child("STDIN_CLOSED")` si `agentSessionId` présent
3. Tests `test_lifecycle_ended_*` (3 cas)

### Étape 4 — Angular

1. Ajouter `lifecycleStatus` à `AgentSession` model
2. Remplacer logique de badge dans `AgentSessionActivitySection`
3. Tests spec (4 cas)
4. Lint + format check

### Étape 5 — Documentation

1. Section "Session Lifecycle" dans `tools/README.md` ou documentation inline
2. Exemple de configuration hook `Stop`

---

## Hors périmètre

- Agent Presence Window (détection temps réel)
- Notifications push / webhooks sur changement d'état
- Historique des états (état courant uniquement en V1)
- Collecte des prompts ou conversations privées
- Support OpenCode session lifecycle
- Orchestrateur d'agents
- RAG
