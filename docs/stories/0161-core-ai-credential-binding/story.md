# Story 0161 - Lier de maniere autorisee un principal, une tache et une credential AI

## Statut

CLOSED - implemented, validated, and delivered.

## Objectif produit

Permettre a Java/Core d’autoriser une credential AI utilisateur pour une tache
precise sans recevoir ni stocker le token OAuth. L’AI Engine doit pouvoir
resoudre une reference opaque valide, mais ne doit jamais choisir une
credential a partir d’un slug, d’un task ID ou de metadata controlees par le
caller.

## Dependances et autorite

- ADR-006 - sorties AI non trustees.
- ADR-067 - Java/Core autorite du scope et de l’autorisation.
- ADR-070 - principal et autorisation projet.
- ADR-072 - Sign in with ChatGPT et isolation des tokens.
- ADR-073 - binding Core principal/tache/credential.
- Story 0160 - provider Sign in with ChatGPT.

## Perimetre

- Definir le modele Core non secret `AiCredentialBinding`.
- Definir la machine d’etats minimale: `ACTIVE`, `REVOKED`, `EXPIRED`.
- Definir les regles d’emission, rotation, revocation et expiration.
- Ajouter l’autorisation Core avant l’emission d’un binding.
- Ajouter un protocole de binding Core -> AI Engine authentifie par service,
  independant du token ChatGPT.
- Associer exactement un binding autorise a une tache et un provider.
- Ajouter la validation AI Engine avant toute resolution de token ou appel
  provider.
- Preserver les snapshots, prompts, callbacks et traces sans token ni secret.
- Ajouter les tests d’isolement inter-principal, inter-projet, inter-tache et
  provider mismatch.

## Hors perimetre

- Implementation OAuth Sign in with ChatGPT elle-meme.
- Choix global de l’identity provider DevLog.
- Stockage du token OpenAI dans Core.
- Delegation generique, service-account impersonation ou multi-tenant billing.
- Modification du grounding, du contexte canonique ou de la promotion trustee.
- Support MCP public distant au-dela de l’utilisation de la meme capacite Core.

## Invariants

1. Core est le seul emetteur et revocateur d’un binding.
2. Un binding contient uniquement des identifiants et metadonnees non secrets.
3. Un binding est limite a un principal, un provider et une tache/capacite.
4. L’AI Engine refuse tout binding inconnu, expire, revoque ou hors scope.
5. L’AI Engine ne recoit jamais le principal comme preuve provenant d’un champ
   de prompt ou de metadata arbitraire.
6. Une credential ChatGPT ne peut pas etre utilisee pour une tache d’un autre
   principal ou projet.
7. L’authentification de service Core-AI ne devient jamais une autorisation
   utilisateur ou projet.
8. Aucun token n’apparait dans les contrats, logs, snapshots, callbacks ou
   traces.
9. La revocation bloque les utilisations futures sans reecrire l’historique.
10. Les chemins REST, MCP et background utilisent la meme decision Core.

## Contrat propose

Core:

- capability interne `issueAiCredentialBinding(principal, task, provider)`;
- capability interne `revokeAiCredentialBinding(principal, bindingId)`;
- validation `authorizeAiCredentialBinding(taskId, bindingId, provider)`;
- persistence des metadonnees non secretes avec expiration et statut.

AI Engine:

- endpoint interne authentifie de registration/association du `bindingId` avec
  une credential locale deja obtenue par OAuth;
- resolver interne `resolve(bindingId, taskId, provider)`;
- invalidation locale sur revocation ou echec de refresh.

Les noms exacts d’endpoint et de DTO sont a fixer dans le plan. Un binding ID
fourni par un client public n’est jamais suffisant sans validation Core.

## Criteres d’acceptation proposes

1. Un principal autorise obtient un binding limite au provider et a la tache.
2. Un principal non autorise, un autre projet ou une autre tache ne peut pas
   utiliser le binding.
3. Un binding expire ou revoque est refuse avant tout appel provider.
4. Un binding fourni dans des metadata arbitraires ne contourne pas Core.
5. L’echange Core-AI refuse une authentification de service absente, invalide
   ou non conforme.
6. Le Core ne persiste ni ne recoit aucun access token, refresh token, code ou
   secret OpenAI.
7. Les snapshots `AiTask`, prompts, callbacks et traces ne contiennent pas de
   token ni de secret.
8. Les retries idempotents ne creent pas plusieurs bindings actifs pour la
   meme tache sans decision explicite.
9. La revocation est observable avec des logs rediges et ne revele pas de
   contenu sensible.
10. Les suites de regression des Stories 0153 a 0160 restent vertes, avec
    Story 0160 encore inactive tant que son OAuth provider n’est pas branche.

## Decisions a confirmer avant implementation

1. Mecanisme d’authentification de service Core-AI: mTLS ou credential de
   service rotatif gere hors repository.
2. Point d’autorisation utilisateur qui fournit le principal a Core.
3. Duree maximale d’un binding et politique de renouvellement.
4. Strategie d’idempotence et de concurrence pour l’emission/revocation.
5. Contrat exact de registration entre AI Engine et Core.

## Regle de gouvernance

Cette Story n’autorise ni l’usage d’un token OAuth par Core, ni une delegation
implicite par metadata. Story 0160 ne peut pas etre declaree complete tant que
la liaison securisee definie ici n’est pas implementee et testee.

## References

- [ADR-006](../../decisions/ADR-006.md)
- [ADR-067](../../decisions/ADR-067.md)
- [ADR-070](../../decisions/ADR-070.md)
- [ADR-072](../../decisions/ADR-072.md)
- [ADR-073](../../decisions/ADR-073.md)
- [Story 0160](../0160-sign-in-with-chatgpt-provider/story.md)
