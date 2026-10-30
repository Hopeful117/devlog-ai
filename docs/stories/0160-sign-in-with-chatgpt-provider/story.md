# Story 0160 - Integrer Sign in with ChatGPT comme provider utilisateur

## Statut

CLOSED - implemented, validated, and delivered.

## Objectif produit

Permettre a un utilisateur de connecter son compte ChatGPT a DevLog AI et,
avec son consentement explicite, d'utiliser un plan ChatGPT eligible pour les
appels de generation du DevLog AI Engine. L'integration doit rester un choix
explicite de credentials et ne doit modifier ni l'autorite Java/Core, ni le
grounding, ni la gouvernance des sorties AI.

## Dependances et autorite

- ADR-006 - gouvernance des propositions et promotion de connaissance.
- ADR-067 - separation Java/Core et Python AI Engine.
- ADR-068 - references AI typees et grounding.
- ADR-069 - contexte canonique et projections non-elargissantes.
- ADR-070 - frontiere principal/projet.
- ADR-071 - lecture autorisee des snapshots.
- ADR-072 - Sign in with ChatGPT pour usage AI autorise par l'utilisateur.

## Perimetre

- Ajouter un mode provider `openai_chatgpt` distinct du provider `openai` par
  cle API.
- Suivre le flux OpenAI Open-Source Sign in with ChatGPT avec
  `dynamic_agent_client` lors de la premiere inscription, puis persister le
  `client_id` attribue par compte pour les connexions et refresh ulterieurs.
- Implementer le demarrage OAuth avec `state`, PKCE et `nonce`.
- Implementer un callback qui valide la transaction avant tout echange de code.
- Echanger le code, verifier les scopes effectivement accordes et associer la
  session a l'identite OpenAI minimale necessaire.
- Ajouter une abstraction de stockage de credentials sans persistance
  plaintext dans le repository, les logs ou les snapshots AI.
- Rafraichir un access token expire lorsqu'un refresh token valide existe;
  sinon retourner un etat demandant une reautorisation.
- Appeler `POST https://api.openai.com/v1/responses` avec `store=false`,
  `stream=true` et le bearer token utilisateur.
- Convertir les evenements de stream vers le contrat de generation existant et
  exiger `response.completed` avant de declarer le provider reussi.
- Propager les erreurs provider de facon bornee et deterministe au cycle de
  tache existant.
- Ajouter des tests unitaires, contractuels et d'integration avec endpoints
  OpenAI mockes, sans secret reel.
- Documenter la configuration des redirect URIs, scopes et secret store pour
  les environnements local, test et production.

## Hors perimetre

- OAuth du serveur MCP distant ou transport Streamable HTTP public.
- Acces aux conversations ChatGPT ou aux endpoints `backend-api`.
- Creation d'un fournisseur d'identite interne ou d'un modele multi-tenant.
- Fallback automatique entre credentials utilisateur et cle API.
- Modification de la construction de contexte, du ranking, du grounding ou
  de la validation Core.
- Promotion automatique d'une sortie AI en connaissance trustee.
- UI complete de gestion de compte; le premier slice peut exposer un flux
  backend documente et une integration minimale de session.
- Gestion de facturation ou garantie d'eligibilite au plan ChatGPT.

## Invariants

1. Une autorisation d'identite ne suffit pas a activer l'usage du plan ChatGPT.
2. Les scopes effectivement accordes sont verifies; les scopes demandes ne
   sont jamais consideres comme acquis.
3. `state`, PKCE, `nonce`, redirect URI et session browser sont verifies avant
   l'echange du code; une transaction est a usage unique et expire rapidement.
4. Aucun access token, refresh token, code OAuth, ID token, prompt ou contenu
   repository n'apparait dans les logs, callbacks Core, MCP ou snapshots.
5. Les tokens utilisateur restent dans la frontiere AI Engine/provider et sont
   transmis uniquement a l'API Responses publique.
6. Chaque appel ChatGPT-plan utilise `store=false` et `stream=true`.
7. Une reponse n'est reussie qu'apres `response.completed`; un stream interrompu
   ou `response.failed` est un echec explicite.
8. Une erreur de refresh demande une reauthorization et ne declenche aucun
   fallback implicite vers une autre credential.
9. Le contexte recu par le provider est exactement celui autorise par Core; le
   provider ne construit ni n'elargit aucun contexte.
10. Les sorties restent non trustees et suivent le callback/validation Core
    existant.

## Contrat fonctionnel propose

Le flux de connexion expose au minimum:

- `GET /api/v1/auth/chatgpt/start` : cree une transaction et redirige vers
  OpenAI;
- `GET /api/v1/auth/chatgpt/callback` : valide la transaction et echange le
  code;
