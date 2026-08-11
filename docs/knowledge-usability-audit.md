# DevLog AI — Audit de la capacité (memory/usability) du corpus de connaissance

- **Date** : 2026-08-12
- **Nature** : audit lecture seule — aucun fichier de code, ADR, DTO ou Story modifié
- **Question fondatrice** : *DevLog a-t-il une vraie mémoire technique, ou seulement des traces structurées ?*
- **Méthode** : 4 explorations parallèles + analyses ciblées des composants clés (pipeline, relations, insights, promotions, frontend)

---

## Résumé exécutif

La chaîne de savoir de DevLog AI est un **pipeline asynchrone en deux temps** qui part des événements métier (stories, commits, décisions) et converge vers des **Proposals** promues en **Insights**. Le corpus est correctement *structuré* (typé, daté, sourcé), mais sous-utilisé en *mémoire* : les informations qui enrichiraient la capacité de raisonnement machine (rationale, confidence, preuve, décision) sont **perdues ou non capturées** à la promotion, et la surface de lecture offerte à l'utilisateur est **déconnectée** (événements verrouillés en base, relations trop fines, instantané de profil incomplet).

Scores d'évaluation :
- **Capacité humaine** : 2/5 — la vue « Knowledge » du frontend n'est qu'une liste d'Insights (sans rationale/evidence), dépourvue des décisions, des faits acquis et du contexte relationnel.
- **Capacité machine** : 3/5 — hiérarchie Insight→Field→Proposal→Event cohérente en aval, mais la richesse du carrefour ne remonte pas ; `ValidatableProposal.payload` est le point de bascule et il est écrémé.

---

## 1. Pipeline asynchrone en deux temps

### Temps 1 — Acquisition (auto, produit du workflow d'analyse)
- Workflow déclenché par les événements ; produit un `AiProposalResult` riche (**outputContract**).
- Marqueurs à l'acquisition : `VALIDATED`, `EXPERIMENTAL`, `GENERATED-BASED-ON`, `GENERATED`.
- La richesse réside dans `ValidatableProposal.payload` (candidat au carrefour).

### Temps 2 — Promotion (conversion proposition → connaissance durable)
- Sélection filtrée + construction maîtrisée des `Field` (lignes de connaissance réutilisables).
- Les **Insights** sont le produit fini qui remonte vers l'utilisateur.
- Hiérarchie : `Insight` → `Field` → `Proposal` → `Event` (mise en correspondance via lien vers l'événement source).

---

## 2. Points de friction identifiés

### P1 — Perte rationale / confidence / evidence à la promotion
- `InsightPromotionService` : reconstruction de types à granularité réduite (**`toDomainType` 8→3 valeurs** selon la source).
- Des métadonnées comme `confidence` et `evidence` existent dans la source (`AiProposalResult`/`payload`) mais **ne sont pas conservées** sur l'Insight final.

### P2 — `ValidatableProposal.decidedAt` jamais rempli
- Le champ de décision n'est **pas peuplé** → aucune information de validation/timing de décision rejouable.

### P3 — `Validation.insightSeverity` non persistée
- La sévérité attribuée lors d'une validation n'est pas conservée (Informational/Low/High persistés ailleurs mais pas sur cette entité).

### P4 — Abandon du diff ligne-à-ligne
- Les diffs détaillés (avant/après) ne sont **pas stockés en base** → la preuve de l'évolution est perdue.

### P5 — Événements verrouillés / déconnectés côté lecture
- `KnowledgeEvent` manuel, présent hors workflow, **déconnecté de la chaîne** Insight∘Field∘Proposal.
- Le pipeline **UI→API knowledge est déconnecté** : l'événement n'alimente pas la projection de lecture de la page.
- L'utilisateur ne peut pas « remonter » à un événement source depuis un Insight.

### P6 — Relations trop fines
- `KnowledgeRelation.EntityType` n'inclut **ni STORY, ni COMMIT, ni FACT** (seulement des types restrictifs).
- Le plus gros volume de relations cible les Proposals ; une seule passe par KnowledgeEvent.

---

## 3. Facteurs aggravants

- **Décorrélation des granularités** : l'intelligence travaille à l'échelle du commit/proposal et de l'effort ; l'acquisition est instantanée mais la projection de lecture reste cohérente sur des périmètres plus larges (projet).
- **Source trop éphémère** : la perte de vues (rebasage/force-push) supprime des KnowledgeEvents référencés.
- **Profil projet incomplet** : `ProjectProfileSnapshot` consolidé relu/généré en aval, mais **pas retraduit en décisions/faits** pour l'utilisateur.

---

## 4. Synthèse par composant

| Composant | Rôle | Usability | Constat |
|---|---|---|---|
| `AnalysisWorkflowServiceImpl` | Déclencheur temps 1 | OK | Produit un `AiProposalResult` riche (outputContract) |
| `AiProposalResult.payload` | Candidat au carrefour | discontinu | Riche mais jamais persisté tel quel |
| `InsightPromotionService` | Promotion temps 2 | limitée | Perd rationale/confidence/evidence ; `toDomainType` 8→3 |
| `ValidatableProposal` | Carrefour | incomplet | `decidedAt` jamais rempli |
| `KnowledgeEvent` | Saisie manuelle | déconnectée | Hors workflow, non reliée à la chaîne |
| `KnowledgeRelation` | Liens | trop fins | `EntityType` exclut STORY/COMMIT/FACT |
| `Insight` | Produit fini | ✓ | Seul artefact exposé à l'utilisateur |
| Page « Knowledge » frontend | Lecture | 2/5 | Simple liste d'Insights, sans rationale/evidence |

---

## 5. Pistes de remédiation (Direction B + C)

Corriger en priorité le point d'étranglement de la promotion, puis exposer la connaissance :

1. **B — Riche :** conserver `confidence`, `evidence` et le lien à la source lors du passage en Insight (promotion non destructive), supprimer l'abus `toDomainType`, peupler `decidedAt`, allouer des types à STORY/COMMIT/FACT.
2. **C — Utilisable :** faire remonter la vraie richesse dans la projection Overview / Timeline (déjà en place côté timeline), puis dans la page Knowledge.
3. **Slice vertical minimal recommandé pour Story 0035 : « Richer Validated Knowledge »** — commencer par la perte rationale/confidence/evidence à la promotion, puis la projection de lecture, puis le frontend.

Base de décision à respecter (ADR) : la séparation evidence / knowledge / proposal posée dans `ADR-006, ADR-016, ADR-035, ADR-028, ADR-036` et les choix `ADR-040, ADR-046, ADR-047, ADR-048`.

---

## 6. Prochaines étapes possibles

- Démarrer **Story 0035** sur la ligne recommandée (promotion richers, puis projection, puis frontend).
- Probable **nouvel ADR** (ou extension de `ADR-047`/`ADR-040`) à rédiger avant toute implémentation.

*Document d'audit, lecture seule — aucune modification apportée au dépôt.*