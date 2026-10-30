# Story 0168 - Simple AI Configuration UX

## Statut

PROPOSED - design materialized; implementation not authorized.

## Objectif produit

Permettre a un utilisateur non technique de configurer ChatGPT OAuth ou
Anthropic API key avec un nombre minimal de decisions.

## Perimetre IN

### OAuth

ChatGPT -> connexion -> autorisation -> modele recommande -> test -> `READY`.

### API key

Anthropic -> saisie de cle -> intake securise -> modele recommande -> test ->
`READY`.

### Interface commune

- catalogue;
- statut;
- modele recommande;
- test;
- erreur actionnable;
- reconnexion;
- remplacement explicite du modele.

## Perimetre OUT

- affichage de la cle apres enregistrement;
- variables d'environnement;
- selection de handle;
- parametres Advanced;
- choix arbitraire d'une credential;
- fallback automatique.

## Exigences fonctionnelles

1. L'utilisateur ne doit pas connaitre les noms internes des providers.
2. Les deux methodes convergent vers les memes etats produit.
3. La cle complete n'est jamais reaffichee.
4. Une configuration `MODEL_UNAVAILABLE` n'est pas affichee comme `READY`.
5. DevLog peut recommander un modele mais l'utilisateur confirme le changement.
6. Une erreur indique une action corrective compréhensible.

## Contraintes architecturales

- Le frontend exprime une intention et consomme un statut Core.
- Il ne devient pas l'autorite de credential ou d'autorisation.
- Simple et Advanced utilisent le meme contrat de configuration.

## Exigences de securite

- jamais de secret dans le modele frontend persistant;
- jamais de credential relue apres intake;
- erreurs sans secret;
- revocation et reauthorization deleguees aux capabilities gouvernees.

## Criteres d'acceptation

- Les parcours OAuth et API key sont utilisables sur desktop et mobile.
- Les etats non configure, incomplet, pret, reauthorization et indisponible
  sont distincts.
- Le test reussi affiche `READY` uniquement pour le modele selectionne.
- Une recommandation mise a jour n'ecrase pas le choix explicite.
- Une tentative de reutiliser une credential arbitraire est impossible.

## Tests attendus

- premier lancement;
- OAuth complet;
- API key complete;
- annulation;
- cle invalide;
- modele indisponible;
- reauthorization;
- responsive et accessibilite.

## Dependances

- [Story 0163](../0163-provider-model-catalog/story.md)
- [Story 0164](../0164-user-ai-configuration-state/story.md)
- [Story 0165](../0165-oauth-provider-configuration/story.md)
- [Story 0166](../0166-user-api-key-credential-lifecycle/story.md)
- [Story 0167](../0167-ai-configuration-test/story.md)

## Documentation concernee

- guide utilisateur Simple;
- parcours d'erreur et de reauthorization.
