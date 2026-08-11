import { HttpErrorResponse } from '@angular/common/http';

import { toRequestError } from './request-error';

describe('toRequestError', () => {
  it('maps status 0 to an unavailable error', () => {
    const result = toRequestError(new HttpErrorResponse({ status: 0 }), 'insight');
    expect(result.kind).toBe('unavailable');
    expect(result.status).toBe(0);
    expect(result.message).toContain('backend is running');
  });

  it('maps 404 to a not-found error with the subject name', () => {
    const result = toRequestError(new HttpErrorResponse({ status: 404 }), 'source');
    expect(result.kind).toBe('not-found');
    expect(result.status).toBe(404);
    expect(result.message).toContain('source was not found');
  });

  it('prefers the backend message over the subject default on 404', () => {
    const result = toRequestError(
      new HttpErrorResponse({ status: 404, error: { message: 'No such project' } }),
      'project',
    );
    expect(result.message).toBe('No such project');
  });

  it('maps 400 without hints to a validation error', () => {
    const result = toRequestError(new HttpErrorResponse({ status: 400 }), 'project');
    expect(result.kind).toBe('validation');
    expect(result.status).toBe(400);
    expect(result.message).toContain('invalid');
  });

  it('maps 422 to a validation error', () => {
    const result = toRequestError(new HttpErrorResponse({ status: 422 }), 'analysis');
    expect(result.kind).toBe('validation');
    expect(result.status).toBe(422);
  });

  it('classifies a revision hint as invalid-revision', () => {
    const result = toRequestError(
      new HttpErrorResponse({ status: 400, error: { message: 'Unknown revision abc' } }),
      'analysis',
    );
    expect(result.kind).toBe('invalid-revision');
    expect(result.message).toBe('Unknown revision abc');
  });

  it('classifies an active-source hint as no-active-source', () => {
    const result = toRequestError(
      new HttpErrorResponse({ status: 422, error: { message: 'No active source is available' } }),
      'analysis',
    );
    expect(result.kind).toBe('no-active-source');
    expect(result.message).toBe('No active source is available');
  });

  it('classifies an intent hint as unsupported-intent', () => {
    const result = toRequestError(
      new HttpErrorResponse({ status: 400, error: { message: 'Unsupported intent dialog-v2' } }),
      'analysis',
    );
    expect(result.kind).toBe('unsupported-intent');
    expect(result.message).toBe('Unsupported intent dialog-v2');
  });

  it('maps 409 to a conflict error', () => {
    const result = toRequestError(new HttpErrorResponse({ status: 409 }), 'source');
    expect(result.kind).toBe('conflict');
    expect(result.status).toBe(409);
    expect(result.message).toContain('conflicts');
  });

  it('maps other statuses to a generic error mentioning the subject', () => {
    const result = toRequestError(new HttpErrorResponse({ status: 500 }), 'deliverable');
    expect(result.kind).toBe('generic');
    expect(result.message).toContain('deliverable request failed');
  });

  it('maps non-HTTP errors to a generic error with status 0', () => {
    const result = toRequestError(new TypeError('boom'), 'project');
    expect(result.kind).toBe('generic');
    expect(result.status).toBe(0);
    expect(result.message).toContain('project request failed');
  });
});
