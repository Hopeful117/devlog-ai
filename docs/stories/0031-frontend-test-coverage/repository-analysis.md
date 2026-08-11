# Repository Analysis — Story 31: Frontend Test Coverage & Bug Fixes

## Context

Le frontend Angular manque de tests e2e et a une couverture de tests unitaires incomplète. Plusieurs fonctionnalités user-facing sont buggées en production.

---

## Scope de la story

### Ce qui est IN scope
1. Analyse de la couverture de tests existante
2. Identification des bugs connus
3. Propositions pour augmenter la couverture
4. Identification des besoins pour tests e2e

### Ce qui est OUT scope
1. Fix des bugs (réservé aux stories techniques)
2. Implémentation de tests e2e (nécessite choix du framework)
3. Refactorisation du code

---

## État actuel des tests frontend

### Infrastructure
- **Framework** : Vitest 4.0.8 via `@angular/build:unit-test`
- **Script** : `ng test`
- **Tests e2e** : Aucun (pas de Cypress, Playwright, ni de directory e2e/)

### Statistiques
| Métrique | Valeur |
|----------|--------|
| Fichiers source | ~45 `.ts` non spec |
| Fichiers test | 37 `.spec.ts` (159 tests) |
| Couverture (source) | Lines ~81%, Statements ~78%, Branches ~75%, Functions ~76% |

> La couverture est mesurée via `ng test --coverage --watch=false` (nécessite
> `@vitest/coverage-v8`). Le périmètre inclut les templates `.html`, qui sont volumineux et
> tirent la moyenne globale vers le bas ; la couverture des `.ts` ciblés est plus élevée.

### Composants critiques SANS tests unitaires

| Composant | Risque | Note |
|-----------|--------|------|
| `project-state-page` | **ÉLEVÉ** | ✔ testé (états + numéro de story, garde Bug 2) |
| `project-state.service` | **ÉLEVÉ** | ✔ testé |
| `workspace-layout` | **ÉLEVÉ** | ✔ testé (nom projet / fallback + nav sidebar) |
| `insight.service` | **ÉLEVÉ** | ✔ testé (branches type/severity) |
| `insight-proposal.service` | **ÉLEVÉ** | ✔ testé (dans `insight-services.spec`) |
| `dashboard-page` | MOYEN | ✔ testé (trivial) |
| `engineering-events/*` | MOYEN | ✔ testé (page + section, états/erreur/exécution) |
| `deliverable-detail-page` | MOYEN | ✔ testé (loaded/not-found/error) |
| `engineering-event-detail-page` | MOYEN | ✔ testé (loading/loaded/erreur/merge commit/audit) |
| `proposal-review-page` | MOYEN | ✔ testé (accept/reject/form invalide/pagination/erreur) |

### Services non testés
- `app.routes.ts` (routing — non testé unitairement ; couvert indirectement par e2e)
  - *Remarque : instrumenter `app.routes.ts` (ses lambdas `loadComponent`) ferait chuter les
    métriques globales car tous les composants lazy sont alors comptés sans être rendus ; couvrir
    ce fichier réduirait artificiellement Statements/Lines/Functions. La couverture applicative
    réelle par e2e est préférable.*
- Models non testés : `dashboard-card.ts`, etc. (des modèles sont couverts par `dashboard-card.spec` et `status-badge.spec`)

> Depuis la Phase 1, `request-error.ts` et `project-state.service` sont désormais testés ;
> `project-state-page` (overview) dispose de tests unitaires complets depuis la session de
> couverture (états loaded / not-found / error + affichage du numéro de story).

---

## Bugs connus en production

### Bug 1: Refresh Understanding — CORRIGÉ
- **Composant** : `project-understanding-section`
- **Symptôme** : Le bouton "Refresh understanding" ne fonctionne pas correctement en prod
- **Cause racine (backend)** : `LazyInitializationException` sur `ProjectCommit.changedFiles`
  (collection `@OneToMany` lazy) accédée hors session Hibernate par
  `CommitScopedFactCollector.groupFilesByModule`, qui s'exécute sur un thread virtuel
  (`CollectorRunner`).
- **Fix** : `@EntityGraph(attributePaths = {"changedFiles"})` sur la requête
  `ProjectCommitRepository.findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc`
  (bénéficie aussi à `CommitDiffEvidenceCollector`).
