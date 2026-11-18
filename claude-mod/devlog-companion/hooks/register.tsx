// devlog-companion v0.2.0
// First version tracked in source control.
// Requires: MCP server sending MCP_TOOL_RESPONDED / MCP_TOOL_FAILED event types (fixed in this release).
import { atom, read, update } from 'claude-code'
import type { Register } from 'claude-code'
import type {} from '../types'

type OpStatus = 'running' | 'done' | 'failed'
type Op = {
  id: string
  tool: string
  startedAt: number
  duration: number | null
  status: OpStatus
  projectId?: string
  aiTaskId?: string
  intent?: string
}

type TraceSection = 'overview' | 'sources' | 'context' | 'diagnostics'
type TraceState = { opId: string; section: TraceSection; data: Record<string, unknown> | null; loading: boolean; error: string | null }

const ops        = atom({ plugin: 'devlog-companion', key: 'ops' }   as const, [] as Op[])
const traceState = atom({ plugin: 'devlog-companion', key: 'trace' } as const, null as TraceState | null)

const PREFIX = 'mcp__devlog__'
const PANE   = 'devlog-companion'
const MAX    = 20
const FONT   = "-apple-system,BlinkMacSystemFont,'SF Pro Text','Segoe UI',sans-serif"

// ── Helpers ───────────────────────────────────────────────────────────────────

const ESC: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }
const xml    = (s: string): string => s.replace(/[&<>"]/g, c => ESC[c] ?? c)
const fmtDur = (ms: number): string => ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms}ms`
const dot    = (s: OpStatus): string => s === 'done' ? '#3fb950' : s === 'failed' ? '#f85149' : '#fbbf24'
const glyph  = (s: OpStatus): string => s === 'done' ? '✓' : s === 'failed' ? '✗' : '◎'
const relTime = (ms: number, now: number): string => {
  const s = Math.floor((now - ms) / 1000)
  return s < 60 ? `${s}s ago` : `${Math.floor(s / 60)}m ago`
}

// ── SVG band (AbovePrompt, desktop) ──────────────────────────────────────────

const bandSvg = (list: Op[], W: number, now: number): string => {
  const running = list.some(o => o.status === 'running')
  const color   = running ? '#fbbf24' : '#3fb950'
  const dotEl   = running
    ? `<circle cx="8" cy="11" r="4" fill="${color}"><animate attributeName="opacity" values="1;0.3;1" dur="1.2s" repeatCount="indefinite"/></circle>`
    : `<circle cx="8" cy="11" r="4" fill="${color}"/>`
  const label = `DevLog  ${list.length} op${list.length !== 1 ? 's' : ''}`
  const last  = list[0]
  const sub   = last != null
    ? `  ·  ${xml(last.tool)}${last.duration != null ? '  ' + fmtDur(last.duration) : ''}`
    : ''
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="22" viewBox="0 0 ${W} 22">
<style>.t{fill:#1f1f1f}.m{fill:#8b949e}@media (prefers-color-scheme:dark){.t{fill:#e6edf3}.m{fill:#8b949e}}</style>
${dotEl}
<text class="t" x="20" y="15.5" font-family="${FONT}" font-size="13" font-weight="500">${xml(label)}</text>
<text class="m" x="${20 + label.length * 7.2}" y="15.5" font-family="${FONT}" font-size="12">${xml(sub)}</text>
</svg>`
}

// ── SVG trace label row ───────────────────────────────────────────────────────

const traceLabelSvg = (label: string, value: string, W: number): string =>
  `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="22" viewBox="0 0 ${W} 22">
<style>.t{fill:#1f1f1f}.m{fill:#8b949e}@media (prefers-color-scheme:dark){.t{fill:#e6edf3}.m{fill:#8b949e}}</style>
<text class="m" x="2" y="15.5" font-family="${FONT}" font-size="11">${xml(label)}</text>
<text class="t" x="90" y="15.5" font-family="${FONT}" font-size="11.5">${xml(value.substring(0, 80))}</text>
</svg>`

