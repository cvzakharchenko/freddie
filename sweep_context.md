# Sweep JetBrains Next Edit Prediction Context

This document describes the context that the discontinued Sweep JetBrains plugin gathered and sent to its backend for editor autocomplete / next edit prediction. It is focused on the data that can improve prediction quality, not on UI rendering or plugin internals.

The active path in this source tree is the `NextEditAutocompleteRequest` request sent to:

```text
/backend/next_edit_autocomplete
```

In cloud mode this goes to `https://autocomplete.sweep.dev/backend/next_edit_autocomplete`. In local autocomplete mode the same request shape is sent to the local `sweep-autocomplete` server.

The older `AutocompleteRequest` type in `data/Models.kt` appears unused in this source tree. The request that actually drives the editor suggestions is `NextEditAutocompleteRequest`.

## Request Shape

The prediction request is a JSON object with these fields:

```json
{
  "repo_name": "project-folder-name",
  "branch": null,
  "file_path": "relative/path/to/current/file.ext",
  "file_contents": "full current editor document text",
  "recent_changes": "recent low-resolution diffs",
  "cursor_position": 1234,
  "original_file_contents": "baseline text for current editor",
  "file_chunks": [
    {
      "file_path": "relative/path.ext",
      "start_line": 1,
      "end_line": 100,
      "content": "chunk text",
      "timestamp": 1710000000000
    }
  ],
  "retrieval_chunks": [
    {
      "file_path": "dropdown.txt",
      "start_line": 1,
      "end_line": 10,
      "content": "retrieved context",
      "timestamp": 1710000000000
    }
  ],
  "recent_user_actions": [
    {
      "action_type": "INSERT_CHAR",
      "line_number": 42,
      "offset": 1234,
      "file_path": "relative/path.ext",
      "timestamp": 1710000000000
    }
  ],
  "multiple_suggestions": true,
  "privacy_mode_enabled": false,
  "client_ip": null,
  "recent_changes_high_res": "recent high-resolution diffs",
  "changes_above_cursor": false,
  "ping": false,
  "editor_diagnostics": []
}
```

Because serialization uses `encodeDefaults = true`, default-valued fields are still sent. `branch` exists in the schema but is not populated by the active request builder. `client_ip` is effectively `null` because the public IP lookup function returns `null` in this source.

All request types extend `BaseRequest`, so the serialized request also includes:

```json
{
  "debug_info": "IDE name/build, OS, Sweep version",
  "device_id": "JetBrains PermanentInstallationID"
}
```

The HTTP request also includes the following headers. These are identity/runtime metadata, not code context:

- `Content-Type: application/json`.
- `Authorization: Bearer <github-token-or-device-id>`.
- `X-Plugin-Version`: Sweep plugin version, or `unknown`.
- `X-IDE-Name`: full application name from `ApplicationInfo`, for example `IntelliJ IDEA 2024.1`.
- `X-IDE-Version`: full IDE version.
- `X-Debug-Info`: same `debug_info` value as in the body.
- `Content-Encoding: br`: only when the request body benefits from Brotli compression. Otherwise omitted and the body is sent uncompressed.

The HTTP client uses a 10 second request timeout. Cloud responses are read as a streaming JSON stream; local autocomplete responses are read line-by-line so partial responses survive a server crash.

## Current Editor Context

The current editor is the anchor for the prediction. The request sends:

- `file_path`: the current file path, relative to the project when possible.
- `file_contents`: the full current editor document text, including unsaved changes.
- `cursor_position`: the current caret offset in the document.
- `original_file_contents`: a baseline copy of the current editor text from when the editor was focused or from a later baseline refresh.
- `repo_name`: just the project base directory name.

The plugin also computes the current line number, document line count, and current line prefix while building editor state, but those are used locally for cache keys and are not sent directly.

`original_file_contents` is refreshed when the current editor gains focus, when the IDE window loses focus, and when the user moves more than 100 lines. This gives the backend a before/after view of the current file, not only the final buffer.

## Recent Edit Diffs

Sweep sends recent edits as unified diffs. These are one of the most important intent signals.

Each edit record contains:

- The file path.
- The diff from previous text to new text.
- The edit offset.
- A timestamp.

The string sent to the backend is formatted as:

```text
File: relative/path.ext
@@ ... @@
- old line
+ new line
```

There are two diff streams:

- `recent_changes`: the last 6 lower-resolution edit records.
- `recent_changes_high_res`: the last 16 higher-resolution edit records.

The lower-resolution stream combines adjacent edits when they appear to be part of the same small change. Combination is only allowed for the same file, with small hunks. This turns character-by-character typing into a more meaningful logical diff.

