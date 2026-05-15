# Mercury Edit 2 API Notes

This document describes what is needed to call Inception Labs' Mercury Edit 2
from an editor plugin. It is focused on the Mercury API and prompt/message
formats only. It intentionally does not mix in Sweep-specific context gathering
ideas.

Sources checked: Inception documentation and the local Zed source tree on
2026-04-25.

## High-Level Model

Mercury Edit 2 is Inception Labs' code editing model. It is separate from the
general chat model, Mercury 2.

Use this model id for new integrations:

```text
mercury-edit-2
```

Officially documented Mercury Edit-family endpoints relevant to this work:

```text
POST https://api.inceptionlabs.ai/v1/fim/completions
POST https://api.inceptionlabs.ai/v1/edit/completions
POST https://api.inceptionlabs.ai/v1/apply/completions
```

The documented context window is 32K tokens for FIM autocomplete and next edit.
The API parameter page also documents Apply Edit defaults for Mercury Edit 2.
Current documented pricing is `$0.25 / 1M input tokens`, `$0.025 / 1M cached
input tokens`, and `$0.75 / 1M output tokens`.

## Authentication

All requests require a bearer token:

```http
Authorization: Bearer <INCEPTION_API_KEY>
Content-Type: application/json
```

For a JetBrains plugin, do not store the API key in a project file. Store it in
JetBrains' credential storage / PasswordSafe, and optionally allow an
environment variable fallback such as:

```text
INCEPTION_API_KEY
```

Zed's Mercury integration also supports an environment variable named
`MERCURY_AI_TOKEN`, but that is a Zed convention, not the primary Inception docs
convention.

## Choosing The Endpoint

Use the FIM endpoint for classic inline autocomplete where the model inserts
text at the cursor using a prefix and suffix.

Use the next-edit endpoint when the model should predict a replacement for an
editable region. This supports edits before and after the cursor inside that
region, not just insertion at the cursor.

Use the apply-edit endpoint when the user already has an explicit update snippet
and wants the model to merge it into a larger original code block. This is more
like "apply this patch-like snippet intelligently" than prediction.

For a JetBrains plugin that wants both capabilities, it is reasonable to start
with:

1. `fim/completions` for low-latency single-location autocomplete.
2. `edit/completions` for next edit prediction / multi-line region replacement.

## Common Parameters

These parameters apply across `/v1/fim/completions`, `/v1/edit/completions`,
and `/v1/apply/completions`. The endpoint sections below list the documented
default value for each one.

| Parameter | Range | Notes |
| --- | --- | --- |
| `max_tokens` | `1`-`8192` | Output token cap. For next edit, output size is mostly bounded by the editable region, so size this against the region instead of using the documented `8192` default. |
| `temperature` | `0.0`-`1.0` | |
| `top_p` | `0.0`-`1.0` | |
| `presence_penalty` | `0.0`-`2.0` | |
| `stop` | up to 4 sequences | Useful for FIM to terminate at a line or block boundary. Do not set `stop` for next edit; the model must be free to emit blank lines and closing tokens inside the editable region. |
| `stream` | bool | Defaults to `false`. SSE is supported, but Zed's working client uses `stream: false` and reads the response with a single `read_to_end`. Recommended starting point for a JetBrains client. |
| `stream_options.include_usage` | bool | Only meaningful when `stream: true`. Adds a final SSE chunk that contains the `usage` block; without it, streamed responses have no token counts. |

`frequency_penalty`, `n`, and `diffusing` are documented for Mercury 2 chat
completions but not for Mercury Edit 2. Do not send them.

### `max_tokens` vs `max_completion_tokens`

Inception's docs use `max_tokens`. OpenAI's newer chat-completions schema uses
`max_completion_tokens`. The Mercury edit endpoint accepts the OpenAI-style
field in practice — Zed's wire struct serializes `max_completion_tokens` (with
`None` in current code) and the request still works. Pick one and stay
consistent. This document uses `max_tokens` to match Inception's docs.

## FIM Autocomplete Endpoint

Endpoint:

```text
POST https://api.inceptionlabs.ai/v1/fim/completions
```

