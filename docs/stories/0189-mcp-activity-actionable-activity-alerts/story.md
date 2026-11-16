# Story 0189 — MCP Activity: Actionable Activity Alerts

## Status

DESIGN — AWAITING HUMAN APPROVAL

---

## Problème

Les Stories 0185–0188 ont livré une chaîne d'observabilité fonctionnelle : capture des appels MCP, reconstruction des opérations, corrélation des sessions, cycle de vie. L'interface Activity permet de voir ce qui s'est passé.

Cependant, la vue reste **passive** : l'utilisateur doit naviguer vers l'interface pour découvrir qu'une opération a échoué. Un échec MCP peut passer inaperçu pendant des heures si l'utilisateur ne consulte pas.

**Besoin fonctionnel :**

> Quand une opération MCP échoue de manière significative, DevLog doit pouvoir le détecter et le signaler dans le Workspace.

L'utilisateur doit pouvoir répondre à trois questions :
1. **Que s'est-il passé ?** (outil, statut, raison)
2. **Quel projet ou agent est concerné ?** (projet, session)
3. **Dois-je faire quelque chose ?** (sévérité, contexte)

---

## Objectif fonctionnel

Ajouter une section **Activity Alerts** au Workspace qui présente les échecs MCP significatifs de manière exploitable, à partir des données déjà collectées par la chaîne d'observabilité existante.

---

## Governing Artifacts

