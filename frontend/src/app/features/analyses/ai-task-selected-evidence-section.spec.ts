import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AiTaskSelectedEvidenceSection } from './ai-task-selected-evidence-section';
import {
  AiTaskSelectedEvidenceResponse,
  AvailableSelectedEvidenceResponse,
  EvidenceAvailability,
  RepositoryContentStatus,
} from './ai-task-selected-evidence.models';
import { AiTaskDetail } from './analysis.models';
import { AnalysisService } from './analysis.service';

const taskIdentity = {
  id: 'task-id',
  taskType: 'INSIGHT_GENERATION',
  status: 'COMPLETED',
  createdAt: '2026-08-27T10:00:00Z',
} as const;

const emptyCategories = (availability: EvidenceAvailability = 'RECORDED') => ({
  facts: { availability, count: 0, items: [] },
  observations: { availability, count: 0, items: [] },
  priorInsights: { availability, count: 0, items: [] },
  architectureKnowledge: { availability, count: 0, items: [] },
  engineeringEvents: { availability, count: 0, items: [] },
  humanContext: { availability, count: 0, items: [] },
  evolutionContext: { availability, count: 0, items: [] },
  repositoryEvidence: { availability, count: 0, items: [] },
});

const availableResponse = (): AvailableSelectedEvidenceResponse => ({
  state: 'AVAILABLE',
  analysisId: 'analysis-id',
  projectId: 'project-id',
  task: taskIdentity,
  selectionVersion: 'knowledge-selection-v4',
  selectionDigest: 'a'.repeat(64),
  snapshotMetadata: {
    project: {
      id: 'project-id',
      name: 'DevLog',
      slug: 'devlog',
      description: 'Engineering memory',
      status: 'ACTIVE',
    },
    analysis: {
      id: 'analysis-id',
      type: 'ARCHITECTURE_REVIEW',
      intentId: 'architecture-overview',
      intentVersion: 'v1',
      status: 'COMPLETED',
      startedAt: '2026-08-27T10:00:00Z',
      completedAt: '2026-08-27T10:01:00Z',
      createdAt: '2026-08-27T09:59:00Z',
    },
    projectProfile: {
      id: 'profile-id',
      projectId: 'project-id',
      analysisId: 'analysis-id',
      profileVersion: 'profile-v1',
      rendererVersion: 'renderer-v1',
      generatedAt: '2026-08-27T10:00:00Z',
      requestedRevision: 'abc123',
      completeness: {
        status: 'COMPLETE',
        collectionComplete: true,
        truncated: false,
        warningCount: 1,
        errorCount: 0,
        successfulCollectorCount: 3,
        collectorsWithWarningsCount: 1,
        failedCollectorCount: 0,
      },
      deterministicSummary: 'Historical profile summary',
      characteristicCount: 3,
    },
    diagnostics: {
      collectionComplete: true,
      truncated: false,
      warningCount: 1,
      errorCount: 0,
    },
    selection: {
      selectionVersion: 'knowledge-selection-v4',
      appliedRules: ['bounded-selection-v1'],
      selectedKnowledgeCount: 8,
      discardedKnowledgeCount: 2,
      knowledgeBudget: {
        maximumFacts: 40,
        maximumObservations: 25,
        maximumInsights: 10,
        maximumArchitectureKnowledge: 5,
        maximumRepositoryEvidence: 60,
      },
      completeness: 'COMPLETE',
    },
    repositoryContext: {
      contextVersion: 'repository-context-engine-v1',
      profile: 'PROJECT_STATE',
      warnings: ['bounded selection'],
      contextDigest: 'c'.repeat(64),
    },
  },
  categories: {
    facts: {
      availability: 'RECORDED',
      count: 1,
      items: [
        {
          id: 'fact-id',
          type: 'SOURCE_DIRECTORY_PRESENT',
          content: 'Fact content',
          source: 'src/main',
          evidenceReferences: ['fact:1'],
          detectedAt: '2026-08-27T10:00:00Z',
        },
      ],
    },
    observations: {
      availability: 'RECORDED',
      count: 1,
      items: [
        {
          id: 'observation-id',
          type: 'ARCHITECTURAL_PATTERN',
          content: 'Observation content',
          ruleId: 'rule-id',
          ruleVersion: 'v1',
          supportingFactIds: ['fact-id'],
          createdAt: '2026-08-27T10:00:01Z',
        },
      ],
    },
    priorInsights: {
      availability: 'RECORDED',
      count: 1,
      items: [
        {
          type: 'ARCHITECTURAL',
          severity: 'INFO',
          title: 'Prior input insight',
          content: 'Prior insight content',
        },
      ],
    },
    architectureKnowledge: {
      availability: 'RECORDED',
      count: 1,
      items: [
        {
          insightId: 'architecture-insight-id',
          proposalId: 'proposal-id',
          normalizedType: 'ARCHITECTURAL',
          severity: 'INFO',
          sourceType: 'ARCHITECTURAL',
          title: 'Architecture input',
          content: 'Architecture content',
          rationale: 'Architecture rationale',
          evidenceReferences: ['git:1'],
          createdAt: '2026-08-27T10:00:02Z',
        },
      ],
    },
    engineeringEvents: {
      availability: 'RECORDED',
      count: 1,
      items: [
        {
          id: 'event-id',
          category: 'ARCHITECTURE',
          title: 'Engineering event',
          summary: 'Event summary',
          sourceId: 'source-id',
          baseCommit: 'base',
          targetCommit: 'target',
          occurredAt: '2026-08-27T10:00:03Z',
          proposalId: 'proposal-id',
        },
      ],
    },
    humanContext: {
      availability: 'RECORDED',
      count: 1,
      items: [
        {
          id: 'human-id',
          type: 'GOAL',
          title: 'Human goal',
          contentMarkdown: '<script>unsafeHuman()</script> # literal markdown',
          status: 'ACTIVE',
          updatedAt: '2026-08-27T10:00:04Z',
        },
      ],
    },
    evolutionContext: {
      availability: 'RECORDED',
      count: 1,
      items: [
        {
          contextVersion: 'evolution-v1',
          projectId: 'project-id',
          sourceId: 'source-id',
          baseCommit: 'base',
          targetCommit: 'target',
          comparisonPolicy: 'FIRST_PARENT',
          mergeCommit: false,
          targetCommittedAt: '2026-08-27T10:00:05Z',
          commitDiff: {
            projectId: 'project-id',
            repositoryId: 'source-id',
            commitHash: 'target',
            firstParentHash: 'base',
            parentHashes: ['base'],
            rootCommit: false,
            mergeCommit: false,
            commitMessage: 'Change source',
            committedAt: '2026-08-27T10:00:05Z',
            changedFiles: [
              {
                changeType: 'MODIFIED',
                oldPath: 'src/App.java',
                newPath: 'src/App.java',
                binary: false,
                insertions: 2,
                deletions: 1,
                language: 'Java',
                category: 'SOURCE',
                excludedFromAnalysis: false,
                exclusionReason: null,
                evidenceReference: 'diff:1',
              },
            ],
            statistics: { filesChanged: 1, insertions: 2, deletions: 1, binaryFiles: 0 },
            candidateAdrReferences: ['ADR-063'],
            candidateRoadmapReferences: [],
            evidenceReferences: ['diff:1'],
            truncated: false,
            warnings: [],
          },
        },
      ],
    },
    repositoryEvidence: {
      availability: 'RECORDED',
      count: 4,
      items: (['COMPLETE', 'TRUNCATED', 'SKIPPED', 'UNAVAILABLE'] as const).map(
        (status: RepositoryContentStatus, index) => ({
          layer: 'CURRENT_ANALYSIS',
          kind: 'SOURCE_FILE',
          reference: `src/Source${index}.java`,
          summary: `Repository item ${status}`,
          occurredAt: '2026-08-27T10:00:00Z',
          relatedReferences: ['pom.xml'],
          content: {
            status,
            text:
              status === 'COMPLETE' || status === 'TRUNCATED'
                ? '<img src=x onerror=unsafeRepository()> class Source {}'
                : null,
            reason: 'bounded content',
            policyId: 'content-policy',
            policyVersion: 'v1',
            revision: 'abc123',
            allocationPolicyId: 'allocation-policy',
            allocationPolicyVersion: 'v1',
            allocationRank: index + 1,
          },
          symbols:
            index === 0
              ? {
                  status: 'EXTRACTED',
                  reason: 'symbols available',
                  policyId: 'symbol-policy',
                  policyVersion: 'v1',
                  extractorId: 'javaparser',
                  extractorVersion: '3',
                  revision: 'abc123',
                  allocationRank: 1,
                  truncated: false,
                  returnedSymbolCount: 1,
                  availableSymbolCount: 1,
                  declarations: [
                    {
                      kind: 'CLASS',
                      name: 'Source',
                      owningType: null,
                      modifiers: ['public'],
                      returnType: null,
                      parameters: [{ type: 'String', name: 'value' }],
                      annotations: ['Component'],
                      location: { beginLine: 1, beginColumn: 1, endLine: 2, endColumn: 1 },
                    },
                  ],
                }
              : null,
        }),
      ),
    },
  },
});

