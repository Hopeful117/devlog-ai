import { TestBed } from '@angular/core/testing';
import { ProposalReviewerSessionService } from './proposal-reviewer-session.service';

describe('ProposalReviewerSessionService', () => {
  const reviewer = '123e4567-e89b-42d3-a456-426614174000';
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({});
  });
  it('stores, reuses and clears an explicit session reviewer', () => {
    const service = TestBed.inject(ProposalReviewerSessionService);
    expect(service.get()).toBeNull();
    expect(service.set(reviewer)).toBe(true);
    expect(service.get()).toBe(reviewer);
    service.clear();
    expect(service.get()).toBeNull();
  });
  it('rejects malformed reviewer identifiers', () => {
    const service = TestBed.inject(ProposalReviewerSessionService);
    expect(service.set('reviewer')).toBe(false);
    expect(service.get()).toBeNull();
  });
});
