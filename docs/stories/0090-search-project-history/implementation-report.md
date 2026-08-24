# Story 0090 — Implementation Report

## Branch

`story/0090-search-project-history`, créée depuis `main` @ efda19f (0087–0089
mergées). Non mergée — laissée pour review.

## Historical data audit

Voir `repository-analysis.md` : messages (sujet+corps), auteurs, dates,
chemins modifiés (old/new), parents/flags, source repository — **disponibles
et exploitables**. Absents/non retenus en V1 : contenu de diff, symboles
persistés, suivi de renommages, relations ADR/story inverses.

## Tool contract

```text
search_project_history(projectSlug*, query*, limit?)
```

- `limit` optionnel (défaut 20, bornes 1..100 validées côté MCP **et** backend).
- Sortie JSON `ProjectHistorySearchResult{query, totalMatches, truncated,
  results[]}` ; chaque résultat `{commitSha, subject, authorName, committedAt,
  repositoryId, relevance, matches[], resource}` ;
  `matches[] {matchedOn: COMMIT_MESSAGE|PATH, matchedValue ≤120 caractères,
  dédupliqués, ≤8 par commit}`.

## Supported matching

- COMMIT_MESSAGE : sujet + corps du message, contenance insensible à la casse.
- PATH : nouveaux/anciens chemins de fichiers modifiés.
- FILENAME_EXACT (interne au ranking) : terme = nom de fichier sans extension.
- AND logique entre termes (tokenisation alphanumérique ≥2 caractères).

## Concept semantics

En V1 « concept » = **terme littéral** présent dans un message ou un chemin,
ou égal au nom d'un fichier modifié. Exemple validé : `RepositoryContextEngine`
retrouve son introduction et ses évolutions via le chemin/nom de fichier.
Aucune recherche sémantique (documenté explicitement dans `docs/mcp-tools.md`).

## Backend architecture

Option B : nouvelle capability read-only dans le module existant
(`history/service/ProjectHistorySearchService(Impl)` + endpoint
`GET /api/v1/project-history/projects/{projectId}/commits/search`) — la
logique appartient à DevLog, pas à mcp-server ; MCP reste un adapter
(`DevlogResourceClient.searchCommits`). Implémentation sans nouvelle
infrastructure : itération sur la liste projet déjà requêtable, matching en
mémoire, transaction readOnly. Limite documentée : volume O(taille de
l'historique projet) ; optimisation DB (LIKE/JPQL) possible plus tard si le
volume l'exige.

## Ranking

`FILENAME_EXACT=30 > PATH=20 > SUBJECT=15 > MESSAGE=10` — somme des forces
par terme (plus de termes pertinents = meilleur score) ; départages :
committedAt desc puis commitHash asc. Constantes centralisées nommées.
Anti-biais-récence vérifié par test dédié (ancien commit exact-filename devant
commit récent à message seul).

## Resource navigation

Chaque résultat porte `resource = DevlogResourceUriFactory.commit(slug, sha)`
(source unique 0089). Vérifié end-to-end en live : SHA du résultat ⇄
`commitHash` du contexte lu via `resources/read`.

## Real scenario — Markdown

Recherche `markdown preview` → **2 commits dont `4c4180000f22… fix project
note markdown preview`** (le fallback réel de l'évaluation) ; `resources/read`
sur son URI → contexte complet avec les 7 fichiers frontend du fix.
**Le fallback `git log --grep` n'est plus nécessaire.**

## Real scenario — RepositoryContextEngine

Recherche `RepositoryContextEngine` → **9 commits**, dont l'introduction
`aeca570… implement ADR-038 repository context engine` (2026-07-23) et toute
la chaîne d'évolution jusqu'au 2026-08-18. **Le fallback `git log --follow`
n'est plus nécessaire pour la découverte** (le suivi fin de renommage précis
reste une limite documentée).

BEFORE/AFTER :

```text
BEFORE: question → get_engineering_context → insuffisant → git log --grep/--follow
AFTER : question → search_project_history → candidats datés → resources/read
```

## Tests

- Backend : service 10/10 (message partiel/casse, ranking filename-exact,
  ET multi-termes, déduplication chemins, borne+truncation, vide propre,
  URI depuis slug, query/limit invalides, projet inconnu) ; contrôleur 3/3
  (200 + 400 + défaut).
- mcp-server : tool 5/5 (passthrough, limit explicite, requête vide, limit
  invalide, projet inconnu). Suite complète 39/39.

## Quality pipeline

`./backend/mvnw -pl backend -am clean verify -B` → **876 tests, 0 échec**
(+13 vs 0089), JaCoCo OK. Working tree propre hors `target/` pré-trackés.

## Documentation

- Nouveau : `docs/mcp-tools.md` (objectif, paramètres, matching, ranking,
  limites, navigation, non-objectifs).
- Artefacts Story 0090 complets.

## Remaining limitations

1. Recherche en mémoire sur l'historique projet chargé (O(historique)) —
   suffisant aux volumes actuels ; optimisation DB documentée si besoin futur.
2. Pas de suivi de renommages (`--follow`) : les chemins pré/post-rename sont
   stockés mais non chaînés.
3. Les erreurs tool remontent en `isError=true` avec messages métier (codes
   JSON-RPC normalisés par la couche annotations Spring AI — constat 0088).

## Suggested next step

Aucune story automatique. Reprendre une période d'utilisation réelle avec
`search_project_history` intégré au workflow et observer : usages réels du
nouveau tool, frictions restantes (timeline/freshness/relations), avant toute
nouvelle évolution.
