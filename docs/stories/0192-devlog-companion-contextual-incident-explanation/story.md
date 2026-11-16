# Story 0192 — DevLog Companion: Contextual Incident Explanation V1

## Status

DESIGN APPROUVÉ — AWAITING IMPLEMENTATION AUTHORIZATION

---

## Problème

Les Stories 0185–0191 ont livré une chaîne d'observabilité MCP complète : capture, reconstruction, corrélation, alertes, groupement en incidents, indicateur visuel, et badge de navigation. L'interface Activity expose des incidents structurés (outil, reasonCode, fréquence, fenêtre temporelle).

Cependant, la chaîne reste **opérationnelle pure** : elle montre *ce qui s'est passé* sans pouvoir expliquer *pourquoi cela importe* dans le contexte du projet.

**Exemple concret :**

> L'utilisateur voit dans l'indicateur d'incidents : `get_engineering_context · FAILED × 3 · dernière heure`.

> Questions sans réponse aujourd'hui :
> 1. Cet outil est-il lié à une Story en cours ou à un changement récent ?
> 2. Cette erreur est-elle connue / documentée dans les décisions du projet ?
> 3. Est-ce un problème d'infrastructure transitoire ou un signal d'une contrainte architecturale ?

L'utilisateur doit naviguer manuellement dans plusieurs sections (Stories, Knowledge, commits) pour construire manuellement un contexte que DevLog possède déjà.

**Besoin fonctionnel :**

> Quand l'utilisateur examine un incident MCP, DevLog doit pouvoir produire une explication contextuelle grondée dans la connaissance du projet — en utilisant l'infrastructure EngineeringContext et l'AI Engine existants.

---

## Objectif fonctionnel

Ajouter une capability **Contextual Incident Explanation** au Companion DevLog :

- Sur demande explicite de l'utilisateur (jamais automatique)
- En entrée : un incident MCP identifié (outil, reasonCode, sévérité, fréquence, fenêtre temporelle)
- En sortie : une analyse contextuelle structurée non-trustée — ce que le projet sait sur cet incident
- Rendu : un panneau d'explication dans l'interface Activity / Incident Indicator

---

## Governing Artifacts

