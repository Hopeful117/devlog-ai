# Rapport d'implémentation — Story 0148

## Changements

1. `AnalyzeStoryContextUseCase.mapGuidance` utilise désormais le contrat
   partagé `UserGuidance.from`. Les champs REST (`audience`, `levelOfDetail`,
   `writingStyle`, `outputContext`, `priorities`) arrivent donc sans décalage
   dans le `PromptRequest`, puis dans le prompt Python.
2. `StoryContextAnalysis` marque le snapshot, le digest Core, la trace prompt
   et la fraîcheur `updatable=false`. La migration V51 documente leur caractère
   insert-only ; l'unicité `ai_task_id` existante garantit une seule ligne.
3. Le test de contrat Java vérifie la propagation exacte de la guidance.

## Vérification

```text
cd backend
./mvnw -q -Dtest=AnalyzeStoryContextUseCaseTest,StoryContextAnalysisQueryServiceTest,AiTaskResultServiceTest test
```

Résultat attendu : tests verts. Les tests couvrent le parcours mocké, les
identités/digests, la validation grounding/relations, l'absence de propositions,
la duplication idempotente et la lecture finale.

## Distinction avec le travail préexistant

Les changements de résolution d'évidence et de projection repository présents
dans le working tree avant Story 0148 n'ont pas été modifiés. Le parcours
Story Context, ses contrôles et ses migrations antérieures étaient déjà
présents ; cette tranche ferme la propagation de guidance et la protection
JPA du snapshot, et ajoute la documentation de preuve.
