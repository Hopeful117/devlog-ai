# Story 0167 - AI Configuration Test

## Statut

PROPOSED - design materialized; implementation not authorized.

## Objectif produit

Fournir un test borne qui verifie qu'une configuration OAuth ou API key est
utilisable avec le provider et le modele selectionnes.

## Perimetre IN

- validation de configuration;
- association et credential;
- accessibilite provider;
- disponibilite modele;
- compatibilite minimale avec DevLog;
- resultat utilisateur Simple;
- diagnostic technique borne Advanced.

## Perimetre OUT

- contenu repository;
- contenu utilisateur arbitraire;
- benchmark;
- evaluation de qualite;
- consommation significative;
- autorisation permanente;
- changement automatique du modele.

## Exigences fonctionnelles

1. Le test fonctionne pour `openai_chatgpt` et `anthropic`.
2. Il n'envoie aucun contexte repository.
3. Il utilise le modele configure et ne choisit pas un autre modele.
4. Il distingue au minimum credential, provider et modele.
5. Un succes contribue a l'etat `READY` sans devenir une autorisation.
6. Un echec ne remplace ni provider ni modele.
7. Le cout, la taille et la duree sont bornes.

## Contraintes architecturales

- Le test doit utiliser les memes controles de credential et binding que
  l'execution reelle lorsque cela est necessaire.
- Le test ne doit pas contourner Core.

## Exigences de securite

- pas de secret dans le resultat;
- pas de prompt repository;
- erreurs redigees et categories stables;
- pas de persistance inutile du contenu de test.

## Criteres d'acceptation

- Une configuration OAuth valide produit un resultat positif.
- Une API key Anthropic valide produit un resultat positif.
- Une credential invalide produit une erreur explicite.
- Un modele indisponible produit `MODEL_UNAVAILABLE`.
- Un provider inaccessible produit une erreur distincte.
- Un timeout est borne et ne laisse pas la configuration faussement `READY`.

## Tests attendus

- OAuth valide;
- API key valide;
- credential absente/invalide;
- provider indisponible;
- modele indisponible;
- timeout;
- absence de secret et de contexte repository.

## Dependances

- [ADR-074](../../decisions/ADR-074.md)
- [ADR-075](../../decisions/ADR-075.md)
- [Story 0165](../0165-oauth-provider-configuration/story.md)
- [Story 0166](../0166-user-api-key-credential-lifecycle/story.md)

## Documentation concernee

- matrice des etats;
- guide de diagnostic.
