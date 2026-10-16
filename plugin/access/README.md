# Restricted access credential store

This module is the credential/enrollment persistence layer, **not a complete authorization boundary**. The integrating plugin must authorize owner-management calls, validate that the assigned agent exists and is appropriate for restricted users, enforce authenticated device ownership on every conversation/history/event/cancellation/audio operation, and never accept a client-supplied identity or unrestricted operator credential as a substitute.

## API

```js
import { AccessStore, AccessError } from './access/store.js';
const store = await new AccessStore({ stateDir: '/absolute/private/voxkin-access' }).initialize();
const invite = await store.createInvite({ agentId: 'household-helper', label: 'Family phone' });
// Deliver invite.inviteSecret once through a private QR/share invitation.
const pending = await store.redeemInvite({ secret: invite.inviteSecret });
// Deliver pending.deviceSecret once to the enrolling phone over TLS; keep in Keychain.
// A separately authenticated owner checks the joining phone before approval.
await store.approveDevice({ deviceId: pending.id });
const identity = store.authenticate(pending.deviceSecret);
// identity = { deviceId, agentId, label }, entirely derived from server records.
await store.revokeDevice({ deviceId: pending.id });
await store.close();
```

- `initialize()` / all mutations / `close()` are asynchronous.
- `authenticate(secret)` and `list()` are synchronous after initialization.
- `createInvite({agentId,label,ttlSeconds?})`: returns `{id, agentId, label, createdAt, expiresAt, inviteSecret}`. Default TTL 600 seconds, maximum 3600 seconds.
- `redeemInvite({secret})`: atomically consumes invitation, returns `{id, agentId, label, createdAt, expiresAt, status:'pending', deviceSecret}`. Pending approval expires after 3600 seconds. Pending credentials cannot authenticate.
- `approveDevice({deviceId})`: owner operation returning public active device record. An already active device cannot be approved again. Active credentials do not expire automatically; explicit revocation remains available.
- `revokeDevice({deviceId})`: owner operation returning public revoked record. After the promise resolves, subsequent authentication fails. Integrators must also disconnect associated streams; this does not automatically cancel work already started.
- `cancelInvite({inviteId})`: owner operation returning `{id,cancelled:true}`.
- `list()`: owner-only `{invites,devices}` records, with no credential hashes or secrets. Expired pending records/invitations are omitted. Revoked records may be pruned on subsequent mutation; this is not an audit log.
- `authenticate(secret)`: returns `{deviceId,agentId,label}` or throws `AccessError('unauthorized')`.
- Errors have a non-secret `code`; avoid forwarding arbitrary internal filesystem errors. Common codes: `invalid_input`, `invalid_invite`, `unauthorized`, `device_not_found`, `invalid_device_state`, `invite_not_found`, `invite_capacity`, `device_capacity`, `not_ready`, `state_locked`, `storage_error`, `unsafe_state_dir`, `unsafe_state_file`, `invalid_state`.

Optional constructor `limits`: `maxInvites` (100), `maxDevices` (100), `inviteTtlSeconds` (600), `maxInviteTtlSeconds` (3600), `pendingTtlSeconds` (3600). Hard bounds are 10,000 records and 86,400 seconds. A test clock can be supplied as `now: () => milliseconds`.

## Persistence and lifecycle

Use a dedicated absolute state directory on a local filesystem owned by the gateway user, mode `0700`. The module creates it if absent and rejects permissive existing directories. Files use `0600`; secrets use 256 bits of OS cryptographic randomness and only SHA-256 hashes are persisted. Randomness makes password-style slow hashing unnecessary. Raw secrets are returned only at issuance and must never be logged or placed in analytics.

Mutations are serialized. State is copied, written to an exclusive temporary file, fsynced, then atomically renamed; live state is replaced only after the write succeeds. Failed persistence does not claim successful enrollment or revocation. Concurrent redemption of an invitation succeeds at most once. Lost issuance responses are not recoverable: the owner should revoke the pending record and create another invite.

A lifetime exclusive `writer.lock` prevents concurrent stores/processes from owning the directory. Call `close()` during plugin shutdown and stop accepting requests before closing. **After an unclean shutdown**, startup intentionally fails closed if the lock remains: stop/verify the previous gateway process is gone, then remove only the stale `writer.lock` and restart. Never remove a live owner's lock. This implementation does not support shared/network filesystems, replicas, or multiple gateway writers. Atomic file replacement protects normal write failures; it is not a replicated or transactional database and does not promise durability through every filesystem/hardware power-loss scenario.

Do not redeem invitations on GET requests, previews, QR rendering, or link-open callbacks. Require a deliberate enrollment POST/action. Apply HTTP body limits and rate limiting outside this module; use TLS for all credential traffic. Never persist raw invitation URLs or bearer headers in logs.

## Tests

```sh
node --test access/store.test.js
```

Tests cover pending approval, assignment binding, revocation, concurrent single use, hash-only persistence, restart reload, file modes, expiration/cancellation, bounded capacity, persistence failures, invalid inputs, credential-domain separation, and exclusive ownership/symlink rejection.