The high-resolution stream keeps more granular edit records. It helps preserve the exact recent typing trail.

Limits and filters:

- At most 16 edit records are tracked in each queue.
- `recent_changes` sends only the last 6 records.
- `recent_changes_high_res` sends only the last 16 records.
- Individual formatted diffs larger than 20,000 characters are filtered out before sending.
- Edit diffs larger than about 32 KB are not stored.
- No-op diffs are skipped.
- Document changes that add more than 3 lines or delete more than 3 lines are not tracked as recent edit records.
- Very large files are skipped for edit tracking.

Large-file guard for autocomplete tracking:

- More than 10,000,000 characters: too large.
- More than 50,000 lines: too large.
- Average line length above the `autocomplete-avg-line-length-threshold` feature flag, default 240: too large.

## Recent User Actions

The request includes `recent_user_actions`, an ordered queue of recent editor actions. This gives the model a behavioral trail, not just text.

The action schema is:

```json
{
  "action_type": "INSERT_CHAR",
  "line_number": 42,
  "offset": 1234,
  "file_path": "relative/path.ext",
  "timestamp": 1710000000000
}
```

Tracked action types:

- `INSERT_CHAR`: one character inserted.
- `INSERT_SELECTION`: multiple characters inserted, for example paste.
- `DELETE_CHAR`: one character deleted.
- `DELETE_SELECTION`: multiple characters deleted.
- `UNDO`: an undo command changed document text.
- `REDO`: a redo command changed document text.
- `CURSOR_MOVEMENT`: caret movement.

Details:

- Insert/delete actions are inferred from document change event lengths.
- Undo and redo are detected from command names and only recorded if the document actually changed.
- Cursor movement is recorded on focus changes and caret changes when it differs from the last recorded action.
- The queue keeps at most 50 user actions.
- `line_number` is 1-based and represents the line after the action completes.
- `offset` is the caret or final edit offset after the action completes.

This is useful because it distinguishes, for example, "the user pasted a block", "the user deleted a selection", and "the user jumped to another region after editing".

## Recent Cursor Position Chunks

Sweep records recent cursor positions and uses them to retrieve code chunks from files the user recently looked at.

Cursor position record:

```json
{
  "filePath": "relative/path.ext",
  "line": 42,
  "cursorOffset": 1234,
  "timestamp": 1710000000000
}
```

Only derived chunks are sent in `file_chunks`; the raw cursor position records are not sent.

How chunks are selected:

- The plugin keeps up to 16 recent cursor positions.
- If the latest cursor position is in the same file and within 50 lines of the previous one, the previous one is replaced. This coalesces small local cursor motion.
- For each recent cursor position, the plugin reads the file and chooses a 200-line chunk around the cursor.
- Chunk windows overlap by 100 lines.
- At most 5 chunks are sent.
- Duplicate chunks with the same file path and start line are skipped.
- The chunk containing the current cursor in the current file is skipped, since the full current file is already sent as `file_contents`.
- Chunks are sorted by timestamp and the latest 5 are kept.

This is a strong signal for "what the user was just reading before making the edit".

## Other Open Editor Chunks

The request also includes context from other selected/open editors through `file_chunks`.

For each selected file other than the current file:

- If it has a text editor, Sweep takes the visible editor range and expands it toward a roughly 100-line chunk.
- It sends the visible/expanded content with `file_path`, `start_line`, `end_line`, and timestamp.
- If there is no text editor, it falls back to reading the whole file through the file reader.

Important nuance: this uses JetBrains `FileEditorManager.selectedFiles`, which is closer to the selected file in each editor split, not necessarily every tab in the project.

The visible-chunk logic makes other open files useful without flooding the request with full buffers.

## Retrieval Chunks

`retrieval_chunks` is a second context channel. It collects semantic and IDE-derived snippets that are likely relevant to the current edit.

The final `retrieval_chunks` list is built from:

1. Current IDE completion dropdown contents.
2. Recent clipboard text.
3. Current-line entity usage snippets from the project.
4. Definition snippets for symbols near the cursor.

Each retrieval chunk is truncated to at most about 200 lines before sending. Chunks whose `file_path` equals the current file path are filtered out. Touching or overlapping chunks from the same file are fused and deduplicated. The final list is reversed before sending.

### IDE Completion Dropdown

If JetBrains has an active completion lookup popup, Sweep captures the current completion items and sends them as a synthetic file chunk:

```json
{
  "file_path": "dropdown.txt",
  "start_line": 1,
  "end_line": 10,
  "content": "CompletionItemA\nCompletionItemB\nCompletionItemC"
}
```

Details:

- At most 10 dropdown items are captured.
- Only the basic lookup string / presentation text is used.
- The capture has a short timeout, about 30 ms.
- It avoids expensive rendering APIs and complex type text resolution.

