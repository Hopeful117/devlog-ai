# Story 0149 — Projection CHANGES déterministe sur fenêtre Git

## Objectif

Projeter un contexte d'Engineering Story borné sur la fenêtre Git (base,target], à partir des commits persistés, de leurs parents et des fichiers modifiés. La chaîne est ProjectContextProvider → RepositoryContextAdapter → AgentContextProjectionService.

## Critères d'acceptation

- Les bornes absentes, identiques, inconnues ou inatteignables produisent un contexte sans preuve technique; aucune inférence implicite n'est autorisée.
- Une parenté incomplète, ambiguë ou incohérente invalide la projection (fail-closed), y compris pour les commits de merge.
- Les relations CHANGES sont déterministes, triées et limitées par les budgets existants; les ajouts, modifications, suppressions, renommages et chemins invalides sont traités sans fuite hors fenêtre.
- Les preuves techniques de l'adapter ne contiennent que les commits de la fenêtre; les données non techniques restent disponibles selon le contrat de contexte.
- La projection agent conserve son digest et ses avertissements de budget; elle ne réintroduit pas de données supprimées lors de la compaction.
- Les tests unitaires ciblent fenêtre, bornes, parentage incomplet, renommage/suppression et budget.

## Limites

La projection dépend de l'historique Git importé et ne répare pas une base incomplète. Aucun push ni publication n'est inclus.
