# Story 0150 — Evidence-grounded Story Change Briefing

## Périmètre accepté

Fournir un briefing descriptif et non causal d'une Engineering Story à partir
du contexte canonique Core défini par ADR-069. Le slice réutilise la fenêtre
Git (base,target] et le filtrage fail-closed livrés par Story 0149. Chaque
modification exposée porte une référence AI typée ADR-068 (REPOSITORY_EVIDENCE
dans le scope REPOSITORY). Le contrat expose l'identité du contexte et un
digest de projection ainsi qu'un snapshot minimal permettant l'audit.

Le REST et le MCP doivent utiliser le même service backend lorsque le client
MCP existant le permet. Le briefing ne modifie pas le contrat causal SCA v1,
n'ajoute pas de retrieval ni de KnowledgeSelectionService, et ne devient pas
une nouvelle autorité de contexte.

## Limites et fail-closed

Des bornes Git absentes, invalides, inconnues ou une parenté incomplète ne
produisent aucune modification technique ni conclusion causale. Le briefing
retourne alors groundingStatus=NOT_ESTABLISHED avec les avertissements et les
digests du contexte construit. Les snapshots sont des identités d'exécution,
pas des connaissances persistées supplémentaires.
