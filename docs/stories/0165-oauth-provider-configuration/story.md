# Story 0165 - OAuth Provider Configuration Integration

## Statut

IMPLEMENTATION AUTHORIZED - approved by the human project owner on 2026-10-30.

## Objectif produit

Relier le lifecycle OAuth ChatGPT existant au modele de configuration produit
sans reimplementer OAuth.

## Perimetre IN

- provider `openai_chatgpt`;
- association de credential existante;
- connexion, reconnexion et revocation;
- distinction identite autorisee, usage plan autorise et reauthorization;
- modele recommande;
- projection du runtime state.

## Perimetre OUT

- nouveau protocole OAuth;
- nouvelle identite DevLog;
- acces aux conversations ChatGPT;
- fallback vers API key ou autre provider;
- changement du grounding ou du binding Core.

## Exigences fonctionnelles

1. Une connexion OAuth reussie cree ou met a jour l'association existante.
2. L'identite seule ne produit pas `READY` si l'usage requis n'est pas autorise.
3. Une reauthorization est demandee lorsque les scopes ou tokens ne sont plus
   utilisables.
4. La revocation rend les utilisations futures impossibles.
5. Le modele configure reste explicite et non remplace automatiquement.

## Contraintes architecturales

- Reutiliser Story 0160 et ADR-072.
- Core continue de gouverner l'association et le binding.
- Les tokens restent dans l'AI Engine.

## Exigences de securite

- aucun token dans frontend, Core, logs, traces, snapshots ou callbacks;
- respect des controles `state`, PKCE, nonce et scopes existants;
- revocation fail-closed.

## Criteres d'acceptation

- Le parcours OAuth aboutit a une configuration produit sans exposer le token.
- `REAUTH_REQUIRED` est distinct de `PROVIDER_UNAVAILABLE`.
- La revocation bloque une nouvelle emission de binding.
- Les appels REST, MCP et background utilisent l'association gouvernee.

## Tests attendus

- connexion;
- annulation;
- expiration;
- reauthorization;
- revocation;
- absence de fuite;
- regression Story 0160.

## Dependances

- [ADR-072](../../decisions/ADR-072.md)
- [ADR-073](../../decisions/ADR-073.md)
- [ADR-075](../../decisions/ADR-075.md)
- [Story 0164](../0164-user-ai-configuration-state/story.md)

## Documentation concernee

- guide de connexion OAuth;
- ADR-072;
- documentation d'exploitation existante.
