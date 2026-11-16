# Architecture Decision Proposal — Actionable Activity Alerts (V1)

**Date:** 2026-11-16
**Contexte:** Story 0189 — MCP Activity: Actionable Activity Alerts
**Décision requise :** Projection déterministe vs entité persistée

---

## 1. Contexte

Les Stories 0185–0188 ont livré une chaîne d'observabilité complète : ingestion d'événements MCP, reconstruction d'opérations, corrélation de sessions, cycle de vie. La vue Activity est passif : l'utilisateur doit consulter pour découvrir un échec.

L'objectif de Story 0189 est de détecter les événements significatifs (échecs MCP) et de les présenter comme alertes exploitables dans le Workspace.

---

## 2. Options analysées

### Option A — Projection déterministe (recommandée)

Une alerte est calculée à la demande à partir des événements `mcp_tool_events` existants.

**Principe :** `GET /api/v1/projects/{projectId}/activity-alerts` exécute une requête SQL qui filtre les événements terminaux non-nominaux, les trie et les pagine.

**Requête cible :**

```sql
WITH terminal_alerts AS (
    SELECT DISTINCT ON (instance_id, operation_id)
        instance_id,
        operation_id,
        tool_name,
        status,
        reason_code,
        occurred_at,
        duration_ms,
        agent_session_id,
        agent_provider
    FROM mcp_tool_events
    WHERE project_id = :projectId
      AND event_type IN ('MCP_TOOL_FAILED', 'MCP_TOOL_INTERRUPTED')
      AND (reason_code IS NULL OR reason_code != 'MCP_INSTANCE_STOPPING')
    ORDER BY instance_id, operation_id, occurred_at DESC
)
SELECT *
FROM terminal_alerts
ORDER BY occurred_at DESC NULLS LAST
LIMIT :size OFFSET :offset
```

**alertId stable :** `instanceId + "/" + operationId` — unique par opération, stable à travers les pages.

**Avantages :**
- Pas de nouvelle table, pas de migration
- Reconstruit à tout moment depuis les événements source
- Isolé par projet (scope existant)
- Déduplication naturelle : une opération = un terminal = une alerte
- Compatible avec le polling Angular existant
- Implémentation minimale (1 service + 1 repository method + 1 controller + 1 view)

**Limitations :**
- Pas d'état de lecture utilisateur (lu / non lu) — acceptable en V1
- Pas d'historique de consultation — acceptable en V1
- Requête recalculée à chaque poll — coût raisonnable avec LIMIT/OFFSET

### Option B — Entité persistée

Une entité `ActivityAlert` est créée lors de la détection d'un événement significatif.

**Avantages :**
- État de lecture possible (`readAt`, `dismissedAt`)
- Historique de consultation
- Accusé de réception explicite
- Suivi d'incident possible

**Limitations :**
- Nouvelle table PostgreSQL + migration
- Nouveau cycle de vie (création, lecture, dismiss)
- Risque de duplication avec `mcp_tool_events` (source de vérité dupliquée)
- Complexité du déclencheur : polling ? event hook ? endpoint séparé ?
- Pas de besoin utilisateur concret identifié pour l'état de lecture en V1

---

## 3. Comparatif

| Critère | Option A (projection) | Option B (entité) |
|---------|----------------------|-------------------|
| Migration requise | Non | Oui |
| Duplication de données | Non | Risque |
| Reconstructible | Oui | Avec synchronisation |
| État de lecture | Non | Oui |
| Complexité implémentation | Faible | Élevée |
| Besoin utilisateur V1 | ✓ Satisfait | Satisfait + non-demandé |
| Évolvabilité vers entité | Possible (additive) | — |

---

## 4. Recommandation

**Option A — Projection déterministe pour V1.**

**Motivation :** Le besoin utilisateur V1 est "détecter et contextualiser un échec MCP significatif dans le Workspace". Une projection répond exactement à ce besoin sans state management supplémentaire. L'état de lecture (lu / non lu) est un besoin future non encore validé par un usage réel.

**Migration vers Option B si nécessaire :** L'ajout d'une entité `ActivityAlert` reste possible de manière additive si l'usage réel démontre le besoin d'un état persisté. La transition ne casse pas les contrats existants.

---

## 5. Règles de classification des alertes

