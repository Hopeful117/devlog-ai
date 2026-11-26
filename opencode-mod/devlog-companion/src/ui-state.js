export function sessionScope(session) {
  if (
    !session ||
    typeof session.key !== "string" ||
    typeof session.id !== "string" ||
    typeof session.server?.id !== "string" ||
    typeof session.location?.directory !== "string"
  ) {
    return undefined;
  }

  return JSON.stringify([
    session.server.id,
    session.project?.id ?? null,
    session.location.directory,
    session.location.workspaceID ?? null,
    session.key,
    session.id,
  ]);
}

export function createScopedRequest(scope) {
  const controller = new AbortController();
  let active = true;
  return Object.freeze({
    signal: controller.signal,
    accepts(currentScope) {
      return active && scope === currentScope;
    },
    dispose() {
      active = false;
      controller.abort();
    },
  });
}

export function scopedSnapshot(state, scope) {
  return scope && state?.scope === scope ? state : undefined;
}

export function updateScopedSnapshot(state, requestScope, currentScope, update) {
  if (!requestScope || requestScope !== currentScope) return state;
  const scope = requestScope;
  return { scope, ...update };
}
