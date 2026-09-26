import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';
import { ProjectService } from '../projects/project.service';
import { StoryDetailPage } from './story-detail-page';
import { StoryService } from './story.service';

const project = {
  id: 'project-id',
  name: 'DevLog AI',
  slug: 'devlog-ai',
  description: null,
  status: 'ACTIVE',
  createdAt: null,
  updatedAt: null,
};

const story = {
  id: 'story-id',
  projectId: 'project-id',
  storyNumber: 151,
  title: 'Read-only Story detail',
  storyPath: 'docs/stories/0151-story-detail/story.md',
  baseCommit: 'base-123',
  targetCommit: 'target-456',
  status: 'IN_PROGRESS',
  createdAt: '2026-09-26T10:00:00Z',
  updatedAt: '2026-09-26T10:01:00Z',
  completedAt: null,
};

const briefing = {
  contractVersion: 'story-change-briefing-v1',
  storyId: 'story-id',
  contextDigest: 'context-digest',
  projectionDigest: 'projection-digest',
  groundingStatus: 'NOT_ESTABLISHED' as const,
  description: 'Descriptive change briefing.',
  changes: [
    {
      reference: { type: 'COMMIT', ref: 'abc123', scope: 'repository' },
      kind: 'MODIFIED',
      summary: 'Added the read-only view.',
      occurredAt: '2026-09-26T10:02:00Z',
      provenance: { source: 'git' },
    },
  ],
  warnings: ['Causality is not established.'],
  snapshot: { baseCommit: 'base-123', targetCommit: 'target-456' },
};

describe('StoryDetailPage', () => {
  const paramMap = new BehaviorSubject(convertToParamMap({ storyId: 'story-id' }));
  const getProject = vi.fn();
  const getStory = vi.fn();
  const getChangeBriefing = vi.fn();

  beforeEach(async () => {
    paramMap.next(convertToParamMap({ storyId: 'story-id' }));
    getProject.mockReset().mockReturnValue(of(project));
    getStory.mockReset().mockReturnValue(of(story));
    getChangeBriefing.mockReset().mockReturnValue(of(briefing));
    await TestBed.configureTestingModule({
      imports: [StoryDetailPage],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap,
            parent: { snapshot: { paramMap: convertToParamMap({ id: 'project-id' }) } },
          },
        },
        { provide: ProjectService, useValue: { getProject } },
        { provide: StoryService, useValue: { get: getStory, getChangeBriefing } },
      ],
    }).compileComponents();
  });

  afterEach(() => TestBed.resetTestingModule());

  async function render(): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(StoryDetailPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('loads by project and story route identifiers and renders critical briefing fields', async () => {
    const element = await render();

    expect(getProject).toHaveBeenCalledWith('project-id');
    expect(getStory).toHaveBeenCalledWith('project-id', 'story-id');
    expect(getChangeBriefing).toHaveBeenCalledWith('devlog-ai', 'story-id');
    expect(element.querySelector('h1')?.textContent).toContain('Read-only Story detail');
    expect(element.textContent).toContain('Descriptive change briefing.');
    expect(element.textContent).toContain('NOT_ESTABLISHED');
    expect(element.textContent).toContain('context-digest');
    expect(element.textContent).toContain('projection-digest');
    expect(element.textContent).toContain('Causality is not established.');
    expect(element.textContent).toContain('base-123');
    expect(element.textContent).toContain('target-456');
    expect(element.textContent).toContain('COMMIT');
    expect(element.textContent).toContain('abc123');
  });

  it('renders bounded empty states for changes and snapshot warnings', async () => {
    getChangeBriefing.mockReturnValue(of({ ...briefing, changes: [], warnings: [], snapshot: {} }));
    const element = await render();

    expect(element.textContent).toContain('Changes (0)');
    expect(element.textContent).toContain('No changes were found.');
    expect(element.textContent).toContain('No warnings were reported.');
    expect(element.querySelector('pre')?.textContent?.trim()).toBe('{}');
  });
  it('renders duplicate warnings as separate entries', async () => {
    getChangeBriefing.mockReturnValue(
      of({ ...briefing, warnings: ['Repeated warning.', 'Repeated warning.'] }),
    );
    const element = await render();

    expect(
      Array.from(element.querySelectorAll('ul li')).filter((item) =>
        item.textContent?.includes('Repeated warning.'),
      ),
    ).toHaveLength(2);
  });

  it('renders loading while either read is pending', () => {
    const pendingStory = new Subject<typeof story>();
    getStory.mockReturnValue(pendingStory);
    const fixture = TestBed.createComponent(StoryDetailPage);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain(
      'Loading Story',
    );
    pendingStory.complete();
  });

  it('renders a story-specific error without attempting a write', async () => {
    getStory.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404, statusText: 'Not Found' })),
    );
    const element = await render();

    expect(element.querySelector('[role="alert"]')).toBeTruthy();
    expect(element.textContent).toContain('Unable to load the story. Please try again.');
    expect(element.textContent).not.toContain('project');
    expect(element.querySelector('form')).toBeNull();
    expect(element.querySelectorAll('button').length).toBe(0);
  });

  it('renders a distinct briefing-specific error', async () => {
    getChangeBriefing.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404, statusText: 'Not Found' })),
    );
    const element = await render();

    expect(element.querySelector('[role="alert"]')).toBeTruthy();
    expect(element.textContent).toContain(
      'Unable to load the story change briefing. Please try again.',
    );
    expect(element.textContent).not.toContain('project');
  });

  it('contains no imperative subscription or mutating service call', () => {
    expect(StoryDetailPage.toString()).not.toContain('.subscribe(');
    expect(getStory).not.toHaveBeenCalledWith(
      expect.anything(),
      expect.anything(),
      expect.anything(),
    );
    expect(getChangeBriefing).not.toHaveBeenCalledWith(
      expect.anything(),
      expect.anything(),
      expect.anything(),
    );
  });
});
