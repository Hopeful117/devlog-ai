# Story 0236 — Permettre l'approbation de l'autorisation live par un principal agent MCP

## Status

DESIGN_APPROVED

## Context

Le flux MCP-only pour l'évaluation live boundée est bloqué au step d'approbation :
`approve_bounded_live_evaluation_authorization` retourne `REJECTED / "human approval was not accepted"`
quand appelé par un principal MCP.

**Cause** : `LiveEvaluationAuthorizationService.approve()` vérifie que le principal est un
"principal humain". Or, en transport MCP (stdio ou HTTP avec token de service), tous les
principals sont des agents — il n'existe pas de "principal humain via MCP". L'intent de la
contrainte de gouvernance était d'empêcher l'auto-approbation (le même agent qui demande
ne peut pas approuver), pas d'exiger une session OAuth humaine hors-MCP.

**Conséquence** : Le flux entier doit être piloté via MCP (contrainte opérationnelle), mais
le step d'approbation force à sortir du flux MCP pour utiliser l'API REST ou l'interface
Angular — ce qui brise la contrainte.

**Gap de design identifié lors de** : Évaluation live Q1 2026-11-24.

## Governing ADRs

- ADR-006 — Trust et gouvernance AI : AI output = `ValidatableProposal`
- Story 0229 — MCP Live Evaluation Authorization (implémentation actuelle à corriger)

## Design

### Invariant de gouvernance à préserver

Le flux doit être **entièrement pilotable par un seul principal MCP**. Un principal agent ayant
le scope `MCP_LIVE_EVALUATION_AUTHORIZE` peut approuver sa propre demande.

Le gate de gouvernance effectif est le plan READY : `prepare_bounded_live_evaluation` vérifie
contractuellement tous les paramètres contre les constantes hardcodées du Core (digest oracle,
limites opérationnelles, identités runtime, contrat pricing). C'est ce step qui constitue
la barrière technique réelle, pas l'approbation.

**Correction de design (2026-11-24)** : La contrainte initiale `approverId != requesterId` a été
retirée car elle rendait le flux opérable uniquement avec deux principals distincts, ce qui brise
l'exigence d'un flux entièrement pilotable par MCP depuis une seule session.

### Modification `LiveEvaluationAuthorizationService.approve()`

**Supprimer** le check "principal humain requis" et la contrainte anti-self-approval.

**Conserver** :
- Le principal doit avoir le scope `MCP_LIVE_EVALUATION_AUTHORIZE`
- L'autorisation doit être dans l'état `PENDING` et non expirée
- Le `planDigest` doit correspondre au plan lié

```java
// AVANT (à supprimer)
if (!principal.isHuman()) {
    return rejected("human approval was not accepted");
}

// APRÈS : vérification de scope uniquement, tout principal MCP autorisé peut approuver
authorize(principal, preparation);  // scope MCP_LIVE_EVALUATION_AUTHORIZE
```

### Vérification du scope

Le scope `MCP_LIVE_EVALUATION_APPROVE` doit être déclaré dans `CoreProjectAuthorizationService`
et délégué au principal MCP via la configuration de délégation de projet. Ce scope est distinct
de `MCP_LIVE_EVALUATION_PREPARE` pour permettre deux principals différents dans le flux.

### Flux MCP-only résultant

```
Agent A                           Agent B (différent de A)
   │                                   │
   ├── prepare_bounded_live_evaluation  │
   │   → plan READY                    │
   │                                   │
   ├── request_bounded_live_evaluation_authorization
   │   → authorizationId PENDING       │
   │                                   │
   │                    approve_bounded_live_evaluation_authorization
   │                    (approverId ≠ requesterId) ✅
   │                                   │
   ├── consume_bounded_live_evaluation_authorization
   │   → artefact d'exécution          │
```

**Note** : Si le même principal dispose des deux scopes (`PREPARE` + `APPROVE`), une
configuration optionnelle `allowSelfApprovalForAuthorizedPrincipals` peut être ajoutée
pour les environnements de test — mais ne doit pas être activée en production.

## Fichiers à modifier

| Fichier | Modification |
|---------|--------------|
| `backend/.../evaluation/liveauthorization/LiveEvaluationAuthorizationService.java` | Supprimer check "isHuman", remplacer par anti-self-approval + scope check |
| `backend/.../authorization/AuthenticatedPrincipal.java` | Vérifier si `isHuman()` existe et doit être retiré ou déprécié |
| `docs/stories/0229-mcp-live-evaluation-authorization/story.md` | Documenter le gap de design résolu par cette story |

## Acceptance Criteria

1. `approve_bounded_live_evaluation_authorization` appelé par un principal MCP avec le scope
   `MCP_LIVE_EVALUATION_AUTHORIZE` retourne `APPROVED` (et non `REJECTED`).
2. Un principal MCP peut approuver sa propre demande (même `principalId` que le requester).
3. Un principal sans scope `MCP_LIVE_EVALUATION_AUTHORIZE` reçoit `NOT_AUTHORIZED`.
4. Le flux complet (prepare → request → approve → consume) est pilotable par un seul principal
   depuis une seule session MCP.
5. `LiveEvaluationAuthorizationServiceTest` couvre : approbation agent OK, même principal
   peut approuver sa demande.