- Story 0185 — MCP Activity: Operation Lifecycle View (fondation)
- Story 0186 — MCP Activity: Agent Session Correlation
- Story 0187 — MCP Activity: Agent Session Activity View
- Story 0188 — MCP Activity: Agent Session Lifecycle (PR #185, audit PR #186)
- E2E Readiness Report — `docs/investigations/mcp-activity-e2e-readiness-report.md`
- Architecture Decision Proposal — `docs/investigations/mcp-activity-actionable-alerts-adp.md`

---

## Sources de données

### Événements MCP terminaux exploitables

La table `mcp_tool_events` contient tous les événements de la chaîne MCP.

Les alertes sont calculées à partir des terminaux non-nominaux :

| eventType | status | reasonCode | Alerte ? | Sévérité |
|-----------|--------|-----------|---------|---------|
| `MCP_TOOL_FAILED` | `FAILED` | n'importe | ✓ Oui | `ERROR` |
| `MCP_TOOL_INTERRUPTED` | `INTERRUPTED` | `MCP_INSTANCE_STOPPING` | ✗ Non | — |
| `MCP_TOOL_INTERRUPTED` | `INTERRUPTED` | `MCP_REAUTH_REQUIRED` | ✓ Oui | `WARN` |
| `MCP_TOOL_INTERRUPTED` | `INTERRUPTED` | `MCP_PROCESS_FAILED` | ✓ Oui | `ERROR` |
| `MCP_TOOL_INTERRUPTED` | `INTERRUPTED` | null | ✓ Oui | `WARN` |
| `MCP_TOOL_RESPONDED` | `RESPONDED` | — | ✗ Non | — |

**Règle SQL :**
```sql
event_type IN ('MCP_TOOL_FAILED', 'MCP_TOOL_INTERRUPTED')
AND (reason_code IS NULL OR reason_code != 'MCP_INSTANCE_STOPPING')
```

**Justification de l'exclusion `MCP_INSTANCE_STOPPING` :** quand l'instance MCP s'arrête (redémarrage, token renewal), toutes les opérations en cours sont interrompues avec ce code. Ce sont des interruptions attendues, non des incidents.

### Champs disponibles par opération

Depuis `McpToolEventRepository` (projection FULL OUTER JOIN) :

`instanceId`, `operationId`, `toolName` (nullable), `status`, `reasonCode` (nullable), `durationMs` (nullable), `occurredAt`, `agentSessionId` (nullable), `agentProvider` (nullable)

---

## Modèle métier — Projection déterministe

**Choix V1 :** Projection calculée à la demande depuis `mcp_tool_events`. Pas d'entité persistée.

**Avantages :**
- Pas de nouvelle table, pas de migration
- Reconstruit à tout moment depuis la source de vérité
- Isolé par projet
- Déduplication naturelle : une opération = un terminal = une alerte
- Compatible avec le polling Angular existant

**Limitation :** Pas d'état de lecture (lu / non lu) en V1. Cette fonctionnalité sera évaluée après validation de la pertinence des alertes en usage réel.

**AlertId stable :** `instanceId + "/" + operationId` — unique par opération, stable à travers les pages.

**Sévérité déterministe :**
- `ERROR` : `status = FAILED` ou `reasonCode = MCP_PROCESS_FAILED`
- `WARN` : `status = INTERRUPTED` avec autre reasonCode

---

## Architecture

### Extension incrémentale de l'existant

```
Existant :
  mcp_tool_events → McpToolEventRepository → McpOperationService → GET /mcp-operations
  mcp_tool_events → McpToolEventRepository → AgentSessionService → GET /agent-sessions

Ajout Story 0189 :
  mcp_tool_events → McpToolEventRepository (nouvelle query)
                     └── ActivityAlertService
                          └── ActivityAlertController
                               └── GET /api/v1/projects/{projectId}/activity-alerts

  Angular ActivityAlertSection
    └── ActivityAlertService
        └── polling timer(0, intervalMs) → liste d'alertes
```

**Aucune nouvelle table. Aucune migration.**

### Nouvelle query SQL

```java
// Dans McpToolEventRepository
@Query(value = """
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
    ORDER BY occurred_at DESC NULLS LAST,
             instance_id ASC,
             operation_id ASC
    LIMIT :size OFFSET :offset
    """, nativeQuery = true)
List<ActivityAlertProjection> findAlertsByProject(
    @Param("projectId") UUID projectId,
    @Param("offset") int offset,
    @Param("size") int size);

@Query(value = """
    SELECT COUNT(DISTINCT (instance_id, operation_id))
    FROM mcp_tool_events
    WHERE project_id = :projectId
      AND event_type IN ('MCP_TOOL_FAILED', 'MCP_TOOL_INTERRUPTED')
      AND (reason_code IS NULL OR reason_code != 'MCP_INSTANCE_STOPPING')
    """, nativeQuery = true)
long countAlertsByProject(@Param("projectId") UUID projectId);
```

### Record de vue Java

```java
public record ActivityAlertView(
    String  alertId,          // instanceId + "/" + operationId
    String  severity,         // "ERROR" | "WARN"
    String  eventType,        // "MCP_TOOL_FAILED" | "MCP_TOOL_INTERRUPTED"
    String  toolName,         // nullable
    String  reasonCode,       // nullable
    Integer durationMs,       // nullable
    Instant occurredAt,
    String  instanceId,
    String  operationId,
    String  agentSessionId,   // nullable
    String  agentProvider     // nullable
) {}

public record ActivityAlertPageResponse(
    List<ActivityAlertView> content,
    long    totalAlerts,
    int     page,
    int     size
) {}
```

### Service Java

```java
@Service
@RequiredArgsConstructor
public class ActivityAlertService {

    private final McpToolEventRepository repository;

    @Transactional(readOnly = true)
    public ActivityAlertPageResponse listByProject(UUID projectId, int page, int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.clamp(size, 1, 50);
        long total = repository.countAlertsByProject(projectId);
        List<McpToolEventRepository.ActivityAlertProjection> rows =
                repository.findAlertsByProject(projectId, clampedPage * clampedSize, clampedSize);
        return new ActivityAlertPageResponse(
                rows.stream().map(ActivityAlertService::toView).toList(),
                total,
                clampedPage,
                clampedSize);
    }

    private static ActivityAlertView toView(McpToolEventRepository.ActivityAlertProjection p) {
        String severity = "FAILED".equals(p.getStatus())
                || "MCP_PROCESS_FAILED".equals(p.getReasonCode())
                ? "ERROR" : "WARN";
        return new ActivityAlertView(
                p.getInstanceId() + "/" + p.getOperationId(),
                severity,
                "FAILED".equals(p.getStatus()) ? "MCP_TOOL_FAILED" : "MCP_TOOL_INTERRUPTED",
                p.getToolName(),
                p.getReasonCode(),
                p.getDurationMs(),
                p.getOccurredAt(),
                p.getInstanceId(),
                p.getOperationId(),
                p.getAgentSessionId(),
                p.getAgentProvider());
    }
}
```

### Contrôleur Java

```java
@RestController
@RequiredArgsConstructor
public class ActivityAlertController {
    private final AuthenticatedPrincipalResolver principalResolver;
    private final CoreProjectAuthorizationService authorizationService;
    private final ActivityAlertService service;

    @GetMapping("/api/v1/projects/{projectId}/activity-alerts")
    public ResponseEntity<ActivityAlertPageResponse> byProject(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = principalResolver.resolve(servletRequest)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        if (!authorizationService.isAuthorized(principal, projectId, "MCP_TOOL_EVENTS_READ")) {
            throw new EntityNotFoundException("Project", projectId);
        }
        return ResponseEntity.ok(service.listByProject(projectId, page, size));
    }
}
```

---

## Contrat REST

### Endpoint

```
GET /api/v1/projects/{projectId}/activity-alerts?page=0&size=20
```

**Auth :** permission `MCP_TOOL_EVENTS_READ` (réutilisation exacte — même acteur, même périmètre)

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

**État vide :** `{ "content": [], "totalAlerts": 0, "page": 0, "size": 20 }`

---

## Expérience Angular

### Composant ActivityAlertSection

Pattern identique aux sections existantes :

```typescript
@Component({
  selector: 'app-activity-alert-section',
  imports: [AsyncPipe, DatePipe],
  templateUrl: './activity-alert-section.html',
  styleUrl: './activity-alert-section.scss',
})
export class ActivityAlertSection implements OnDestroy {
  @Input({ required: true }) projectId = '';
  private readonly service = inject(ActivityAlertService);
  private readonly intervalMs = inject(APP_ENVIRONMENT).analysisPollingIntervalMs;
  private readonly destroyed$ = new Subject<void>();

  readonly alerts$: Observable<AlertListState> = new Observable<string>(observer => {
    observer.next(this.projectId);
    observer.complete();
  }).pipe(
    switchMap(id =>
      timer(0, this.intervalMs).pipe(
        switchMap(() =>
          this.service.byProject(id).pipe(
            map(page => ({ state: 'loaded' as const, alerts: page.content })),
            catchError(() => of({ state: 'error' as const }))
          )
        ),
        takeUntil(this.destroyed$)
      )
    )
  );
}
```

### Template ActivityAlertSection (cible)

```
Activity Alerts
│
├── [ERROR] devlog_get_story_context — FAILED
│   Project: DevLog AI · Session: cc7d159d
│   Duration: 4521ms · 11:42:03
│
├── [WARN] devlog_search_project_history — INTERRUPTED
│   Reason: MCP_REAUTH_REQUIRED · Session: cc7d159d
│   Duration: 892ms · 11:38:17
│
└── (Pagination ou "Show more")
```

Chaque alerte affiche :
- Badge sévérité (`ERROR` → rouge, `WARN` → orange)
- Nom de l'outil (`toolName ?? 'unknown tool'`)
- Statut de l'événement (`eventType` abrégé)
- Code de raison si disponible (`reasonCode`)
- Durée si disponible (`durationMs`)
- Horodatage (`occurredAt | date: 'HH:mm:ss'`)
- Context session si disponible (`agentSessionId | slice:0:8`)

**État vide :** "No failed operations — all MCP tools responded successfully."

**Pas de nouvelle bibliothèque de state management.**

### Placement

Section `Activity Alerts` dans la vue Activity (`/activity`), au-dessus de la section Agent Sessions.

Réutilise le composant `app-agent-session-activity-section` existant pour le projectId binding pattern.

---

## Sécurité

- Même permission `MCP_TOOL_EVENTS_READ` que les endpoints existants
- Pas de payload privé exposé (pas de prompt, pas de réponse MCP, pas de credentials)
- `toolName` et `reasonCode` sont des métadonnées opérationnelles, pas des données utilisateur
- `agentSessionId` est une clé technique opaque, pas un identifiant personnel
- `EntityNotFoundException` masque les projets non autorisés (cohérent avec l'existant)

---

## Critères d'acceptation

**AC1** — `GET /api/v1/projects/{projectId}/activity-alerts` retourne les alertes éligibles paginées.

**AC2** — Une opération `FAILED` (sans restriction de reasonCode) apparaît comme alerte `ERROR`.

**AC3** — Une opération `INTERRUPTED` avec `reasonCode = MCP_INSTANCE_STOPPING` n'apparaît PAS comme alerte.

**AC4** — Une opération `INTERRUPTED` avec `reasonCode = MCP_REAUTH_REQUIRED` apparaît comme alerte `WARN`.

**AC5** — Une opération `INTERRUPTED` avec `reasonCode = null` apparaît comme alerte `WARN`.

**AC6** — Une opération `RESPONDED` n'apparaît pas comme alerte.

**AC7** — Une opération ne produit qu'une alerte (déduplication par `instance_id + operation_id`).

**AC8** — Les alertes sont isolées par projet.

**AC9** — Un projet non autorisé retourne 404 (pas 403).

**AC10** — L'`alertId` est stable : `instanceId + "/" + operationId`.

**AC11** — L'Angular affiche correctement : alerte ERROR, alerte WARN, état vide, état chargement, état erreur.

**AC12** — L'`agentSessionId` absent est géré (affichage conditionnel, pas d'erreur Angular).

**AC13** — Le `toolName` absent est géré (`'unknown tool'`).

**AC14** — Les alertes sont triées par `occurredAt DESC` de manière déterministe.

**AC15** — Aucun secret, payload privé ou donnée inutile n'est exposé dans la réponse.

---

## Tests

### Backend — `ActivityAlertServiceTest` (unitaire)

```
listByProjectReturnsFailedOperations
listByProjectExcludesMcpInstanceStopping
listByProjectIncludesNullReasonCode
listByProjectIncludesReauthRequired
listByProjectIncludesMcpProcessFailed
listByProjectExcludesRespondedOperations
listByProjectClampsPageSize
listByProjectAssignsErrorSeverityToFailed
listByProjectAssignsWarnSeverityToInterrupted
listByProjectAssignsAlertId
```

### Backend — `ActivityAlertControllerWebMvcTest` (WebMVC)

```
byProjectReturns200WithAlerts
byProjectReturns401WithoutPrincipal
byProjectReturns404ForUnauthorizedProject
byProjectReturns200WithEmptyContent
```

### Backend — `ActivityAlertPostgresIntegrationTest` (Testcontainers)

```
failedOperationAppearsAsAlert
mcpInstanceStoppingExcluded
respondedOperationExcluded
reauthRequiredAppearsAsAlert
alertsIsolatedByProject
deduplicationPreservesOneAlertPerOperation
alertsSortedByOccurredAtDesc
```

### Angular — `activity-alert-section.spec.ts`

```
showsErrorAlertForFailedOperation
showsWarnAlertForInterruptedOperation
showsEmptyStateWhenNoAlerts
showsLoadingState
showsErrorState
handlesNullToolName
handlesNullAgentSessionId
```

---

## Risques

| Risque | Probabilité | Impact | Mitigation |
|--------|-------------|--------|-----------|
| Bruit trop élevé (trop d'alertes) | Moyen | Élevé | Exclure MCP_INSTANCE_STOPPING — cible les incidents réels |
| Alertes sans toolName (MCP_TOOL_STARTED perdu) | Faible | Faible | Affichage "unknown tool" — information contextuelle suffisante |
| Latence SQL sur grand volume | Faible | Faible | DISTINCT ON + LIMIT/OFFSET + index existant sur project_id |
| Polling trop fréquent | Faible | Faible | Réutilise intervalMs existant (15s ou configurable) |
| Évolution de reasonCode non anticipée | Faible | Moyen | Règle d'exclusion par liste (`!= MCP_INSTANCE_STOPPING`) — nouveaux codes alertent par défaut |

---

## Décisions à valider

**D1 — Validation du modèle de projection vs entité persistée**
Recommandation : projection pour V1. Valider avant implémentation.

**D2 — Périmètre temporel des alertes**
Option 1 : Toutes les alertes historiques (paginées) — recommandé.
Option 2 : Fenêtre glissante (N dernières heures).

**D3 — Regroupement des alertes répétitives**
Recommandation : alertes individuelles en V1 (une par opération). Regroupement = V2 si bruit observé.

**D4 — Placement dans le Workspace**
Recommandation : nouvelle section "Activity Alerts" dans la vue Activity, au-dessus de Agent Sessions.

---

## Plan d'implémentation

### Étape 1 — Backend : query + service + contrôleur

1. Ajouter `ActivityAlertProjection` interface dans `McpToolEventRepository`
2. Ajouter `findAlertsByProject()` et `countAlertsByProject()` dans `McpToolEventRepository`
3. Créer `ActivityAlertView` et `ActivityAlertPageResponse` records
4. Créer `ActivityAlertService.listByProject()` avec calcul sévérité et alertId
5. Créer `ActivityAlertController` → `GET /api/v1/projects/{projectId}/activity-alerts`
6. Tests unitaires `ActivityAlertServiceTest` (10 cas)
7. Tests WebMVC `ActivityAlertControllerWebMvcTest` (4 cas)
8. Tests Testcontainers `ActivityAlertPostgresIntegrationTest` (7 cas)

### Étape 2 — Angular : service + composant + section

1. Créer `ActivityAlert` et `ActivityAlertPage` interfaces (`activity-alert.models.ts`)
2. Créer `ActivityAlertService` (`activity-alert.service.ts`)
3. Créer `ActivityAlertSection` composant (ts + html + scss)
4. Intégrer dans la vue Activity (projet-workspace-section-page ou section dédiée)
5. Tests spec `activity-alert-section.spec.ts` (7 cas)
6. Lint + format check

---

## Hors périmètre

- État de lecture (lu / non lu)
- Notifications push, WebSocket, SSE, desktop
- Alertes sur inactivité de session
- Alertes sur cycle de vie MCP runtime disconnect
- Regroupement d'alertes répétitives
- Scoring ou priorité dynamique
- Agent Presence Window
- Analyse des conversations privées
- Orchestrateur d'agents
- RAG
