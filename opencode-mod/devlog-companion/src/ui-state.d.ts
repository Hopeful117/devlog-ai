export interface SessionScopeInput {
  key: string;
  id: string;
  server: { id: string };
  location?: { directory: string; workspaceID?: string };
  project?: { id?: string };
}

export interface ScopedSnapshot<T> {
  scope: string;
  value?: T;
  error: boolean;
}

export function sessionScope(session: SessionScopeInput | undefined): string | undefined;
export function createScopedRequest(scope: string): {
  readonly signal: AbortSignal;
  accepts(currentScope: string | undefined): boolean;
  dispose(): void;
};
export function scopedSnapshot<T>(
  state: ScopedSnapshot<T> | undefined,
  scope: string | undefined,
): ScopedSnapshot<T> | undefined;
export function updateScopedSnapshot<T>(
  state: ScopedSnapshot<T> | undefined,
  requestScope: string,
  currentScope: string | undefined,
  update: Omit<ScopedSnapshot<T>, "scope">,
): ScopedSnapshot<T> | undefined;