Minimal request:

```json
{
  "model": "mercury-edit-2",
  "prompt": "def fibonacci(",
  "suffix": "return a + b"
}
```

Meaning:

- `prompt`: code before the cursor.
- `suffix`: code after the cursor.
- The model returns text to insert between `prompt` and `suffix`.

Useful optional parameters from Inception's Mercury Edit 2 parameter docs:

```json
{
  "max_tokens": 512,
  "temperature": 0.0,
  "top_p": 1.0,
  "presence_penalty": 1.5,
  "stop": ["\n\n"],
  "stream": false,
  "stream_options": {
    "include_usage": true
  }
}
```

Documented defaults for autocomplete are:

- `max_tokens`: `512`
- `temperature`: `0.0`
- `top_p`: `1.0`
- `presence_penalty`: `1.5`

For editor autocomplete, keep `max_tokens` low at first, often in the 64-256
range, then increase for multi-line suggestions. Low output limits reduce tail
latency and make cancellation less painful.

Example cURL:

```bash
curl https://api.inceptionlabs.ai/v1/fim/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $INCEPTION_API_KEY" \
  -d '{
    "model": "mercury-edit-2",
    "prompt": "def fibonacci(",
    "suffix": "return a + b",
    "max_tokens": 128,
    "temperature": 0.0
  }'
```

## Next Edit Endpoint

Endpoint:

```text
POST https://api.inceptionlabs.ai/v1/edit/completions
```

The request is chat-shaped, but it is not sent to the normal chat endpoint. It
is sent to the dedicated edit endpoint.

Minimal request shape:

```json
{
  "model": "mercury-edit-2",
  "messages": [
    {
      "role": "user",
      "content": "<next edit prompt>"
    }
  ],
  "max_tokens": 8192,
  "temperature": 0.3,
  "top_p": 0.8,
  "presence_penalty": 1.0,
  "stream": false
}
```

Documented defaults for next edit are:

- `max_tokens`: `8192`
- `temperature`: `0.3`
- `top_p`: `0.8`
- `presence_penalty`: `1.0`

For an interactive editor plugin, do not blindly use `8192` output tokens. The
model returns a full replacement for the editable region, so output token usage
is mostly controlled by the size of that region. Keep the editable region small
and set a practical `max_tokens` ceiling.

## Next Edit Prompt Format

Mercury Edit expects the user message content to contain three top-level
sections:

1. Recently viewed code snippets.
2. Current file content with a marked editable region.
3. Time-ordered edit history in unified diff format.

Use the special tags exactly.

### Full Skeleton

```text
<|recently_viewed_code_snippets|>
<|recently_viewed_code_snippet|>
code_snippet_file_path: path/to/file_1.ext
...snippet text...
<|/recently_viewed_code_snippet|>

<|recently_viewed_code_snippet|>
code_snippet_file_path: path/to/file_2.ext
...snippet text...
<|/recently_viewed_code_snippet|>
<|/recently_viewed_code_snippets|>

<|current_file_content|>
current_file_path: path/to/current_file.ext
...code above editable region...
<|code_to_edit|>
...editable region before cursor...<|cursor|>...editable region after cursor...
<|/code_to_edit|>
...code below editable region...
<|/current_file_content|>

<|edit_diff_history|>
--- path/to/file_1.ext
+++ path/to/file_1.ext
@@ -10,3 +10,4 @@
 unchanged line
-old line
+new line

--- path/to/current_file.ext
+++ path/to/current_file.ext
@@ -40,2 +40,2 @@
-previous user edit
+latest user edit
<|/edit_diff_history|>
```

Note: the unified diff file header uses `---` for the old file and `+++` for
the new file. The plus signs in the `+++` lines are part of unified diff syntax,
not Markdown.

### Empty Sections

If there are no recently viewed snippets, still include the empty wrapper:

```text
<|recently_viewed_code_snippets|>

<|/recently_viewed_code_snippets|>
```

If there is no edit history, still include:

```text
<|edit_diff_history|>

<|/edit_diff_history|>
```

### Recently Viewed Snippets

