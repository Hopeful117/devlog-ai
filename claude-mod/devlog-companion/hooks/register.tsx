import { atom, read, update } from 'claude-code'
import type { Register } from 'claude-code'
import type {} from '../types'

// ── Types ─────────────────────────────────────────────────────────────────────

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

type View = 'now' | 'context' | 'history'
type CompanionState = 'IDLE' | 'WORKING' | 'READY' | 'ERROR' | 'DISCONNECTED'

// ── Atoms ─────────────────────────────────────────────────────────────────────

const ops        = atom({ plugin: 'devlog-companion', key: 'ops' }          as const, [] as Op[])
const traceState = atom({ plugin: 'devlog-companion', key: 'trace' }        as const, null as TraceState | null)
const activeView = atom({ plugin: 'devlog-companion', key: 'view' }         as const, 'now' as View, { shape: 'view-1' })
const isDisconn  = atom({ plugin: 'devlog-companion', key: 'disconnected' } as const, false as boolean, { shape: 'disconnected-1' })

// ── Constants ─────────────────────────────────────────────────────────────────

const PREFIX          = 'mcp__devlog__'
const PANE            = 'devlog-companion'
const MAX             = 20
const FONT            = "-apple-system,BlinkMacSystemFont,'SF Pro Text','Segoe UI',sans-serif"
const READY_WINDOW_MS = 10 * 60 * 1000

// ── State derivation ──────────────────────────────────────────────────────────

function deriveState(list: Op[], now: number, disco: boolean): CompanionState {
  if (disco) return 'DISCONNECTED'
  if (list.some(o => o.status === 'running')) return 'WORKING'
  const last = list[0]
  if (!last) return 'IDLE'
  if (last.status === 'failed') return 'ERROR'
  if (last.status === 'done' && (now - last.startedAt) < READY_WINDOW_MS) return 'READY'
  return 'IDLE'
}

const STATE_COLOR: Record<CompanionState, string> = {
  IDLE:         '#3fb950',
  WORKING:      '#fbbf24',
  READY:        '#58a6ff',
  ERROR:        '#f85149',
  DISCONNECTED: '#8b949e',
}

const STATE_LABEL: Record<CompanionState, string> = {
  IDLE:         'disponible',
  WORKING:      'en cours',
  READY:        'contexte prêt',
  ERROR:        'erreur',
  DISCONNECTED: 'déconnecté',
}

// ── Helpers ───────────────────────────────────────────────────────────────────

const ESC: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }
const xml    = (s: string): string => s.replace(/[&<>"]/g, c => ESC[c] ?? c)
const fmtDur = (ms: number): string => ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms}ms`
const relTime = (ms: number, now: number): string => {
  const s = Math.floor((now - ms) / 1000)
  return s < 60 ? `${s}s ago` : `${Math.floor(s / 60)}m ago`
}

// ── Avatar elements (20×20 viewBox, center 10,10) ─────────────────────────────

function avatarElements(state: CompanionState): string {
  const c       = STATE_COLOR[state]
  const isDisco   = state === 'DISCONNECTED'
  const isWorking = state === 'WORKING'
  const isIdle    = state === 'IDLE'
  const isReady   = state === 'READY'

  const satOp  = isDisco ? '0.25' : state === 'ERROR' ? '0.35' : '0.7'
  const ringOp = isDisco ? '0.2' : '0.4'
  const dash   = isDisco ? 'stroke-dasharray="2 3"' : ''

  let ringInner = ''
  if (isIdle)  ringInner = `<animate attributeName="opacity" values="${ringOp};0.75;${ringOp}" dur="2.4s" repeatCount="indefinite"/>`
  if (isReady) ringInner = `<animate attributeName="opacity" values="${ringOp};0.9;${ringOp};0.9;${ringOp}" dur="1.6s" repeatCount="1" fill="freeze"/>`

  const satContent =
    `<circle cx="10" cy="3.5" r="1.4" fill="${c}" opacity="${satOp}"/>` +
    `<circle cx="16.5" cy="13" r="1.4" fill="${c}" opacity="${satOp}"/>` +
    `<circle cx="3.5" cy="13" r="1.4" fill="${c}" opacity="${satOp}"/>`

  const satellites = isWorking
    ? `<g>${satContent}<animateTransform attributeName="transform" type="rotate" from="0 10 10" to="360 10 10" dur="1.6s" repeatCount="indefinite"/></g>`
    : `<g>${satContent}</g>`

  const lines = isWorking || isDisco ? '' :
    `<line x1="10" y1="6.5" x2="10" y2="4.9" stroke="${c}" stroke-width="0.8" opacity="0.3"/>` +
    `<line x1="13.4" y1="8.3" x2="14.8" y2="11.6" stroke="${c}" stroke-width="0.8" opacity="0.3"/>` +
    `<line x1="6.6" y1="8.3" x2="5.2" y2="11.6" stroke="${c}" stroke-width="0.8" opacity="0.3"/>`

  return `<circle cx="10" cy="10" r="3" fill="${c}" opacity="0.9"/>` +
    `<circle cx="10" cy="10" r="6.5" fill="none" stroke="${c}" stroke-width="1" ${dash} opacity="${ringOp}">${ringInner}</circle>` +
    lines + satellites
}

// Standalone avatar SVG (for Pane usage)
function avatarSvg(state: CompanionState, size: number): string {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 20 20">` +
    `<style>@media(prefers-reduced-motion:reduce){animate,animateTransform{display:none}}</style>` +
    avatarElements(state) + `</svg>`
}

