# Story: Project Notes Markdown Preview and Full Rendering

## Problem
Project Notes are stored correctly as raw Markdown in the database, but the Angular frontend displayed `contentMarkdown` in preview contexts without removing Markdown syntax, exposing formatting characters (`**bold**`, `# headings`, `` `code` ``) to users. Additionally, there was **no full/readable content view** where Project Notes could be rendered with proper Markdown formatting.

Example input:
```markdown
# Architecture

This is **important**.

- Item 1
- Item 2

Use `ValidatableProposal`.
```

**Preview issue**: Raw Markdown markers were visible in the compact preview.
**Full content issue**: No view existed to render formatted Markdown.

## User Impact
- Users seeing raw Markdown syntax in project note previews within the project overview page
- Inconsistent presentation of project knowledge across UI contexts
- No way to read Project Notes with formatted content (headings, bold, lists, etc.)

## Scope
- **In scope**: 
  - Update `humanContextPreview()` in `ProjectStatePage` component to strip Markdown syntax before whitespace normalization and truncation (compact preview)
  - Add full Markdown rendering using `ngx-markdown` for Project Note readable content (full/readable view)
  - Security-safe rendering without `bypassSecurityTrustHtml()`
- **Out of scope**: 
  - Backend changes (persistence, API, DTOs)
  - Markdown → HTML rendering in the backend
  - Full-project-note detail view with editing capabilities

## Acceptance Criteria - PREVIEW (compact)
- `humanContextPreview()` accepts raw Markdown and returns plain readable text
- Headings (`#`, `##`, `###`) are stripped, preserving only the text content
- Bold (`**text**`) is stripped, preserving the text
- Italic (`*text*` / `_text_`) is stripped, preserving the text
- Inline code (` `code``) is stripped, preserving the code text
- Unordered list markers (`- ` or `* `) are removed
- Ordered list markers (`1. `, `2.`) are removed
- Markdown links `[text](url)` display only the link text
- Whitespace is normalized (multiple spaces/newlines collapsed to single space)
- Truncation at 180 chars (177 + `...`) preserves existing behavior
- Null/empty content returns empty string

## Acceptance Criteria - FULL CONTENT (rendered)
- Project Notes render with proper Markdown formatting when viewed in full context
- Supported constructs: `#`, `##`, `###` headings; paragraphs; bold; italic; unordered lists; ordered lists; blockquotes; inline code; fenced code blocks; links
- Raw Markdown markers (`#`, `**`, `-`, `` ` ``, `[text](url)`) are **not** displayed in the full content view
- Markdown is rendered safely without executing embedded HTML
- No `DomSanitizer.bypassSecurityTrustHtml()` is used
- Dangerous embedded HTML (e.g., `<script>`) is neutralized/escaped

## Technical Constraints
- Must not change backend persistence, domain models, Spring Boot controllers, API DTOs, or `contentMarkdown` format
- Markdown must remain stored and transmitted as raw Markdown
- No unsafe HTML bypasses or `bypassSecurityTrustHtml()`
- `ngx-markdown` v22.0.0 is the selected Markdown renderer
- Implementation must handle both compact preview and full rendering within the same component
- Existing frontend tests must pass (13/13 ProjectStatePage tests)

## Security Requirements
- Project Note Markdown must be treated as **untrusted user-controlled content**
- The `ngx-markdown` library escapes HTML by default — `<script>alert('xss')</script>` becomes literal text, not executable code
- No `DomSanitizer.bypassSecurityTrustHtml()` is used at any point
- The `MarkdownService` from `ngx-markdown` uses Angular's built-in sanitization (DomSanitizer.sanitize()) which creates safe text nodes, not innerHTML
- Markdown content is rendered via `<markdown [data]="...">` which internally uses the marked parser with sanitization

## Out-of-Scope Behavior
- Full Markdown editing capabilities
- Backend persistence of rendered HTML
- Custom Markdown parser implementation
- Support for all Markdown constructs (tables, footers, etc. — planned for future)

## Testing Expectations
- Existing `ProjectStatePage` test suite: 13/13 tests pass (0 failures)
- New tests cover both preview behavior (humanContextPreview) and rendering behavior (ngx-markdown output)
- Backend: 754/754 tests pass with 0 failures, 0 errors
- Frontend build and lint checks pass

## branching and Completion
- Work isolated on branch `fix/project-note-markdown-preview`
- Branch left ready for human review and is **not merged**