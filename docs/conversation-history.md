# Gateway-backed conversation history — 0.4.1 candidate

## Use

Connect, then open **History**. Select a conversation to read it or **Continue in
Talk** to resume it. **New conversation** starts a separate agent context. Opening
history does not speak, record, or resend a message. The last selected conversation
is restored on reconnect; failed reads cannot become sendable selections.

Only Voxkin conversations for the selected gateway/agent are listed. Owner access
uses normal gateway authorization (agent selection is not a permission boundary).
Restricted phones use authenticated device+agent ownership and server-generated
conversation IDs; the plugin never proxies operator history RPCs.

## Names, times, and swipe actions

History shows an abbreviated date and local time. Swipe **left** on a row for
**Rename** and **Delete**; long-press exposes the same actions. Full-swipe deletion
is disabled. Delete asks for confirmation; Cancel leaves the conversation intact.
Actions are unavailable during an active turn or history operation. Restricted
phones need the 0.4.1 plugin capability before these actions are enabled.

Automatic names are short excerpts, not an additional AI/model request. Throwaway
hello/test turns are skipped when a meaningful question follows. Restricted titles
use the first three user turns. Owner lists backfill from a bounded recent preview;
a meaningful name is saved as `autoLabel` after at least two user messages are
available, then kept stable. Existing automatic names and explicit manual names
win over later previews. Manual names (up to 80 Unicode codepoints) live on the
gateway; owner labels must be unique. Rename errors do not optimistically replace
the displayed title. Gateway metadata updates, including naming, can update the
shown modification time and list order; the time is not strictly last-message time.

Owner deletion uses identity-guarded archive-then-delete with ordinary read/write
permissions, not elevated admin access. If deletion fails, the app attempts to
restore the archived row; if the outcome is unknown it says to refresh rather than
replaying the mutation. A successful delete resets the selected conversation and
refreshes pagination so older rows remain reachable. No real history is deleted
by development tests.

## Bounded loading

- Owner: `sessions.list` scoped search + exact UUID namespace validation, 20 rows
  per request, plus at most one `sessions.preview` batch for unnamed rows (eight
  messages per row, max800 characters per message). A truncated Voxkin preference
  wrapper triggers one bounded `chat.history` fallback for that unnamed row
  (eight messages, max32,000 response bytes), so hidden speech guidance cannot
  consume the title preview. Read selected `chat.history` in pages of 50,
  max128,000 response bytes.
- Restricted: 20 conversation summaries, 50 sanitized text messages per page.
  List is newest-created first; messages use stable before-ID cursors.
- Older pages load only on demand. UI lists deduplicate IDs. No all-history startup
  download, external archiver, audio archive, Discord integration, or app backend.
- Only the chosen conversation identifier is saved locally; text remains in memory
  while displayed. Preferences are scoped by endpoint, agent, and restricted device.

## Retention and limits

Owner history follows OpenClaw retention. Restricted history stores user text and
successful final replies in private `history.json` under the access state directory,
using atomic writes and the existing state-directory lock. It excludes tool results,
reasoning, connection credentials and raw audio. Text you choose to say or send
can still contain sensitive information. This is a separate sanitized archive from the
agent's own gateway transcript. App reply-preference wrappers are removed.

Restricted archive limits: 4MiB per device, 32MiB aggregate. Capacity exhaustion
rejects new writes; it never silently deletes older conversations. Delete removes
an owned conversation from the restricted archive and clears its settled polling
buffers. It does **not** erase the underlying agent transcript or backups. Owner
gateway deletion also retains transcript archives under gateway policy; neither
flow promises secure erasure. There is no retention-policy editor. Revoking access
prevents future reads but does not erase records. See `plugin/restricted/HISTORY.md`.

## Boundaries

No legacy session discovery or credential migration. History access is online only;
there is no durable offline transcript cache. Disconnect/agent change invalidates
pending requests. Loading a message page never calls TTS. Failed/aborted user inputs
can remain recorded even when no assistant answer was produced.
