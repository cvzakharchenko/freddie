# Mercury-Compatible Context From Sweep

This document describes the subset of Sweep JetBrains plugin context that maps
cleanly onto Mercury Edit 2's documented next-edit prompt format.

The goal for a first implementation is stability and predictability. Mercury
should receive only context that fits its trained sections:

```text
<|recently_viewed_code_snippets|>
<|current_file_content|>
<|code_to_edit|>
<|cursor|>
<|edit_diff_history|>
```

This document intentionally avoids custom tags and excludes Sweep signals that
do not fit Mercury's expected prompt shape.

## Recommended First-Version Context

The stable subset is:

1. Current file context.
2. Editable region with cursor.
3. Recent edit diff history.
4. Recently viewed and related code snippets.

This subset preserves the highest-value Sweep signals while staying within
Mercury's native prompt contract.

## Current File Context

Sweep sends the current editor as structured JSON:

```json
{
  "file_path": "relative/path/to/current/file.ext",
  "file_contents": "full current editor document text",
  "cursor_position": 1234
}
```

Mercury needs the same information, but represented as text inside the current
file section:

```text
<|current_file_content|>
current_file_path: path/to/current/file.ext
...code above editable region...
<|code_to_edit|>
...editable text before cursor...<|cursor|>...editable text after cursor...
<|/code_to_edit|>
...code below editable region...
<|/current_file_content|>
```

Keep these Sweep inputs:

- Current project-relative file path.
- Current file text, including unsaved editor changes.
- Current cursor offset.

Transform them into:

- `current_file_path: ...`
- the current file content as plain code text
- a line-aligned `<|code_to_edit|>` block
- an embedded `<|cursor|>` marker at the current caret position

For a first implementation:

- Send the full current file when the file is small enough.
- For large files, trim distant content while preserving the editable region,
  surrounding function/class context, imports when useful, and nearby code.
- Keep the editable region small enough for interactive latency.
- Preserve exact whitespace and line endings.
- Use project-relative paths when possible.

## Editable Region

Sweep sends a raw cursor offset and lets its backend decide what to predict.
Mercury instead predicts a replacement for a specific editable region.

The editable region is the only part of the current file that Mercury should
rewrite. The response should be interpreted as the replacement text for this
region, not as a patch and not as a full-file rewrite.

Recommended first-version behavior:

- Choose a line-aligned region around the cursor.
- Start with roughly 10-15 lines.
- Allow a configurable maximum around 25 lines.
- A simple initial range is current line minus 5 lines through current line plus
  10 lines.
- Ensure the cursor marker is inside this region.
- Store the original region offsets and text when the request is sent.
- Discard the response if the document changes in a way that invalidates the
  saved range.

The editable region should include enough local structure for the model to make
a meaningful edit, but not so much that output size and latency become large.

## Recent Edit Diff History

Sweep tracks recent user edits as unified diffs. This maps directly to
Mercury's edit history section.

Sweep has two relevant streams:

- `recent_changes`: a smaller, more coalesced set of recent logical edits.
- `recent_changes_high_res`: a larger, more granular trail of recent edits.

Mercury expects one chronological edit history:

```text
<|edit_diff_history|>
--- path/to/file.ext
+++ path/to/file.ext
@@ -10,3 +10,4 @@
 unchanged context
-old line
+new line

--- path/to/current/file.ext
+++ path/to/current/file.ext
@@ -40,2 +40,2 @@
-previous edit
+latest edit
<|/edit_diff_history|>
```

Important ordering rule:

- Put the oldest included diff first.
- Put the newest included diff last.
- The bottommost diff should be the user's most recent edit.

Recommended first-version behavior:

- Do not send both Sweep diff streams verbatim.
- Build a single compact edit history from the best available recent edits.
- Prefer coalesced logical diffs over character-by-character noise.
- Preserve the last few small edits that show user intent.
- Include roughly 3-8 recent diffs.
- Include diffs from other files if they are recent and relevant.
- Skip very large diffs.
- Skip no-op diffs.
- Keep unified diff formatting valid.

If an implementation tracks a baseline copy of the current file, similar to
Sweep's `original_file_contents`, use it to derive meaningful diffs when useful.
Do not send the original file as a separate Mercury section.

## Recently Viewed Code Snippets

Sweep records recently visited cursor locations and selected/open editor chunks.
This maps directly to Mercury's recently viewed snippets section.

Mercury format:

```text
<|recently_viewed_code_snippets|>
<|recently_viewed_code_snippet|>
code_snippet_file_path: path/to/other/file.ext
...snippet text...
<|/recently_viewed_code_snippet|>

<|recently_viewed_code_snippet|>
code_snippet_file_path: path/to/recent/file.ext
...snippet text...
<|/recently_viewed_code_snippet|>
<|/recently_viewed_code_snippets|>
```

Keep these Sweep inputs:

- Recent cursor-position chunks from files the user inspected.
- Visible chunks from other selected editor splits.

Recommended first-version behavior:

- Use real project file snippets only.
- Prefer fresh text from the current editor/project state.
- Center recent-view snippets around the line the user inspected.
- Use smaller snippets than Sweep's original 200-line chunks.
- Start with roughly 20-60 lines per snippet.
- Deduplicate overlapping snippets.
- Skip snippets that duplicate the current editable region.
- Usually skip the current cursor chunk in the current file, since the current
  file section already contains it.