Public docs recommend 3-5 snippets, each around 20 lines, centered around code
the user recently viewed. These should be focused excerpts rather than large
files.

Each snippet is formatted as:

```text
<|recently_viewed_code_snippet|>
code_snippet_file_path: path/to/file.ext
...snippet text...
<|/recently_viewed_code_snippet|>
```

Important details:

- Prefer fresh text from the current editor/project state.
- Stale snippets can make the model suggest reverting recent changes.
- Full declarations, signatures, docstrings, and local type definitions are all
  useful snippet material.
- Paths should be stable and readable. Use project-relative paths where
  possible, or absolute paths if that is the convention used by the client.

### Current File Content

The current file section is formatted as:

```text
<|current_file_content|>
current_file_path: path/to/current_file.ext
...code above editable region...
<|code_to_edit|>
...editable region code with <|cursor|> inserted...
<|/code_to_edit|>
...code below editable region...
<|/current_file_content|>
```

The model returns an updated version of only the editable region, not the whole
file. The plugin must replace the original editable region with the returned
text, then compute/display the resulting editor diff.

The cursor marker is:

```text
<|cursor|>
```

Put it exactly at the logical cursor offset inside the editable region.

Official prompting guidance says to pass the entire current file when practical
so imports, declarations, and surrounding functions are available. For very
large files, trim distant content while preserving the editable region and
surrounding context.

### Editable Region

The editable region directly controls output size and latency. Inception's docs
recommend starting with 10-15 lines, roughly 100-150 tokens, around the cursor.
A simple baseline is:

```text
[currentLine - 5, currentLine + 10]
```

The docs also mention an upper bound around 25 lines / 250 tokens as a practical
interactive size, depending on network latency.

For a first JetBrains implementation:

- Make editable-region size configurable.
- Start small.
- Keep the region line-aligned.
- Preserve exact text, indentation, and line endings.
- Store the original editable-region range and text so the response can be
  applied safely only if the document has not changed incompatibly.

### Edit History

The edit history section is formatted as unified diffs:

```text
<|edit_diff_history|>
--- path/to/file.ext
+++ path/to/file.ext
@@ -oldStart,oldLen +newStart,newLen @@
-old text
+new text

--- path/to/another_file.ext
+++ path/to/another_file.ext
@@ -oldStart,oldLen +newStart,newLen @@
-old text
+new text
<|/edit_diff_history|>
```

Important ordering rule:

- The bottommost edit should be the most recent edit.
- In other words, order edits chronologically from oldest to newest.

Official docs recommend at least the last 3-5 user edits. If the user made many
small edits in the same area, combine them into one range-based diff rather than
sending many tiny diffs. The edit history is one of the strongest intent signals
for next edit.

## Response Shape And Parsing

The edit endpoint behaves like an OpenAI-style chat completion in Zed's current
client:

```json
{
  "id": "request-id",
  "object": "...",
  "created": 123,
  "model": "mercury-edit-2",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "```\nreplacement editable region\n```"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 1234,
    "completion_tokens": 100,
    "total_tokens": 1334
  }
}
```

The public next-edit docs say Mercury returns the updated editable region
enclosed in triple backticks. Zed also handles responses by stripping a leading
````text
```
````

and trailing

````text
```
````

wrapper before diffing the returned text against the editable region.

Zed also treats exact output:

```text
None
```

as "no prediction." That sentinel is observed in Zed's client, not emphasized in
the public docs. A new plugin should support it defensively.

Recommended parser behavior:

1. Read `choices[0].message.content`.
2. Trim only the surrounding Markdown code fence if present.
3. If the remaining text is exactly `None`, treat the response as no suggestion.
4. Otherwise, treat the text as the full replacement for the original editable
   region.
5. Compute a diff from original editable-region text to returned text.
6. Present only meaningful changes.
7. Drop the response if the document changed in a way that invalidates the saved
   editable-region range.

Do not ask the model to return a patch unless you have verified that the endpoint
handles that reliably. The documented contract is replacement text for the
editable region.

## Apply Edit Endpoint

Endpoint:

```text
POST https://api.inceptionlabs.ai/v1/apply/completions
```

Apply Edit is useful when the editor has:

