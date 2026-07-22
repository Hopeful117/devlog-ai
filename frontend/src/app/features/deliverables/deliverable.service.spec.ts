import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { DeliverableService } from './deliverable.service';

describe('DeliverableService', () => {
  it('uses only Java Core deliverable endpoints', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_ENVIRONMENT, useValue: { backendBaseUrl: '' } },
      ],
    });
    const service = TestBed.inject(DeliverableService);
    const http = TestBed.inject(HttpTestingController);
    const payload = {
      projectId: 'project-id',
      type: 'README' as const,
      audience: 'Engineers',
      style: 'Concise',
      language: 'en',
    };
    service.generate(payload).subscribe();
    const create = http.expectOne('/api/v1/deliverables');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(payload);
    create.flush({ id: 'deliverable-id' });
    service.getByProject('project-id').subscribe();
    const list = http.expectOne('/api/v1/deliverables/project/project-id');
    expect(list.request.method).toBe('GET');
    list.flush([]);
    service.get('deliverable-id').subscribe();
    const detail = http.expectOne('/api/v1/deliverables/deliverable-id');
    expect(detail.request.method).toBe('GET');
    detail.flush({});
    http.verify();
  });
});
