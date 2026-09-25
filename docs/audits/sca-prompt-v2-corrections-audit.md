# Rapport d’audit — Corrections de la projection SCA / prompt v2

## Statut

- **Périmètre :** corrections de production et de tests de la projection du Story Context Agent (SCA).
- **Version auditée :** `sca-prompt-projection-v2`.
- **Verdict Reviewer :** `PASS`.
- **Date :** 2026-09-25.

## Findings Reviewer traités

Les corrections traitent les écarts relevés sur la cohérence du contrat SCA/prompt v2 :

1. la projection devait rester dérivée du `CanonicalEngineeringContext`, sans reconstruire un contexte ni conserver de repli legacy ;
2. le digest de projection devait couvrir la projection et le contrat de grounding, avec une sérialisation canonique ;
3. la version, le digest et la politique devaient être propagés de façon cohérente dans `PromptRequest` et dans le snapshot `AiTask` ;
4. les tests devaient verrouiller ces invariants et le comportement fail-closed.

## Corrections appliquées

### Production

- La version publique de projection est `sca-prompt-projection-v2` et est réutilisée par la projection et le use case.
- `AnalyzeStoryContextUseCase` utilise le `CanonicalEngineeringContext` fourni par la façade. Un contexte canonique absent provoque désormais une erreur explicite ; aucun fallback vers l’ancien `EngineeringContext` n’est conservé.
- Les dépendances mortes de sélection/projection legacy et la reconstruction de contexte ont été retirées du use case.
- Le `projectionDigest` est calculé sur une enveloppe versionnée contenant `selectedKnowledge` et `groundingContract`, après canonicalisation JSON. L’ordre des clés de map ne modifie donc pas le digest, tandis qu’une modification du grounding ou des connaissances le modifie.
- `PromptRequest.metadata` reçoit le `contextDigest`, le `projectionDigest`, `contractVersion` et `projectionVersion` cohérents avec la v2.
- Le snapshot `AiTask` conserve la même projection enveloppée, le même contrat de grounding, le digest de projection et la politique (`CORE_CANONICAL_ONLY`, `retrieval: NONE`, allow-list typée). Le digest est aussi présent dans la politique du snapshot.

### Tests

- Les tests ont été réalignés sur la construction canonique et ne préparent plus les services legacy retirés.
- Les assertions vérifient la propagation `PromptRequest` → `AiTask` snapshot, la version v2, le digest et la politique.
- Un test vérifie la canonicalisation du digest : réordonner les clés ne change pas le résultat ; modifier le grounding ou la projection le change.
- Un test vérifie l’échec fermé lorsque le contexte canonique est indisponible et l’absence de soumission dans ce cas.

## Vérifications

- **13 tests ciblés :** passés (`AnalyzeStoryContextUseCaseTest`).
- **Compilation backend :** OK (`./backend/mvnw -f backend/pom.xml -DskipTests compile -q`).
- **Intégrité du diff :** OK (`git diff --check`).
- Aucun changement de logique métier hors du périmètre de propagation, de projection et de ses tests n’est inclus dans ce rapport.

## Risque résiduel

La suite ciblée passe, mais l’exécution affiche l’avertissement Mockito/JDK relatif à l’auto-attachement de l’agent Byte Buddy. Cette configuration devra être traitée avant le durcissement des JDK qui interdiront le chargement dynamique ; elle ne remet pas en cause le verdict Reviewer `PASS` de cette correction.
