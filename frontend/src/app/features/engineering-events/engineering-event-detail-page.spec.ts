import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';

import { EngineeringEvent } from './engineering-event.models';
import { EngineeringEventService } from './engineering-event.service';
import { EngineeringEventDetailPage } from './engineering-event-detail-page';

const event: EngineeringEvent = {
  version: 'v1',
  id: 'e1',
  projectId: 'p1',
  analysisId: 'a1',
  proposalId: 'pro1',
  validationId: 'val1',
  sourceId: 'src1',
  category: 'FEATURE_INTRODUCTION',
  title: 'Introduce search',
  summary: 'Adds full-text search.',
  significance: 'New primary capability.',
  baseCommit: 'abc',
  targetCommit: 'def',
  comparisonPolicy: 'FIRST_PARENT',
  mergeCommit: true,
  occurredAt: '2026-08-01T10:00:00Z',
  createdAt: '2026-08-01T11:00:00Z',
  confidence: 0.9,
  supportingFactIds: ['f1'],
  supportingObservationIds: ['o1'],
  evidenceReferences: ['ref-1', 'ref-2'],
};

describe('EngineeringEventDetailPage', () => {
  const paramMap = new BehaviorSubject(convertToParamMap({ id: 'e1' }));
  const get = vi.fn();

  beforeEach(() => {
    paramMap.next(convertToParamMap({ id: 'e1' }));
    get.mockReset();
  });

  afterEach(() => TestBed.resetTestingModule());

  async function render() {
    await TestBed.configureTestingModule({
      imports: [EngineeringEventDetailPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap } },
        { provide: EngineeringEventService, useValue: { get } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(EngineeringEventDetailPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows a loading status before the event resolves', async () => {
    get.mockReturnValue(new Subject());
    const element = await render();
    expect(element.querySelector('[role="status"]')?.textContent).toContain('Loading');
  });

  it('loads the event by route id and renders its details', async () => {
    get.mockReturnValue(of(event));
    const element = await render();

    expect(get).toHaveBeenCalledWith('e1');
    expect(element.querySelector('h1')?.textContent).toBe('Introduce search');
    expect(element.textContent).toContain('FEATURE INTRODUCTION');
    expect(element.textContent).toContain('Adds full-text search.');
    expect(element.textContent).toContain('New primary capability.');
    expect(element.textContent).toContain('abc');
    expect(element.textContent).toContain('def');
  });

  it('renders merge commit note and audit links', async () => {
    get.mockReturnValue(of(event));
    const element = await render();

    expect(element.textContent).toContain('Merge commit compared');
    const labels = Array.from(element.querySelectorAll('a')).map((a) => a.textContent);
    expect(labels).toContain('Analysis');
    expect(labels).toContain('Proposal and Validation');
  });

  it('lists the evidence references', async () => {
    get.mockReturnValue(of(event));
    const element = await render();
    expect(element.textContent).toContain('ref-1');
    expect(element.textContent).toContain('ref-2');
  });

  it('does not render the merge note for a non-merge event', async () => {
    get.mockReturnValue(of({ ...event, mergeCommit: false }));
    const element = await render();
    expect(element.textContent).not.toContain('Merge commit compared');
  });

  it('renders an error state on failure', async () => {
    get.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const element = await render();
    expect(element.querySelector('[role="alert"]')).toBeTruthy();
  });

  it('reacts to a route id change', async () => {
    get.mockReturnValue(of(event));
    await render();
    get.mockClear();

    paramMap.next(convertToParamMap({ id: 'e2' }));
    expect(get).toHaveBeenCalledWith('e2');
  });
});