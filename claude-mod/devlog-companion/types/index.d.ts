declare module 'claude-code' {
  interface PluginState {
    'devlog-companion': {
      ops: Array<{
        id: string
        tool: string
        startedAt: number
        duration: number | null
        status: 'running' | 'done' | 'failed'
        projectId?: string
        aiTaskId?: string
        intent?: string
      }>
      trace: {
        opId: string
        section: 'overview' | 'sources' | 'context' | 'diagnostics'
        data: Record<string, unknown> | null
        loading: boolean
        error: string | null
      } | null
      view: 'now' | 'context' | 'history'
      disconnected: boolean
    }
  }
}
