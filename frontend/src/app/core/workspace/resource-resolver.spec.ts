import { TestBed } from '@angular/core/testing';
import { WorkspaceResourceResolver } from './resource-resolver';

describe('WorkspaceResourceResolver', () => {
  let resolver: WorkspaceResourceResolver;

  beforeEach(() => {
    resolver = TestBed.inject(WorkspaceResourceResolver);
  });

  it('resolves the canonical DevLog freshness resource to the project cockpit', () => {
    expect(resolver.resolve('devlog://projects/devlog-ai/freshness')).toEqual({
      status: 'resolved',
      canonicalReference: 'devlog://projects/devlog-ai/freshness',
      destination: '/projects/devlog-ai',
    });
  });

  it('rejects malformed resources and missing project slugs', () => {
    expect(resolver.resolve('devlog://projects//freshness').status).toBe('unsupported');
    expect(resolver.resolve('devlog://projects/devlog-ai/freshness/extra').status).toBe(
      'unsupported',
    );
    expect(resolver.resolve(null).status).toBe('unsupported');
  });

  it('rejects unsupported schemes and DevLog resource shapes', () => {
    expect(resolver.resolve('https://example.com/projects/devlog-ai/freshness').status).toBe(
      'unsupported',
    );
    expect(resolver.resolve('javascript:alert(1)').status).toBe('unsupported');
    expect(resolver.resolve('devlog://projects/devlog-ai/context').status).toBe('unsupported');
  });

  it('validates project slugs before constructing a destination', () => {
    expect(resolver.resolve('devlog://projects/DEVLOG_AI/freshness').status).toBe('unsupported');
    expect(resolver.resolve('devlog://projects/devlog-ai%2Fother/freshness').status).toBe(
      'unsupported',
    );
  });
});
