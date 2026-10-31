# Story 0170 - Advanced UX, Security and Documentation

## Statut

IMPLEMENTATION AUTHORIZED - Repository Analysis and Implementation Plan
approved by the human project owner on 2026-10-31.

## Objectif produit

Fournir le mode Advanced et finaliser les exigences de securite, observabilite
et documentation de l'Epic.

## Perimetre IN

- selection explicite du modele;
- parametres autorises;
- diagnostics avances;
- remplacement et revocation;
- retour au modele recommande;
- redaction et audit;
- isolation et erreurs;
- guide utilisateur et operateur;
- diagrammes du lifecycle;
- reconciliation ADR-072/ADR-073.

## Perimetre OUT

- multi-credentials complet en V1;
- parametres arbitraires;
- nouvelle plateforme d'observabilite;
- remediation `.env`;
- changement de statut ADR sans approbation humaine.

## Exigences fonctionnelles

1. Advanced utilise le meme modele que Simple.
2. Les parametres sont valides cote backend.
3. Un choix explicite est preserve comme choix explicite.
4. Un modele indisponible necessite une action explicite.
5. Le retour a la recommandation est une action utilisateur.
6. Les diagnostics avances restent bornes et non sensibles.

## Contraintes architecturales

- Advanced ne contourne aucune decision Core.
- Les bindings restent limites a la tache.
- OAuth et API key conservent leurs lifecycles propres.
- La documentation des ADR-072/073 distingue implementation existante et
  approbation architecturale.

## Exigences de securite

- aucun secret dans logs, traces, snapshots, erreurs ou reponses;
- audit des connexions, tests, remplacements et revocations;
- expiration et revocation fail-closed;
- diagnostics pseudonymises et categorises.

## Criteres d'acceptation

- Advanced permet un modele explicite sans fallback automatique.
- Un modele configure indisponible n'est jamais affiche comme `READY`.
- Les parametres non supportes sont refuses.
- Les actions sensibles sont auditees sans valeur secrete.
- La documentation utilisateur distingue Simple et Advanced.
- La documentation operateur explique le secret store et la revocation.
- La decision humaine requise pour ADR-072/073 est explicitement conservee.

## Tests attendus

- modele explicite;
- parametre invalide;
- retour a la recommandation;
- revocation;
- expiration;
- redaction;
- isolation;
- verification des liens documentaires;
- non-regression du vertical slice.

## Dependances

- [ADR-074](../../decisions/ADR-074.md)
- [ADR-075](../../decisions/ADR-075.md)
- [ADR-076](../../decisions/ADR-076.md)
- [Story 0168](../0168-simple-ai-configuration-ux/story.md)
- [Story 0169](../0169-devlog-task-configuration-integration/story.md)

## Documentation concernee

- guide utilisateur Simple/Advanced;
- guide operateur;
- ADR-072;
- ADR-073;
- Epic et Stories de configuration IA.
