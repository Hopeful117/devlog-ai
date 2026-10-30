# Story 0162 - Decisions et frontiere documentaire de configuration IA

## Statut

DESIGN MATERIALIZED - implementation not authorized.

## Objectif produit

Materialiser la frontiere documentaire de l'Epic de configuration IA et
confirmer que les Stories fonctionnelles ne deleguent aucune decision
architecturale non resolue a un coding agent.

## Contexte

L'Epic introduit un catalogue, une configuration utilisateur, un runtime state
et un lifecycle d'API key utilisateur. Ces sujets sont couverts par ADR-074,
ADR-075 et ADR-076. ADR-072 et ADR-073 restent `Proposed` alors que Stories
0160 et 0161 sont marquees livrees.

## Perimetre IN

- verifier les liens Epic/ADR/Stories;
- verifier que les ADR-074 a 076 precedent leurs Stories dependantes;
- documenter la reconciliation ADR-072/ADR-073;
- verifier que les statuts restent compatibles avec les conventions du projet;
- conserver les decisions humaines requises comme decisions explicites.

## Perimetre OUT

- changement de statut ADR sans approbation humaine;
- implementation Core, AI Engine ou frontend;
- modification OAuth, credential ou binding;
- validation live d'un provider.

## Contraintes architecturales

- ADR-072 et ADR-073 restent l'autorite existante pour OAuth et bindings tant
  qu'un statut different n'est pas approuve.
- Les nouveaux ADR ne les remplacent pas.
- Les Stories fonctionnelles ne choisissent pas le stockage ou l'autorisation
  des API keys.

## Exigences de securite

- aucun secret dans les artefacts;
- aucune valeur de credential reelle;
- les diagrammes utilisent uniquement des handles abstraits.

## Criteres d'acceptation

1. Les ADR-074, ADR-075 et ADR-076 sont references par l'Epic.
2. Chaque Story fonctionnelle reference ses ADR applicables.
3. L'Epic decrit explicitement que les ADR-072/073 n'ont pas ete acceptes
   automatiquement.
4. L'implementation API key est impossible a planifier sans ADR-076 accepte.
5. Aucun artefact ne contient de secret ou de configuration reelle.

## Tests attendus

- verification des liens relatifs;
- recherche des identifiants ADR et Story;
- recherche de motifs de secrets dans les nouveaux documents.

## Dependances

- [ADR-072](../../decisions/ADR-072.md)
- [ADR-073](../../decisions/ADR-073.md)
- [ADR-074](../../decisions/ADR-074.md)
- [ADR-075](../../decisions/ADR-075.md)
- [ADR-076](../../decisions/ADR-076.md)

## Documentation concernee

- Epic de configuration IA;
- ADR-072 et ADR-073 pour leur reconciliation de statut;
- Stories 0163 a 0170.