Les règles suivantes sont déterministes (pas d'IA) :

| Événement | Condition | Traitement |
|-----------|-----------|-----------|
| `MCP_TOOL_RESPONDED` | — | Pas d'alerte |
| `MCP_TOOL_FAILED` | Toujours | ✓ Alerte — severity ERROR |
| `MCP_TOOL_INTERRUPTED` | reasonCode = `MCP_INSTANCE_STOPPING` | Pas d'alerte (arrêt attendu) |
| `MCP_TOOL_INTERRUPTED` | reasonCode = `MCP_REAUTH_REQUIRED` | ✓ Alerte — severity WARN |
| `MCP_TOOL_INTERRUPTED` | reasonCode = `MCP_PROCESS_FAILED` | ✓ Alerte — severity ERROR |
| `MCP_TOOL_INTERRUPTED` | reasonCode = null | ✓ Alerte — severity WARN |
| `ACTIVE` (pas de terminal) | — | Pas d'alerte immédiate V1 |
| Session lifecycle | — | Pas d'alerte V1 |

**Résumé SQL :** `event_type IN ('MCP_TOOL_FAILED', 'MCP_TOOL_INTERRUPTED') AND (reason_code IS NULL OR reason_code != 'MCP_INSTANCE_STOPPING')`

---

## 6. Stratégie anti-bruit

**Déduplication naturelle :** `DISTINCT ON (instance_id, operation_id)` — une opération ne génère qu'une alerte.

**Pas de regroupement en V1 :** Chaque échec est une alerte indépendante. Le regroupement par tool ou par session est une évolution future.

**Tri déterministe :** `ORDER BY occurred_at DESC NULLS LAST, instance_id ASC, operation_id ASC` — ordre stable à travers les pages.

**Volume :** Limité à 20 par défaut, 50 maximum (cohérent avec les autres endpoints). Pas de TTL en V1 (tous les échecs historiques sont consultables).

**Pas de scoring dynamique :** Les sévérités ERROR / WARN sont déterministes selon le status et le reasonCode.

---

## 7. Contrat REST proposé

### Endpoint

```
GET /api/v1/projects/{projectId}/activity-alerts
  ?page=0&size=20
```

**Auth :** permission `MCP_TOOL_EVENTS_READ` (réutilisation exacte de l'existant)

### Réponse

```json
{
  "content": [
    {
      "alertId": "a1b2c3d4e5f6a7b8/0011223344556677",
      "severity": "ERROR",
      "eventType": "MCP_TOOL_FAILED",
      "toolName": "devlog_get_story_context",
      "reasonCode": null,
      "durationMs": 4521,
      "occurredAt": "2026-11-16T11:42:03Z",
      "instanceId": "a1b2c3d4e5f6a7b8",
      "operationId": "0011223344556677",
      "agentSessionId": "cc7d159d-a138-4b73-b14e-1e7279d013ab",
      "agentProvider": "claude-code_2-1-325_agent"
    }
  ],
  "totalAlerts": 3,
  "page": 0,
  "size": 20
}
```

**Champs :**

| Champ | Type | Description |
|-------|------|-------------|
| `alertId` | string | `instanceId + "/" + operationId` — identité stable |
| `severity` | `ERROR` / `WARN` | ERROR pour FAILED et MCP_PROCESS_FAILED, WARN pour INTERRUPTED |
| `eventType` | string | `MCP_TOOL_FAILED` ou `MCP_TOOL_INTERRUPTED` |
| `toolName` | string / null | Nom de l'outil (null si MCP_TOOL_STARTED non reçu) |
| `reasonCode` | string / null | Code de raison tel que persisté |
| `durationMs` | integer / null | Durée de l'opération |
| `occurredAt` | ISO8601 | Horodatage du terminal |
| `instanceId` | string | Instance MCP source |
| `operationId` | string | Lien logique vers l'opération |
| `agentSessionId` | string / null | Session agent associée (nullable) |
| `agentProvider` | string / null | Provider agent (nullable) |

**Pas de payload privé, pas de prompt, pas de secret.**

### Record Java proposé

```java
public record ActivityAlertView(
    String  alertId,
    String  severity,
    String  eventType,
    String  toolName,
    String  reasonCode,
    Integer durationMs,
    Instant occurredAt,
    String  instanceId,
    String  operationId,
    String  agentSessionId,
    String  agentProvider
) {}
```

---

## 8. Intégration Angular

### Composant : ActivityAlertSection

Suit le pattern des composants existants (`AgentSessionActivitySection`, `McpOperationActivitySection`) :
- `@Input() projectId: string`
- `sessions$: Observable<AlertListState>` via `timer(0, intervalMs).pipe(switchMap(...))`
- `AsyncPipe` + états `loading` / `loaded` / `error` / vide
- Pas de nouvelle bibliothèque

### Service : ActivityAlertService

```typescript
@Injectable({ providedIn: 'root' })
export class ActivityAlertService {
  byProject(projectId: string, page = 0, size = 20): Observable<ActivityAlertPage> {
    return this.http.get<ActivityAlertPage>(
      `${this.base}/projects/${encodeURIComponent(projectId)}/activity-alerts?page=${page}&size=${size}`
    );
  }
}
```

### Affichage par alerte

Chaque alerte affiche :
- Badge sévérité (`ERROR` / `WARN`)
- Nom de l'outil (`toolName ?? 'unknown tool'`)
- Code de raison (`reasonCode ?? 'operation failed'`)
- Durée si disponible (`durationMs`)
- Horodatage (`occurredAt | date: 'HH:mm:ss'`)
- Lien contextuel vers la session ou l'opération source

### Placement dans le Workspace

Section **Activity Alerts** dans la vue Activity (`routerLink="activity"`), au-dessus ou à côté de Agent Sessions.

---

## 9. Décisions humaines nécessaires

**D1 — Validation de la recommandation Option A vs Option B**
Préférence initiale : Option A (projection). À valider avant implémentation.

**D2 — Périmètre temporel des alertes en V1**
Option 1 : Toutes les alertes historiques (paginées).
Option 2 : Alertes des dernières N heures (fenêtre glissante).
Recommandation : Option 1 — plus simple, l'utilisateur peut paginer.

**D3 — Regroupement des alertes répétitives**
Option 1 : Alertes individuelles (une par opération).
Option 2 : Regroupement par `toolName + reasonCode` sur une fenêtre temporelle.
Recommandation : Option 1 pour V1 — déduplication est naturelle, regroupement est V2.

**D4 — Placement dans le Workspace**
Option 1 : Nouvelle section "Activity Alerts" dans la vue Activity.
Option 2 : Intégrée dans la vue Agent Sessions (alerte par session).
Recommandation : Option 1 — section indépendante, plus visible.