- the original code block, and
- a proposed update snippet that should be merged into it.

The documented page currently shows `model: "mercury-edit"` while the rest of
the current Mercury Edit 2 docs use `model: "mercury-edit-2"`. Before building
against Apply Edit, verify the accepted model id with the live API or Inception.
For FIM and Next Edit, use the documented `mercury-edit-2`.

Request shape:

```json
{
  "model": "mercury-edit-2",
  "messages": [
    {
      "role": "user",
      "content": "<|original_code|>\n...original code...\n<|/original_code|>\n\n<|update_snippet|>\n// ... existing code ...\n...updated code...\n// ... existing code ...\n<|/update_snippet|>"
    }
  ],
  "max_tokens": 8192,
  "temperature": 0.0,
  "top_p": 1.0,
  "presence_penalty": 0.0,
  "stream": false
}
```

Documented defaults for apply edit are:

- `max_tokens`: `8192`
- `temperature`: `0.0`
- `top_p`: `1.0`
- `presence_penalty`: `0.0`

Prompt format:

```text
<|original_code|>
{original_code}
<|/original_code|>

<|update_snippet|>
// ... existing code ...
[UPDATED CODE SNIPPET 1]
// ... existing code ...
[UPDATED CODE SNIPPET 2]
// ... existing code ...
<|/update_snippet|>
```

Use Apply Edit for explicit user/agent edits, not for ambient autocomplete. For
inline prediction, FIM and Next Edit are the relevant endpoints.

## Zed Integration Observations

These points are useful because Zed has a working Mercury provider, but they are
client implementation details rather than the public API contract.

Current local Zed source calls:

```text
https://api.inceptionlabs.ai/v1/edit/completions
```

with this request shape:

```json
{
  "model": "mercury-coder",
  "messages": [
    {
      "role": "user",
      "content": "<formatted next-edit prompt>"
    }
  ],
  "stream": false
}
```

The public docs now use `mercury-edit-2`. Treat `mercury-coder` as a legacy
alias or stale integration detail unless Inception confirms otherwise.

Zed's next-edit prompt is built from:

- Related/recent snippets, using the same `<|recently_viewed_code_snippets|>`
  section.
- A current-file excerpt, not always the entire file.
- A syntax-aware editable region around the cursor.
- Recent user edit history as unified diffs.

Zed marks accepted predictions in the edit history with:

```text
// User accepted prediction:
```

before the accepted-prediction diff. This gives the model a signal that a
previous suggestion matched the user's intent.

Zed sends Mercury feedback to an observed endpoint:

```text
POST https://api-feedback.inceptionlabs.ai/feedback
```

Observed body:

```json
{
  "request_id": "<id from completion response>",
  "provider_name": "zed",
  "user_action": "accept",
  "provider_version": "<zed app version>"
}
```

Possible `user_action` values observed in Zed:

```text
accept
reject
ignore
```

The feedback request sends only `Content-Type: application/json` — there is no
`Authorization` header. Zed fires the call without awaiting the response and
logs any error rather than surfacing it. Treat feedback as best-effort
telemetry, not a critical-path dependency.

This feedback endpoint is not part of the main public API docs used for this
document. Do not depend on it for core functionality without confirming it with
Inception.

## Error Handling

Documented error handling:

- `401`: incorrect API key. Ask the user to re-enter or regenerate the key.
- `429`: rate limit reached. Back off and retry later.
- `500`: server error. Retry after a short delay.
- `503`: engine overloaded. Retry after a short delay.

Zed also treats HTTP `402 Payment Required` specially for Mercury and displays a
free-tier/payment-required state. A JetBrains plugin should handle `402`
gracefully even if it is not listed in the public error-code table.

On `402`, Mercury returns a JSON body of the form:

```json
{
  "error": {
    "message": "Human-readable explanation, typically a free-tier or payment notice."
  }
}
```

Surface `error.message` to the user when present. Fall back to the raw body
text if the JSON does not parse.

Recommended behavior:

