import { HttpErrorResponse } from '@angular/common/http';

export type RequestErrorKind =
  | 'unavailable'
  | 'not-found'
  | 'validation'
  | 'conflict'
  | 'invalid-revision'
  | 'no-active-source'
  | 'unsupported-intent'
  | 'generic';

export interface RequestError {
  readonly kind: RequestErrorKind;
  readonly message: string;
  readonly status: number;
}

interface ApiErrorBody {
  readonly message?: unknown;
}

function backendMessage(error: HttpErrorResponse): string | undefined {
  const body = error.error as ApiErrorBody | null;
  return typeof body?.message === 'string' ? body.message : undefined;
}

export function toRequestError(
  error: unknown,
  subject:
    | 'project'
    | 'source'
    | 'sources'
    | 'analysis'
    | 'diagnostics'
    | 'proposal'
    | 'insight'
    | 'deliverable' = 'project',
): RequestError {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return {
        kind: 'unavailable',
        message: 'The Java Core is unavailable. Check that the backend is running.',
        status: error.status,
      };
    }

    if (error.status === 404) {
      return {
        kind: 'not-found',
        message: backendMessage(error) ?? `The requested ${subject} was not found.`,
        status: error.status,
      };
    }

    if (error.status === 400 || error.status === 422) {
      const message = backendMessage(error) ?? 'The submitted data is invalid.';
      const normalized = message.toLowerCase();
      return {
        kind: normalized.includes('revision')
          ? 'invalid-revision'
          : normalized.includes('active source')
            ? 'no-active-source'
            : normalized.includes('intent')
              ? 'unsupported-intent'
              : 'validation',
        message,
        status: error.status,
      };
    }

    if (error.status === 409) {
      return {
        kind: 'conflict',
        message: backendMessage(error) ?? 'The source conflicts with existing data.',
        status: error.status,
      };
    }

    return {
      kind: 'generic',
      message: `The ${subject} request failed. Please try again.`,
      status: error.status,
    };
  }

  return {
    kind: 'generic',
    message: `The ${subject} request failed. Please try again.`,
    status: 0,
  };
}
