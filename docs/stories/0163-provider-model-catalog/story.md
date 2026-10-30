# Story 0163 - Provider and Model Catalog

## Statut

IMPLEMENTED - pending human acceptance.

## Objectif produit

Fournir un catalogue produit controle par DevLog pour presenter les providers,
leurs methodes d'authentification, leurs modeles connus et leur modele
recommande.

## Perimetre IN

- providers `mock`, `openai`, `openai_chatgpt`, `gemini`, `anthropic` et
  `openrouter`;
- methods OAuth, API key et aucune authentification;
- modeles connus scopes par provider;
- modele recommande;
- capacites pertinentes;
- statut stable/experimental;
- version du catalogue;
- etat declaratif de disponibilite.

## Perimetre OUT

- decouverte dynamique complete en V1;
- stockage de credentials;
- autorisation principal/projet/tache;
- scoring de modeles;
- fallback automatique de modele ou provider.

## Exigences fonctionnelles

1. `openai_chatgpt` expose OAuth.
2. `anthropic` expose API key.
3. `mock` expose l'absence d'authentification.
4. Un provider peut declarer plusieurs methodes.
5. Le modele recommande est distinct du modele explicitement choisi.
6. Un modele indisponible est representable sans suppression de la
   configuration existante.
7. Les modeles sont identifies dans le namespace de leur provider.

## Contraintes architecturales

- Le catalogue n'est pas une preuve d'autorisation.
- Le catalogue ne choisit aucune credential.
- `LlmProvider` reste l'abstraction d'execution.
- Une future runtime discovery peut enrichir le catalogue sans devenir
  autoritaire ni modifier une configuration existante.

## Exigences de securite

- aucun secret, token ou credential;
- aucune information d'identite utilisateur;
- les etats runtime ne doivent pas exposer de details sensibles du provider.

## Criteres d'acceptation

- Les six providers supportes sont representables.
- Le vertical slice Anthropic et ChatGPT est representable sans extension
  provider-specific du contrat produit.
- Une mise a jour de recommandation ne modifie pas un choix explicite.
- La suppression ou indisponibilite d'un modele est explicite.
- Une version de catalogue est identifiable.

## Tests attendus

- validation du catalogue;
- provider inconnu;
- modele inconnu;
- recommandation indisponible;
- compatibilite de version;
- absence de secret dans la projection.

## Dependances

- [ADR-074](../../decisions/ADR-074.md)
- [Story 0162](../0162-ai-provider-configuration-decisions/story.md)

## Documentation concernee

- [Epic](../../epics/simplify-ai-provider-model-configuration/epic.md)
- ADR-074.
