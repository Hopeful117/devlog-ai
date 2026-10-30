# Story 0166 - User API Key Credential Lifecycle

## Statut

IMPLEMENTATION AUTHORIZED - approved by the human project owner on 2026-10-30.

## Objectif produit

Permettre la configuration utilisateur du provider Anthropic par API key sans
faire transiter ou persister la cle dans Core.

## Perimetre IN

- intent d'enregistrement Core;
- signature et verification de l'intent;
- duree courte et usage unique;
- intake de la cle par l'AI Engine;
- chiffrement au repos;
- handle opaque;
- association principal/provider/methode;
- remplacement;
- revocation;
- isolation;
- redaction.

## Perimetre OUT

- modification OAuth;
- stockage dans Core;
- stockage browser;
- multi-credentials complet;
- fallback automatique;
- autre provider API key que le slice Anthropic.

## Exigences fonctionnelles

1. Core emet une intention liee au principal, provider `anthropic`, methode
   API key, expiration et identifiant unique.
2. L'AI Engine verifie l'intent via la confiance de service existante.
3. L'intent est atomiquement consomme et ne peut pas etre rejoue.
4. L'AI Engine chiffre et stocke la cle.
5. Seul un handle opaque et des metadonnees non secretes reviennent vers Core.
6. Core persiste l'association sans la cle.
7. Une seule association active existe par utilisateur et provider en V1.
8. Le remplacement invalide l'ancienne association apres stockage reussi.
9. La revocation bloque les bindings futurs et invalide le mapping AI Engine.
10. Un handle ne suffit jamais a appeler le provider.

## Contraintes architecturales

- Reutiliser le credential store chiffre de l'AI Engine.
- Generaliser l'association existante uniquement si necessaire, sans supprimer
  les metadonnees ChatGPT specifiques.
- Le binding Core reste obligatoire pour l'execution.
- L'intent n'est pas un `AiCredentialBinding`.

## Exigences de securite

- Core ne recoit jamais la valeur de la cle;
- la cle n'est jamais retournee au frontend apres intake;
- aucun secret dans logs, traces, snapshots, prompts, callbacks ou erreurs;
- principal, provider et methode sont verifies;
- expiration, replay et mismatch sont refuses;
- remplacement et revocation sont auditables sans contenu sensible.

## Criteres d'acceptation

- Une cle valide peut etre associee a un principal autorise.
- Une cle invalide produit une erreur bornee.
- Un intent expire ou consomme est refuse.
- Un intent d'un autre principal ou provider est refuse.
- Une ancienne cle ne fonctionne plus apres remplacement/revocation.
- Une tache sans binding valide ne peut pas resoudre la cle.
- Aucun secret n'apparait dans les contrats publics.

## Tests attendus

- enregistrement valide;
- expiration;
- replay;
- mismatch principal/provider/methode;
- remplacement;
- revocation;
- cle invalide;
- isolation inter-principal;
- redaction exhaustive;
- regression du credential store et de Story 0161.

## Dependances

- [ADR-073](../../decisions/ADR-073.md)
- [ADR-076](../../decisions/ADR-076.md)
- [Story 0164](../0164-user-ai-configuration-state/story.md)

## Documentation concernee

- ADR-076;
- guide operateur du secret store;
- guide utilisateur de remplacement/revocation.
