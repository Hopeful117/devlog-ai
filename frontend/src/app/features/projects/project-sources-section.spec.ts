import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, Subject, throwError } from 'rxjs';

import { ProjectSourcesSection } from './project-sources-section';
import { CreateSourceRequest, SourceDetail } from './source.models';
import { SourceService } from './source.service';

const projectId = 'a1ee6d55-e034-491a-a6e6-cdad70573b24';
const source: SourceDetail = {
  id: '0bc4252e-bd52-4a98-8337-622f81c4d4fa',
  projectId,
  type: 'GIT_REPOSITORY',
  name: 'Core repository',
  repositoryUrl: 'https://example.test/core.git',
  defaultBranch: 'main',
  provider: 'GENERIC_GIT',
  active: true,
  lastSynchronizedAt: null,
  createdAt: '2026-07-20T10:00:00Z',
  updatedAt: '2026-07-22T12:00:00Z',
};
const createRequest: CreateSourceRequest = {
  projectId,
  type: 'GIT_REPOSITORY',
  name: source.name,
  repositoryUrl: source.repositoryUrl,
  defaultBranch: source.defaultBranch,
  provider: source.provider,
};

describe('ProjectSourcesSection', () => {
  const getSourcesByProject = vi.fn();
  const createSource = vi.fn();
  const setSourceActive = vi.fn();

  beforeEach(async () => {
    getSourcesByProject.mockReset();
    createSource.mockReset();
    setSourceActive.mockReset();
    await TestBed.configureTestingModule({
      imports: [ProjectSourcesSection],
      providers: [
        {
          provide: SourceService,
          useValue: { getSourcesByProject, createSource, setSourceActive },
        },
      ],
    }).compileComponents();
  });

  function render(
    sources$: Observable<readonly SourceDetail[]> = of([source]),
  ): ComponentFixture<ProjectSourcesSection> {
    getSourcesByProject.mockReturnValue(sources$);
    const fixture = TestBed.createComponent(ProjectSourcesSection);
    fixture.componentInstance.projectId = projectId;
    fixture.detectChanges();
    return fixture;
  }

  it('propagates the Project UUID and displays Source metadata', () => {
    const fixture = render();
    const element = fixture.nativeElement as HTMLElement;

    expect(getSourcesByProject).toHaveBeenCalledWith(projectId);
    expect(element.textContent).toContain('Core repository');
    expect(element.textContent).toContain(source.repositoryUrl);
    expect(element.textContent).toContain('Active');
  });

  it('displays the empty Sources state', () => {
    const fixture = render(of([]));
    expect(fixture.nativeElement.querySelector('[data-testid="sources-empty"]')).toBeTruthy();
  });

  it('displays Sources loading independently', () => {
    const fixture = render(new Subject<readonly SourceDetail[]>());
    expect(fixture.nativeElement.textContent).toContain('Loading Sources');
  });

  it('displays a Sources request error', () => {
    const fixture = render(
      throwError(() => new HttpErrorResponse({ status: 0, statusText: 'Unknown Error' })),
    );
    expect(fixture.nativeElement.textContent).toContain('Java Core is unavailable');
  });

  it('creates a Source and reloads the list from the backend', () => {
    createSource.mockReturnValue(of(source));
    const fixture = render();

    fixture.componentInstance.createSource(createRequest);
    fixture.detectChanges();

    expect(createSource).toHaveBeenCalledWith(createRequest);
    expect(getSourcesByProject).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('Source created successfully');
  });

  it('displays backend validation errors without refreshing', () => {
    createSource.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { message: 'Repository URL is invalid.' },
          }),
      ),
    );
    const fixture = render();

    fixture.componentInstance.createSource(createRequest);
    fixture.detectChanges();

    expect(getSourcesByProject).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.textContent).toContain('Repository URL is invalid');
  });

  it('deactivates a Source and reloads the list from the backend', () => {
    setSourceActive.mockReturnValue(of({ ...source, active: false }));
    const fixture = render();

    fixture.componentInstance.setActive(source, false);
    fixture.detectChanges();

    expect(setSourceActive).toHaveBeenCalledWith(source.id, false);
    expect(getSourcesByProject).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('now inactive');
  });

  it('does not implement an imperative subscription', () => {
    expect(ProjectSourcesSection.toString()).not.toContain('.subscribe(');
  });
});