- **Tests** : test d'intégration `CommitChangedFilesEagerFetchIntegrationTest` qui reproduit
  l'accès hors session sur thread virtuel (échoue avec la `LazyInitializationException` exacte
  sans le fix).
- **Impact** : **ÉLEVÉ** — flux utilisateur critique pour comprendre les projets
- **Status** : ✔ Résolu

### Bug 2: Overview page (number: null) — CORRIGÉ
- **Composant** : `project-state-page` (overview)
- **Symptôme** : `number: null` pour toutes les stories affichées ("null # — title")
- **Cause** : MapStruct ne mappe pas `EngineeringStory.storyNumber` → `StorySummary.number`
  (noms de propriétés différents → nombre laissé à `null`).
- **Fix** : `@Mapping(target = "number", source = "storyNumber")` sur
  `ProjectStateMapper.toStorySummary`.
- **Tests** : test dédié `ProjectStateMapperTest` ; garde-fou e2e dans `projects-flow.spec.ts`
  (l'overview affiche `#N —` et aucun `null #`).
- **Impact** : **MOYEN**
- **Status** : ✔ Résolu

### Bug 3: Aucun test e2e — TRAITÉ
- **Impact** : **ÉLEVÉ** — risque de régressions invisibles
- **Status** : ✔ Playwright choisi et configuré (voir ci-dessous)
- **Complexité** : nécessite infrastructure (Selenium, Cypress, Playwright)

### Bug 4: Overview page illisible (styles body non appliqués) — CORRIGÉ
- **Composant** : `project-state-page` (overview)
- **Symptôme** : la page est presque illisible — panneaux blancs avec texte clair, couleurs legacy
- **Cause** : le SCSS de la page utilisait des noms de variables CSS **legacy** (`--color-surface`,
  `--color-border`, `--color-primary`, `--color-text-secondary`, `--color-error`) qui **n'existent
  plus** dans le design system global (`src/styles.scss` définit `--panel-bg`, `--border`,
  `--accent`, `--text-*`, `--danger`). Les fallbacks light (`#fff`, `#666`, …) s'appliquaient alors
  sur un thème sombre → `--color-surface`→`#fff` produisait des panneaux blancs avec le texte clair
  hérité du `body`, rendant la page illisible.
- **Fix** : remplacement des tokens legacy par les tokens du design system dans
  `project-state-page.scss` (et correction du même problème dans
  `project-engineering-events-section.scss`).
- **Vérif** : styles calculés — panneau `rgb(17,26,43)` (dark) + texte `rgb(238,244,255)` (clair),
  bon contraste.
- **Impact** : **ÉLEVÉ** — lisibilité de l'écran principal du projet
- **Status** : ✔ Résolu

---

## Architecture frontend actuelle

### Structure
```
src/app/
├── core/           config, http, layout
├── features/       pages par feature
├── shared/         composants réutilisables
└── app.routes.ts   routing principale
```

### Patterns
- Angular 19 standalone components (pas de modules)
- Services injectables avec `inject()`
- State via Observables/RxJS
- HTTP via `HttpClient`

---

## Options pour les tests e2e

| Framework | Avantages | Inconvénients |
|-----------|-----------|---------------|
| **Playwright** | Cross-browser, auto-wait, API puissante | Courbe d'apprentissage |
| **Cypress** | Large communauté, plugins | Limité à Chromium par défaut |
| **TestCafe** | Pas de configuration WebDriver | Moins populaire Angular |

**Recommandation retenue** : Playwright — plus simple à configurer que Cypress pour un projet Angular.
Le setup est décrit dans `docs/stories/0031-frontend-test-coverage/implementation-plan.md`
(section "Testing Strategy"), et le README racine documente l'exécution.

---

## Recommandations

1. **Court terme** : Ajouter des tests unitaires pour les services critiques (`request-error`, `insight.service`) — *fait pour `request-error` et `project-state.service`*
2. **Moyen terme** : Investiguer le bug "refresh understanding" plus en détail — *fait : cause backend (LazyInitializationException), corrigé et testé*
3. **Long terme** : Décider d'un framework e2e (Playwright recommandé) — *fait : Playwright configuré, tests e2e initiaux en place*

## Conclusion

La story #31 est **réalisée** :
1. Phase 1 : Analyse des bugs + tests unitaires services critiques
2. Phase 2 : Fix bug "refresh understanding" + tests associés (cause racine backend)
3. Phase 3 : Playwright configuré et POC e2e (couvre aussi le Bug 2 / overview)