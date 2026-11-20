# Story 0212 - Repository Analysis

## Statut

ANALYSIS COMPLETE - implementation not authorized.

Ce document decrit l'etat actuel du depot et les ecarts pertinents pour Story
0212. Il ne constitue pas une autorisation d'implementation.

## Contexte et sources

Sources consultees :

- Story 0172 et son Implementation Report ;
- Story 0174 et son Implementation Report ;
- Story 0211 et son Implementation Plan ;
- ADR-077, ADR-078 et ADR-081 ;
- `tools/devlog_mcp/runtime.py` ;
- `tools/devlog_mcp/status.py` ;
- `tools/devlog_mcp/tests/test_runtime.py` ;
- `mcp-server` et ses tests de mapping d'erreurs ;
- `TOOLS.md`.

Le contexte DevLog n'a pas pu etre interroge dans cet environnement. L'analyse
repose donc sur les artefacts versionnes et l'etat courant du depot. Le rapport
de Story 0174 indique lui aussi un echec d'authentification du Story Agent lors
de son implementation.

## Etat courant

### Runtime Python

`tools/devlog_mcp/runtime.py` contient deja :

- `TokenLease` avec expiration calculee depuis `expires_in` ou le claim JWT
  `exp` ;
- validation d'une duree minimale de lease ;
- renouvellement avant expiration lors du traitement d'une nouvelle requete ;
- remplacement du child Java appartenant a l'instance courante ;
- replay du handshake `initialize` et de `notifications/initialized` ;
- suivi des operations par `operationId`, request id et generation ;
- une allowlist `RETRY_SAFE_TOOLS` ;
- un seul retry pour une erreur transport reconnue ;
- fail-closed lorsque plusieurs operations sont en vol ou lorsque le
  renouvellement echoue ;
- redaction des evenements et separation STDOUT/STDERR.

### Status et diagnostics

`tools/devlog_mcp/status.py` expose l'etat des instances et le dernier
evenement de renouvellement. Le diagnostic est read-only, mais il ne projette
pas encore une taxonomie complete de provenance et de retry telle que definie
par ADR-081.

### Serveur MCP Java

`ResourceSupport` distingue maintenant les erreurs `401` Core par une donnee
structurelle `source=CORE` et `category=CORE_AUTHENTICATION`. Le runtime Python
refuse deja de traiter cette forme comme une erreur transport.

Cette correction doit rester la frontiere : Story 0212 ne doit pas introduire
un second retry dans les adaptateurs Java sans decision d'autorite unique et
budget partage.

## Findings

### F-01 - Budget d'acquisition du token incomplet

`acquire_token` transforme les erreurs HTTP et reseau en echec de renouvellement,
mais aucun retry explicite borne du token endpoint n'est defini. La matrice de
Story 0211 distingue pourtant les erreurs transitoires des erreurs definitives.

Impact : une indisponibilite breve de Keycloak peut terminer le runtime alors
qu'un retry d'acquisition technique serait sans effet metier et pourrait etre
borne sans rejouer l'operation MCP.

### F-02 - Compatibilite historique de classification a decider

`is_transport_auth_failure` accepte encore le couple code/message historique
`-32600` / `Authentication is required` lorsqu'aucune donnee structurelle n'est
presente. Lorsqu'une donnee existe, `source=TRANSPORT` est exige et une source
Core est refusee.

Impact : la compatibilite avec les anciens serveurs est preservee, mais la
provenance n'est pas obligatoire dans tous les cas. L'Implementation Plan doit
decider si cette compatibilite est maintenue, delimitee a une version de
protocole ou supprimee.

### F-03 - Budgets de retry separes mais non formalises

Le code limite le retry d'operation a une tentative et protege le renouvellement
concurrent par `renewal_in_progress`, `renewal_cooldown` et une condition. En
revanche, le budget de reacquisition du token et le budget de rejeu MCP ne sont
pas modelises comme deux politiques observables et independantes.

Impact : une evolution naive pourrait compter un retry Keycloak comme un retry
d'operation, ou inversement, et rendre les diagnostics ambigus.

