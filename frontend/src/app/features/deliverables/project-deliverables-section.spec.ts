import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { DeliverableService } from './deliverable.service';
import { ProjectDeliverablesSection } from './project-deliverables-section';

describe('ProjectDeliverablesSection', () => {
  const service = {
    getByProject: vi.fn(() => of([])),
    generate: vi.fn(() => of({ id: 'deliverable-id' })),
  };
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProjectDeliverablesSection],
      providers: [provideRouter([]), { provide: DeliverableService, useValue: service }],
    }).compileComponents();
  });
  it('generates, reloads the backend list, and navigates to the persisted result', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ProjectDeliverablesSection);
    fixture.componentInstance.projectId = 'project-id';
    fixture.detectChanges();
    fixture.componentInstance.generate({
      projectId: 'project-id',
      type: 'README',
      audience: 'Engineers',
      style: 'Concise',
      language: 'en',
    });
    fixture.detectChanges();
    expect(service.generate).toHaveBeenCalled();
    expect(service.getByProject).toHaveBeenCalledTimes(2);
    expect(navigate).toHaveBeenCalledWith(['/deliverables', 'deliverable-id']);
  });
  it('contains no manual subscription', () =>
    expect(ProjectDeliverablesSection.toString()).not.toContain('.subscribe('));
});