- Use project-relative paths when possible.

These snippets preserve the Sweep signal of "what the user just looked at"
without requiring any custom Mercury prompt sections.

## Related Definitions And Usages

Sweep also gathers semantically relevant code:

- Definitions for symbols near the cursor.
- Usage snippets for terms on or near the current line.

These are not literally "recently viewed" by the user, but they are related
code snippets. They fit Mercury's existing snippet section well enough to keep
in the first version, without inventing new tags.

Represent them exactly like normal recently viewed snippets:

```text
<|recently_viewed_code_snippet|>
code_snippet_file_path: path/to/definition.ext
...definition or usage excerpt...
<|/recently_viewed_code_snippet|>
```

Do not label them with custom tags. The file path and code content should be
enough.

Recommended first-version priority:

1. Definitions of symbols on or near the current line.
2. Usage examples of the same symbols or patterns.
3. Files the user recently viewed.
4. Other selected editor split snippets.

Definitions and usages are likely one of the most valuable parts of Sweep's
context strategy because they tell the model what nearby names mean and how
similar code is written elsewhere in the project.

## Snippet Ranking And Budgeting

Mercury has a documented 32K-token context window, but an editor plugin should
not fill it by default. Larger prompts increase latency, cost, and the chance of
irrelevant context distracting the model.

Suggested first-version ranking:

1. Current file and editable region are mandatory.
2. Recent edit history is the strongest intent signal.
3. Definitions near the cursor.
4. Usage snippets from matching file types.
5. Recently viewed files.
6. Visible snippets from other selected editor splits.

Suggested first-version limits:

- Current file: full file when small, otherwise trimmed.
- Editable region: 10-25 lines.
- Edit history: 3-8 coalesced diffs.
- Snippets: 3-8 total snippets.
- Snippet size: around 20-60 lines each.

If the prompt needs trimming, trim in this order:

1. Remove lower-ranked snippets.
2. Shorten snippet ranges.
3. Reduce edit history to the most recent meaningful diffs.
4. Trim distant current-file content while preserving the editable region.

Avoid trimming inside the editable region unless absolutely necessary.

## Context To Exclude In The First Version

Do not include these Sweep fields in the first Mercury implementation:

- IDE completion dropdown contents.
- Clipboard text.
- Editor diagnostics.
- Structured `recent_user_actions`.
- `multiple_suggestions`.
- `changes_above_cursor`.
- `privacy_mode_enabled`.
- `ping`.
- `debug_info`.
- `device_id`.
- `client_ip`.
- `repo_name`.
- `branch`.
- Sweep-specific request headers.

Reasons:

- They do not map cleanly to Mercury's documented next-edit sections.
- Some are backend-control or telemetry fields, not model context.
- Some would require custom prompt tags.
- Some may add privacy risk or irrelevant noise.

The useful parts of `recent_user_actions` should be represented indirectly:

- typing, paste, delete, undo, and redo become recent diffs
- cursor movement becomes recently viewed snippets

## Final Prompt Shape

A first-version Mercury prompt built from Sweep-compatible context should look
like this:

```text
<|recently_viewed_code_snippets|>
<|recently_viewed_code_snippet|>
code_snippet_file_path: path/to/definition_or_usage.ext
...definition, usage, recent view, or open editor excerpt...
<|/recently_viewed_code_snippet|>

<|recently_viewed_code_snippet|>
code_snippet_file_path: path/to/recently_viewed.ext
...related code...
<|/recently_viewed_code_snippet|>
<|/recently_viewed_code_snippets|>

<|current_file_content|>
current_file_path: path/to/current/file.ext
...current file content above editable region...
<|code_to_edit|>
...editable text before cursor...<|cursor|>...editable text after cursor...
<|/code_to_edit|>
...current file content below editable region...
<|/current_file_content|>

<|edit_diff_history|>
--- path/to/file.ext
+++ path/to/file.ext
@@ -10,3 +10,4 @@
 unchanged context
-old line
+new line

--- path/to/current/file.ext
+++ path/to/current/file.ext
@@ -40,2 +40,2 @@
-previous edit
+latest edit
<|/edit_diff_history|>
```

This gives Mercury the clearest version of Sweep's most useful context:

- what file the user is editing
- exactly where the cursor is
- what region can be changed
- what the user just changed
- what nearby symbols mean
- how similar code appears elsewhere
- what code the user recently inspected

## Implementation Summary

To reuse Sweep-style context gathering with Mercury:

1. Keep Sweep-like collectors for current file, recent diffs, cursor history,
   open editor snippets, definitions, and usages.
2. Drop collectors that require non-Mercury sections for the first version.
3. Rank, deduplicate, and trim snippets before building the prompt.
4. Convert all relevant snippets into
   `<|recently_viewed_code_snippet|>` blocks.
5. Convert recent edits into one chronological `<|edit_diff_history|>` block.
6. Convert current file text and cursor offset into
   `<|current_file_content|>` with a `<|code_to_edit|>` region.
7. Send the resulting prompt to Mercury's `/v1/edit/completions` endpoint.

This is not a full copy of Sweep's backend request. It is a stable Mercury
translation of the Sweep context that clearly fits the model's documented
next-edit input format.