- **ADR-006** — Gouvernance des propositions AI (ValidatableProposal)
- **ADR-063** — Architecture du contexte (EngineeringContext)
- **ADR-065** — Séparation synthesis/proposal, snapshots non-trustés
- **ADR-066** — Évaluation réplicable (harness Python)
- **ADR-067** — Première capability agent (D1–D11, non-goals, failure semantics)
- Story 0111 — EngineeringContext contract (fondation)
- Story 0112 — Story Context Agent V1 (pattern d'implémentation de référence)
- Story 0189 — MCP Activity: Actionable Activity Alerts (fondation incident data)
- Story 0190 — MCP Activity: Incident Attention Center (groupement incidents)
- Story 0191 — MCP Activity: Alert Navigation Badge (badge navigation)

---

## Sources de données

### Incidents MCP (chaîne existante)

| Champ | Source | Note |
|-------|--------|------|
| `incidentId` | SHA-256(toolName + reasonCode) → 16 hex | Stable, calculé par `ActivityIncidentService.computeIncidentId` |
| `toolName` | `mcp_tool_events.tool_name` | nullable |
| `reasonCode` | `mcp_tool_events.reason_code` | nullable |
| `severity` | `BOOL_OR(status='FAILED' OR reasonCode='MCP_PROCESS_FAILED')` | ERROR/WARN |
| `occurrenceCount` | COUNT dans la fenêtre | 1-N |
| `firstOccurredAt` / `lastOccurredAt` | MIN/MAX `occurred_at` | ISO-8601 |

### EngineeringContext (Java Core — ADR-067 D3)

Profils à inclure pour l'analyse d'incident :

| Profil | Contenu | Raison |
|--------|---------|--------|
| `project-state-v1` | Stories actives, milestones, artifacts | Lier l'incident aux Stories en cours |
| `history-v1` | Commits récents, Engineering Events, Insights | Détecter une corrélation temporelle avec un changement |

Le Java Core est **seul autorité** pour la construction et l'autorisation du `EngineeringContext`. Python consomme ce contexte comme entrée autorisée immuable (D3).

**Données d'incident transmises au Python AI Engine :** Les données d'incident (outil, reasonCode, sévérité, fréquence, fenêtre) sont incluses dans le grounding contract de l'`AiTask` comme `incidentContext`, pas comme partie de l'`EngineeringContext`. Elles sont des métadonnées opérationnelles, pas de la connaissance de projet.

---

## Architecture

### Flow de bout en bout

```
Utilisateur
  │
  │ [Bouton "Explain" sur l'indicateur d'incident]
  │ POST /api/v1/projects/{projectId}/activity-incidents/{incidentId}/analyze
  ▼
ActivityIncidentAnalysisController (Java Core)
  │ 1. Auth: MCP_TOOL_EVENTS_READ
  │ 2. Valide incidentId connu dans la fenêtre active
  │ 3. Construit IncidentAnalysisRequest
  ▼
IncidentContextualAnalysisService (Java Core)
  │ 1. Construit EngineeringContext [project-state-v1, history-v1]
  │ 2. Construit grounding contract (EngineeringContext + incidentContext)
  │ 3. Crée AiTask (intent: activity-incident-context-analysis-v1)
  │ 4. Envoie à Python AI Engine (callback pattern existant)
  ▼
Python AI Engine — IncidentContextAnalysisGenerationService
  │ 1. Reçoit EngineeringContext + incidentContext (immuable)
  │ 2. Construit prompt depuis IncidentContextAnalysisPromptBuilder
  │ 3. Génération structurée (LLM provider)
  │ 4. Validation défensive (grounding, trust safety, relationship semantics)
  │ 5. Retry correctif si nécessaire (max 1, pattern ADR-066)
  │ 6. Callback Core avec résultat structuré
  ▼
IncidentContextualAnalysisService — résultat
  │ 1. Validation autoritaire Java Core (grounding contract)
  │ 2. Persiste snapshot non-trustée via AiTask.synthesisSnapshot (D5)
  │ 3. Retourne IncidentContextualAnalysisResult
  ▼
Réponse REST → Angular ActivityIncidentExplanationPanel
```

### Alignement ADR-067

| Décision | Application à Story 0192 |
|----------|--------------------------|
| D1 — Hybrid ownership | Java Core : capability, auth, EngineeringContext, grounding, snapshot. Python : exécution, génération structurée. |
| D2 — MCP + REST adapters | V1 : REST uniquement. MCP adapter différé. Un seul point d'entrée capability. |
| D3 — Java seul autorité EngineeringContext | Python reçoit EngineeringContext immuable. Incident data = grounding séparé. |
| D5 — Hybrid persistence | Snapshot non-trustée via `AiTask.synthesisSnapshot`. Pas de nouvelle table. |
| D6 — Analysis only, no ValidatableProposal | Résultat = analyse non-trustée. Jamais de promotion automatique. |
| D7 — Strict grounding | Toute affirmation factuelle/interprétative : référence d'évidence obligatoire. |
| D8 — Explicit relationship semantics | Types EXPLICIT / TEMPORAL_PROXIMITY / POSSIBLE_RELEVANCE / INFERRED_HYPOTHESIS obligatoires. |
| D9 — Explicit human invocation | Bouton "Explain" uniquement. Jamais de déclenchement automatique. |
| D10 — ADR-066 evaluation | Évaluation réplicable via harness existant. Nouveau scénario `incident-context-analysis-v1`. |
| D11 — Runtime-agnostic | Capability indépendante du transport. MCP adapter possible sans changer la capability. |

---

## Nouveau Intent

### IntentCatalog — entrée

```java
register(result, new IntentDefinition(
    "activity-incident-context-analysis", "v1",
    "Produce a structured, grounded contextual explanation of a MCP tool incident "
    + "in the context of the project's current engineering state.",
    ProposalType.NONE, IntentExecutionMode.GENERIC,
    List.of(),
    List.of(
        "Utiliser uniquement le EngineeringContext fourni et le incidentContext du grounding contract.",
        "Produire une analyse contextuelle structurée sans créer de ValidatableProposal.",
        "Toutes les affirmations factuelles et interprétations doivent être ancrées dans les références d'évidence.",
        "Les types de relation doivent être EXPLICIT, TEMPORAL_PROXIMITY, POSSIBLE_RELEVANCE ou INFERRED_HYPOTHESIS.",
        "La confiance ne promeut jamais une catégorie de relation.",
        "Ne jamais présenter une analyse comme une connaissance validée.",
        "Si le contexte est insuffisant, retourner une analyse avec confidence=LOW et uncertainties explicites."
    ),
    incidentContextAnalysisOutputContract(),
    "activity-incident-context-analysis-prompt-v1",
    List.of(PROJECT_STATE_PROFILE, HISTORY_PROFILE)));
```

### Output contract

```java
private static Map<String, Object> incidentContextAnalysisOutputContract() {
    return Map.of(
        "type", "object",
        "structured", true,
        "hasProposals", false,
        "hasAnalysisResult", true,
        "root", "incidentAnalysis",
        "requiredAnalysisFields", List.of(
            "toolContext", "relevantFindings", "likelyCause",
            "recommendedInvestigation", "uncertainties",
            "confidence", "provenance"));
}
```

---

## Contrat de grounding (AiTask)

Le grounding contract transmis au Python AI Engine :

```json
{
  "intent": "activity-incident-context-analysis-v1",
  "engineeringContext": { /* EngineeringContext standard — Java Core authority */ },
  "incidentContext": {
    "incidentId": "a1b2c3d4e5f6a7b8",
    "toolName": "get_engineering_context",
    "reasonCode": null,
    "severity": "ERROR",
    "occurrenceCount": 3,
    "firstOccurredAt": "2026-11-16T10:15:00Z",
    "lastOccurredAt": "2026-11-16T11:02:30Z",
    "windowMinutes": 60
  }
}
```

L'`incidentContext` est des métadonnées opérationnelles passées en entrée. Ce n'est pas de la connaissance de projet. Python ne peut pas enrichir ni modifier ces données.

---

## Contrat REST

### Endpoint

```
POST /api/v1/projects/{projectId}/activity-incidents/{incidentId}/analyze
```

**Auth :** permission `MCP_TOOL_EVENTS_READ` (réutilisation exacte — même acteur, même périmètre)

**Body (JSON) :**

```json
{
  "toolName": "get_engineering_context",
  "reasonCode": null,
  "severity": "ERROR",
  "occurrenceCount": 3,
  "firstOccurredAt": "2026-11-16T10:15:00Z",
  "lastOccurredAt": "2026-11-16T11:02:30Z",
  "windowMinutes": 60
}
```

L'`incidentId` dans l'URL est le hash calculé par `ActivityIncidentService.computeIncidentId(toolName, reasonCode)`. Le frontend le possède depuis la réponse de l'endpoint `GET /activity-incidents`.

### Réponse succès (200 OK)

```json
{
  "incidentId": "a1b2c3d4e5f6a7b8",
  "toolContext": {
    "toolPurpose": "Retrieves structured engineering context for a project scope.",
    "observedPattern": "Three consecutive failures within 60 minutes suggest a persistent issue rather than a transient error.",
    "evidenceReferences": ["engineering-story-0192-ref1"]
  },
  "relevantFindings": [
    {
      "category": "ACTIVE_STORY",
      "finding": "Story 0192 is currently in design phase, involving EngineeringContext retrieval for incident analysis.",
      "evidenceReferences": ["story-0192-state"],
      "confidence": "MEDIUM",
      "relationType": "TEMPORAL_PROXIMITY"
    }
  ],
  "likelyCause": {
    "classification": "CODE_CHANGE",
    "explanation": "A recent modification to context selection may have introduced a timeout scenario.",
    "confidence": "LOW",
    "evidenceReferences": ["commit-abc1234"]
  },
  "recommendedInvestigation": "Inspect recent commits to EngineeringContextFacadeImpl for timeout behavior.",
  "uncertainties": [
    {
      "reason": "No direct error log evidence in EngineeringContext — operational logs are outside project knowledge scope.",
      "impact": "Cannot determine exact error cause from project knowledge alone."
    }
  ],
  "confidence": "LOW",
  "provenance": {
    "engineeringContextDigest": "sha256:abc...",
    "contextFreshness": "FRESH",
    "analyzedAt": "2026-11-16T11:05:00Z",
    "aiTaskId": "uuid-of-aitask"
  }
}
```

**Réponses d'erreur :**

| Code | Condition |
|------|-----------|
| 401 | Principal non authentifié |
| 404 | Projet non autorisé |
| 503 | AI Engine indisponible |
| 500 | Échec de validation grounding après retry |

---

## Contrat de sortie Python (Pydantic)

```python
from __future__ import annotations
from typing import Any, Literal, Optional
from pydantic import BaseModel


class IncidentToolContext(BaseModel):
    tool_purpose: str
    observed_pattern: str
    evidence_references: list[str]


class IncidentFinding(BaseModel):
    category: Literal["ACTIVE_STORY", "RECENT_CHANGE", "KNOWN_CONSTRAINT", "ONGOING_INVESTIGATION"]
    finding: str
    evidence_references: list[str]
    confidence: Literal["HIGH", "MEDIUM", "LOW"]
    relation_type: Literal["EXPLICIT", "TEMPORAL_PROXIMITY", "POSSIBLE_RELEVANCE", "INFERRED_HYPOTHESIS"]


class IncidentLikelyCause(BaseModel):
    classification: Literal["ENVIRONMENT", "CODE_CHANGE", "CONFIGURATION", "LOAD", "UNKNOWN"]
    explanation: str
    confidence: Literal["HIGH", "MEDIUM", "LOW"]
    evidence_references: list[str]


class IncidentUncertainty(BaseModel):
    reason: str
    impact: str


class IncidentContextAnalysisResult(BaseModel):
    tool_context: IncidentToolContext
    relevant_findings: list[IncidentFinding]   # vide si contexte insuffisant
    likely_cause: IncidentLikelyCause
    recommended_investigation: Optional[str]   # null si contexte insuffisant
    uncertainties: list[IncidentUncertainty]
    confidence: Literal["HIGH", "MEDIUM", "LOW"]
    provenance: dict[str, Any]
```

---

## Contraintes de scope V1

| Contrainte | Raison |
|------------|--------|
| Invocation explicite uniquement (bouton) | D9 — pas de déclenchement autonome |
| REST uniquement (pas MCP adapter en V1) | Simplicité V1 — MCP adapter différé |
| Pas de ValidatableProposal | D6 — analyse seulement |
| Snapshot via AiTask.synthesisSnapshot uniquement | D5 — pas de nouvelle table |
| Pas de conversation persistante | D9 — one-shot |
| Profils project-state-v1 + history-v1 uniquement | Périmètre borné — engineering-story-v1 différé |
| Un seul incident à la fois | Périmètre V1 — analyse batch différée |
| Pas de cache côté Angular | V1 — chaque clic reexécute |

---

## Expérience Angular

### Flux UI

```
ActivityIncidentIndicator (existant)
│  [ERROR] get_engineering_context × 3   [Explain →]
│
└── [clic sur "Explain"]
    │
    ├── État LOADING
    │   "Analyzing incident context..."
    │
    ├── État LOADED
    │   ┌─────────────────────────────────────────┐
    │   │ Incident Context                   [✕]  │
    │   ├─────────────────────────────────────────┤
    │   │ Tool: get_engineering_context            │
    │   │ This tool retrieves structured           │
    │   │ engineering context for a project.       │
    │   │                                         │
    │   │ Relevant findings:                      │
    │   │ · [ACTIVE_STORY] Story 0192 is active   │
    │   │   TEMPORAL_PROXIMITY · MEDIUM           │
    │   │                                         │
    │   │ Likely cause: CODE_CHANGE (LOW)         │
    │   │ Inspect recent commits to               │
    │   │ EngineeringContextFacadeImpl...         │
    │   │                                         │
    │   │ Confidence: LOW                         │
    │   └─────────────────────────────────────────┘
    │
    └── État ERROR
        "Could not analyze incident context. Try again."
```

### Composant ActivityIncidentExplanationPanel

```typescript
@Component({
  selector: 'app-activity-incident-explanation-panel',
  imports: [AsyncPipe, DatePipe],
  templateUrl: './activity-incident-explanation-panel.html',
  styleUrl: './activity-incident-explanation-panel.scss',
})
export class ActivityIncidentExplanationPanel implements OnDestroy {
  @Input({ required: true }) projectId = '';
  @Input({ required: true }) incident!: ActivityIncidentView;
  @Output() close = new EventEmitter<void>();

  private readonly service = inject(ActivityIncidentAnalysisService);
  private readonly destroyed$ = new Subject<void>();

  state: ExplanationState = { kind: 'idle' };

  explain(): void {
    this.state = { kind: 'loading' };
    this.service
      .analyzeIncident(this.projectId, this.incident.incidentId, {
        toolName: this.incident.toolName,
        reasonCode: this.incident.reasonCode,
        severity: this.incident.severity,
        occurrenceCount: this.incident.occurrenceCount,
        firstOccurredAt: this.incident.firstOccurredAt,
        lastOccurredAt: this.incident.lastOccurredAt,
        windowMinutes: 60,
      })
      .pipe(takeUntil(this.destroyed$))
      .subscribe({
        next: (result) => { this.state = { kind: 'loaded', result }; },
        error: ()       => { this.state = { kind: 'error' }; },
      });
  }

  ngOnDestroy(): void {
    this.destroyed$.next();
    this.destroyed$.complete();
  }
}

type ExplanationState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'loaded'; result: IncidentContextualAnalysis }
  | { kind: 'error' };
```

### Nouveau service Angular

```typescript
// activity-incident-analysis.service.ts
@Injectable({ providedIn: 'root' })
export class ActivityIncidentAnalysisService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(APP_ENVIRONMENT).backendBaseUrl;

  analyzeIncident(
    projectId: string,
    incidentId: string,
    request: IncidentAnalysisRequest,
  ): Observable<IncidentContextualAnalysis> {
    return this.http.post<IncidentContextualAnalysis>(
      `${this.base}/projects/${encodeURIComponent(projectId)}/activity-incidents/${encodeURIComponent(incidentId)}/analyze`,
      request,
    );
  }
}
```

---

## Sécurité

- Même permission `MCP_TOOL_EVENTS_READ` que tous les endpoints Activity existants
- `EntityNotFoundException` masque les projets non autorisés (cohérent avec l'existant)
- Le grounding contract ne contient que des métadonnées opérationnelles (pas de prompt, pas de réponse MCP, pas de credentials)
- L'`EngineeringContext` est construit et autorisé par Java Core — Python ne reconstruit rien indépendamment
- Pas de données personnelles dans la réponse analyse

---

## Critères d'acceptation

**AC1** — `POST /api/v1/projects/{projectId}/activity-incidents/{incidentId}/analyze` retourne une analyse structurée pour un incident avec un projet ayant du contexte.

**AC2** — La réponse contient tous les champs requis : `toolContext`, `relevantFindings`, `likelyCause`, `uncertainties`, `confidence`, `provenance`.

**AC3** — L'`EngineeringContext` est construit par Java Core avec les profils `project-state-v1` et `history-v1`. Python ne reconstruit pas de contexte indépendamment.

**AC4** — Toute affirmation factuelle ou interprétative dans la réponse référence une évidence valide de l'`EngineeringContext`.

**AC5** — `relevantFindings` peut être vide (liste vide valide si contexte insuffisant).

**AC6** — Le `relationType` de chaque finding est l'un de : EXPLICIT, TEMPORAL_PROXIMITY, POSSIBLE_RELEVANCE, INFERRED_HYPOTHESIS.

**AC7** — La confidence ne promeut pas une catégorie de relation (POSSIBLE_RELEVANCE + HIGH confidence ≠ EXPLICIT).

**AC8** — Le résultat est persisté comme snapshot non-trustée via `AiTask.synthesisSnapshot`. Il n'est jamais promu en Insight, Decision, ou EngineeringEvent.

**AC9** — Aucun `ValidatableProposal` n'est créé.

**AC10** — Un projet non autorisé retourne 404.

**AC11** — Une indisponibilité de l'AI Engine retourne 503. Aucun contournement de frontière de trust n'est introduit.

**AC12** — En cas d'échec de validation grounding après retry (max 1), l'erreur est retournée. Le résultat non-groundé n'est jamais retourné.

**AC13** — L'Angular affiche correctement : état loading, résultat chargé avec findings, état erreur, et absence de findings (liste vide).

**AC14** — L'invocation est explicitement déclenchée par l'utilisateur (bouton). Pas de polling, pas de déclenchement automatique.

**AC15** — La réponse inclut `provenance.contextFreshness`. Si le contexte est STALE, une uncertainty est incluse avec `reason=STALE_CONTEXT`.

---

## Tests — Aperçu

### Backend — unitaires

**`IncidentContextualAnalysisServiceTest`**
- Vérifie la construction du grounding contract (incident data + EngineeringContext profiles corrects)
- Vérifie qu'aucun ValidatableProposal n'est créé
- Vérifie la persistance via AiTask.synthesisSnapshot

**`ActivityIncidentAnalysisControllerTest`**
- AC10 : projet non autorisé → 404
- AC11 : AI Engine indisponible → 503
- AC12 : grounding failure → 500

### Python AI Engine — unitaires

**`test_incident_context_analysis_generation_service.py`**
- Génération structurée avec données minimales (confidence LOW, findings vides)
- Validation grounding : claim sans référence → rejet
- Violation relation type → rejet
- Retry correctif après validation failure (max 1)

### Python AI Engine — évaluation ADR-066

**Scénario** : `incident-context-analysis-v1`
- Fixture : incident `get_engineering_context FAILED × 3`, contexte minimaliste
- Gates déterministes : schema validity, grounding, trust safety, relationship semantics
- Gate humain : relevance et actionability sur vrai incident du projet devlog-ai

### Angular — unitaires

**`activity-incident-analysis.service.spec.ts`**
- POST avec projectId UUID (jamais slug)
- Gestion erreurs : 404, 503, réseau

**`activity-incident-explanation-panel.spec.ts`**
- Rendu état loading
- Rendu résultat : toolContext, findings, likelyCause, confidence
- Rendu état erreur
- Fermeture panneau (émission `close`)
- findings vide → "No relevant context found in project knowledge."

---

## Hors périmètre V1

- MCP adapter pour `analyzeIncident`
- Analyse batch de plusieurs incidents
- Cache ou conversation persistante
- Déclenchement automatique sur nouvel incident
- Profil `engineering-story-v1` dans le contexte d'incident
- Promotion de l'analyse en ValidatableProposal
- Affichage dans la section Cockpit (V1 : Activity uniquement)

---

## Dépendances

| Dépendance | Type | Disponibilité |
|------------|------|---------------|
| `ActivityIncidentService.computeIncidentId` | Existante | Story 0190 ✓ |
| `EngineeringContextFacadeImpl` | Existante | Story 0111 ✓ |
| `AiTask` lifecycle + synthesisSnapshot | Existante | Story 0112 ✓ |
| `IntentCatalog` (nouveau intent) | Extension | Ce Story |
| `IncidentContextAnalysisGenerationService` (Python) | Nouveau | Ce Story |
| `ActivityIncidentAnalysisController` (Java) | Nouveau | Ce Story |
| `ActivityIncidentExplanationPanel` (Angular) | Nouveau | Ce Story |
