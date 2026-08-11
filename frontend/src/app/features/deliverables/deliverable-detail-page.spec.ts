import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { DeliverableDetailPage } from './deliverable-detail-page';
import { DeliverableService } from './deliverable.service';
import { Deliverable } from './deliverable.models';

const deliverable: Deliverable = {
  id: 'd1',
  projectId: 'p1',
  analysisId: null,
  type: 'README',
  audience: 'Developers',
  style: 'Concise',
  language: 'English',
  additionalGuidance: null,
  title: 'Project README',
  content: '# DevLog\n\nDocumentation.',
  promptVersion: 'v1',
  promptDigest: 'abc123',
  provider: 'mock',
  modelIdentifier: 'mock/deterministic-v1',
  generatedAt: '2026-08-01T10:00:00Z',
  sourceInsightIds: ['i1', 'i2'],
};

describe('DeliverableDetailPage', () => {
  const paramMap = new BehaviorSubject(convertToParamMap({ id: 'd1' }));
  const get = vi.fn();

  beforeEach(() => {
    paramMap.next(convertToParamMap({ id: 'd1' }));
    get.mockReset();
  });

  afterEach(() => TestBed.resetTestingModule());

  async function render() {
    await TestBed.configureTestingModule({
      imports: [DeliverableDetailPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap } },
        { provide: DeliverableService, useValue: { get } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(DeliverableDetailPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('loads the deliverable by route id and renders its content', async () => {
    get.mockReturnValue(of(deliverable));
    const element = await render();

    expect(get).toHaveBeenCalledWith('d1');
    expect(element.querySelector('h1')?.textContent).toContain('Project README');
    expect(element.textContent).toContain('Developers');
    expect(element.textContent).toContain('# DevLog');
    expect(element.textContent).toContain('mock / mock/deterministic-v1');
  });

  it('lists the source insight references', async () => {
    get.mockReturnValue(of(deliverable));
    const element = await render();

    expect(element.textContent).toContain('i1');
    expect(element.textContent).toContain('i2');
  });

  it('renders the not-found state for a 404', async () => {
    get.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    const element = await render();

    expect(element.textContent).toContain('Deliverable not found');
  });

  it('renders an error state for a non-404 failure', async () => {
    get.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const element = await render();

    expect(element.querySelector('[role="alert"]')).toBeTruthy();
  });

  it('does not implement an imperative subscription', () => {
    expect(DeliverableDetailPage.toString()).not.toContain('.subscribe(');
  });
});