- Use short request timeouts for interactive autocomplete.
- Cancel stale in-flight requests when the document changes or cursor moves.
- Use exponential backoff for `429`, `500`, and `503`.
- Do not retry immediately on `401` or `402`.
- Log status code and response body for diagnostics, but redact the API key and
  avoid logging source code unless the user opts into debug logging.

## Rate Limits And Throttling

Current documented per-minute rate limits:

| Tier | Requests | Input Tokens | Output Tokens |
| --- | ---: | ---: | ---: |
| Free | 100 | 100,000 | 10,000 |
| Pay As You Go | 1,000 | 1,000,000 | 100,000 |
| Enterprise | 10,000+ | 10,000,000+ | 1,000,000+ |

An editor plugin can hit these limits quickly if it sends a request on every
keystroke. Implement client-side controls:

- Debounce typing before sending.
- Throttle requests per file.
- Cancel stale requests.
- Coalesce small edit-history events.
- Avoid sending requests while indexing, refactoring, or applying large edits.
- Disable or slow down requests in very large files.
- Skip binary, generated, secret, or minified files.

## Implementation Notes For A JetBrains Plugin

### Data Classes

FIM request:

```kotlin
data class MercuryFimRequest(
    val model: String = "mercury-edit-2",
    val prompt: String,
    val suffix: String,
    val max_tokens: Int? = null,
    val temperature: Double? = null,
    val top_p: Double? = null,
    val presence_penalty: Double? = null,
    val stop: List<String>? = null,
    val stream: Boolean? = false
)
```

Next edit request:

```kotlin
data class MercuryEditRequest(
    val model: String = "mercury-edit-2",
    val messages: List<MercuryMessage>,
    val max_tokens: Int? = null,
    val temperature: Double? = null,
    val top_p: Double? = null,
    val presence_penalty: Double? = null,
    val stop: List<String>? = null,
    val stream: Boolean? = false
)

data class MercuryMessage(
    val role: String,
    val content: String
)
```

Response skeleton:

```kotlin
data class MercuryChatResponse(
    val id: String?,
    val model: String?,
    val choices: List<MercuryChoice> = emptyList(),
    val usage: MercuryUsage? = null
)

data class MercuryChoice(
    val index: Int?,
    val message: MercuryMessage?,
    val finish_reason: String?
)

data class MercuryUsage(
    val prompt_tokens: Int?,
    val completion_tokens: Int?,
    val total_tokens: Int?
)
```

The FIM endpoint may use a compatible completion-style response. Verify against
the live API before hard-coding one parser for both endpoints. For robustness,
write response parsing that can extract text from common fields:

- `choices[0].message.content`
- `choices[0].text`
- a top-level text field, if Inception adds one later

### Applying Next Edit Safely

When sending a next-edit request, capture:

- Document identity.
- Document modification stamp.
- Editable region start/end offsets.
- Original editable region text.
- Cursor offset within the editable region.
- Prompt string used for the request.

When the response returns:

1. Verify the same document is still open.
2. Verify the modification stamp or rebase the saved range.
3. Strip optional code fences.
4. Treat `None` as no suggestion.
5. Compute the diff between original editable-region text and response text.
6. Suppress empty diffs.
7. Render the replacement as an inline ghost edit or diff preview.
8. On accept, replace only the saved editable region.

If the file changed too much while the request was in flight, discard the result
instead of applying it to a stale offset.

### Message Builder Pseudocode

```kotlin
fun buildNextEditPrompt(
    snippets: List<Snippet>,
    currentFilePath: String,
    codeAboveEditableRegion: String,
    editableBeforeCursor: String,
    editableAfterCursor: String,
    codeBelowEditableRegion: String,
    editHistoryDiffsOldestToNewest: List<String>
): String = buildString {
    appendLine("<|recently_viewed_code_snippets|>")
    for (snippet in snippets) {
        appendLine("<|recently_viewed_code_snippet|>")
        append("code_snippet_file_path: ")
        appendLine(snippet.path)
        append(snippet.text)
        if (!snippet.text.endsWith("\n")) appendLine()
        appendLine("<|/recently_viewed_code_snippet|>")
        appendLine()
    }
    appendLine("<|/recently_viewed_code_snippets|>")
    appendLine()

    appendLine("<|current_file_content|>")
    append("current_file_path: ")
    appendLine(currentFilePath)
    append(codeAboveEditableRegion)
    appendLine("<|code_to_edit|>")
    append(editableBeforeCursor)
    append("<|cursor|>")
    append(editableAfterCursor)
    if (!editableAfterCursor.endsWith("\n")) appendLine()
    appendLine("<|/code_to_edit|>")
    append(codeBelowEditableRegion)
    if (!codeBelowEditableRegion.endsWith("\n")) appendLine()
    appendLine("<|/current_file_content|>")
    appendLine()

    appendLine("<|edit_diff_history|>")
    for (diff in editHistoryDiffsOldestToNewest) {
        append(diff)
        if (!diff.endsWith("\n")) appendLine()
        appendLine()
    }
    appendLine("<|/edit_diff_history|>")
}
```

