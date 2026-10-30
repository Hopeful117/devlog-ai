# Story 0164 - User AI Configuration and State

## Statut

IMPLEMENTED - independent review passed; pending human acceptance.

## Objectif produit

Definir et exposer dans Core les preferences IA non secretes et les etats
produit necessaires au parcours Simple et Advanced.

## Perimetre IN

- provider;
- authentication method;
- modele selectionne;
- origine du choix (`RECOMMENDED` ou `EXPLICIT`);
- parametres autorises;
- preference globale par utilisateur;
- etats `NOT_CONFIGURED`, `CONFIGURATION_INCOMPLETE`, `READY`,
  `REAUTH_REQUIRED`, `PROVIDER_UNAVAILABLE`, `MODEL_UNAVAILABLE`,
  `CREDENTIAL_INVALID` et `REVOKED` selon les conventions API retenues.

## Perimetre OUT

- secrets ou tokens;
- override projet;
- binding de tache;
- multi-credentials complet;
- fallback automatique.

## Exigences fonctionnelles

1. Une configuration persistante ne contient aucun secret.
2. Une configuration est globale par utilisateur en V1.
3. `READY` signifie que provider, credential association, modele et checks
   requis sont utilisables au moment de la verification.
4. Un modele indisponible retire `READY`.
5. Le choix explicite d'un modele reste persistant quand la recommandation
   change.
6. Le remplacement d'un modele necessite une action explicite.

## Contraintes architecturales

- Core possede les preferences non secretes.
- Core reste l'autorite de l'association et de l'autorisation.
- La configuration ne constitue pas une autorisation de tache.
- Simple et Advanced utilisent le meme modele interne.

## Exigences de securite

- aucune API key, token, code OAuth ou refresh token en persistance Core;
- acces limite au principal authentifie;
- erreurs non divulgatrices;
- audit sans valeur sensible.

## Criteres d'acceptation

- Un utilisateur sans configuration est distinct d'un utilisateur non autorise.
- Une configuration explicite survit a une mise a jour du catalogue.
- `MODEL_UNAVAILABLE` est visible et non `READY`.
- Une autre identite ne peut pas lire ou modifier la configuration.
- Les parametres non autorises sont refuses.

## Tests attendus

- creation, modification et suppression;
- modele recommande modifie;
- modele explicite indisponible;
- acces inter-principal;
- absence de secret dans la persistance et les reponses.

## Dependances

- [ADR-075](../../decisions/ADR-075.md)
- [Story 0163](../0163-provider-model-catalog/story.md)

## Documentation concernee

- ADR-075;
- contrat de configuration de l'Epic.
