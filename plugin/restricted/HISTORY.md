# Restricted phone history

`POST /voxkin/v1` uses the existing approved-device Bearer credential. The server binds every conversation and cursor to the authenticated device and assigned agent; client session keys and agent overrides are rejected.

- `history.create` returns a server-generated conversation UUID and summary.
- `history.list` accepts `before` (conversation UUID) and `limit` (1–20, default 20), returning `conversations` and optional `nextCursor`. Ordering is newest **created**, with UUID tie-breaking; sending to an older conversation does not move its pagination position.
- `history.messages` requires `conversationId`, accepts `before` (message UUID) and `limit` (1–50, default 50), returning chronological `messages` from the latest page and optional `nextCursor` for older messages.
- `history.rename` requires `conversationId` and `title`; returns the updated summary. Whitespace is collapsed and trimmed; blank or longer than 80 Unicode code points is rejected. Manual titles persist and take precedence over automatic excerpts.
- `history.delete` requires `conversationId`; returns `{deleted:true}` after the archive write succeeds. Both actions require ownership by the authenticated device and assigned agent. They return `BUSY` while that conversation has an unsettled run (including an aborted run still draining). Sends are blocked during either mutation. Deletion purges completed polling buffers and cannot be undone through this API.
- `send` requires `conversationId`, `text`, and `requestId`. New conversations get separate gateway context; selecting an existing ID resumes that context.

Summary fields are `id`, `title`, `preview`, `createdAt`, `updatedAt`, plus `customTitle` when explicitly renamed. Message fields are `id`, `role`, `text`, `createdAt`. Dates are epoch milliseconds. `hello` advertises `history:true` and `historyActions:true`. Automatic titles use a whitespace-normalized excerpt (at most 80 Unicode code points) of the first meaningful user message among the first three user turns, skipping simple greetings/test messages when a real topic follows. This is a deterministic excerpt, not an additional model request.

The gateway archives user text and successful final assistant text in private `history.json` beside access state, using the existing lifetime state-directory lock and atomic writes. The app's exact leading Voxkin reply-preference envelope is removed from archived user text, but retained in the dispatched model input. No audio, raw OpenClaw transcripts, tool payloads, or operator RPC proxy is exposed.

Storage is bounded to **4 MiB per device** and **32 MiB for the shared archive**. Reaching either limit fails the write with `BUSY`; existing records are never silently deleted. An input write failure prevents dispatch. A final-output write failure produces an error, never a successful final acknowledgment. An abort during a final write remains aborted. A completed write may remain in history even if cancellation arrives while it is being persisted.

Revoking access prevents subsequent reads but does not erase stored history. **Deleting a restricted conversation removes only its Voxkin archive entry, not the underlying OpenClaw agent session/transcript or backups.** Deleted IDs cannot be resumed or recreated by late archive appends. Gateway operators retain responsibility for underlying-session and backup retention. The limits are defensive prototype bounds, not a configurable retention policy.
