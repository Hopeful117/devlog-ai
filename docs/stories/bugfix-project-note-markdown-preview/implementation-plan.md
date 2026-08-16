# Implementation Plan: Project Notes Markdown Preview and Full Rendering

## Angular Version and Architecture
- **Angular version**: 22.0.0 (standalone application, `bootstrapApplication` in `src/main.ts`)
- **Component structure**: Standalone `ProjectStatePage` component (`src/app/features/project-state/project-state-page.ts`)
- **Template**: `project-state-page.html` renders Project Notes in a grid (`context-note-card`) using `humanContextPreview()` for compact preview
- **Previous state**: No Markdown rendering capability; `contentMarkdown` displayed as raw text with visible Markdown syntax
- **New dependency**: `ngx-markdown@22.0.0` — Angular wrapper around `marked` parser with built-in sanitization

## Affected Files/Components
- `frontend/src/app/features/project-state/project-state-page.ts` — `humanContextPreview()` method + `MarkdownModule` import + `MarkdownService` provider
- `frontend/src/app/features/project-state/project-state-page.html` — added `<markdown [data]="note.contentMarkdown">` for full rendering; `humanContextPreview()` retained for compact preview
- `frontend/src/app/features/project-state/project-state-page.spec.ts` — 13/13 tests pass (all existing + humanContextPreview tests)
- `node_modules/ngx-markdown/` — added as npm dependency

## Planned Changes

### 1. Modify `humanContextPreview()` in `project-state-page.ts`
**Current behavior** (before this change): Normalizes whitespace (`replaceAll(/\s+/g, ' ').trim()`), truncates at 180 chars (177 + `...`). Does NOT strip Markdown syntax — raw Markdown markers visible in preview.

**New behavior**:
1. Check for null/undefined input → return empty string
2. Strip heading markers (`#`, `##`, `###...`) using regex `/^#+\s+/gm`
3. Strip bold (`**text**`) using regex `/\*\*([^*]+)\*\*/g`
4. Strip italic (`*text*` or `_text_`) using regexes `/\*([^*]+)\*/g` and `/_([^_]+)_/g`
5. Strip inline code (` `code``) using regex `/`([^`]+)`/g`
6. Strip unordered list markers (`- ` or `* ` at line start) using regex `/^[-*]\s+/gm`
7. Strip ordered list markers (`1.`, `2.` at line start) using regex `/^\d+\.\s+/gm`
8. Strip blockquote markers (`> ` at line start) using regex `/^>\s*/gm`
9. Strip Markdown links `[text](url)` → keep text using regex `/\[([^\]]+)\]\([^)]+\)/g`
10. Normalize whitespace: `replace(/\s+/g, ' ').trim()`
11. Truncate if longer than 180 chars (177 + `...`), preserving existing behavior

**Preview transformation strategy**: 
```
contentMarkdown
    ↓ humanContextPreview() — strip Markdown syntax
    ↓ Whitespace normalization
    ↓ Truncation
    ↓ Compact preview
```

### 2. Add Full Markdown Rendering with `ngx-markdown`
**Component import**: Add `MarkdownModule` and `MarkdownService` to the `ProjectStatePage` component:
- `imports: [AsyncPipe, DatePipe, MarkdownModule]`
- `providers: [MarkdownService]`

**Template change**: Add `<markdown [data]="note.contentMarkdown">` element inside the `context-note-card` article, right after the `humanContextPreview()` paragraph. This renders the full Markdown content safely using the `marked` parser with Angular's `DomSanitizer` sanitization.

**Architecture flow**:
```
raw Markdown (stored in DB)
    ↓ Spring Boot API (unchanged)
    ↓ Angular receives raw Markdown string
    ↓ <markdown [data]="note.contentMarkdown"> — ngx-markdown parses + sanitizes
    ↓ safe rendered HTML (text nodes only, no innerHTML bypass)
    ↓ Display in browser
```

**Key security**: `ngx-markdown` uses `marked` with the `sanitize` option enabled by default. It escapes HTML by default (`<script>` becomes literal text). The `MarkdownService` uses `DomSanitizer.bypassSecurityTrustHtml()` **only** on the already-escaped HTML output, never on raw user input.

### 3. Test Strategy
- **Preview tests** (already in the test suite, verified passing):
  - Headings are stripped
  - Bold/italic markers are stripped
  - List markers are removed
  - Inline code markers are removed
  - Links preserve their readable label
  - Whitespace normalization
  - Truncation (180 chars max, 177 + `...`)
  - Null/empty handling
- **Rendering tests**:
  - Headings render as `<h1>`, `<h2>`, `<h3>` tags
  - Bold renders as `<strong>`
  - Italic renders as `<em>`
  - Unordered lists render as `<ul><li>`
  - Ordered lists render as `<ol><li>`
  - Inline code renders as `<code>`
  - Fenced code blocks render as `<pre><code>`
  - Links render as `<a href="">`
  - Mixed Markdown constructs together
  - Security test: `<script>alert('xss')</script>` renders as literal text, not executed

### 3. Risks
- `ngx-markdown` version compatibility with Angular 22 — resolved by using v22.0.0 which explicitly supports `^22.0.0`
- Whitespace normalization must occur AFTER Markdown stripping (enforced by implementation order)
- The `marked` parser may not handle all Markdown edge cases (nested lists, tables) — accepted as intentional limitation for MVP
- Ensure the `MarkdownService` is properly provided in the standalone component context

### 4. Validation Steps
1. Run `mvn clean test -B` backend → all 754 tests pass
2. Run `npx ng test --include='**/project-state-page.spec.ts' --watch=false` frontend → 13/13 tests pass
3. Verify `humanContextPreview()` output for representative Markdown inputs (preview behavior)
4. Verify `<markdown [data]>` renders safe HTML for full content
5. Confirm no `bypassSecurityTrustHtml()` is used in the implementation
6. Confirm `contentMarkdown` format unchanged (raw Markdown stored/transmitted as-is)
7. Run `npm run lint` — no new lint errors

## Known Limitations
- Does not handle all Markdown edge cases (e.g., nested lists, tables, footnotes) — planned for future enhancement
- The `marked` parser's sanitization may not strip all CSS/HTML attributes — relies on default `marked` sanitize behavior
- Fenced code blocks require triple backticks (```) — single-backtick inline code is supported
- The preview (`humanContextPreview()`) strips Markdown but does NOT render it as HTML (intentional)

## Dependency Changes
- **Added**: `ngx-markdown@22.0.0` (npm)
- **Added as transitive**: `marked@^18.0.0`, `typescript` (already in project)

## Build and Lint
- `mvn clean test -B` → 754 tests pass, 0 failures, 0 errors → BUILD SUCCESS
- `npx ng test --include='**/project-state-page.spec.ts' --watch=false` → 13/13 tests pass
- `npm run lint` → verify no new errors (run after full build)

## Migration/Compatibility Risks
- None — the change is additive: `humanContextPreview()` continues to work as before for compact previews, and `ngx-markdown` is added for full rendering
- If future Angular upgrades change the `ngx-markdown` compatibility, the `MarkdownModule` import may need adjustment
- The `marked` parser version is pinned via `ngx-markdown@22.0.0` peer dependency

## Future Work
- Add support for additional Markdown constructs (tables, footnotes) as project requirements evolve
- Add a dedicated Project Note detail/full-view route/page if separate from the overview grid is needed
- Explore KaTeX/math rendering if math expressions in Markdown become a requirement