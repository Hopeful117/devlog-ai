import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, Subject, throwError } from 'rxjs';

import {
  CreateProjectHumanContextInputRequest,
  ProjectHumanContextInput,
} from './project-context-input.models';
import { ProjectContextInputService } from './project-context-input.service';
import { ProjectContextInputsSection } from './project-context-inputs-section';

const projectId = 'a1ee6d55-e034-491a-a6e6-cdad70573b24';
const input: ProjectHumanContextInput = {
  id: '0bc4252e-bd52-4a98-8337-622f81c4d4fa',
  projectId,
  title: 'Medium-term goal',
  contentMarkdown: 'Improve semantic usefulness for humans and agents.',
  type: 'GOAL',
  status: 'ACTIVE',
  createdAt: '2026-08-13T10:00:00Z',
  updatedAt: '2026-08-13T10:00:00Z',
};

const createRequest: CreateProjectHumanContextInputRequest = {
  title: input.title,
  contentMarkdown: input.contentMarkdown,
  type: input.type,
};

describe('ProjectContextInputsSection', () => {
  const getByProject = vi.fn();
  const create = vi.fn();
  const archive = vi.fn();

  beforeEach(async () => {
    getByProject.mockReset();
    create.mockReset();
    archive.mockReset();
    await TestBed.configureTestingModule({
      imports: [ProjectContextInputsSection],
      providers: [
        {
          provide: ProjectContextInputService,
          useValue: { getByProject, create, archive },
        },
      ],
    }).compileComponents();
  });

  function render(
    inputs$: Observable<readonly ProjectHumanContextInput[]> = of([input]),
  ): ComponentFixture<ProjectContextInputsSection> {
    getByProject.mockReturnValue(inputs$);
    const fixture = TestBed.createComponent(ProjectContextInputsSection);
    fixture.componentInstance.projectId = projectId;
    fixture.detectChanges();
    return fixture;
  }

  it('propagates the project id and displays the saved note', () => {
    const fixture = render();
    const element = fixture.nativeElement as HTMLElement;

    expect(getByProject).toHaveBeenCalledWith(projectId);
    // Note content is rendered by ngx-markdown; check title and type are present
    expect(element.textContent).toContain('Medium-term goal');
    expect(element.textContent).toContain('GOAL');
    expect(element.textContent).toContain('Active');
  });

  it('displays the empty state', () => {
    const fixture = render(of([]));
    expect(
      fixture.nativeElement.querySelector('[data-testid="context-inputs-empty"]'),
    ).toBeTruthy();
  });

  it('displays loading state independently', () => {
    const fixture = render(new Subject<readonly ProjectHumanContextInput[]>());
    expect(fixture.nativeElement.textContent).toContain('Loading Project Notes');
  });

  it('displays request errors', () => {
    const fixture = render(
      throwError(() => new HttpErrorResponse({ status: 0, statusText: 'Unknown Error' })),
    );
    expect(fixture.nativeElement.textContent).toContain('Java Core is unavailable');
  });

  it('creates a project note and reloads the list', () => {
    create.mockReturnValue(of(input));
    const fixture = render();

    fixture.componentInstance.form.setValue({
      title: input.title,
      type: input.type,
      contentMarkdown: input.contentMarkdown,
    });
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(create).toHaveBeenCalledWith(projectId, createRequest);
    expect(getByProject).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('Project note saved successfully');
  });

  it('archives an active project note and reloads the list', () => {
    archive.mockReturnValue(of({ ...input, status: 'ARCHIVED' as const }));
    const fixture = render();

    fixture.componentInstance.archive(input);
    fixture.detectChanges();

    expect(archive).toHaveBeenCalledWith(projectId, input.id);
    expect(getByProject).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('archived successfully');
  });

  it('does not implement an imperative subscription', () => {
    expect(ProjectContextInputsSection.toString()).not.toContain('.subscribe(');
  });
});