// ── Band SVG (AbovePrompt, desktop) ───────────────────────────────────────────

function bandSvg(list: Op[], W: number, now: number, disco: boolean): string {
  const state = deriveState(list, now, disco)
  const label = 'DevLog'
  const stateLbl = STATE_LABEL[state]
  const last  = list[0]
  const sub   = last != null && (state === 'WORKING' || state === 'READY' || state === 'ERROR')
    ? `  ·  ${xml(last.intent ? last.intent.substring(0, 40) : last.tool)}${last.duration != null ? '  ' + fmtDur(last.duration) : ''}`
    : ''
  const stateX = 23 + label.length * 8 + 8
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="22" viewBox="0 0 ${W} 22">` +
    `<style>.t{fill:#1f1f1f}.m{fill:#8b949e}@media (prefers-color-scheme:dark){.t{fill:#e6edf3}}@media(prefers-reduced-motion:reduce){animate,animateTransform{display:none}}</style>` +
    `<g transform="translate(1,1)">${avatarElements(state)}</g>` +
    `<text class="t" x="23" y="15.5" font-family="${FONT}" font-size="13" font-weight="500">${xml(label)}</text>` +
    `<text class="m" x="${stateX}" y="15.5" font-family="${FONT}" font-size="12">${xml(stateLbl)}${xml(sub)}</text>` +
    `</svg>`
}

// ── SVG trace row helpers ─────────────────────────────────────────────────────

const traceLabelSvg = (label: string, value: string, W: number): string =>
  `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="22" viewBox="0 0 ${W} 22">` +
  `<style>.t{fill:#1f1f1f}.m{fill:#8b949e}@media (prefers-color-scheme:dark){.t{fill:#e6edf3}}</style>` +
  `<text class="m" x="2" y="15.5" font-family="${FONT}" font-size="11">${xml(label)}</text>` +
  `<text class="t" x="90" y="15.5" font-family="${FONT}" font-size="11.5">${xml(value.substring(0, 80))}</text>` +
  `</svg>`

const sourceRowSvg = (type: string, title: string, W: number): string =>
  `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="22" viewBox="0 0 ${W} 22">` +
  `<style>.t{fill:#1f1f1f}.m{fill:#8b949e}.tag{fill:#0d1117;stroke:#30363d;stroke-width:1}@media (prefers-color-scheme:dark){.t{fill:#e6edf3}.tag{fill:#161b22}}</style>` +
  `<rect class="tag" x="2" y="4" width="${type.length * 7 + 8}" height="14" rx="3"/>` +
  `<text class="m" x="6" y="14.5" font-family="${FONT}" font-size="10">${xml(type)}</text>` +
  `<text class="t" x="${type.length * 7 + 16}" y="15.5" font-family="${FONT}" font-size="11.5">${xml(title.substring(0, 70))}</text>` +
  `</svg>`

// ── register ──────────────────────────────────────────────────────────────────

export const register: Register = (on) => {

  on('session.start', async ($, e, next) => {
    const r = await next(e)
    await $.command.register({ name: 'devlog-companion', description: 'Show or hide DevLog Companion panel' })
    await update($, activeView, () => 'now' as View)
    void $.ui.open({ id: PANE, title: 'DevLog Companion' })
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
    await update($, isDisconn, () => false)
    try {
      const result = await next(e)
      const duration = (await $.clock.now()) - startedAt
      // Detect MCP error results (isError: true) — these don't throw, they return
      let isErrorResult = false
      try {
        const r = result as unknown as { isError?: boolean; content?: Array<{ isError?: boolean }> }
        isErrorResult = r?.isError === true
          || (Array.isArray(r?.content) && r.content.some(c => c?.isError === true))
      } catch {}
      if (isErrorResult) {
        await update($, ops, list => list.map(o =>
          o.id === id ? { ...o, status: 'failed' as OpStatus, duration } : o))
        return result
      }
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

  // ── Band above the prompt ─────────────────────────────────────────────────

  on('ui.render', { component: 'AbovePrompt' }, async ($, e, next) => {
    const list  = await read($, ops)
    const disco = await read($, isDisconn)
    const drawn = await Promise.resolve(next(e)).catch(() => null)
    const below = drawn && typeof drawn === 'object' && (drawn as { type?: string }).type !== 'engine' ? drawn : null
    const ui    = $.ui.resolve(e)
    const { Box, Text } = ui
    const now   = Date.now()
    const state = deriveState(list, now, disco)

    if (e.surface !== 'terminal' && 'Svg' in ui) {
      const { Svg } = ui
      const W = Math.max(180, Math.min(1600, (e.props.bodyColumns || 100) * 8 - 96))
      return (
        <Box flexDirection="column">
          <Svg source={bandSvg(list, W, now, disco)} alt={`DevLog ${STATE_LABEL[state]}`} width={W} height={22} />
          {below}
        </Box>
      )
    }

    const last = list[0]
    return (
      <Box flexDirection="column">
        <Box flexDirection="row" gap={1}>
          <Text color={STATE_COLOR[state]}>{state === 'WORKING' ? '◎' : '●'}</Text>
          <Text dimColor>DevLog</Text>
          <Text dimColor>{STATE_LABEL[state]}</Text>
          {last != null && (state === 'WORKING' || state === 'READY' || state === 'ERROR')
            ? <Text dimColor>· {last.intent ? last.intent.substring(0, 40) : last.tool}{last.duration != null ? ` ${fmtDur(last.duration)}` : ''}</Text>
            : null}
        </Box>
        {below}
      </Box>
    )
  })

  // ── Side pane ─────────────────────────────────────────────────────────────

  on('ui.render', { component: 'Pane', requestId: PANE }, async ($, e) => {
    let list: Op[]
    let trace: TraceState | null
    let view: View
    let disco: boolean
    try {
      list  = await read($, ops)
      trace = await read($, traceState)
      view  = await read($, activeView)
      disco = await read($, isDisconn)
    } catch (atomErr) {
      const { Box, Text } = $.ui.resolve(e)
      return <Box padding={1}><Text color="#f85149">atom read error: {String(atomErr)}</Text></Box>
    }
    const ui    = $.ui.resolve(e)
    const { Box, Text, Button } = ui
    const now   = Date.now()
    let state: CompanionState
    try {
      state = deriveState(list, now, disco)
    } catch (deriveErr) {
      return <Box padding={1}><Text color="#f85149">deriveState error: {String(deriveErr)}</Text></Box>
    }

    // Fetch context trace via backend REST API
    const fetchTrace = async (op: Op) => {
      if (!op.aiTaskId) {
        await update($, traceState, s => s ? {
          ...s,
          error: "Pas de Context Trace disponible — cet appel a été enregistré avant l'activation du Context Inspector. Invoquez un nouvel outil DevLog pour obtenir un trace inspectable.",
          loading: false
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
        await update($, isDisconn, () => false)
        await update($, traceState, s => s ? { ...s, data, loading: false } : null)
      } catch (err) {
        const msg = String(err)
        const isNetwork = msg.includes('ECONNREFUSED') || msg.includes('curl') || msg.includes('connect')
        if (isNetwork) await update($, isDisconn, () => true)
        await update($, traceState, s => s ? { ...s, error: msg, loading: false } : null)
      }
    }

    // Start loading trace for an op and switch to Context tab
    const inspectOp = async (op: Op) => {
      await update($, traceState, () => ({
        opId: op.id,
        section: 'overview' as TraceSection,
        data: null,
        loading: true,
        error: null
      }))
      await update($, activeView, () => 'context' as View)
      void fetchTrace(op)
    }

    // ── Tab navigation bar ────────────────────────────────────────────────

    const renderTabs = () => (
      <Box flexDirection="row" gap={0} paddingX={1} paddingBottom={1}>
        {(['now', 'context', 'history'] as const).map(tab => (
          <Button key={tab} plain onPress={async () => { await update($, activeView, () => tab) }}>
            <Text bold={view === tab} dimColor={view !== tab} color={view === tab ? '#58a6ff' : undefined}>
              {tab === 'now' ? 'Now' : tab === 'context' ? 'Context' : 'History'}{'  '}
            </Text>
          </Button>
        ))}
      </Box>
    )

    // ── Now view ──────────────────────────────────────────────────────────

    try { // top-level render guard
    if (view === 'now') {
      const lastOp: Op | undefined = list[0]
      const statusDot = state === 'WORKING' ? '◎' : state === 'ERROR' ? '✗' : state === 'DISCONNECTED' ? '○' : '●'
      const sources = trace?.data && Array.isArray(trace.data.sources)
        ? trace.data.sources as Array<{ type: string; id: string | null; title: string | null }>
        : []

      if (e.surface !== 'terminal' && 'Svg' in ui) {
        const { Svg } = ui
        const W = Math.max(200, Math.min(700, (e.props.bodyColumns || 50) * 8 - 16))
        return (
          <Box flexDirection="column">
            {renderTabs()}
            <Box flexDirection="row" gap={1} paddingX={1} paddingBottom={1}>
              <Text color={STATE_COLOR[state]}>{statusDot}</Text>
              <Text bold>DevLog</Text>
              <Text dimColor>{STATE_LABEL[state]}</Text>
            </Box>
            {lastOp != null ? (
              <Box flexDirection="column" paddingX={1} paddingBottom={1}>
                <Box flexDirection="row" gap={1}>
                  <Text dimColor>{lastOp.tool}</Text>
                  <Text dimColor>·  {lastOp.duration != null ? fmtDur(lastOp.duration) : lastOp.status === 'running' ? '…' : ''}</Text>
                </Box>
                {lastOp.intent ? <Text dimColor>{lastOp.intent.substring(0, 65)}</Text> : null}
                {lastOp.aiTaskId
                  ? <Button key="inspect" plain onPress={async () => { await inspectOp(lastOp!) }}>
                      <Text color="#58a6ff">Inspecter le contexte →</Text>
                    </Button>
                  : <Text key="no-trace" dimColor>Pas de Context Trace disponible.</Text>}
              </Box>
            ) : (
              <Box padding={1}><Text dimColor>En attente d'une opération DevLog.</Text></Box>
            )}
            {sources.length > 0 ? (
              <Box flexDirection="column" paddingX={1}>
                <Box paddingBottom={1}><Text dimColor>{sources.length} source{sources.length !== 1 ? 's' : ''}</Text></Box>
                {sources.slice(0, 4).map((src, i) => (
                  <Svg key={i}
                    source={sourceRowSvg(src.type ?? '?', src.title ?? src.id ?? '—', W)}
                    alt={`${src.type} ${src.title}`} width={W} height={22} />
                ))}
                {sources.length > 4 ? <Text dimColor>+{sources.length - 4} autres</Text> : null}
              </Box>
            ) : null}
          </Box>
        )
      }

      // Terminal "Now" view
      return (
        <Box flexDirection="column">
          {renderTabs()}
          <Box flexDirection="row" gap={1} paddingX={1} paddingBottom={1}>
            <Text color={STATE_COLOR[state]}>{statusDot}</Text>
            <Text bold>DevLog</Text>
            <Text dimColor>{STATE_LABEL[state]}</Text>
          </Box>
          {lastOp != null ? (
            <Box flexDirection="column" paddingX={1} paddingBottom={1}>
              <Box flexDirection="row" gap={1}>
                <Text dimColor>{lastOp.tool}</Text>
                {lastOp.duration != null ? <Text dimColor>·  {fmtDur(lastOp.duration)}</Text> : null}
              </Box>
              {lastOp.intent ? <Text dimColor>{lastOp.intent.substring(0, 65)}</Text> : null}
              {lastOp.aiTaskId
                ? <Button key="inspect" plain onPress={async () => { await inspectOp(lastOp!) }}>
                    <Text color="#58a6ff">Inspecter →</Text>
                  </Button>
                : null}
            </Box>
          ) : (
            <Box padding={1}><Text dimColor>En attente d'une opération DevLog.</Text></Box>
          )}
        </Box>
      )
    }

    // ── Context tab ───────────────────────────────────────────────────────

    if (view === 'context') {
      if (trace === null) {
        return (
          <Box flexDirection="column">
            {renderTabs()}
            <Box padding={1}>
              <Text dimColor>Sélectionnez une opération dans History, ou cliquez "Inspecter le contexte →" dans Now.</Text>
            </Box>
          </Box>
        )
      }

      const sections: TraceSection[] = ['overview', 'sources', 'context', 'diagnostics']
      const sectionLabels: Record<TraceSection, string> = {
        overview: 'Overview', sources: 'Sources', context: 'Context', diagnostics: 'Diagnostics'
      }

      const renderSectionTabs = () => (
        <Box flexDirection="row" gap={1} paddingX={1} paddingBottom={1}>
          {sections.map(s => (
            <Button key={s} plain
              onPress={async () => { await update($, traceState, prev => prev ? { ...prev, section: s } : null) }}>
              <Text bold={trace.section === s} dimColor={trace.section !== s} color={trace.section === s ? '#58a6ff' : undefined}>
                {sectionLabels[s]}
              </Text>
            </Button>
          ))}
        </Box>
      )

      const renderContent = () => {
        if (trace.loading) return <Box padding={1}><Text dimColor>Chargement du context trace…</Text></Box>
        if (trace.error)   return <Box padding={1}><Text color="#f85149">{trace.error}</Text></Box>
        if (!trace.data)   return <Box padding={1}><Text dimColor>Aucune donnée disponible.</Text></Box>

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
            return <Box padding={1}><Text dimColor>{d.contextAvailable ? 'No sources in context.' : 'Context not available.'}</Text></Box>
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
              {!hasFailure && !hasStop ? <Text dimColor>No errors or diagnostics detected.</Text> : null}
            </Box>
          )
        }
        return null
      }

      const ctxOp = list.find(o => o.id === trace.opId)
      return (
        <Box flexDirection="column">
          {renderTabs()}
          <Box paddingX={1} paddingBottom={1} flexDirection="column" gap={0}>
            <Text bold>Context Trace</Text>
            {ctxOp?.intent
              ? <Text key="op-intent" dimColor wrap="truncate-end">{ctxOp.intent}</Text>
              : <Text key="op-tool" dimColor>{ctxOp?.tool ?? '?'}</Text>}
          </Box>
          {renderSectionTabs()}
          {renderContent()}
        </Box>
      )
    }

    // ── History tab ───────────────────────────────────────────────────────

    if (view === 'history') {
      return (
        <Box flexDirection="column">
          {renderTabs()}
          {list.length === 0 ? (
            <Box padding={1}><Text dimColor>Aucune opération DevLog cette session.</Text></Box>
          ) : (
            <Box flexDirection="column">
              <Box paddingX={1} paddingBottom={1} flexDirection="row" gap={1}>
                <Text bold>History</Text>
                <Text dimColor>· {list.length} ops</Text>
              </Box>
              {list.map(op => {
                const dur    = op.duration != null ? fmtDur(op.duration) : '…'
                const when   = relTime(op.startedAt, now)
                const hasTrace = !!op.aiTaskId
                const label  = op.intent ? op.intent.substring(0, 55) : op.tool
                const dotColor = op.status === 'done' ? '#3fb950' : op.status === 'failed' ? '#f85149' : '#fbbf24'
                return (
                  <Button key={op.id} plain onPress={async () => { await inspectOp(op) }}>
                    <Text color={hasTrace ? dotColor : undefined} dimColor={!hasTrace}>
                      {hasTrace ? (op.status === 'done' ? '✓' : op.status === 'failed' ? '✗' : '◎') : '○'}
                    </Text>
                    {' '}<Text dimColor={!hasTrace} wrap="truncate-end">{label}</Text>
                    {' '}<Text dimColor>{dur}  ·  {when}</Text>
                  </Button>
                )
              })}
            </Box>
          )}
        </Box>
      )
    }

    return null
    } catch (renderErr) {
      return <Box padding={1}><Text color="#f85149">render error: {String(renderErr)}</Text></Box>
    }
  })
}
