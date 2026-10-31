# Story 0169 - DevLog Task Configuration Integration

## Statut

IMPLEMENTATION AUTHORIZED - approved by the human project owner after Repository Analysis and Implementation Plan approval on 2026-10-31.

## Objectif produit

Demontrer que les deux configurations `READY` peuvent executer une tache
DevLog en utilisant l'autorisation Core et les bindings existants.

## Perimetre IN

- resolution de la configuration active;
- verification du runtime state;
- emission du binding Core;
- association a une tache;
- validation principal/projet/tache/provider;
- execution ChatGPT OAuth;
- execution Anthropic API key;
- expiration et revocation;
- parite REST, MCP et background.

## Perimetre OUT

- nouvelle autorite dans Python;
- binding global de projet;
- selection par metadata client;
- fallback de credential, provider ou modele;
- modification du grounding ou du contexte.

## Exigences fonctionnelles

1. Une configuration `READY` permet d'emettre un binding conforme.
2. Le binding est limite au principal, projet, tache et provider.
3. Un modele indisponible bloque la soumission.
4. Un binding expire ou revoque bloque l'appel provider.
5. Un mauvais principal, projet, tache ou provider est refuse.
6. Les deux chemins du vertical slice atteignent une tache reelle.
7. REST, MCP et background utilisent la meme decision Core.

## Contraintes architecturales

- Reutiliser `AiCredentialBinding` et `ProviderCredentialResolver`.
- L'AI Engine ne choisit jamais un principal ou une credential a partir de
  metadata arbitraire.
- Core reste l'autorite avant tout appel provider.

## Exigences de securite

- fail-closed sur binding inconnu, expire, revoque ou mismatche;
- aucun secret dans `AiTask`, prompt, callback ou trace;
- isolation inter-principal, inter-projet et inter-tache;
- audit borne et redige.

## Criteres d'acceptation

- Le vertical slice OAuth est prouve de bout en bout.
- Le vertical slice API key Anthropic est prouve de bout en bout.
- Une revocation avant utilisation bloque la tache.
- Une modification de metadata ne contourne pas Core.
- Les sorties restent soumises au cycle existant de validation et de confiance.

## Tests attendus

- parcours OAuth;
- parcours API key;
- mauvais principal;
- mauvais projet;
- mauvaise tache;
- provider mismatch;
- binding expire;
- binding revoque;
- parite REST/MCP/background;
- non-regression Stories 0160/0161.

## Dependances

- [ADR-073](../../decisions/ADR-073.md)
- [Story 0164](../0164-user-ai-configuration-state/story.md)
- [Story 0165](../0165-oauth-provider-configuration/story.md)
- [Story 0166](../0166-user-api-key-credential-lifecycle/story.md)
- [Story 0167](../0167-ai-configuration-test/story.md)
- [Story 0168](../0168-simple-ai-configuration-ux/story.md)

## Documentation concernee

- sequence configuration -> binding -> tache;
- documentation de la gouvernance Core.
