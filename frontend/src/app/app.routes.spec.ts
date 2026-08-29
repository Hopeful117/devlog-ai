import { routes } from './app.routes';

async function loadRouteComponent(path: string): Promise<{ readonly name: string } | undefined> {
  const analysesRoute = routes.find((route) => route.path === 'analyses/:id');
  const targetRoute = analysesRoute?.children?.find((route) => route.path === path);
  if (!targetRoute?.loadComponent) return undefined;
  const loaded = await targetRoute.loadComponent();
  if (loaded && typeof loaded === 'object' && 'default' in loaded) {
    return loaded.default as { readonly name: string };
  }
  return loaded as { readonly name: string };
}

describe('app.routes', () => {
  it('routes /analyses/:id/result to the canonical AnalysisResultPage', async () => {
    const component = await loadRouteComponent('result');
    expect(component?.name.endsWith('AnalysisResultPage')).toBe(true);
  });

  it('redirects /analyses/:id to /analyses/:id/result', () => {
    const analysesRoute = routes.find((route) => route.path === 'analyses/:id');
    const redirectRoute = analysesRoute?.children?.find((route) => route.path === '');

    expect(redirectRoute?.pathMatch).toBe('full');
    expect(redirectRoute?.redirectTo).toBe('result');
  });

  it('routes /analyses/:id/diagnostics to AnalysisDiagnosticsPage', async () => {
    const component = await loadRouteComponent('diagnostics');
    expect(component?.name.endsWith('AnalysisDiagnosticsPage')).toBe(true);
  });

  it('keeps /analyses/:id/proposal-review reachable before the default redirect', async () => {
    const component = await loadRouteComponent('proposal-review');
    expect(component?.name.endsWith('ProposalReviewPage')).toBe(true);
  });
});
