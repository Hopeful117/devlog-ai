# MCP Activity — E2E Readiness Report

**Date:** 2026-11-16
**Scope:** Stories 0185–0188 + stabilisation PR #184 + audit PR #186
**HEAD:** `b205a079ba8d70336aeac63592bef445115dc09d`
**Branch:** `main`

---

## 1. État du repository

```
Branch: main
HEAD:   b205a07  fix(mcp-activity): audit Story 0188 — lifecycle reliability (#186)
Status: propre (aucune modification locale ou staged)
Worktrees: aucun actif
```

Commits récents pertinents :

| Commit | Sujet |
|--------|-------|
| b205a07 | fix(mcp-activity): audit Story 0188 — lifecycle reliability (#186) |
| 1fbd3bb | feat(mcp-activity): agent session lifecycle events — Story 0188 (#185) |
| 9e467ff | fix(mcp-activity): stabilize agent session view — pending label, detail race, sort |
| 72a0059 | Merge PR #183 — Story 0187 Agent Session Activity View |
| bf69c54 | feat(mcp-activity): agent session correlation — Story 0186 |

---

## 2. Validation de la chaîne MCP

### Chemin nominal complet

```
Claude Code
  → invoque outil MCP
  → Python runtime capte (relay_stdin / handle_tool_call)
  → emit_tool_event (MCP_TOOL_STARTED)
  → HTTP POST /api/v1/mcp/tool-events (Bearer token Keycloak)
  → McpToolEventController → auth (MCP_TOOL_EVENTS_INGEST)
  → McpToolEventService.ingest() → McpToolEventRepository.insertIfAbsent()
  → PostgreSQL mcp_tool_events (idempotent ON CONFLICT)
  → Réponse MCP → Python capture terminal (MCP_TOOL_RESPONDED / FAILED / INTERRUPTED)
  → HTTP POST /api/v1/mcp/tool-events (terminal event)
  → Angular polling timer(0, intervalMs)
  → GET /api/v1/projects/{id}/mcp-operations → SQL FULL OUTER JOIN
  → GET /api/v1/projects/{id}/agent-sessions → projection + lifecycleStatus
  → Angular render badges / détail
```

### Niveaux de validation par étape

| Étape | Méthode | Résultat |
|-------|---------|---------|
| 1. Capture Python → emit_tool_event | Tests unitaires runtime | ✓ Validé |
| 2. Construction event fields | Tests unitaires runtime | ✓ Validé |
| 3. HTTP POST avec Bearer token | WebMVC + Testcontainers | ✓ Validé |
| 4. Auth actor MCP (401/403/404) | McpToolEventControllerWebMvcTest | ✓ Validé |
| 5. Persistance PostgreSQL idempotente | McpToolEventServiceTest + Testcontainers | ✓ Validé |
| 6. Reconstruction opération (FULL JOIN) | McpOperationPostgresIntegrationTest | ✓ Validé |
| 7. Corrélation session agentSessionId | AgentSessionPostgresIntegrationTest | ✓ Validé |
| 8. API REST avec auth | McpOperationControllerWebMvcTest | ✓ Validé |
| 9. lifecycleStatus (4 états, 8 cas) | AgentSessionServiceTest | ✓ Validé |
| 10. Affichage Angular badges | agent-session-activity-section.spec.ts | ✓ Validé |
| **E2E conditions réelles** | Non exécuté | ⚠ Non validé |

**Suites de tests au HEAD b205a07 :**
- Java (Maven) : 1730 tests — PASS
- Python (pytest) : 38 tests — PASS
- Angular : 317 tests — PASS

---

## 3. Fiabilité des identités

### agentSessionId (CLAUDE_CODE_SESSION_ID)

- Capturé depuis `CLAUDE_CODE_SESSION_ID` au démarrage du runtime Python
- Nullable — opérations sans session exclues de la vue Sessions
- **Fiabilité :** Haute pour Claude Code ≥ V78. Non disponible pour OpenCode ≤ 1.18.11, Claude Desktop non vérifié.

### instanceId

- `secrets.token_hex(16)` au démarrage — stable par instance MCP
- Clé de déduplication avec `operationId` (UNIQUE constraint sur mcp_tool_events)

### operationId

- `secrets.token_hex(16)` par opération, unique dans l'instance
- Identité stable de l'alerte future

---

## 4. Fiabilité du cycle de vie (Story 0188)

### Modèle implémenté

```
ENDED       ← SESSION_LIFECYCLE_ENDED reçu
ACTIVE_TOOL ← activeOperationCount > 0 AND lastObservedAt récent (< 2 min)
IDLE        ← lastObservedAt > 10 min ET pas d'ENDED
UNKNOWN     ← défaut
```

### Ambiguïté sémantique résiduelle

**Signal :** `SESSION_LIFECYCLE_ENDED` est émis dans `relay_stdin` sur `STDIN_CLOSED` (EOF stdin du runtime).

**Ce que prouve STDIN_CLOSED :** le parent (Claude Code) a fermé son pipe stdin vers ce runtime MCP.

**Ce que STDIN_CLOSED ne prouve pas :** que la session d'agent (`CLAUDE_CODE_SESSION_ID`) est terminée.

**Scénario problématique identifié :** Si Claude Code redémarre le runtime MCP pendant une session active :
1. Ancienne instance : STDIN_CLOSED → `SESSION_LIFECYCLE_ENDED` → `lifecycleStatus = ENDED`
2. Nouvelle instance MCP : même `CLAUDE_CODE_SESSION_ID`, nouvelles opérations
3. La session reste `ENDED` malgré l'activité récente

**Mitigation appliquée (PR #186) :** tooltip corrigé — "MCP runtime disconnected — session end inferred". Honnête : inférence, pas certitude.

**Codes d'arrêt et émission lifecycle :**

| Code d'arrêt | Émet SESSION_LIFECYCLE_ENDED ? | Raison |
|-------------|--------------------------------|--------|
| `STDIN_CLOSED` | ✓ Oui | Arrêt propre |
| `SIGNAL_RECEIVED` | ✗ Non | Signal POSIX — arrêt abrupt |
| `PARENT_PROCESS_GONE` | ✗ Non | Parent disparu |
| `TOKEN_RENEWAL_FAILED` | ✗ Non | Échec technique |
| `MCP_PROCESS_FAILED` | ✗ Non | Crash MCP |

**Correction minimale V1 :** Aucune supplémentaire. Dans une Story future : distinguer `RUNTIME_DISCONNECTED` (Python, signal technique) de `SESSION_LIFECYCLE_ENDED` (hook `Stop`, signal certain), et n'utiliser `ENDED` que pour le signal certain.

---

## 5. Événements MCP disponibles pour les alertes

### Types d'événements

| eventType | status | Exploitabilité |
|-----------|--------|---------------|
| `MCP_TOOL_STARTED` | `ACTIVE` | Context — pas d'alerte |
| `MCP_TOOL_RESPONDED` | `RESPONDED` | Pas d'alerte |
| `MCP_TOOL_FAILED` | `FAILED` | ✓ Alerte candidate |
| `MCP_TOOL_INTERRUPTED` | `INTERRUPTED` | ✓ Alerte si reasonCode ≠ MCP_INSTANCE_STOPPING |

### Codes de raison observés dans le code

| reasonCode | Contexte | Alerte ? |
|------------|---------|---------|
| `MCP_INSTANCE_STOPPING` | Opération interrompue à l'arrêt de l'instance | ✗ Interruption attendue |
| `MCP_REAUTH_REQUIRED` | Token renouvelé, opération interrompue | ✓ Incident inattendu |
| `MCP_PROCESS_FAILED` | Processus MCP mort | ✓ Incident |
| `null` | Échec MCP ou réponse terminale | ✓ Si status=FAILED |

### Champs disponibles par opération (McpOperationView)

`instanceId`, `operationId`, `toolName` (nullable), `status`, `generation`, `startedAt`, `completedAt`, `durationMs`, `reasonCode` (nullable), `agentSessionId` (nullable), `agentProvider` (nullable)

---

## 6. Limitations connues

| Limitation | Impact | Mitigation |
|-----------|--------|-----------|
| E2E non validé en réel | Incertitude réseau/Keycloak | Testcontainers couvrent la logique |
| `CLAUDE_CODE_SESSION_ID` absent dans hook `Stop` (non vérifié D4) | Signal lifecycle incertain via hook | Émission auto Python sur STDIN_CLOSED |
| OpenCode / Claude Desktop sans agentSessionId | Sessions non identifiées | Opérations visibles dans All Operations |
| STDIN_CLOSED ≠ fin de session certaine | lifecycleStatus ENDED prématuré possible | Tooltip "inferred" |
| reasonCode null pour vrais échecs MCP | Qualification partielle | toolName + status suffisent |

---

## 7. Verdict

**`READY`** pour Story 0189 — Actionable Activity Alerts.

La chaîne est fonctionnelle et testée. Les données disponibles (`FAILED`, `INTERRUPTED` hors `MCP_INSTANCE_STOPPING`) sont suffisantes pour produire des alertes significatives.

**Condition :** Story 0189 doit exclure `reasonCode = 'MCP_INSTANCE_STOPPING'` des alertes.