This gives the backend access to what the IDE itself thinks is likely at the cursor.

### Clipboard Text

Sweep can include recent clipboard text as:

```json
{
  "file_path": "clipboard.txt",
  "start_line": 1,
  "end_line": 20,
  "content": "recent clipboard text"
}
```

Filters:

- Clipboard must be text.
- Clipboard content must have changed and been tracked by the clipboard service.
- It must be newer than the last accepted autocomplete time.
- It must be fresh: under 30 seconds in `getClipboardEntry`, and under 1 minute at request construction.
- It must be non-blank.
- It must be at most 20 lines.
- Content is trimmed and capped to the first 20 lines.

This helps when the user copies code or text and then starts editing nearby.

### Definition Snippets Near The Cursor

Sweep uses JetBrains PSI/reference resolution to fetch definitions for symbols near the cursor.

Definition chunk shape:

```json
{
  "file_path": "relative/path/of/definition.ext",
  "start_line": 1,
  "end_line": 25,
  "content": "text of resolved definition"
}
```

How symbols are selected:

- Walk backward from the cursor to the start of the current line.
- Walk forward from the cursor to the end of the current line.
- Walk upward through previous lines, capped at 6 non-whitespace lines.
- Skip whitespace, punctuation/operator characters, and known language keywords.
- For each candidate PSI element, resolve its reference or parent reference.
- Deduplicate resolved target elements.
- Use the resolved target element text as the definition content.

Limits:

- Number of definitions comes from feature flag `entity-usage-num-def-to-fetch`, default 6.
- Definition resolution timeout is about 500 ms.
- If definition prefetching is enabled by feature flag `remove-debounce-for-entity-extraction-step`, definitions may be prefetched when autocomplete is scheduled and reused if the editor state still matches.

The definition cache key uses file path, current line number, document line count, and current line prefix. The cache is still valid when the same file and line are used, document line count changes by at most 5, and the line prefix still matches closely.

This is one of the likely "secret sauce" parts: the backend gets the actual definitions of names the user is using near the caret, even if those definitions are elsewhere.

### Usage Snippets From The Project

Sweep also searches the project for usages of terms near the cursor.

How search terms are chosen:

- Take text from the current line plus up to the previous 2 lines before the cursor.
- Remove common symbols.
- Split into terms.
- Keep terms with length at least 3.
- Drop pure numbers.
- Drop language keywords for the current file type.
- Take the last 15 candidate terms.
- Sort terms by complexity, favoring names with underscores, PascalCase/capital letters, and length.
- Search up to 5 terms.

How usages are searched:

- Use JetBrains `PsiSearchHelper` over project scope.
- Skip the current file.
- Only keep files with the same extension as the current file, when the current file has an extension.
- Limit to about 100 raw matches per term.
- Search timeout is about 30 ms.
- Build chunks around found lines with 9 lines above and 9 lines below.
- Avoid overlapping context windows in the same file.
- Sort chunks by similarity to the current line.
- Keep at most the feature flag `entity-usage-num-usages-to-fetch`, default 6.

Usage chunks let the backend see how the same names/patterns are used elsewhere in the codebase.

## Editor Diagnostics

If feature flag `send_editor_diagnostics` is enabled, Sweep sends diagnostics from already-populated editor highlights.

Diagnostic shape:

```json
{
  "line": 42,
  "start_offset": 123,
  "end_offset": 140,
  "severity": "WARNING",
  "message": "[inspection-id] diagnostic message",
  "timestamp": 1710000000000
}
```

Details:

- Reads `DocumentMarkupModel` highlighters for the current document.
- Includes only severities at warning or above.
- Uses inspection tool id when available; otherwise uses severity name.
- Deduplicates by start offset, end offset, and message.
- Sends at most 50 diagnostics per request.
- Tracks first-seen timestamp for each diagnostic. The plugin keeps a project-wide cache of up to 500 distinct diagnostic keys so the same warning keeps a stable first-seen timestamp across requests; when the cache fills, the oldest 10% are evicted.

Diagnostics can help the model fix incomplete edits, type errors, unresolved symbols, and warnings that appeared as the user typed.

## Suggestion Options And Flags

These fields do not add code context but can influence backend behavior:

- `multiple_suggestions = true`: asks backend to return multiple candidate edit completions.
- `changes_above_cursor`: feature flag `autocomplete-changes-above-cursor`; likely tells backend whether edits above the cursor are allowed or expected.
- `privacy_mode_enabled`: client sends the user's privacy-mode setting.
- `ping = false`: default field in request schema.