- `POST /api/v1/auth/chatgpt/disconnect` : supprime les credentials locaux;
- un etat de session indiquant separement `IDENTITY_AUTHORIZED`,
  `PLAN_USAGE_AUTHORIZED`, `REAUTH_REQUIRED` et `DISCONNECTED`.

Les chemins exacts peuvent etre adaptes aux conventions existantes avant le
plan d'implementation. Le provider de generation recoit une reference de
credential interne non sensible, jamais le token dans le contrat de tache.

## Criteres d'acceptation proposes

1. Une transaction OAuth genere un `state`, un verifier PKCE et un `nonce`
   uniques, lies a une session browser et expires apres la duree configuree.
2. Un callback sans transaction, expire, reutilise ou avec `state` divergent est
   refuse sans echange de code.
3. Une premiere inscription utilise `dynamic_agent_client`, conserve le
   `client_id` attribue par OpenAI et rejette un callback qui ne le retourne pas.
4. Un code valide est echange avec le redirect URI original et le verifier
   PKCE; les scopes effectivement accordes sont persistés sans secret en clair.
5. L'identite seule n'active pas le provider ChatGPT-plan; l'absence de
   `resource.invoke` et `chatgpt.tokens.use.direct` produit `REAUTH_REQUIRED`.
6. Un token valide produit un appel Responses avec `store=false`, `stream=true`
   et le bearer token approprie; aucun endpoint prive ChatGPT n'est appele.
7. `response.completed` produit le resultat provider attendu; `response.failed`,
   `response.incomplete`, timeout et stream interrompu produisent un echec
   borne sans faux succes.
8. Un access token expire est rafraichi une seule fois avec le credential
   associe; un refresh invalide invalide la session et demande une
   reauthorization sans fallback silencieux.
9. Les appels provider restent compatibles avec les contrats existants Core,
   callback, grounding et non-trust; aucun token n'est persiste dans `AiTask`.
10. Disconnect supprime les credentials locaux et rend les appels ulterieurs
   impossibles jusqu'a une nouvelle autorisation.
11. Les tests prouvent l'absence de fuite de code, token, prompt et contenu
    repository dans les logs, erreurs et traces.
12. Les tests existants du provider OpenAI par cle API restent verts et le mode
    `openai` ne change pas de comportement.
13. La documentation d'exploitation couvre les redirect URIs, scopes, secret
    store, rotation et limites d'eligibilite.

## Decoupage d'implementation propose

1. Contrat de configuration, session OAuth et stockage abstrait des credentials.
2. Transaction PKCE/state/nonce, callback et validation des scopes.
3. Provider Responses stream avec credential utilisateur et mapping d'erreurs.
4. Integration avec le cycle de tache existant sans changement de grounding.
5. Tests unitaires, wire, integration mock et tests de non-divulgation.
6. Documentation de deploiement et procedure de reauthorization/disconnect.
7. Validation live optionnelle uniquement apres configuration humaine des
   credentials et redirect URIs, jamais avec un secret commite.

## Decisions a confirmer avant implementation

1. Le secret store concret pour les refresh tokens en local et production.
2. Le mecanisme de session browser compatible avec le deploiement Docker
   actuel.
3. La duree exacte des transactions OAuth et la politique de retention de
   l'identite locale.
4. Le modele choisi pour associer une credential utilisateur au principal
   Java/Core sans envoyer le token au Core.
5. Le modele OpenAI eligible par defaut et la configuration permettant de le
   remplacer sans code.

## Regle de gouvernance

Cette Story n'autorise pas encore l'implementation. L'approbation de l'ADR-072,
du Repository Analysis puis de l'Implementation Plan est requise avant toute
modification de code de production. Aucune validation live ne doit etre lancee
avant configuration humaine des credentials et de la redirect URI.

## Reconciliation documentaire

Le statut `CLOSED - implemented, validated, and delivered` constate la
livraison de la Story dans le repository. Il ne constitue pas a lui seul une
approbation humaine de l'ADR-072, dont le statut `Proposed` reste donc
inchangé jusqu'a une decision architecturale explicite.

## References

- [ADR-006](../../decisions/ADR-006.md)
- [ADR-067](../../decisions/ADR-067.md)
- [ADR-068](../../decisions/ADR-068.md)
- [ADR-069](../../decisions/ADR-069.md)
- [ADR-070](../../decisions/ADR-070.md)
- [ADR-071](../../decisions/ADR-071.md)
- [ADR-072](../../decisions/ADR-072.md)
- [OpenAI Sign in with ChatGPT](https://developers.openai.com/siwc/quickstart)
- [OpenAI token sharing](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)