### Strip Code Fence Pseudocode

```kotlin
fun cleanNextEditOutput(raw: String): String? {
    var text = raw.trimEnd()

    if (text.startsWith("```\n")) {
        text = text.removePrefix("```\n")
    } else if (text.startsWith("```")) {
        val newline = text.indexOf('\n')
        if (newline >= 0) text = text.substring(newline + 1)
    }

    if (text.endsWith("\n```")) {
        text = text.removeSuffix("\n```")
    } else if (text.endsWith("```")) {
        text = text.removeSuffix("```")
    }

    if (text == "None") return null
    return text
}
```

Do not aggressively trim leading whitespace from model output. Whitespace is
code.

## Practical Defaults For A First Plugin

FIM:

```json
{
  "model": "mercury-edit-2",
  "max_tokens": 128,
  "temperature": 0.0,
  "top_p": 1.0,
  "presence_penalty": 1.5,
  "stream": false
}
```

Next edit:

```json
{
  "model": "mercury-edit-2",
  "max_tokens": 512,
  "temperature": 0.3,
  "top_p": 0.8,
  "presence_penalty": 1.0,
  "stream": false
}
```

Prompt sizing:

- Current file: full file when small, otherwise trimmed.
- Editable region: start with 10-15 lines.
- Recently viewed snippets: 3-5 snippets, roughly 20 lines each.
- Edit history: last 3-5 coalesced user edits, oldest first and newest last.

Network behavior:

- Debounce request after typing.
- Cancel stale requests.
- Do not issue more than one active FIM request per editor.
- Allow at most one or two next-edit requests per editor.
- Back off after rate limits or overload.

## Important Distinctions

- FIM returns inserted text.
- Next edit returns replacement text for the editable region.
- Apply edit merges an explicit update snippet into original code.
- The next-edit endpoint uses a chat-shaped request, but not the chat endpoint.
- Mercury Edit 2 uses `mercury-edit-2` in current public docs.
- Zed's production client sends `mercury-coder` against the same
  `/v1/edit/completions` endpoint and it works. Both ids appear to route to the
  same backend. Prefer `mercury-edit-2` for new code, but it is fine to test
  both before committing.
- The next-edit prompt format is part of the model contract. The special tags
  matter.
- The final diff-history item should be the user's most recent edit.
- Do not trim significant whitespace from code responses.

## References

- Inception Next Edit docs:
  https://docs.inceptionlabs.ai/capabilities/next-edit
- Inception Autocomplete / FIM docs:
  https://docs.inceptionlabs.ai/capabilities/fim
- Inception Apply Edit docs:
  https://docs.inceptionlabs.ai/capabilities/apply-edit
- Inception API parameters:
  https://docs.inceptionlabs.ai/get-started/api-parameters
- Inception models, endpoints, pricing:
  https://docs.inceptionlabs.ai/get-started/models
- Inception authentication:
  https://docs.inceptionlabs.ai/get-started/authentication
- Inception rate limits:
  https://docs.inceptionlabs.ai/get-started/rate-limits
- Inception error codes:
  https://docs.inceptionlabs.ai/resources/error-codes
- Local Zed Mercury client:
  `C:\Users\k_zakharchanka\IdeaProjects\freddie\zed\crates\edit_prediction\src\mercury.rs`
