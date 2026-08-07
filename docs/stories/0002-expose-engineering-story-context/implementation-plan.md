# Implementation Plan

## Overview

Story 0002 ajoute un type `EngineeringStoryContext`, un service `EngineeringStoryContextService` et un controller `EngineeringStoryContextController` pour exposer le contexte project-scoped via une API REST. Le tout repose sur `ProjectContextProvider` (Story 0001) sans modifier les services existants.

L'implémentation est simple : 3 nouveaux fichiers, 1 fichier de test, 0 modification de code existant.

---

## Planned Changes

### Step 1 : Créer `EngineeringStoryContext` (record)

**Composant** : `projectcontext`

**Fichier** : `backend/src/main/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContext.java`

**Changement** : Nouveau record Java avec les champs :
- `projectContext` (ProjectContextSnapshot)
- `generatedAt` (Instant)
- `projectId` (UUID)

Le compact constructor applique `List.copyOf` sur les listes du snapshot (délégation au snapshot qui est déjà immutable).

**Raison** : Type de réponse dédié au consommateur Kiko. Sépare la forme de la réponse du modèle interne.

**Contrainte** : Le record est immutable. Pas de setter. Le `projectContext` est le `ProjectContextSnapshot` retourné par le provider.

### Step 2 : Créer `EngineeringStoryContextService` (interface)

**Composant** : `projectcontext`

**Fichier** : `backend/src/main/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextService.java`

**Changement** : Interface avec une méthode :
```java
EngineeringStoryContext build(UUID projectId);
```

**Raison** : Abstraction permettant de remplacer l'implémentation (test, mock). Pattern cohérent avec le reste du codebase.

### Step 3 : Créer `EngineeringStoryContextServiceImpl`

**Composant** : `projectcontext`

**Fichier** : `backend/src/main/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextServiceImpl.java`

**Changement** : Classe `@Service` avec `@RequiredArgsConstructor`. Injection de `ProjectContextProvider`. La méthode `build` :
1. Appelle `projectContextProvider.build(projectId)`
2. Enveloppe le résultat dans un `EngineeringStoryContext` avec `Instant.now()` et le `projectId`
3. Laisse les exceptions (`EntityNotFoundException`) se propager (gérées par `GlobalExceptionHandler`)

**Raison** : Délégation simple au provider existant. Pas de logique métier supplémentaire.

**Contrainte** : Pas de filtering, pas d'interprétation, pas de persistence. Le contexte complet est retourné.

### Step 4 : Créer `EngineeringStoryContextController`

**Composant** : `controller` (nouveau fichier)

**Fichier** : `backend/src/main/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextController.java`

**Changement** : `@RestController` avec :
- `@GetMapping("/api/projects/{projectId}/engineering-story-context")`
- `@PathVariable UUID projectId`
- Retour `ResponseEntity<EngineeringStoryContext>`
- Injection de `EngineeringStoryContextService` via `@RequiredArgsConstructor`

**Raison** : Exposition REST du contexte. Le `GlobalExceptionHandler` gère `EntityNotFoundException` → 404.

**Contrainte** : Pas de parameter optionnel. Le filtering sera ajouté dans une story future.

### Step 5 : Créer `EngineeringStoryContextServiceTest`

**Composant** : `projectcontext` (test)

**Fichier** : `backend/src/test/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextServiceTest.java`

**Changement** : Test unitaire avec `@ExtendWith(MockitoExtension.class)` :
- Mock : `ProjectContextProvider`
- Test 1 : `shouldBuildEngineeringStoryContext` — mock le provider avec un snapshot valide, vérifie que le contexte contient le snapshot, le projectId, et un generatedAt
- Test 2 : `shouldPropagateExceptionWhenProjectNotFound` — mock le provider pour lancer `EntityNotFoundException`, vérifie que l'exception se propage

**Raison** : Validation du comportement nominal et du cas d'erreur. Le controller est testé indirectement via le service (pattern existant dans le projet).

---

## Files to Modify

Aucun fichier existant n'est modifié.

---

## Files to Create

| Fichier | Type |
|---|---|
| `backend/src/main/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContext.java` | Record |
| `backend/src/main/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextService.java` | Interface |
| `backend/src/main/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextServiceImpl.java` | Service |
| `backend/src/main/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextController.java` | Controller |
| `backend/src/test/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextServiceTest.java` | Test |

---

## Dependencies

- **Story 0001** : `ProjectContextProvider` et `ProjectContextSnapshot` (déjà implémentés)
- **Aucune nouvelle dépendance externe**
- **Aucun changement de schéma BDD**
- **Aucune nouvelle entité JPA**
- **Aucun changement de pom.xml**

---

## Test Plan

### Tests à créer

| Test | AC couverte | Description |
|---|---|---|
| `shouldBuildEngineeringStoryContext` | AC-1, AC-2, AC-4, AC-7 | Vérifie que le contexte contient le snapshot, le projectId, et un generatedAt |
| `shouldPropagateExceptionWhenProjectNotFound` | AC-6 | Vérifie que EntityNotFoundException se propage |

### Validation

```
cd backend && ./mvnw compile -q
cd backend && ./mvnw test -Dtest="EngineeringStoryContextServiceTest,ProjectContextProviderTest,AnalysisContextServiceTest"
```

### Conditions de succès

- Compilation sans erreur
- 3 tests existants passent (Story 0001)
- 2 nouveaux tests passent
- Aucun test existant ne régresse

---

## Risks

### Risk-1 : Le contexte complet peut être volumineux

**Mitigation** : Les listes sont paginées dans `ProjectContextProviderImpl` (max 20 éléments par liste). La taille est contrôlée. Le filtering client-side par Kiko est acceptable pour V1.

### Risk-2 : Absence de contexte repository

**Mitigation** : Ce gap est documenté dans la Story. Le contexte repository sera ajouté dans une story future quand un mécanisme de mapping story→fichiers sera défini. Pour V1, le contexte project-scoped est suffisant.

---

## Validation Checklist

- [ ] `EngineeringStoryContext.java` créé avec projectContext, generatedAt, projectId
- [ ] `EngineeringStoryContextService.java` créé avec méthode `build(UUID projectId)`
- [ ] `EngineeringStoryContextServiceImpl.java` créé, annoté @Service, injecte ProjectContextProvider
- [ ] `EngineeringStoryContextController.java` créé, endpoint GET /api/projects/{projectId}/engineering-story-context
- [ ] `EngineeringStoryContextServiceTest.java` créé avec 2 tests
- [ ] `mvn compile -q`成功
- [ ] `mvn test -Dtest="EngineeringStoryContextServiceTest"` — tous passent
- [ ] Aucun fichier existant modifié
- [ ] Aucun changement dans AnalysisContextService, ProjectContextProvider, etc.
- [ ] AC-1 à AC-9 vérifiées

---

## Recommendation

**Ready for implementation**

L'implémentation est clairement définie. 5 fichiers à créer, 0 à modifier. Aucune ambiguïté. Aucun blocking prerequisite.

---

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.