const sourceRowSvg = (type: string, title: string, W: number): string =>
  `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="22" viewBox="0 0 ${W} 22">
<style>.t{fill:#1f1f1f}.m{fill:#8b949e}.tag{fill:#0d1117;stroke:#30363d;stroke-width:1}@media (prefers-color-scheme:dark){.t{fill:#e6edf3}.tag{fill:#161b22}}</style>
<rect class="tag" x="2" y="4" width="${type.length * 7 + 8}" height="14" rx="3"/>
<text class="m" x="6" y="14.5" font-family="${FONT}" font-size="10">${xml(type)}</text>
<text class="t" x="${type.length * 7 + 16}" y="15.5" font-family="${FONT}" font-size="11.5">${xml(title.substring(0, 70))}</text>
</svg>`

// ── register ──────────────────────────────────────────────────────────────────

export const register: Register = (on) => {

  on('session.start', async ($, e, next) => {
    const r = await next(e)
    await $.command.register({ name: 'devlog-companion', description: 'Show or hide DevLog Companion panel' })
    return r
  })

  on('command.run', { command: 'devlog-companion' }, async ($, e) => {
    const panes  = await $.ui.panes()
    const isOpen = panes.some(p => p.id === PANE)
    if (isOpen) { await $.ui.close({ id: PANE }); return { text: 'DevLog Companion closed.' } }
    await $.ui.open({ id: PANE, title: 'DevLog Companion' })
    return { text: 'DevLog Companion opened.' }
  })

  on('tool.call', async ($, e, next) => {
    const ev   = e as unknown as { tool?: string; input?: Record<string, unknown> }
    const name = typeof ev.tool === 'string' ? ev.tool : ''
    if (!name.startsWith(PREFIX)) return next(e)
    const shortName = name.slice(PREFIX.length)
    const id        = `${shortName}-${Date.now()}`
    const startedAt = await $.clock.now()
    const input     = ev.input ?? {}
    const projectId = typeof input.projectId === 'string' ? input.projectId : undefined
    const intent    = typeof input.intent === 'string' ? input.intent : undefined
    await update($, ops, list => [
      { id, tool: shortName, startedAt, duration: null, status: 'running' as OpStatus, projectId, intent },
      ...list
    ].slice(0, MAX))
    try {
      const result = await next(e)
      const duration = (await $.clock.now()) - startedAt
      // Extract aiTaskId from tool response (devlog_story_agent returns JSON with aiTaskId)
      let aiTaskId: string | undefined
      try {
        const r = result as unknown as { text?: string; content?: Array<{ text?: string }> }
        const rawText = typeof r?.text === 'string' ? r.text
          : (Array.isArray(r?.content) && typeof r.content[0]?.text === 'string' ? r.content[0].text : '')
        if (rawText) {
          const payload = JSON.parse(rawText) as Record<string, unknown>
          if (typeof payload.aiTaskId === 'string' && payload.aiTaskId.length > 30) {
            aiTaskId = payload.aiTaskId
          }
        }
      } catch {}
      await update($, ops, list => list.map(o =>
        o.id === id ? { ...o, status: 'done' as OpStatus, duration, aiTaskId } : o))
      return result
    } catch (err) {
      const duration = (await $.clock.now()) - startedAt
      await update($, ops, list => list.map(o =>
        o.id === id ? { ...o, status: 'failed' as OpStatus, duration } : o))
      throw err
    }
  })

  // Band above the prompt
  on('ui.render', { component: 'AbovePrompt' }, async ($, e, next) => {
    const list = await read($, ops)
    if (list.length === 0) return next(e)
    const drawn = await Promise.resolve(next(e)).catch(() => null)
    const below = drawn && typeof drawn === 'object' && (drawn as { type?: string }).type !== 'engine' ? drawn : null
    const ui    = $.ui.resolve(e)
    const { Box, Text } = ui
    const isRunning = list.some(o => o.status === 'running')
    const last = list[0]

    if (e.surface !== 'terminal' && 'Svg' in ui) {
      const { Svg } = ui
      const W   = Math.max(180, Math.min(1600, (e.props.bodyColumns || 100) * 8 - 96))
      const now = Date.now()
      return (
        <Box flexDirection="column">
          <Svg source={bandSvg(list, W, now)} alt={`DevLog ${list.length} ops`} width={W} height={22} />
          {below}
        </Box>
      )
    }

    return (
      <Box flexDirection="column">
        <Box flexDirection="row" gap={1}>
          <Text color={isRunning ? '#fbbf24' : '#3fb950'}>{isRunning ? '◎' : '●'}</Text>
          <Text dimColor>DevLog</Text>
          <Text>{list.length} ops</Text>
          {last != null ? <Text dimColor>· {last.tool}{last.duration != null ? ` ${fmtDur(last.duration)}` : ''}</Text> : null}
        </Box>
        {below}
      </Box>
    )
  })

  // Side pane
  on('ui.render', { component: 'Pane', requestId: PANE }, async ($, e) => {
    const list  = await read($, ops)
    const trace = await read($, traceState)
    const ui    = $.ui.resolve(e)
    const { Box, Text, Button } = ui
    const now   = Date.now()

    // Fetch context trace via backend REST API
    const fetchTrace = async (op: Op) => {
      if (!op.aiTaskId) {
        await update($, traceState, s => s ? {
          ...s, error: 'Pas de Context Trace disponible — cet appel a été enregistré avant l\'activation du Context Inspector. Invoquez un nouvel outil DevLog pour obtenir un trace inspectable.', loading: false
        } : null)
        return
      }
      try {
        const home   = await $.env.get('HOME') ?? '/home/ludo'
        const secret = (await $.fs.read(`${home}/.config/opencode/devlog-mcp-client-secret`)).trim()
        const tokenRes = await $.process.run([
          'curl', '-s', '-X', 'POST',
          'http://127.0.0.1:18084/realms/devlog/protocol/openid-connect/token',
          '-H', 'Content-Type: application/x-www-form-urlencoded',
          '--data-urlencode', 'grant_type=client_credentials',
          '--data-urlencode', 'client_id=devlog-mcp-opencode',
          '--data-urlencode', `client_secret=${secret}`
        ])
        const tokenData = JSON.parse(tokenRes.stdout) as Record<string, unknown>
        const token = typeof tokenData.access_token === 'string' ? tokenData.access_token : ''
        if (!token) throw new Error(`Token error: ${tokenRes.stdout.substring(0, 120)}`)
        const traceRes = await $.process.run([
          'curl', '-s', '-H', `Authorization: Bearer ${token}`,
          `http://localhost:18080/api/v1/context-traces/${op.aiTaskId}`
        ])
        const data = JSON.parse(traceRes.stdout) as Record<string, unknown>
        if (data.status === 404 || (typeof data.error === 'string' && data.status !== 200)) {
          throw new Error(typeof data.message === 'string' ? data.message : `HTTP ${data.status ?? '?'}`)
        }
        await update($, traceState, s => s ? { ...s, data, loading: false } : null)
      } catch (err) {
        await update($, traceState, s => s ? { ...s, error: String(err), loading: false } : null)
      }
    }

    // ── Detail view ───────────────────────────────────────────────────────────
    if (trace !== null) {
      const sections: TraceSection[] = ['overview', 'sources', 'context', 'diagnostics']
      const sectionLabels: Record<TraceSection, string> = {
        overview: 'Overview', sources: 'Sources', context: 'Context', diagnostics: 'Diagnostics'
      }

      const renderSectionTabs = () => (
        <Box flexDirection="row" gap={1} paddingX={1} paddingBottom={1}>
          {sections.map(s => (
            <Button key={s} plain
              onPress={async () => { await update($, traceState, prev => prev ? { ...prev, section: s } : null) }}>
              <Text
                bold={trace.section === s}
                dimColor={trace.section !== s}
                color={trace.section === s ? '#58a6ff' : undefined}>
                {sectionLabels[s]}
              </Text>
            </Button>
          ))}
        </Box>
      )

      const renderContent = () => {
        if (trace.loading) return <Box padding={1}><Text dimColor>Loading context trace…</Text></Box>
        if (trace.error)   return <Box padding={1}><Text color="#f85149">{trace.error}</Text></Box>
        if (!trace.data)   return <Box padding={1}><Text dimColor>No data available.</Text></Box>

        const d = trace.data

        if (trace.section === 'overview') {
          const fields: Array<[string, unknown]> = [
            ['Tool',      d.toolName],
            ['Status',    d.status],
            ['Project',   d.projectSlug],
            ['Story',     d.storyId],
            ['Intent',    d.intent],
            ['Duration',  d.durationMs != null ? fmtDur(d.durationMs as number) : null],
            ['Freshness', d.freshnessState],
            ['Revision',  d.freshnessRevision],
          ]
          if (e.surface === 'desktop' && 'Svg' in ui) {
            const { Svg } = ui
            const W = Math.max(200, Math.min(700, (e.props.bodyColumns || 50) * 8 - 16))
            return (
              <Box flexDirection="column">
                {fields.filter(([, v]) => v != null && v !== '').map(([label, value]) => (
                  <Svg key={label}
                    source={traceLabelSvg(label, String(value), W)}
                    alt={`${label}: ${value}`} width={W} height={22} />
                ))}
              </Box>
            )
          }
          return (
            <Box flexDirection="column" gap={0}>
              {fields.filter(([, v]) => v != null && v !== '').map(([label, value]) => (
                <Box key={label} flexDirection="row" gap={1}>
                  <Text dimColor>{label.padEnd(10)}</Text>
                  <Text>{String(value)}</Text>
                </Box>
              ))}
            </Box>
          )
        }

        if (trace.section === 'sources') {
          const sources = Array.isArray(d.sources)
            ? d.sources as Array<{ type: string; id: string | null; title: string | null }>
            : []
          const accounting = d.accounting as Record<string, unknown> | null
          const total    = (accounting?.candidateCount ?? accounting?.totalCandidates) as number | null
          const selected = accounting?.selectedCount as number | null
          const summary  = total != null && selected != null
            ? `${selected} selected / ${total} candidates`
            : sources.length > 0 ? `${sources.length} sources` : 'No sources'
          if (sources.length === 0) {
            return <Box padding={1}><Text dimColor>{d.contextAvailable ? 'No sources in context.' : 'Context not available — no correlation ID yet.'}</Text></Box>
          }
          if (e.surface === 'desktop' && 'Svg' in ui) {
            const { Svg } = ui
            const W = Math.max(200, Math.min(700, (e.props.bodyColumns || 50) * 8 - 16))
            return (
              <Box flexDirection="column">
                <Box paddingX={1} paddingBottom={1}><Text dimColor>{summary}</Text></Box>
                {sources.map((src, i) => (
                  <Svg key={i}
                    source={sourceRowSvg(src.type ?? '?', src.title ?? src.id ?? '—', W)}
                    alt={`${src.type} ${src.title}`} width={W} height={22} />
                ))}
              </Box>
            )
          }
          return (
            <Box flexDirection="column" gap={0}>
              <Box paddingBottom={1}><Text dimColor>{summary}</Text></Box>
              {sources.map((src, i) => (
                <Box key={i} flexDirection="row" gap={1}>
                  <Text dimColor>[{src.type ?? '?'}]</Text>
                  <Text wrap="truncate-end">{src.title ?? src.id ?? '—'}</Text>
                </Box>
              ))}
            </Box>
          )
        }

        if (trace.section === 'context') {
          const accounting = d.accounting as Record<string, unknown> | null
          const fields: Array<[string, unknown]> = [
            ['contextDigest',    d.contextDigest],
            ['projectionDigest', d.projectionDigest],
            ['candidateCount',   accounting?.candidateCount],
            ['selectedCount',    accounting?.selectedCount],
            ['discardedCount',   accounting?.discardedCount],
            ['budget',           accounting?.budget],
            ['usedTokens',       accounting?.usedTokens],
          ]
          if (e.surface === 'desktop' && 'Svg' in ui) {
            const { Svg } = ui
            const W = Math.max(200, Math.min(700, (e.props.bodyColumns || 50) * 8 - 16))
            return (
              <Box flexDirection="column">
                {fields.filter(([, v]) => v != null).map(([label, value]) => (
                  <Svg key={label}
                    source={traceLabelSvg(label, String(value), W)}
                    alt={`${label}: ${value}`} width={W} height={22} />
                ))}
              </Box>
            )
          }
          return (
            <Box flexDirection="column" gap={0}>
              {fields.filter(([, v]) => v != null).map(([label, value]) => (
                <Box key={label} flexDirection="row" gap={1}>
                  <Text dimColor>{label.padEnd(18)}</Text>
                  <Text>{String(value)}</Text>
                </Box>
              ))}
            </Box>
          )
        }

        if (trace.section === 'diagnostics') {
          const hasFailure = d.failureCode || d.failureMessage
          const hasStop    = d.investigationStopReason
          const noIssues   = !hasFailure && !hasStop
          return (
            <Box flexDirection="column" gap={1} padding={1}>
              {hasFailure ? (
                <Box flexDirection="column">
                  <Text color="#f85149">Failure: {String(d.failureCode ?? '')}</Text>
                  {d.failureMessage ? <Text dimColor>{String(d.failureMessage).substring(0, 200)}</Text> : null}
                </Box>
              ) : null}
              {hasStop ? (
                <Box flexDirection="column">
                  <Text color="#fbbf24">Investigation stopped</Text>
                  <Text dimColor>{String(d.investigationStopReason).substring(0, 200)}</Text>
                </Box>
              ) : null}
              {noIssues ? <Text dimColor>No errors or diagnostics detected.</Text> : null}
            </Box>
          )
        }
        return null
      }

      return (
        <Box flexDirection="column">
          <Box paddingX={1} paddingBottom={1} flexDirection="column" gap={0}>
            <Box flexDirection="row" gap={1}>
              <Button key="back" plain onPress={async () => { await update($, traceState, () => null) }}>
                <Text dimColor>← Back</Text>
              </Button>
              <Text bold>Context Trace</Text>
            </Box>
            {(() => { const op = list.find(o => o.id === trace.opId); return op?.intent
              ? <Text dimColor wrap="truncate-end">{op.intent}</Text>
              : <Text dimColor>{op?.tool ?? '?'}</Text> })()}
          </Box>
          {renderSectionTabs()}
          {renderContent()}
        </Box>
      )
    }

    // ── List view ─────────────────────────────────────────────────────────────
    if (list.length === 0) {
      return <Box padding={1}><Text dimColor>No DevLog MCP operations yet. Invoke a DevLog tool to start.</Text></Box>
    }

    return (
      <Box flexDirection="column">
        <Box paddingX={1} paddingBottom={1} flexDirection="row" gap={1}>
          <Text bold>DevLog Activity</Text>
          <Text dimColor>· {list.length} ops</Text>
        </Box>
        {list.map(op => {
          const dur  = op.duration != null ? fmtDur(op.duration) : '…'
          const when = relTime(op.startedAt, now)
          const handleInspect = async () => {
            await update($, traceState, () => ({
              opId: op.id,
              section: 'overview' as TraceSection,
              data: null,
              loading: true,
              error: null
            }))
            void fetchTrace(op)
          }
          const hasTrace = !!op.aiTaskId
          const label = op.intent ? op.intent.substring(0, 55) : op.tool
          return (
            <Button key={op.id} plain onPress={handleInspect}>
              <Text color={hasTrace ? dot(op.status) : undefined} dimColor={!hasTrace}>{hasTrace ? glyph(op.status) : '○'}</Text>
              {' '}<Text dimColor={!hasTrace}>{label}</Text>{' '}
              <Text dimColor>{dur}  ·  {when}</Text>
            </Button>
          )
        })}
      </Box>
    )
  })
}