### F-04 - Concurrence conservative deja acceptee

Lorsque plusieurs operations sont en vol au moment d'une erreur
d'authentification, le runtime refuse le renouvellement, termine l'operation en
`MCP_REAUTH_REQUIRED` et interrompt les autres en
`MCP_INSTANCE_STOPPING`. Ce comportement est explicitement accepte par Story
0172 et couvert par les tests.

Impact : Story 0212 doit conserver cette barriere, sauf decision humaine
explicite. Elle ne doit pas tenter de rejouer selectivement une operation sans
garantie sur l'etat des autres operations.

### F-05 - Renouvellement a la demande uniquement

Le renouvellement est verifie lorsqu'une nouvelle requete arrive. Un child
inactif dont le bearer expire n'est pas redemarre proactivement.

Impact : cette limitation est deja documentee par Story 0174. Story 0212 doit
confirmer qu'elle reste hors perimetre, ou ouvrir une decision distincte ; elle
ne doit pas etre changee implicitement.

### F-06 - Diagnostics incomplets pour la nouvelle matrice

Les evenements de renouvellement existent et `status.py` expose le dernier
evenement, mais les categories timeout, `5xx`, `429`, provenance et budget
operationnel ne sont pas encore uniformement visibles.

Impact : une implementation peut necessiter une extension minimale de
`status.py`, des evenements et de `TOOLS.md`, sans exposer de payload ni de
secret.

## Fichiers concernes probables

### Implementation principale

- `tools/devlog_mcp/runtime.py` : classification, budget de reacquisition,
  backoff, terminalisation et renouvellement ;
- `tools/devlog_mcp/status.py` : projection read-only des diagnostics si le
  contrat retenu l'exige ;
- `tools/devlog_mcp/tests/test_runtime.py` : tests unitaires et concurrents ;
- tests Java MCP existants : verification que les erreurs Core restent
  structurelles et qu'aucun retry Java concurrent n'est ajoute ;
- `TOOLS.md` : documentation operateur et limites.

### Fichiers a ne pas modifier sans justification

- Core authorization et delegation ;
- AI Engine provider credentials ;
- contrats de binding ;
- identite technique et humaine ;
- configuration Keycloak et deploiement.

## Decisions necessaires avant implementation

1. Maintenir ou supprimer la compatibilite avec une erreur transport sans
   `data.source`.
2. Definir le nombre maximal de tentatives d'acquisition du token et son
   backoff.
3. Definir les statuts transitoires du token endpoint ; le traitement de `429`
   doit etre confirme ou reporte.
4. Confirmer que le renouvellement reste a la demande et ne devient pas
   proactif.
5. Confirmer que le runtime Python reste l'unique autorite de retry pour cette
   Story.

## Proposition de strategie de test

1. Tests de classification structurelle avec source transport, Core, delegation,
   provider et source absente.
2. Tests d'acquisition avec timeout, `500`, `502`, `503`, `504`, `429`, erreur
   client et reponse invalide.
3. Tests prouvant que le retry d'acquisition ne rejoue pas l'operation MCP.
4. Tests prouvant qu'un seul retry d'operation est possible apres renouvellement.
5. Tests de concurrence entre renouvellement, stop, ancienne generation et
   operations multiples.
6. Tests de redaction des diagnostics et d'integrite STDIO.
7. Tests de non-regression des comportements Story 0172/0174 et du mapping
   Java Core.

## Conclusion

Le depot contient deja le mecanisme principal de Story 0174 et la correction de
collision Core/transport issue de Story 0211. Story 0212 doit donc etre une
evolution bornee du runtime : formaliser les budgets, traiter les erreurs
transitoires d'acquisition selon une decision explicite, renforcer la
classification structurelle et completer les diagnostics.

Aucune contradiction bloquante avec ADR-081, Story 0172 ou Story 0174 n'a ete
identifiee. Les cinq decisions listees ci-dessus doivent etre tranchees dans
l'Implementation Plan avant toute modification de code.
