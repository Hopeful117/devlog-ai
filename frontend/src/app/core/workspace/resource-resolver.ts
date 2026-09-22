import { Injectable } from '@angular/core';

export type ResourceResolution =
  | {
      readonly status: 'resolved';
      readonly canonicalReference: string;
      readonly destination: string;
    }
  | { readonly status: 'unsupported'; readonly reason: string };

const FRESHNESS_REFERENCE = /^devlog:\/\/projects\/([a-z0-9](?:[a-z0-9-]{0,62}))\/freshness$/;

@Injectable({ providedIn: 'root' })
export class WorkspaceResourceResolver {
  resolve(reference: string | null | undefined): ResourceResolution {
    if (!reference) {
      return { status: 'unsupported', reason: 'No resource reference was provided.' };
    }

    const match = FRESHNESS_REFERENCE.exec(reference);
    if (!match) {
      return { status: 'unsupported', reason: 'This Workspace resource is not supported.' };
    }

    const projectSlug = match[1];
    return {
      status: 'resolved',
      canonicalReference: reference,
      destination: `/projects/${encodeURIComponent(projectSlug)}`,
    };
  }
}