const task = (overrides: Partial<AiTaskDetail> = {}): AiTaskDetail => ({
  id: 'task-id',
  analysisId: 'analysis-id',
  correlationId: 'correlation-id',
  taskType: 'INSIGHT_GENERATION',
  intentId: 'architecture-overview',
  intentVersion: 'v1',
  intentSnapshot: null,
  userGuidanceSnapshot: null,
  promptRequestId: null,
  promptVersion: null,
  provider: null,
  modelIdentifier: null,
  promptContentDigest: null,
  contextDigest: null,
  selectedKnowledgeSnapshot: null,
  selectionVersion: null,
  selectionDigest: null,
  status: 'PROCESSING',
  contextSnapshot: null,
  externalJobId: null,
  attemptCount: 0,
  failureCode: null,
  failureMessage: null,
  createdAt: '2026-08-27T10:00:00Z',
  submittedAt: null,
  startedAt: null,
  completedAt: null,
  ...overrides,
});

describe('AiTaskSelectedEvidenceSection', () => {
  const getSelectedEvidence = vi.fn();
  let fixture: ComponentFixture<AiTaskSelectedEvidenceSection>;

  beforeEach(async () => {
    getSelectedEvidence.mockReset().mockReturnValue(of(availableResponse()));
    await TestBed.configureTestingModule({
      imports: [AiTaskSelectedEvidenceSection],
      providers: [{ provide: AnalysisService, useValue: { getSelectedEvidence } }],
    }).compileComponents();
    fixture = TestBed.createComponent(AiTaskSelectedEvidenceSection);
    fixture.componentRef.setInput('analysisId', 'analysis-id');
    fixture.componentRef.setInput('newestTask', task());
  });

  afterEach(() => TestBed.resetTestingModule());

  function render(response: AiTaskSelectedEvidenceResponse): HTMLElement {
    getSelectedEvidence.mockReturnValue(of(response));
    fixture.detectChanges();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it.each([
    {
      state: 'NO_AI_TASK',
      response: {
        state: 'NO_AI_TASK',
        analysisId: 'analysis-id',
        projectId: 'project-id',
        task: null,
        selectionVersion: null,
        selectionDigest: null,
        snapshotMetadata: null,
        categories: null,
      } as const,
      text: 'No AI Task exists',
    },
    {
      state: 'SNAPSHOT_PENDING',
      response: {
        state: 'SNAPSHOT_PENDING',
        analysisId: 'analysis-id',
        projectId: 'project-id',
        task: { ...taskIdentity, status: 'PROCESSING' },
        selectionVersion: null,
        selectionDigest: null,
        snapshotMetadata: null,
        categories: null,
      } as const,
      text: 'Snapshot pending',
    },
    {
      state: 'SNAPSHOT_UNAVAILABLE',
      response: {
        state: 'SNAPSHOT_UNAVAILABLE',
        analysisId: 'analysis-id',
        projectId: 'project-id',
        task: taskIdentity,
        selectionVersion: null,
        selectionDigest: null,
        snapshotMetadata: null,
        categories: null,
      } as const,
      text: 'Snapshot unavailable',
    },
  ])('renders the $state outer state honestly', ({ response, text }) => {
    const element = render(response);
    expect(element.textContent).toContain(text);
    expect(element.querySelector('[role="status"]')?.textContent).toContain(text);
  });

  it('renders every typed category, audit identity, content status and symbol detail', () => {
    const element = render(availableResponse());
    const text = element.textContent ?? '';

    for (const expected of [
      'Facts',
      'Observations',
      'Prior Insights',
      'Architecture Knowledge',
      'Engineering Events',
      'Human Context',
      'Evolution Context',
      'Repository Evidence',
      'Fact content',
      'Observation content',
      'Prior input insight',
      'Architecture rationale',
      'Engineering event',
      'src/App.java',
      'COMPLETE',
      'TRUNCATED',
      'SKIPPED',
      'UNAVAILABLE',
      'Source',
      'Selection digest',
    ]) {
      expect(text).toContain(expected);
    }
    expect(text).not.toContain('checksum');
    expect(text).not.toContain('integrity hash');
    expect(element.querySelectorAll('article.evidence-category')).toHaveLength(8);
    expect(element.querySelector('summary')).not.toBeNull();
  });

  it('distinguishes not-recorded, recorded-empty and globally empty categories', () => {
    const response = availableResponse();
    const empty: AvailableSelectedEvidenceResponse = {
      ...response,
      categories: {
        ...emptyCategories('RECORDED'),
        observations: { availability: 'NOT_RECORDED', count: 0, items: [] },
      },
    };

    const element = render(empty);
    expect(element.textContent).toContain(
      'This evidence snapshot exists but contains no displayable',
    );
    expect(element.textContent).toContain('Category recorded; none selected.');
    expect(element.textContent).toContain('This historical snapshot did not record this category.');
  });

  it('does not fabricate historical commit-diff booleans when they were not recorded', () => {
    const response = availableResponse();
    const evolution = response.categories.evolutionContext.items[0];
    if (!evolution?.commitDiff) throw new Error('Expected commit-diff fixture');
    const element = render({
      ...response,
      categories: {
        ...response.categories,
        evolutionContext: {
          ...response.categories.evolutionContext,
          items: [
            {
              ...evolution,
              commitDiff: {
                ...evolution.commitDiff,
                rootCommit: null,
                mergeCommit: null,
                truncated: null,
              },
            },
          ],
        },
      },
    });
    const valueFor = (label: string): string | null | undefined =>
      Array.from(element.querySelectorAll('dt'))
        .find((term) => term.textContent === label)
        ?.nextElementSibling?.textContent?.trim();

    expect(valueFor('Root / merge')).toBe('Not recorded / Not recorded');
    expect(valueFor('Truncated')).toBe('Not recorded');
  });

  it('renders human and repository bodies as inert plain text', () => {
    const element = render(availableResponse());
    expect(element.textContent).toContain('<script>unsafeHuman()</script>');
    expect(element.textContent).toContain('<img src=x onerror=unsafeRepository()>');
    expect(element.querySelector('script')).toBeNull();
    expect(element.querySelector('img')).toBeNull();
  });

  it('shows a sanitized read failure and retries explicitly', () => {
    getSelectedEvidence
      .mockReturnValueOnce(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 500,
              error: { code: 'INTERNAL_ERROR', message: 'An unexpected error occurred.' },
            }),
        ),
      )
      .mockReturnValueOnce(of(availableResponse()));
    fixture.detectChanges();
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'The analysis request failed',
    );

    (element.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(getSelectedEvidence).toHaveBeenCalledTimes(2);
    expect(element.textContent).toContain('Selection digest');
  });

  it('requests once per stable task readiness key and reloads for attachment or replacement', () => {
    const pending: AiTaskSelectedEvidenceResponse = {
      state: 'SNAPSHOT_PENDING',
      analysisId: 'analysis-id',
      projectId: 'project-id',
      task: { ...taskIdentity, status: 'PROCESSING' },
      selectionVersion: null,
      selectionDigest: null,
      snapshotMetadata: null,
      categories: null,
    };
    getSelectedEvidence.mockReturnValue(of(pending));
    fixture.detectChanges();
    expect(getSelectedEvidence).toHaveBeenCalledTimes(1);

    fixture.componentRef.setInput('newestTask', task({ status: 'PROCESSING' }));
    fixture.detectChanges();
    expect(getSelectedEvidence).toHaveBeenCalledTimes(1);

    getSelectedEvidence.mockReturnValue(of(availableResponse()));
    fixture.componentRef.setInput(
      'newestTask',
      task({ selectionVersion: 'knowledge-selection-v4', selectionDigest: 'a'.repeat(64) }),
    );
    fixture.detectChanges();
    expect(getSelectedEvidence).toHaveBeenCalledTimes(2);

    fixture.componentRef.setInput(
      'newestTask',
      task({ id: 'replacement-task', selectionVersion: 'knowledge-selection-v4' }),
    );
    fixture.detectChanges();
    expect(getSelectedEvidence).toHaveBeenCalledTimes(3);
  });

  it('uses semantic headings, native disclosure, status roles and responsive layout hooks', () => {
    const element = render(availableResponse());
    expect(
      element.querySelector('section[aria-labelledby="selected-evidence-title"]'),
    ).not.toBeNull();
    expect(element.querySelectorAll('h3[id]')).toHaveLength(8);
    const disclosure = element.querySelector('details');
    const summary = disclosure?.querySelector('summary') as HTMLElement;
    expect(summary.tagName).toBe('SUMMARY');
    summary.click();
    expect(disclosure?.hasAttribute('open')).toBe(true);
    expect(element.querySelector('.category-grid')).not.toBeNull();
  });

  it('does not manually subscribe or start a polling timer', () => {
    const source = AiTaskSelectedEvidenceSection.toString();
    expect(source).not.toContain('.subscribe(');
    expect(source).not.toContain('timer(');
  });
});