In this client code, privacy mode is only sent as a flag. The same context fields are still serialized by the plugin. Any redaction or retention behavior would need to happen in the backend or local autocomplete server.

## Context That Prevents A Request

The following do not become model context. Instead, they suppress next edit prediction requests:

- Autocomplete snooze is active.
- Current document is read-only.
- Current editor state cannot be read.
- Multi-line selection is active.
- Current file name matches an autocomplete exclusion pattern.
- User is in a live template or refactoring UI.
- Prompt bar / command UI is active.
- Applied code blocks are interacting with the current file. The feature flag `enable_autocomplete_when_code_blocks_present` chooses the strictness: when enabled, requests are suppressed only while `AppliedCodeBlockManager.isApplyingCodeBlocksToCurrentFile()` is true; when disabled, requests are suppressed if any applied code block exists in the current file.
- Current file is excluded by patterns. Default exclusion includes `.env`.
- Current document is in bulk update mode.

Autocomplete exclusion patterns match only the file name:

- `scratch**` means prefix match.
- `.env` means suffix match, so `something.env` matches.

## Request Timing Signals

The context is gathered after a debounce. The default migrated debounce is 10 ms and the setting is clamped to 10-1000 ms.

Requests are scheduled after:

- Small document edits.
- Cursor movement if there was a recent edit within the `cursor-movement-threshold` feature flag, default 60 seconds.
- Editor focus/file switch if there was a recent edit within 8 seconds.

Requests are not scheduled for changes identified as agent-created.

This timing matters because it makes the context strongly reflect the user's immediate edit intent: recent diffs, cursor path, current dropdown, and clipboard are all fresh.

## File Size And Content Limits

Several guards prevent huge contexts:

- Editor/file large guard: over 10,000,000 characters, over 50,000 lines, or average line length over default 240.
- File reader max file size: 2.56 MB.
- Retrieval chunk truncation: about 200 lines per retrieval chunk.
- Recent cursor chunks: 200-line chunk size, 100-line overlap, at most 5 chunks.
- Visible open-file chunk: roughly 100 lines.
- Clipboard chunk: at most 20 lines.
- Dropdown chunk: at most 10 items.
- Diagnostics: at most 50.
- Recent user actions: at most 50.
- Recent edit queues: at most 16 records, with 6 sent in low-resolution form and 16 in high-resolution form.
- Individual recent diff strings over 20,000 characters are dropped before sending.

## Things Not Sent In The Active Prediction Request

The active next-edit request does not send:

- Chat history.
- Selected chat model.
- Full project tree.
- Full codebase index.
- Git branch name.
- Git diff against HEAD/default branch.
- Terminal output.
- Problems tool output, except current editor diagnostics if the diagnostics feature flag is enabled.
- All open tabs. It sends current file, recent cursor chunks, and selected files from editor splits.
- The older `parent_block` autocomplete context from the unused `AutocompleteRequest` type.

Some of those exist elsewhere in the chat/agent product, but they are not part of `NextEditAutocompleteRequest`.

## Separate Autocomplete Metrics

There is a separate metrics endpoint:

```text
/backend/track_autocomplete_metrics
```

This is not part of the immediate prediction request, but it can send code content after a suggestion is shown.

For shown suggestions, the plugin may send:

- Event type: shown, accepted, disposed, or edit tracking.
- Suggestion type.
- Additions/deletions count.
- Autocomplete id.
- Number of definitions/usages retrieved.
- Suggestion lifespan.
- Privacy mode flag.
- Full current document text at 15, 30, 60, 120, and 300 seconds after the suggestion is shown.
- The affected line/range content when a range marker is available.

For no-suggestion cases, there is also sampling controlled by feature flag `autocomplete-edit-tracking-not-shown-ratio`, default 10/1000, that can track document content after no suggestion was generated.

This metrics stream is useful to know about for privacy and evaluation, but it is not context that directly forms a single next edit prediction response.

## Practical Reimplementation Checklist

For a simple reimplementation with another completions provider, the highest-value context seems to be:

1. Full current file text plus cursor offset.
2. Baseline/original current file text.
3. Recent edit diffs, especially a high-resolution short trail and a coalesced logical trail.
4. Recent user actions with timestamps and offsets.
5. Recent cursor-position code chunks from files the user just inspected.
6. Visible chunks from other selected editor splits.
7. Current IDE completion dropdown items.
8. Fresh clipboard text, tightly limited.
9. Resolved definitions for symbols near the cursor.
10. Usage snippets for current-line terms from other same-extension files.
11. Current warning/error diagnostics.

The distinctive parts are not any single field, but the combination of temporal intent signals and semantic retrieval: "what the user just changed", "where they just looked", "what symbols near the caret mean", and "how similar terms are used elsewhere".
