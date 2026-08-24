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

## `echo_message`

Retourne le message reçu inchangé — outil de diagnostic de connectivité.
