# Companion Integration Audit — MCP Activity Chain (Stories 0185–0191)

**Date :** 2026-11-16  
**Scope :** Audit de la chaîne MCP Activity Companion (Phase A de la mission)  
**Statut :** COMPLÉTÉ — P1 bug corrigé, suite de tests verte

---

## Résumé exécutif

La chaîne MCP Activity Companion (Stories 0185–0191) est fonctionnellement solide. Un bug P1 (UUID/slug dans le badge de navigation) a été identifié et corrigé. Aucun autre bug P1 ou P2 n'a été trouvé. Toutes les suites de tests passent.

---

## Périmètre audité

| Story | Composant | Verdict |
|-------|-----------|---------|
| 0185 | Operation Lifecycle View | ✓ OK |
| 0186 | Agent Session Correlation | ✓ OK |
| 0187 | Agent Session Activity View | ✓ OK |
| 0188 | Agent Session Lifecycle | ✓ OK |
| 0189 | Actionable Activity Alerts | ✓ OK |
| 0190 | Incident Attention Center | ✓ OK (fix PR #189 appliqué) |
| 0191 | Alert Navigation Badge | ✓ OK (bug P1 corrigé cette session) |

---

## Phase 1 — Routing audit

### Constat

**`app.routes.ts`** déclare la route projet comme `projects/:id` où `:id` est un **slug** (ex: `"devlog-ai"`).

**`ProjectController.java`** résout les projets par slug via `GET /api/v1/projects/{slug}`.

Tous les endpoints Activity backend attendent un `@PathVariable UUID projectId`. Le `ProjectService.getProject(identifier)` retourne un objet avec un `id` UUID résolu.

### Composants Activity — statut

| Composant | Obtient l'UUID via | Statut |
|-----------|-------------------|--------|
| `ActivityAlertSection` | `@Input() projectId` → `view.project.id` | ✓ OK |
| `ActivityIncidentSection` | `@Input() projectId` → `view.project.id` | ✓ OK |
| `ActivityIncidentIndicator` | `@Input() projectId` → `view.project.id` | ✓ OK |
| `ProjectWorkspaceLayout.alertCount$` | **route.paramMap.get('id')** (slug) → **BUG P1** | ✗ KO avant fix |

---

## Phase 2 — Bug P1 identifié et corrigé

### Problème

`ProjectWorkspaceLayout.alertCount$` passait le slug de route directement à `ActivityAlertService.countByProject()`. Spring UUID binding échoue avec un slug → 400 → capturé par `catchError(() => of({ errorCount: 0 }))` → badge toujours vide.

### Fix appliqué

**Avant :**
```typescript
readonly alertCount$ = this.route.paramMap.pipe(
  map(params => params.get('id') ?? ''),
  switchMap(slug =>
    timer(0, this.intervalMs).pipe(
      switchMap(() => this.alertService.countByProject(slug).pipe(...))
    )
  )
);
```

**Après :**
```typescript
readonly alertCount$ = this.project$.pipe(
  switchMap((view) => {
    if (view.state !== 'loaded') return of({ errorCount: 0 });
    return timer(0, this.intervalMs).pipe(
      switchMap(() =>
        this.alertService.countByProject(view.project.id).pipe(
          catchError(() => of({ errorCount: 0 })),
        ),
      ),
      takeUntil(this.destroyed$),
    );
  }),
);
```

Fichiers modifiés :
- `frontend/src/app/features/workspace/project-workspace-layout.ts`
- `frontend/src/app/features/workspace/project-workspace-layout.spec.ts`

---

## Phase 3 — Audit fonctionnel

### 3.1 Consistance des compteurs

| Compteur | Window | Critère "ERROR" |
|----------|--------|-----------------|
| Badge nav (`alertCount$`) | 1440 min (24h) | `status=FAILED OR reasonCode=MCP_PROCESS_FAILED` |
| Incident indicator | 60 min (1h) | `BOOL_OR(status=FAILED OR reasonCode=MCP_PROCESS_FAILED)` |

**Observation :** Les deux composants utilisent des fenêtres différentes par design — le badge est un signal d'attention large (24h), l'indicateur montre le regroupement récent (1h). Les critères de sévérité ERROR sont identiques. Pas de bug — comportement intentionnel.

**À documenter :** Cette différence de fenêtre peut surprendre un utilisateur qui voit le badge allumé mais pas l'indicateur. Acceptable pour V1 ; à considérer dans la documentation UX.

### 3.2 Flux de navigation

| Flux | Comportement | Verdict |
|------|-------------|---------|
| Clic badge nav → Activity | `routerLink="activity"` → route activity | ✓ OK |
| `navigateToAlerts()` → Alerts tab | `navigate(['../activity'], { relativeTo: route, queryParams: { view: 'alerts' } })` | ✓ OK |
| `ActivatedRoute` injecté dans `ActivityIncidentIndicator` | `useValue: {}` dans les tests | ✓ OK |

### 3.3 Gestion des subscriptions

| Composant | Protection | Verdict |
|-----------|-----------|---------|
| `ProjectWorkspaceLayout` | `takeUntil(destroyed$)` + `ngOnDestroy` | ✓ OK |
| `ActivityIncidentIndicator` | `takeUntil(destroyed$)` + `ngOnDestroy` | ✓ OK |
| `AgentSessionActivitySection` | `.subscribe()` avec `takeUntil(destroyed$)` upstream | ✓ OK |
| Pas de subscription dans les autres composants Activity | Utilisation du `async` pipe | ✓ OK |

Aucune fuite de subscription détectée.

### 3.4 Validation E2E

Non effectuée dans cette session (pas de stack Docker en cours). La validation E2E avec vraies données reste à faire avant la mise en production.

---

## Phase 4 — Résultats des tests

### Angular — suite complète

```
Test Files  60 passed (60)
Tests       335 passed (335)
```

### Backend — tests Activity Alert/Incident

```
Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## Findings classifiés

| ID | Sévérité | Description | Statut |
|----|----------|-------------|--------|
| F-01 | P1 | `alertCount$` passait le slug de route comme UUID → badge toujours vide | ✓ CORRIGÉ |
| F-02 | P2/UX | Badge (24h) et indicateur (1h) utilisent des fenêtres différentes | KNOWN — intentionnel, à documenter |
| F-03 | INFO | Validation E2E non effectuée (stack non démarrée) | À FAIRE avant prod |

---

## Conclusion

La chaîne MCP Activity Companion est prête pour la validation humaine avec le bug P1 corrigé. L'architecture globale est solide, les subscriptions sont gérées correctement, et la navigation est cohérente. La prochaine étape naturelle est l'extension vers le Companion contextuel (Story 0192).
