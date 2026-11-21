# DevLog MCP Tools

> **Scope:** outils exposés par le serveur MCP (`mcp-server`). Les Resources sont
> documentées dans `docs/mcp-engineering-context-contract.md` et les stories
> 0088/0090.

## `get_engineering_context`

Construit un contexte borné et justifié pour une tâche d'ingénierie.
Contrat détaillé : `docs/mcp-engineering-context-contract.md`.

```text
arguments : projectSlug*, intent*
retour    : EngineeringContext (project, evidence[], metadata)
```

## `resolve_engineering_story`

Résout la référence externe d'une Story vers l'identité persistée du Core.
Le numéro est qualifié par le projet et l'UUID retournée ne doit pas être
générée ou configurée par un client.

```text
arguments : projectSlug*, storyNumber*
retour    : EngineeringStoryResponse { id, projectId, storyNumber, title,
            storyPath, status, baseCommit, targetCommit }
```

This operation is read-only and does not register missing repository Stories.

## `sync_project_stories`

Déclenche explicitement la synchronisation du catalogue des Engineering Stories
depuis la source repository active. Il s'agit du fallback MCP lorsque la
synchronisation automatique du repository n'a pas encore été exécutée.

```text
arguments : projectSlug*, revision
retour    : StoryCatalogSynchronizationResult
```

La synchronisation automatique et cet outil appellent le même service Core et
sont idempotents sur `(projectId, storyNumber)`.

`sync_project_history` déclenche également cette synchronisation de catalogue
après l'import de l'historique. `sync_project_stories` permet de demander le
catalogue seul lorsque l'import historique n'est pas nécessaire.

## Story Context Agent v1 (canonical)

The canonical runtime route is the versioned asynchronous protocol:

```text
submit_task -> AI Engine callback -> get_snapshot -> result
```

MCP tools:

```text
story_context_agent_get_projection
story_context_agent_submit_task
story_context_agent_get_snapshot_authenticated
story_context_agent_follow_up_authenticated
story_context_agent_prepare_task_binding
```

`story_context_agent_prepare_task_binding` is an evaluation-only preparation
operation. It creates the Core task and credential binding without dispatching
provider work. The MCP runtime owns the technical bearer; callers receive only
the bounded task identity and must provide an idempotency key.

The high-level `devlog_story_agent` consumer orchestrates the same protocol and
must be preferred by runtime validation. It must not call the legacy
`/api/v1/projects/{projectSlug}/story-agent` route.

## `analyze_story_context` (legacy compatibility)

Analyse le contexte d'une Engineering Story pour les phases Discuss/Plan et
retourne le résultat structuré et persisté de l'analyse.

```text
arguments : projectSlug*, storyId*, intent*, question*, files, guidance
retour    : StoryContextAnalysisResult
```

This tool and the legacy REST routes remain compatibility surfaces only. They
are not canonical runtime entry points and must not be selected when the v1
Story Context Agent protocol is available.

## `search_project_history` *(Story 0090)*

Recherche déterministe dans l'**historique déjà importé** par DevLog :
messages de commits (sujet + corps) et chemins de fichiers modifiés.

```text
arguments : projectSlug*      — projet ciblé
            query*            — mots-clés séparés par des espaces
            limit             — optionnel, défaut 20, max 100
retour    : ProjectHistorySearchResult
            { query, totalMatches, truncated,
              results[] : { commitSha, subject, authorName, committedAt,
                            repositoryId, relevance,
                            matches[] { matchedOn: COMMIT_MESSAGE|PATH,
                                        matchedValue },
                            resource } }
```

### Sémantique de recherche

- Tokenisation simple de la requête (termes alphanumériques ≥ 2 caractères) ;
- **ET** logique : un commit ne correspond que si **chaque** terme matche
  quelque part (message ou chemin) — sémantique proche de `git log --grep`;
- matching par contenance insensible à la casse ; « concept » en V1 =
  terme présent dans un message, un chemin, ou égal au nom de fichier sans
  extension. **Ce n'est pas une recherche sémantique** (aucune IA, aucun
  embedding).

### Ranking déterministe

Par terme, le champ le plus fort compte ; la somme classe les résultats :

```text
nom de fichier exact = 30   >   chemin = 20   >   sujet = 15   >   corps du message = 10
```

Départage final : date décroissante puis hash — la récence n'est qu'un
tie-breaker, jamais un facteur principal (un ancien commit très pertinent
devance un commit récent faiblement apparié).

### Navigation

Chaque résultat expose `resource = devlog://projects/{slug}/commits/{sha}` :
l'URI s'utilise telle quelle dans `resources/read` pour obtenir le contexte
déterministe complet du commit (fichiers classifiés, références ADR/roadmap…).

### Ce que le tool ne fait pas

- aucune recherche sémantique/IA/embeddings ;
- pas de suivi de renommages (`git log --follow`) ;
- pas de contenu de diff (passez par la resource commit-context) ;
- pas de filtres booléens/temporels en V1 ;
- limité aux données importées par DevLog pour le projet demandé.

## Découverte des capacités

La resource `devlog://server/info` fournit une vue concise des tools, prompts et
resources opérationnels enregistrés. Les schémas détaillés restent exposés par
les opérations MCP standard de listing.
