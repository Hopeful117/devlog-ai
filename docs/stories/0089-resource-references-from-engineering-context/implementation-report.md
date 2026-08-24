# Story 0089 — Implementation Report

## Branch

`story/0089-resource-references-from-engineering-context`, créée depuis
`main` @ 4d16138 (contient les Stories 0087+0088 fusionnées ; la branche
story/0088 avait été supprimée après son merge, vérifié avant de démarrer).
Non mergée — laissée pour review.

## Contract changes

`EngineeringEvidence`: champ **additif** `String resource` (nullable) ajouté
en fin de record après `symbols`. Tous les champs 0087 inchangés. Absence =
`null`, état normal (pas warning, pas erreur).

## Resource URI architecture

Source unique : `DevlogResourceUriFactory`
(`devlog-contracts/…/engineeringcontext/`) — classe finale statique, pure
construction de chaînes, sans I/O ni logique métier. Placée dans
devlog-contracts pour être partagée par le backend (mapping) et le mcp-server
(garde-fou) sans dépendance MCP au runtime. Les annotations `@McpResource`
conservent leurs templates littéraux (constantes Java obligatoires) ;
`ResourceUriTemplateSyncTest` (mcp-server, 6 tests par réflexion) garantit
l'égalité factory ⇄ templates — anti-dérive.

Le mapper backend résout le slug depuis `projectContext.project().slug()`
(déjà chargé par la facade → zéro lookup supplémentaire, §9).

## Direct mapping matrix

| Evidence | Identifier (contrat) | Resource | Mapping |
|---|---|---|---|
| DECISION | uuid | decisions/{id} | ✅ direct |
| INSIGHT | uuid | insights/{id} | ✅ direct (ACTIVE-only des deux côtés) |
| ENGINEERING_STORY | uuid | stories/{id} | ✅ direct |
| ENGINEERING_EVENT | uuid | engineering-events/{id} | ✅ direct |
| COMMIT | null (SHA dans référence interne `git:{source}:{sha}`) | commits/{sha} | ✅ Case A (l'evidence EST le commit) |
| CHANGED_FILE | `commit-diff:{path}` | — | ❌ Case B (groupe de diffs agrégé ≠ commit) ; relatedReferences conservent les commits |
| MILESTONE / ARTIFACT / ANALYSIS / FACT / OBSERVATION / CHALLENGE | uuid/path | — | ❌ aucune resource existante |
| Structure repo (MODULE, SOURCE_FILE…) | path/name refs | — | ❌ |

Identifiant invalide/inattendu ou kind inconnu ⇒ `resource = null`
(never throw), testé.

## Git / Diff semantics

**Case A retenu pour COMMIT** : l'evidence représente le commit lui-même ; le
contexte DevLog du même SHA est exactement ce que la resource renvoie. SHA
extrait de façon déterministe de la référence interne (regex 40/64 hex),
normalisé minuscule. **Case B pour CHANGED_FILE** : groupe agrégé multi-commits,
fidélité sémantique priorisée → `resource = null` ; navigation indirecte
possible via `relatedReferences` (documentée, non implémentée — §19).

## Governance

- Insight : les evidences INSIGHT proviennent exclusivement de la requête
  ACTIVE-only (`RepositoryContextAdapter`), même règle que la ressource 0088 →
  une URI n'est émise que pour une connaissance réellement accessible.
  Aucun affaiblissement.
- Isolation projet : l'URI intègre le slug du projet courant ; les resources
  0088 revérifient l'appartenance à la lecture.

## Digest

**Non affecté.** Le SHA-256 est calculé dans `RepositoryContextEngine.digest()`
avant le mapping contrat, sur le résultat interne uniquement ; `resource`
n'existe qu'à la frontière MCP. Les tests moteur inchangés restent verts
(garde implicite). Décision documentée conformément à l'hypothèse privilégiée
du prompt, confirmée par inspection.

## Tests

- backend : `DevlogResourceUriFactoryTest` (4), mapper +3 cas (adressables
  avec URIs exactes / non-adressables null / identifiants invalides sûrs),
  fixture contrôleur + assertion JSON `resource`.
- mcp-server : `ResourceUriTemplateSyncTest` (6), fixture tool + assertion.
- Résultats : backend ciblé 13/13 ; mcp-server **34/34**.

## Quality pipeline

`./backend/mvnw -pl backend -am clean verify -B` → **863 tests, 0 échec**
(+7 vs 0088), couverture JaCoCo OK. Working tree propre hors `target/`
pré-trackés (non stagés).

## Manual MCP validation

Session stdio réelle contre jar buildé + backend reconstruit :

1. `tools/call get_engineering_context(devlog-ai)` → 60 evidences :
   **18 avec resource**, 42 sans (état normal).
2. Parcours prioritaire Decision : evidence `ae47a47d…` → URI
   `devlog://projects/devlog-ai/decisions/ae47a47d…` utilisée **verbatim**
   dans `resources/read` → décision complète (titre, rationale, consequences).
3. Commit : `…/commits/dd0f8a0d…` → commitHash identique, fichiers classifiés.
4. Insight : `…/insights/06d34458…` → insight ACTIVE lu.
5. CHANGED_FILE vérifié `resource: null` avec relatedReferences intactes.

## Documentation

`docs/mcp-engineering-context-contract.md` : champ `resource` documenté +
section « Navigation resource » (règles adressables/non-adressables, optionnalité,
difference resource ↔ relatedReferences).

## Remaining issues

1. La sélection varie selon l'intent : certaines exécutions ne contiennent
   aucune evidence Decision (comportement du moteur, non un défaut de mapping).
2. Rien d'autre observé.

## Suggested next step

**Période d'utilisation réelle du MCP** : les trois surfaces (tool enrichi,
resources, navigation) sont cohérentes et validées ; c'est maintenant l'usage
par des agents réels (OpenCode/Kiko sur des tâches DevLog) qui doit arbitrer
la suite entre `search_project_history`, resources timeline/freshness/relations
ou l'intent→profile dynamique — plutôt qu'une nouvelle capacité speculative.
